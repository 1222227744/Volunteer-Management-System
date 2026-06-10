package com.volunteer.vms.honor;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.feedback.ActivityFeedback;
import com.volunteer.vms.feedback.ActivityFeedbackRepository;
import com.volunteer.vms.notification.NotificationService;
import com.volunteer.vms.service.ServiceRecord;
import com.volunteer.vms.service.ServiceRecordRepository;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import com.volunteer.vms.user.UserRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/honors")
public class HonorController {
    private final HonorRecordRepository honorRecordRepository;
    private final UserRepository userRepository;
    private final ServiceRecordRepository serviceRecordRepository;
    private final ActivityFeedbackRepository activityFeedbackRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public HonorController(HonorRecordRepository honorRecordRepository,
                           UserRepository userRepository,
                           ServiceRecordRepository serviceRecordRepository,
                           ActivityFeedbackRepository activityFeedbackRepository,
                           NotificationService notificationService,
                           AuditLogService auditLogService) {
        this.honorRecordRepository = honorRecordRepository;
        this.userRepository = userRepository;
        this.serviceRecordRepository = serviceRecordRepository;
        this.activityFeedbackRepository = activityFeedbackRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @GetMapping("/candidates")
    public ApiResponse<List<HonorCandidateResponse>> candidates(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        return ApiResponse.success(buildCandidates());
    }

    @PostMapping
    @Transactional
    public ApiResponse<HonorRecordResponse> award(HttpServletRequest request,
                                                  @Valid @RequestBody AwardHonorRequest awardRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        User targetUser = userRepository.findById(awardRequest.userId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "用户不存在"));

        HonorRecord record = new HonorRecord();
        record.setUserId(targetUser.getId());
        record.setHonorType(awardRequest.honorType());
        record.setTitle(awardRequest.title());
        record.setReason(awardRequest.reason());
        record.setShowcaseText(awardRequest.showcaseText());
        record.setRelatedActivityId(awardRequest.relatedActivityId());
        record.setPointsAwarded(awardRequest.pointsAwarded());
        record.setAwardedBy(currentUser.getId());
        record.setPublicVisible(awardRequest.publicVisible());
        HonorRecord saved = honorRecordRepository.save(record);

        if (awardRequest.pointsAwarded() > 0) {
            targetUser.setPoints(targetUser.getPoints() + awardRequest.pointsAwarded());
            userRepository.save(targetUser);
        }
        notificationService.notifyUser(
                targetUser.getId(),
                "获得荣誉激励",
                "你获得“" + saved.getTitle() + "”荣誉，积分 +" + awardRequest.pointsAwarded() + "。"
        );
        auditLogService.log(
                request,
                currentUser,
                "HONOR_AWARDED",
                "HONOR",
                saved.getId(),
                "用户ID=" + targetUser.getId() + ", 荣誉=" + saved.getTitle() + ", 积分=" + awardRequest.pointsAwarded()
        );
        return ApiResponse.success(HonorRecordResponse.from(saved, targetUser.getDisplayName()));
    }

    @GetMapping("/my")
    public ApiResponse<List<HonorRecordResponse>> myHonors(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        return ApiResponse.success(honorRecordRepository.findByUserIdOrderByAwardedAtDesc(currentUser.getId()).stream()
                .map(item -> HonorRecordResponse.from(item, currentUser.getDisplayName()))
                .toList());
    }

    @GetMapping("/showcase")
    public ApiResponse<List<HonorRecordResponse>> showcase() {
        return ApiResponse.success(toResponses(honorRecordRepository.findByPublicVisibleTrueOrderByAwardedAtDesc()));
    }

    @GetMapping
    public ApiResponse<List<HonorRecordResponse>> all(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        return ApiResponse.success(toResponses(honorRecordRepository.findAllByOrderByAwardedAtDesc()));
    }

    private List<HonorCandidateResponse> buildCandidates() {
        List<User> volunteers = userRepository.findAll().stream()
                .filter(user -> user.getRole() == Role.VOLUNTEER)
                .toList();
        Map<Long, List<ServiceRecord>> recordMap = serviceRecordRepository.findAll().stream()
                .collect(Collectors.groupingBy(ServiceRecord::getUserId));
        Map<Long, List<ActivityFeedback>> feedbackMap = activityFeedbackRepository.findAll().stream()
                .collect(Collectors.groupingBy(ActivityFeedback::getUserId));
        return volunteers.stream()
                .map(user -> buildCandidate(user, recordMap.getOrDefault(user.getId(), List.of()),
                        feedbackMap.getOrDefault(user.getId(), List.of())))
                .sorted(Comparator.comparing(HonorCandidateResponse::score).reversed())
                .limit(20)
                .toList();
    }

    private HonorCandidateResponse buildCandidate(User user,
                                                  List<ServiceRecord> records,
                                                  List<ActivityFeedback> feedbacks) {
        BigDecimal totalHours = records.stream()
                .map(ServiceRecord::getHours)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        double averageRating = feedbacks.stream()
                .mapToInt(ActivityFeedback::getRating)
                .average()
                .orElse(0);
        int score = totalHours.multiply(BigDecimal.TEN).intValue()
                + records.size() * 20
                + (int) Math.round(averageRating * 10)
                + user.getPoints();
        String reason = "累计服务 " + totalHours + " 小时，参与 " + records.size()
                + " 次，平均评价 " + BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP) + " 分。";
        return new HonorCandidateResponse(
                user.getId(),
                user.getDisplayName(),
                user.getPoints(),
                totalHours,
                records.size(),
                BigDecimal.valueOf(averageRating).setScale(1, RoundingMode.HALF_UP),
                score,
                reason
        );
    }

    private List<HonorRecordResponse> toResponses(List<HonorRecord> records) {
        Map<Long, User> userMap = userRepository.findAllById(records.stream().map(HonorRecord::getUserId).toList())
                .stream()
                .collect(Collectors.toMap(User::getId, Function.identity()));
        return records.stream()
                .map(item -> HonorRecordResponse.from(item,
                        userMap.get(item.getUserId()) == null ? "未知用户" : userMap.get(item.getUserId()).getDisplayName()))
                .toList();
    }

    public record AwardHonorRequest(
            @NotNull(message = "用户ID不能为空")
            Long userId,
            @NotNull(message = "荣誉类型不能为空")
            HonorType honorType,
            @NotBlank(message = "荣誉标题不能为空")
            @Size(max = 120, message = "荣誉标题最多120字")
            String title,
            @NotBlank(message = "评定原因不能为空")
            @Size(max = 1000, message = "评定原因最多1000字")
            String reason,
            @Size(max = 2000, message = "风采展示最多2000字")
            String showcaseText,
            Long relatedActivityId,
            @NotNull(message = "奖励积分不能为空")
            @Min(value = 0, message = "奖励积分不能为负数")
            Integer pointsAwarded,
            Boolean publicVisible
    ) {
        public AwardHonorRequest {
            if (honorType == null) {
                honorType = HonorType.EXCELLENT_VOLUNTEER;
            }
            if (pointsAwarded == null) {
                pointsAwarded = 0;
            }
            if (publicVisible == null) {
                publicVisible = true;
            }
        }
    }

    public record HonorCandidateResponse(Long userId,
                                         String displayName,
                                         Integer points,
                                         BigDecimal totalHours,
                                         Integer serviceCount,
                                         BigDecimal averageRating,
                                         Integer score,
                                         String reason) {
    }

    public record HonorRecordResponse(Long id,
                                      Long userId,
                                      String userDisplayName,
                                      HonorType honorType,
                                      String title,
                                      String reason,
                                      String showcaseText,
                                      Long relatedActivityId,
                                      Integer pointsAwarded,
                                      Long awardedBy,
                                      LocalDateTime awardedAt,
                                      Boolean publicVisible) {
        static HonorRecordResponse from(HonorRecord record, String userDisplayName) {
            return new HonorRecordResponse(
                    record.getId(),
                    record.getUserId(),
                    userDisplayName,
                    record.getHonorType(),
                    record.getTitle(),
                    record.getReason(),
                    record.getShowcaseText(),
                    record.getRelatedActivityId(),
                    record.getPointsAwarded(),
                    record.getAwardedBy(),
                    record.getAwardedAt(),
                    record.getPublicVisible()
            );
        }
    }
}
