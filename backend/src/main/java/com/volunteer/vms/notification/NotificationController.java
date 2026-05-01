package com.volunteer.vms.notification;

import com.volunteer.vms.common.ApiResponse;
import com.volunteer.vms.common.AuthUtils;
import com.volunteer.vms.common.BizException;
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

    public NotificationController(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    @GetMapping("/my")
    public ApiResponse<Map<String, Object>> myNotifications(HttpServletRequest request) {
        User currentUser = AuthUtils.currentUser(request);
        List<NotificationResponse> notifications = notificationRepository.findByUserIdOrUserIdIsNullOrderByCreatedAtDesc(currentUser.getId())
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
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new BizException(HttpStatus.NOT_FOUND, "通知不存在"));
        if (notification.getUserId() != null && !notification.getUserId().equals(currentUser.getId())) {
            throw new BizException(HttpStatus.FORBIDDEN, "不能操作他人通知");
        }
        notification.setReadFlag(true);
        notificationRepository.save(notification);
        return ApiResponse.success();
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
}
