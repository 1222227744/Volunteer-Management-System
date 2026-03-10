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

import java.util.Map;

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
        return ApiResponse.success(Map.of(
                "userCount", userRepository.count(),
                "activityCount", activityRepository.count(),
                "registrationCount", activityRegistrationRepository.count(),
                "completedRegistrationCount", activityRegistrationRepository.countByStatus(RegistrationStatus.COMPLETED),
                "totalServiceHours", serviceRecordRepository.sumHoursAll(),
                "pendingContentCount", contentPostRepository.countByStatus(ContentStatus.PENDING),
                "feedbackOpenCount", feedbackRepository.countByStatus(FeedbackStatus.OPEN),
                "donationTotalAmount", donationRepository.totalAmount()
        ));
    }
}
