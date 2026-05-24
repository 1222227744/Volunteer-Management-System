package com.volunteer.vms.notification;

import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.User;
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
class NotificationControllerTest {

    @Mock
    private NotificationRepository notificationRepository;

    @Mock
    private ExternalNotificationTaskRepository externalTaskRepository;

    private ExternalNotificationService externalNotificationService;

    private NotificationController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        externalNotificationService = new ExternalNotificationService(externalTaskRepository, null);
        controller = new NotificationController(notificationRepository, externalTaskRepository, externalNotificationService);

        User user = new User();
        user.setId(5L);
        user.setUsername("volunteer");
        user.setDisplayName("用户甲");
        user.setPoints(0);

        request = new MockHttpServletRequest();
        request.setAttribute(AuthUtils.CURRENT_USER_ATTR, user);
    }

    @Test
    void markReadShouldOnlyReadCurrentUsersNotification() {
        Notification notification = new Notification();
        notification.setId(8L);
        notification.setUserId(5L);
        notification.setTitle("消息");
        notification.setContent("内容");
        notification.setReadFlag(false);

        when(notificationRepository.findByIdAndUserId(8L, 5L)).thenReturn(Optional.of(notification));

        controller.markRead(request, 8L);

        assertThat(notification.getReadFlag()).isTrue();
        verify(notificationRepository).save(notification);
    }

    @Test
    void markReadShouldRejectOtherUsersNotification() {
        when(notificationRepository.findByIdAndUserId(8L, 5L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> controller.markRead(request, 8L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizException = (BizException) ex;
                    assertThat(bizException.getStatus()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(bizException.getMessage()).isEqualTo("通知不存在");
                });

        verify(notificationRepository, never()).save(any());
    }
}
