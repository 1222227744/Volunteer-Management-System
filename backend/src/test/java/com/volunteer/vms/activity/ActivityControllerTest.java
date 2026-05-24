package com.volunteer.vms.activity;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.notification.NotificationService;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.HttpStatus;

import java.util.Collection;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivityControllerTest {

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityRegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    private RecordingNotificationService notificationService;
    private RecordingAuditLogService auditLogService;

    private ActivityController controller;
    private User organizer;
    private Activity activity;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        notificationService = new RecordingNotificationService();
        auditLogService = new RecordingAuditLogService();
        controller = new ActivityController(
                activityRepository,
                registrationRepository,
                userRepository,
                notificationService,
                auditLogService,
                null
        );

        organizer = new User();
        organizer.setId(10L);
        organizer.setRole(Role.ORGANIZER);
        organizer.setDisplayName("组织者");
        organizer.setUsername("organizer");
        organizer.setPoints(0);

        activity = new Activity();
        activity.setId(1L);
        activity.setTitle("校园清洁");
        activity.setOrganizerId(10L);
        activity.setStatus(ActivityStatus.PUBLISHED);
        activity.setDescription("desc");
        activity.setLocation("操场");
        activity.setStartTime(LocalDateTime.of(2026, 5, 20, 9, 0));
        activity.setEndTime(LocalDateTime.of(2026, 5, 20, 11, 0));
        activity.setMaxParticipants(20);

        request = authRequest(organizer);
    }

    @Test
    void updateStatusShouldRejectIllegalTransition() {
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));

        assertThatThrownBy(() -> controller.updateStatus(
                request,
                1L,
                new ActivityController.UpdateStatusRequest(ActivityStatus.FINISHED)
        ))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizException = (BizException) ex;
                    assertThat(bizException.getStatus()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(bizException.getMessage()).isEqualTo("活动状态不允许从 PUBLISHED 直接变更为 FINISHED");
                });

        assertThat(notificationService.notifyUsersCallCount).isZero();
        assertThat(auditLogService.callCount).isZero();
    }

    @Test
    void cancelActivityShouldCancelRelatedRegistrationsAndNotifyUsers() {
        ActivityRegistration pending = registration(101L, 1001L, RegistrationStatus.PENDING);
        ActivityRegistration approved = registration(102L, 1002L, RegistrationStatus.APPROVED);
        ActivityRegistration checkedIn = registration(103L, 1003L, RegistrationStatus.CHECKED_IN);
        checkedIn.setCheckInAt(LocalDateTime.of(2026, 5, 20, 9, 10));
        ActivityRegistration completed = registration(104L, 1004L, RegistrationStatus.COMPLETED);
        completed.setCheckInAt(LocalDateTime.of(2026, 5, 20, 9, 0));
        completed.setCheckOutAt(LocalDateTime.of(2026, 5, 20, 11, 0));

        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(activityRepository.save(any(Activity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(registrationRepository.findByActivityId(1L)).thenReturn(List.of(pending, approved, checkedIn, completed));

        controller.updateStatus(request, 1L, new ActivityController.UpdateStatusRequest(ActivityStatus.CANCELLED));

        assertThat(activity.getStatus()).isEqualTo(ActivityStatus.CANCELLED);
        assertThat(pending.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(approved.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(checkedIn.getStatus()).isEqualTo(RegistrationStatus.CANCELLED);
        assertThat(checkedIn.getCheckInAt()).isNull();
        assertThat(checkedIn.getCheckOutAt()).isNull();
        assertThat(completed.getStatus()).isEqualTo(RegistrationStatus.COMPLETED);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<ActivityRegistration>> changedCaptor = ArgumentCaptor.forClass(List.class);
        verify(registrationRepository).saveAll(changedCaptor.capture());
        assertThat(changedCaptor.getValue()).hasSize(3);

        assertThat(notificationService.notifyUsersCallCount).isEqualTo(1);
        assertThat(notificationService.lastUserIds).containsExactly(1001L, 1002L, 1003L);
        assertThat(notificationService.lastTitle).isEqualTo("活动已取消");
        assertThat(notificationService.lastContent).isEqualTo("活动《校园清洁》已取消，原报名记录已同步关闭。");

        assertThat(auditLogService.callCount).isEqualTo(1);
        assertThat(auditLogService.lastCall).isNotNull();
        assertThat(auditLogService.lastCall.request()).isEqualTo(request);
        assertThat(auditLogService.lastCall.operator()).isEqualTo(organizer);
        assertThat(auditLogService.lastCall.action()).isEqualTo("ACTIVITY_STATUS_UPDATED");
        assertThat(auditLogService.lastCall.targetType()).isEqualTo("ACTIVITY");
        assertThat(auditLogService.lastCall.targetId()).isEqualTo(1L);
        assertThat(auditLogService.lastCall.detail()).isEqualTo("状态从 PUBLISHED 更新为 CANCELLED");
    }

    private ActivityRegistration registration(Long id, Long userId, RegistrationStatus status) {
        ActivityRegistration registration = new ActivityRegistration();
        registration.setId(id);
        registration.setActivityId(1L);
        registration.setUserId(userId);
        registration.setStatus(status);
        registration.setRegisteredAt(LocalDateTime.of(2026, 5, 18, 8, 0));
        return registration;
    }

    private MockHttpServletRequest authRequest(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthUtils.CURRENT_USER_ATTR, user);
        return request;
    }

    private static class RecordingNotificationService extends NotificationService {
        private int notifyUsersCallCount;
        private List<Long> lastUserIds = List.of();
        private String lastTitle;
        private String lastContent;

        RecordingNotificationService() {
            super(null, null, null);
        }

        @Override
        public void notifyUsers(Collection<Long> userIds, String title, String content) {
            notifyUsersCallCount++;
            lastUserIds = List.copyOf(userIds);
            lastTitle = title;
            lastContent = content;
        }
    }

    private static class RecordingAuditLogService extends AuditLogService {
        private int callCount;
        private AuditCall lastCall;

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
            lastCall = new AuditCall(request, operator, action, targetType, targetId, detail);
        }
    }

    private record AuditCall(HttpServletRequest request,
                             User operator,
                             String action,
                             String targetType,
                             Object targetId,
                             String detail) {
    }
}
