package com.volunteer.vms.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class NotificationWebSocketHandler extends TextWebSocketHandler {
    public static final String USER_ID_ATTR = "userId";

    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<Long, Set<WebSocketSession>> sessions = new ConcurrentHashMap<>();

    public NotificationWebSocketHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        Long userId = resolveUserId(session);
        if (userId != null) {
            sessions.computeIfAbsent(userId, ignored -> ConcurrentHashMap.newKeySet()).add(session);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        Long userId = resolveUserId(session);
        if (userId == null) {
            return;
        }
        Set<WebSocketSession> userSessions = sessions.get(userId);
        if (userSessions != null) {
            userSessions.remove(session);
            if (userSessions.isEmpty()) {
                sessions.remove(userId);
            }
        }
    }

    public void push(Notification notification) {
        Set<WebSocketSession> userSessions = sessions.get(notification.getUserId());
        if (userSessions == null || userSessions.isEmpty()) {
            return;
        }
        String payload;
        try {
            payload = objectMapper.writeValueAsString(Map.of(
                    "type", "NOTIFICATION_CREATED",
                    "id", notification.getId(),
                    "title", notification.getTitle(),
                    "content", notification.getContent(),
                    "createdAt", notification.getCreatedAt()
            ));
        } catch (IOException ex) {
            return;
        }
        TextMessage message = new TextMessage(payload);
        for (WebSocketSession session : userSessions) {
            if (session.isOpen()) {
                try {
                    session.sendMessage(message);
                } catch (IOException ignored) {
                    // 实时推送失败不能影响通知落库，失败会在下一次页面拉取时补齐。
                }
            }
        }
    }

    private Long resolveUserId(WebSocketSession session) {
        Object value = session.getAttributes().get(USER_ID_ATTR);
        return value instanceof Long userId ? userId : null;
    }
}
