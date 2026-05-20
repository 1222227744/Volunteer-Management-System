package com.volunteer.vms.feedback;

import com.volunteer.vms.activity.Activity;
import com.volunteer.vms.activity.ActivityRepository;
import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.service.ServiceRecordRepository;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityFeedbackControllerTest {

    @Mock
    private ActivityFeedbackRepository activityFeedbackRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    @Mock
    private com.volunteer.vms.user.UserRepository userRepository;

    private RecordingAuditLogService auditLogService;

    private ActivityFeedbackController controller;
    private MockHttpServletRequest request;
    private Activity activity;

    @BeforeEach
    void setUp() {
        auditLogService = new RecordingAuditLogService();
        controller = new ActivityFeedbackController(
                activityFeedbackRepository,
                activityRepository,
                serviceRecordRepository,
                userRepository,
                auditLogService
        );

        User user = new User();
        user.setId(21L);
        user.setDisplayName("志愿者甲");
        user.setUsername("volunteer");
        user.setPoints(0);

        request = new MockHttpServletRequest();
        request.setAttribute(AuthUtils.CURRENT_USER_ATTR, user);

        activity = new Activity();
        activity.setId(7L);
        activity.setTitle("社区探访");
        activity.setOrganizerId(30L);
    }

    @Test
    void submitShouldRejectUserWithoutServiceRecord() {
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(serviceRecordRepository.existsByActivityIdAndUserId(7L, 21L)).thenReturn(false);

        assertThatThrownBy(() -> controller.submit(
                request,
                new ActivityFeedbackController.SubmitActivityFeedbackRequest(7L, 5, "组织有序")
        ))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizException = (BizException) ex;
                    assertThat(bizException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bizException.getMessage()).isEqualTo("只有已完成服务记录的志愿者才能评价活动");
                });

        verify(activityFeedbackRepository, never()).save(any());
        assertThat(auditLogService.callCount).isZero();
    }

    @Test
    void submitShouldRejectDuplicateFeedback() {
        when(activityRepository.findById(7L)).thenReturn(Optional.of(activity));
        when(serviceRecordRepository.existsByActivityIdAndUserId(7L, 21L)).thenReturn(true);
        when(activityFeedbackRepository.existsByActivityIdAndUserId(7L, 21L)).thenReturn(true);

        assertThatThrownBy(() -> controller.submit(
                request,
                new ActivityFeedbackController.SubmitActivityFeedbackRequest(7L, 4, "还不错")
        ))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizException = (BizException) ex;
                    assertThat(bizException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bizException.getMessage()).isEqualTo("你已提交过该活动评价");
                });

        verify(activityFeedbackRepository, never()).save(any());
        assertThat(auditLogService.callCount).isZero();
    }

    private static class RecordingAuditLogService extends AuditLogService {
        private int callCount;

        RecordingAuditLogService() {
            super(null);
        }

        @Override
        public void log(HttpServletRequest request,
                        User operator,
                        String action,
                        String targetType,
                        Object targetId,
                        String detail) {
            callCount++;
        }
    }
}
