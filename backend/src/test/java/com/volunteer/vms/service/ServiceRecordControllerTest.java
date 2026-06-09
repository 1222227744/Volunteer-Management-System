package com.volunteer.vms.service;

import com.volunteer.vms.activity.Activity;
import com.volunteer.vms.activity.ActivityRegistrationRepository;
import com.volunteer.vms.activity.ActivityRepository;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServiceRecordControllerTest {
    @Mock
    private ServiceRecordRepository serviceRecordRepository;

    @Mock
    private ServiceRecordCorrectionRepository correctionRepository;

    @Mock
    private ActivityRepository activityRepository;

    @Mock
    private ActivityRegistrationRepository registrationRepository;

    @Mock
    private UserRepository userRepository;

    private RecordingNotificationService notificationService;
    private RecordingAuditLogService auditLogService;
    private ServiceRecordController controller;

    @BeforeEach
    void setUp() {
        notificationService = new RecordingNotificationService();
        auditLogService = new RecordingAuditLogService();
        controller = new ServiceRecordController(
                serviceRecordRepository,
                correctionRepository,
                activityRepository,
                registrationRepository,
                userRepository,
                notificationService,
                auditLogService,
                null
        );
    }

    @Test
    void createCorrectionShouldRejectOtherUsersRecord() {
        User requester = user(21L, Role.VOLUNTEER, "其他志愿者", 0);
        ServiceRecord record = serviceRecord(50L, 20L, 1L, "2.00", "旧成果");
        MockHttpServletRequest request = authRequest(requester);

        when(serviceRecordRepository.findById(50L)).thenReturn(Optional.of(record));

        assertThatThrownBy(() -> controller.createCorrection(
                request,
                50L,
                new ServiceRecordController.CreateServiceRecordCorrectionRequest(
                        new BigDecimal("3.00"),
                        "更正后成果",
                        null,
                        null,
                        "登记时长有误"
                )
        ))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException bizException = (BizException) ex;
                    assertThat(bizException.getStatus()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(bizException.getMessage()).isEqualTo("只能为自己的服务记录提交更正申请");
                });

        verify(correctionRepository, never()).save(any(ServiceRecordCorrection.class));
        assertThat(notificationService.callCount).isZero();
        assertThat(auditLogService.callCount).isZero();
    }

    @Test
    void approveCorrectionShouldApplyRecordAndAdjustUserPoints() {
        User organizer = user(10L, Role.ORGANIZER, "组织方", 0);
        User volunteer = user(20L, Role.VOLUNTEER, "志愿者", 20);
        Activity activity = activity();
        ServiceRecord record = serviceRecord(50L, 20L, 1L, "2.00", "旧成果");
        ServiceRecordCorrection correction = correction(record, "3.50", "更正后成果");
        MockHttpServletRequest request = authRequest(organizer);

        when(correctionRepository.findById(70L)).thenReturn(Optional.of(correction));
        when(activityRepository.findById(1L)).thenReturn(Optional.of(activity));
        when(serviceRecordRepository.findById(50L)).thenReturn(Optional.of(record));
        when(userRepository.findById(20L)).thenReturn(Optional.of(volunteer));
        when(serviceRecordRepository.save(any(ServiceRecord.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(correctionRepository.save(any(ServiceRecordCorrection.class))).thenAnswer(invocation -> invocation.getArgument(0));

        controller.reviewCorrection(
                request,
                70L,
                new ServiceRecordController.ReviewServiceRecordCorrectionRequest(
                        ServiceRecordCorrectionStatus.APPROVED,
                        "核对签到表后确认"
                )
        );

        assertThat(record.getHours()).isEqualByComparingTo("3.50");
        assertThat(record.getAchievement()).isEqualTo("更正后成果");
        assertThat(volunteer.getPoints()).isEqualTo(35);
        assertThat(correction.getStatus()).isEqualTo(ServiceRecordCorrectionStatus.APPROVED);
        assertThat(correction.getReviewedBy()).isEqualTo(10L);
        assertThat(correction.getReviewedByName()).isEqualTo("组织方");
        assertThat(correction.getReviewedAt()).isNotNull();

        assertThat(notificationService.callCount).isEqualTo(1);
        assertThat(notificationService.lastUserId).isEqualTo(20L);
        assertThat(notificationService.lastTitle).isEqualTo("服务记录更正审核完成");
        assertThat(notificationService.lastContent).contains("已通过");

        assertThat(auditLogService.callCount).isEqualTo(1);
        assertThat(auditLogService.lastCall.action()).isEqualTo("SERVICE_RECORD_CORRECTION_REVIEWED");
        assertThat(auditLogService.lastCall.targetType()).isEqualTo("SERVICE_RECORD_CORRECTION");
    }

    private ServiceRecord serviceRecord(Long id, Long userId, Long activityId, String hours, String achievement) {
        ServiceRecord record = new ServiceRecord();
        record.setId(id);
        record.setUserId(userId);
        record.setActivityId(activityId);
        record.setHours(new BigDecimal(hours));
        record.setAchievement(achievement);
        return record;
    }

    private ServiceRecordCorrection correction(ServiceRecord record, String newHours, String newAchievement) {
        ServiceRecordCorrection correction = new ServiceRecordCorrection();
        correction.setServiceRecordId(record.getId());
        correction.setActivityId(record.getActivityId());
        correction.setUserId(record.getUserId());
        correction.setRequesterId(record.getUserId());
        correction.setRequesterName("志愿者");
        correction.setStatus(ServiceRecordCorrectionStatus.PENDING);
        correction.setOldHours(record.getHours());
        correction.setNewHours(new BigDecimal(newHours));
        correction.setOldAchievement(record.getAchievement());
        correction.setNewAchievement(newAchievement);
        correction.setReason("登记时长有误");
        return correction;
    }

    private Activity activity() {
        Activity activity = new Activity();
        activity.setId(1L);
        activity.setTitle("社区服务");
        activity.setOrganizerId(10L);
        return activity;
    }

    private User user(Long id, Role role, String displayName, int points) {
        User user = new User();
        user.setId(id);
        user.setRole(role);
        user.setDisplayName(displayName);
        user.setUsername("user" + id);
        user.setPoints(points);
        return user;
    }

    private MockHttpServletRequest authRequest(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(AuthUtils.CURRENT_USER_ATTR, user);
        return request;
    }

    private static class RecordingNotificationService extends NotificationService {
        private int callCount;
        private Long lastUserId;
        private String lastTitle;
        private String lastContent;

        RecordingNotificationService() {
            super(null, null, null, null);
        }

        @Override
        public void notifyUser(Long userId, String title, String content) {
            callCount++;
            lastUserId = userId;
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
