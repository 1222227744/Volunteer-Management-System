package com.volunteer.vms.audit;

import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditLogControllerTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditLogController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        controller = new AuditLogController(auditLogRepository);

        User admin = new User();
        admin.setId(9L);
        admin.setRole(Role.ADMIN);
        admin.setDisplayName("管理员");
        admin.setUsername("admin");
        admin.setPoints(0);

        request = new MockHttpServletRequest();
        request.setAttribute(AuthUtils.CURRENT_USER_ATTR, admin);
    }

    @Test
    void pagedShouldPassRealPagingArgumentsToRepository() {
        AuditLog log = new AuditLog();
        log.setId(1L);
        log.setOperatorId(9L);
        log.setOperatorName("管理员");
        log.setOperatorRole("ADMIN");
        log.setAction("USER_LOGGED_IN");
        log.setTargetType("USER");
        log.setTargetId("9");
        log.setDetail("用户登录成功");
        log.setIpAddress("127.0.0.1");
        log.setCreatedAt(LocalDateTime.of(2026, 5, 20, 12, 30));

        when(auditLogRepository.findAllBySpec(any(), any())).thenReturn(
                new PageImpl<>(List.of(log), PageRequest.of(1, 5), 11)
        );

        ApiResponse<Map<String, Object>> response = controller.paged(
                request,
                "USER_LOGGED_IN",
                null,
                null,
                null,
                null,
                null,
                2,
                5
        );

        assertThat(response.code()).isEqualTo(200);
        assertThat(response.data().get("page")).isEqualTo(2);
        assertThat(response.data().get("size")).isEqualTo(5);
        assertThat(response.data().get("total")).isEqualTo(11L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Specification<AuditLog>> specCaptor = ArgumentCaptor.forClass(Specification.class);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(auditLogRepository).findAllBySpec(specCaptor.capture(), pageableCaptor.capture());

        assertThat(specCaptor.getValue()).isNotNull();
        assertThat(pageableCaptor.getValue().getPageNumber()).isEqualTo(1);
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(5);
        Sort.Order createdAtOrder = pageableCaptor.getValue().getSort().getOrderFor("createdAt");
        assertThat(createdAtOrder).isNotNull();
        assertThat(createdAtOrder.getDirection()).isEqualTo(Sort.Direction.DESC);
    }

    @Test
    void pagedShouldRejectInvalidTimeRange() {
        assertThatThrownBy(() -> controller.paged(
                request,
                null,
                null,
                null,
                null,
                "2026-05-20T12:00",
                "2026-05-20T11:00",
                1,
                20
        ))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizException = (BizException) ex;
                    assertThat(bizException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bizException.getMessage()).isEqualTo("结束时间不能早于开始时间");
                });
    }
}
