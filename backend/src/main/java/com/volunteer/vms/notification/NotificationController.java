package com.volunteer.vms.notification;

import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
import com.volunteer.vms.user.Role;
import com.volunteer.vms.user.User;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 接口层：实现 SRS FR-06 的消息通知读取与回执查看。
 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {
    private final NotificationRepository notificationRepository;
    private final ExternalNotificationTaskRepository externalTaskRepository;
    private final ExternalNotificationService externalNotificationService;

    public NotificationController(NotificationRepository notificationRepository,
                                  ExternalNotificationTaskRepository externalTaskRepository,
                                  ExternalNotificationService externalNotificationService) {
        this.notificationRepository = notificationRepository;
        this.externalTaskRepository = externalTaskRepository;
        this.externalNotificationService = externalNotificationService;
    }

    @GetMapping("/my")
    public ApiResponse<Map<String, Object>> myNotifications(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<NotificationResponse> notifications = notificationRepository.findByUserIdOrderByCreatedAtDesc(currentUser.getId())
                .stream()
                .map(NotificationResponse::from)
                .toList();
        long unreadCount = notifications.stream().filter(item -> !item.readFlag()).count();
        return ApiResponse.success(Map.of(
                "unreadCount", unreadCount,
                "items", notifications
        ));
    }

    @PatchMapping("/{id}/read")
    public ApiResponse<Void> markRead(HttpServletRequest request, @PathVariable Long id) {
        User currentUser = AuthUtils.currentUser(request);
        Notification notification = notificationRepository.findByIdAndUserId(id, currentUser.getId())
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "通知不存在"));
        notification.setReadFlag(true);
        notificationRepository.save(notification);
        return ApiResponse.success();
    }

    @GetMapping("/external-tasks")
    public ApiResponse<List<ExternalNotificationTaskResponse>> externalTasks(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        return ApiResponse.success(externalTaskRepository.findAllByOrderByCreatedAtDesc().stream()
                .map(ExternalNotificationTaskResponse::from)
                .toList());
    }

    @PostMapping("/external-tasks/{taskId}/retry")
    public ApiResponse<ExternalNotificationTaskResponse> retryExternalTask(HttpServletRequest request, @PathVariable Long taskId) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        return ApiResponse.success(ExternalNotificationTaskResponse.from(externalNotificationService.retry(taskId)));
    }

    @PostMapping("/external-tasks/retry-failed")
    public ApiResponse<Map<String, Object>> retryFailedExternalTasks(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        AuthUtils.requireRole(currentUser, Role.ADMIN);
        int count = externalNotificationService.retryFailed();
        return ApiResponse.success(Map.of("retried", count));
    }

    public record NotificationResponse(Long id, Long userId, String title, String content, Boolean readFlag, LocalDateTime createdAt) {
        static NotificationResponse from(Notification notification) {
            return new NotificationResponse(
                    notification.getId(),
                    notification.getUserId(),
                    notification.getTitle(),
                    notification.getContent(),
                    notification.getReadFlag(),
                    notification.getCreatedAt()
            );
        }
    }

    public record ExternalNotificationTaskResponse(Long id,
                                                   Long userId,
                                                   ExternalNotificationChannel channel,
                                                   String title,
                                                   String recipient,
                                                   ExternalNotificationStatus status,
                                                   Integer retryCount,
                                                   Integer maxRetries,
                                                   String lastError,
                                                   LocalDateTime createdAt,
                                                   LocalDateTime lastTriedAt,
                                                   LocalDateTime sentAt) {
        static ExternalNotificationTaskResponse from(ExternalNotificationTask task) {
            return new ExternalNotificationTaskResponse(
                    task.getId(),
                    task.getUserId(),
                    task.getChannel(),
                    task.getTitle(),
                    task.getRecipient(),
                    task.getStatus(),
                    task.getRetryCount(),
                    task.getMaxRetries(),
                    task.getLastError(),
                    task.getCreatedAt(),
                    task.getLastTriedAt(),
                    task.getSentAt()
            );
        }
    }
}
