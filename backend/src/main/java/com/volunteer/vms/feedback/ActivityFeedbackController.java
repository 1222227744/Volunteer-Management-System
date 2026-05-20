package com.volunteer.vms.feedback;

import com.volunteer.vms.activity.Activity;
import com.volunteer.vms.activity.ActivityRepository;
import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.service.ServiceRecordRepository;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 接口层：补齐 SRS FR-05 的课程版活动评价能力。
 * 评价前置条件为活动已形成正式服务记录，避免与未参与用户混淆。
 */
@RestController
@RequestMapping("/api/activity-feedbacks")
public class ActivityFeedbackController {
    private final ActivityFeedbackRepository activityFeedbackRepository;
    private final ActivityRepository activityRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final UserRepository userRepository;
    private final AuditLogService auditLogService;

    public ActivityFeedbackController(ActivityFeedbackRepository activityFeedbackRepository,
                                      ActivityRepository activityRepository,
                                      ServiceRecordRepository serviceRecordRepository,
                                      UserRepository userRepository,
                                      AuditLogService auditLogService) {
        this.activityFeedbackRepository = activityFeedbackRepository;
        this.activityRepository = activityRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.userRepository = userRepository;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public ApiResponse<Void> submit(HttpServletRequest request,
                                    @Valid @RequestBody SubmitActivityFeedbackRequest submitRequest) {
        User currentUser = AuthUtils.currentUser(request);
        Activity activity = activityRepository.findById(submitRequest.activityId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        if (!serviceRecordRepository.existsByActivityIdAndUserId(submitRequest.activityId(), currentUser.getId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "只有已完成服务记录的志愿者才能评价活动");
        }
        if (activityFeedbackRepository.existsByActivityIdAndUserId(submitRequest.activityId(), currentUser.getId())) {
            throw new BizException(HttpStatus.BAD_REQUEST, "你已提交过该活动评价");
        }
        ActivityFeedback feedback = new ActivityFeedback();
        feedback.setActivityId(submitRequest.activityId());
        feedback.setUserId(currentUser.getId());
        feedback.setRating(submitRequest.rating());
        feedback.setComment(submitRequest.comment());
        activityFeedbackRepository.save(feedback);
        auditLogService.log(
                request,
                currentUser,
                "ACTIVITY_FEEDBACK_SUBMITTED",
                "ACTIVITY_FEEDBACK",
                feedback.getId(),
                "活动《" + activity.getTitle() + "》评分=" + submitRequest.rating()
        );
        return ApiResponse.success();
    }

    @GetMapping("/my")
    public ApiResponse<List<ActivityFeedbackResponse>> myFeedback(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<ActivityFeedback> feedbacks = activityFeedbackRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId());
        return ApiResponse.success(toResponses(feedbacks));
    }

    @GetMapping("/activity/{activityId}")
    public ApiResponse<Map<String, Object>> activityFeedbacks(HttpServletRequest request, @PathVariable Long activityId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN, Role.ORGANIZER);
        Activity activity = activityRepository.findById(activityId)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "活动不存在"));
        if (currentUser.getRole() == Role.ORGANIZER && !currentUser.getId().equals(activity.getOrganizerId())) {
            throw new BizException(HttpStatus.FORBIDDEN, "只能查看自己活动的评价");
        }
        List<ActivityFeedback> feedbacks = activityFeedbackRepository.findByActivityIdOrderByCreatedAtDesc(activityId);
        List<ActivityFeedbackResponse> items = toResponses(feedbacks);
        double averageRating = feedbacks.isEmpty()
                ? 0
                : feedbacks.stream().mapToInt(ActivityFeedback::getRating).average().orElse(0);
        return ApiResponse.success(Map.of(
                "averageRating", Math.round(averageRating * 10.0) / 10.0,
                "count", feedbacks.size(),
                "items", items
        ));
    }

    private List<ActivityFeedbackResponse> toResponses(List<ActivityFeedback> feedbacks) {
        Map<Long, Activity> activityMap = activityRepository.findAllById(
                feedbacks.stream().map(ActivityFeedback::getActivityId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(Activity::getId, item -> item));
        Map<Long, String> userNameMap = userRepository.findAllById(
                feedbacks.stream().map(ActivityFeedback::getUserId).collect(Collectors.toSet())
        ).stream().collect(Collectors.toMap(User::getId, User::getDisplayName));

        return feedbacks.stream()
                .map(item -> new ActivityFeedbackResponse(
                        item.getId(),
                        item.getActivityId(),
                        activityMap.get(item.getActivityId()) == null ? "未知活动" : activityMap.get(item.getActivityId()).getTitle(),
                        item.getUserId(),
                        userNameMap.getOrDefault(item.getUserId(), "未知用户"),
                        item.getRating(),
                        item.getComment(),
                        item.getCreatedAt()
                ))
                .toList();
    }

    public record SubmitActivityFeedbackRequest(
            @NotNull(message = "活动ID不能为空")
            Long activityId,
            @NotNull(message = "评分不能为空")
            @Min(value = 1, message = "评分至少为1")
            @Max(value = 5, message = "评分最多为5")
            Integer rating,
            @NotBlank(message = "评价内容不能为空")
            @Size(max = 1000, message = "评价内容最多1000字")
            String comment
    ) {
    }

    public record ActivityFeedbackResponse(Long id,
                                           Long activityId,
                                           String activityTitle,
                                           Long userId,
                                           String userDisplayName,
                                           Integer rating,
                                           String comment,
                                           LocalDateTime createdAt) {
    }
}
