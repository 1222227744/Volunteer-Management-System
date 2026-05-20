package com.volunteer.vms.feedback;

import com.volunteer.vms.audit.AuditLogService;
import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.notification.NotificationService;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 接口层：实现 SRS FR-08 互动反馈闭环。
 * 当前以“反馈提交 + 管理端处理 + 通知回执”作为课程版闭环实现。
 */
@RestController
@RequestMapping("/api/feedbacks")
public class FeedbackController {
    private final FeedbackRepository feedbackRepository;
    private final NotificationService notificationService;
    private final AuditLogService auditLogService;

    public FeedbackController(FeedbackRepository feedbackRepository,
                              NotificationService notificationService,
                              AuditLogService auditLogService) {
        this.feedbackRepository = feedbackRepository;
        this.notificationService = notificationService;
        this.auditLogService = auditLogService;
    }

    @PostMapping
    public ApiResponse<Void> submitFeedback(HttpServletRequest request,
                                            @Valid @RequestBody SubmitFeedbackRequest submitRequest) {
        User currentUser = AuthUtils.currentUser(request);
        Feedback feedback = new Feedback();
        feedback.setUserId(currentUser.getId());
        feedback.setContent(submitRequest.content());
        feedback.setStatus(FeedbackStatus.OPEN);
        Feedback saved = feedbackRepository.save(feedback);
        auditLogService.log(
                request,
                currentUser,
                "FEEDBACK_SUBMITTED",
                "FEEDBACK",
                saved.getId(),
                "提交反馈: " + summarizeForAudit(submitRequest.content())
        );
        return ApiResponse.success();
    }

    @GetMapping("/my")
    public ApiResponse<List<FeedbackResponse>> myFeedback(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<FeedbackResponse> data = feedbackRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(FeedbackResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @GetMapping
    public ApiResponse<List<FeedbackResponse>> allFeedback(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        List<FeedbackResponse> data = feedbackRepository.findAllByOrderByCreatedAtDesc()
                .stream()
                .map(FeedbackResponse::from)
                .toList();
        return ApiResponse.success(data);
    }

    @PatchMapping("/{id}/resolve")
    public ApiResponse<Void> resolve(HttpServletRequest request,
                                     @PathVariable Long id,
                                     @Valid @RequestBody ResolveFeedbackRequest resolveRequest) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        Feedback feedback = feedbackRepository.findById(id)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "反馈不存在"));
        feedback.setStatus(FeedbackStatus.RESOLVED);
        feedback.setReply(resolveRequest.reply());
        feedback.setResolvedAt(LocalDateTime.now());
        feedbackRepository.save(feedback);

        notificationService.notifyUser(feedback.getUserId(), "反馈已处理", resolveRequest.reply());
        auditLogService.log(
                request,
                currentUser,
                "FEEDBACK_RESOLVED",
                "FEEDBACK",
                id,
                "处理回复=" + resolveRequest.reply()
        );
        return ApiResponse.success();
    }

    private String summarizeForAudit(String content) {
        String normalized = content == null ? "" : content.trim();
        if (normalized.length() <= 60) {
            return normalized;
        }
        return normalized.substring(0, 60) + "...";
    }

    public record SubmitFeedbackRequest(
            @NotBlank(message = "反馈内容不能为空")
            @Size(max = 1000, message = "反馈内容最多1000字")
            String content
    ) {
    }

    public record ResolveFeedbackRequest(
            @NotBlank(message = "处理回复不能为空")
            @Size(max = 1000, message = "处理回复最多1000字")
            String reply
    ) {
    }

    public record FeedbackResponse(Long id,
                                   Long userId,
                                   String content,
                                   FeedbackStatus status,
                                   String reply,
                                   LocalDateTime createdAt,
                                   LocalDateTime resolvedAt) {
        static FeedbackResponse from(Feedback feedback) {
            return new FeedbackResponse(
                    feedback.getId(),
                    feedback.getUserId(),
                    feedback.getContent(),
                    feedback.getStatus(),
                    feedback.getReply(),
                    feedback.getCreatedAt(),
                    feedback.getResolvedAt()
            );
        }
    }
}
