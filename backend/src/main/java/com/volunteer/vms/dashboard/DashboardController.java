package com.volunteer.vms.dashboard;

import com.volunteer.vms.activity.ActivityRepository;
import com.volunteer.vms.activity.ActivityRegistrationRepository;
import com.volunteer.vms.activity.RegistrationStatus;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.content.ContentPostRepository;
import com.volunteer.vms.content.ContentStatus;
import com.volunteer.vms.donation.DonationRepository;
import com.volunteer.vms.feedback.FeedbackRepository;
import com.volunteer.vms.feedback.FeedbackStatus;
import com.volunteer.vms.service.ServiceRecordRepository;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接口层：实现 SRS FR-08 统计分析与后台治理视图的数据聚合出口。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {
    private final UserRepository userRepository;
    private final ActivityRepository activityRepository;
    private final ActivityRegistrationRepository activityRegistrationRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final ContentPostRepository contentPostRepository;
    private final DonationRepository donationRepository;
    private final FeedbackRepository feedbackRepository;

    public DashboardController(UserRepository userRepository,
                               ActivityRepository activityRepository,
                               ActivityRegistrationRepository activityRegistrationRepository,
                               ServiceRecordRepository serviceRecordRepository,
                               ContentPostRepository contentPostRepository,
                               DonationRepository donationRepository,
                               FeedbackRepository feedbackRepository) {
        this.userRepository = userRepository;
        this.activityRepository = activityRepository;
        this.activityRegistrationRepository = activityRegistrationRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.contentPostRepository = contentPostRepository;
        this.donationRepository = donationRepository;
        this.feedbackRepository = feedbackRepository;
    }

    @GetMapping("/stats")
    public ApiResponse<Map<String, Object>> stats(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN, Role.ORGANIZER);
        if (currentUser.getRole() == Role.ORGANIZER) {
            return ApiResponse.success(buildOrganizerStats(currentUser));
        }
        return ApiResponse.success(buildGlobalStats());
    }

    private Map<String, Object> buildOrganizerStats(User currentUser) {
        List<Long> activityIds = activityRepository.findIdsByOrganizerId(currentUser.getId());
        if (activityIds.isEmpty()) {
            return buildStatsPayload(
                    "ORGANIZER",
                    "我管理的活动",
                    false,
                    false,
                    0L,
                    0L,
                    0L,
                    0L,
                    BigDecimal.ZERO,
                    contentPostRepository.countByStatus(ContentStatus.PENDING),
                    0L,
                    BigDecimal.ZERO
            );
        }
        return buildStatsPayload(
                "ORGANIZER",
                "我管理的活动",
                false,
                false,
                activityRegistrationRepository.countDistinctUserIdByActivityIdIn(activityIds),
                activityRepository.countByOrganizerId(currentUser.getId()),
                activityRegistrationRepository.countByActivityIdIn(activityIds),
                activityRegistrationRepository.countByActivityIdInAndStatus(activityIds, RegistrationStatus.COMPLETED),
                serviceRecordRepository.sumHoursByActivityIds(activityIds),
                contentPostRepository.countByStatus(ContentStatus.PENDING),
                0L,
                BigDecimal.ZERO
        );
    }

    private Map<String, Object> buildGlobalStats() {
        return buildStatsPayload(
                "GLOBAL",
                "平台全局",
                true,
                true,
                userRepository.count(),
                activityRepository.count(),
                activityRegistrationRepository.count(),
                activityRegistrationRepository.countByStatus(RegistrationStatus.COMPLETED),
                serviceRecordRepository.sumHoursAll(),
                contentPostRepository.countByStatus(ContentStatus.PENDING),
                feedbackRepository.countByStatus(FeedbackStatus.OPEN),
                donationRepository.totalAmount()
        );
    }

    private Map<String, Object> buildStatsPayload(String scope,
                                                  String scopeLabel,
                                                  boolean canViewFeedbackMetrics,
                                                  boolean canViewDonationMetrics,
                                                  long userCount,
                                                  long activityCount,
                                                  long registrationCount,
                                                  long completedRegistrationCount,
                                                  BigDecimal totalServiceHours,
                                                  long pendingContentCount,
                                                  long feedbackOpenCount,
                                                  BigDecimal donationTotalAmount) {
        Map<String, Object> stats = new LinkedHashMap<>();
        stats.put("scope", scope);
        stats.put("scopeLabel", scopeLabel);
        stats.put("canViewFeedbackMetrics", canViewFeedbackMetrics);
        stats.put("canViewDonationMetrics", canViewDonationMetrics);
        stats.put("userCount", userCount);
        stats.put("activityCount", activityCount);
        stats.put("registrationCount", registrationCount);
        stats.put("completedRegistrationCount", completedRegistrationCount);
        stats.put("totalServiceHours", totalServiceHours);
        stats.put("pendingContentCount", pendingContentCount);
        stats.put("feedbackOpenCount", feedbackOpenCount);
        stats.put("donationTotalAmount", donationTotalAmount);
        return stats;
    }
}
