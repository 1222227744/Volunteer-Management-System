package com.volunteer.vms.notification;

import com.volunteer.vms.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 应用服务：承接 SRS FR-06 的站内通知分发。
 * v2 起统一按“用户级通知记录”落库，避免广播消息的已读状态互相影响。
 */
@Service
public class NotificationService {
    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final NotificationWebSocketHandler webSocketHandler;

    public NotificationService(NotificationRepository notificationRepository,
                               UserRepository userRepository,
                               NotificationWebSocketHandler webSocketHandler) {
        this.notificationRepository = notificationRepository;
        this.userRepository = userRepository;
        this.webSocketHandler = webSocketHandler;
    }

    @Transactional
    public void notifyUser(Long userId, String title, String content) {
        if (userId == null) {
            return;
        }
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        Notification saved = notificationRepository.save(notification);
        pushRealtime(saved);
    }

    @Transactional
    public void notifyUsers(Collection<Long> userIds, String title, String content) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> distinctUserIds = new LinkedHashSet<>();
        for (Long userId : userIds) {
            if (userId != null) {
                distinctUserIds.add(userId);
            }
        }
        if (distinctUserIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<Notification> notifications = distinctUserIds.stream()
                .map(userId -> buildNotification(userId, title, content, now))
                .toList();
        notificationRepository.saveAll(notifications).forEach(this::pushRealtime);
    }

    @Transactional
    public void notifyAllUsers(String title, String content) {
        notifyUsers(userRepository.findAllIds(), title, content);
    }

    private Notification buildNotification(Long userId, String title, String content, LocalDateTime createdAt) {
        Notification notification = new Notification();
        notification.setUserId(userId);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setCreatedAt(createdAt);
        return notification;
    }

    private void pushRealtime(Notification notification) {
        if (webSocketHandler != null) {
            webSocketHandler.push(notification);
        }
    }
}
