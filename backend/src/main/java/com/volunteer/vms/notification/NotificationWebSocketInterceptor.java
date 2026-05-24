package com.volunteer.vms.notification;

import com.volunteer.vms.auth.TokenSessionService;
import com.volunteer.vms.user.User;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.Map;

@Component
public class NotificationWebSocketInterceptor implements HandshakeInterceptor {
    private final TokenSessionService tokenSessionService;

    public NotificationWebSocketInterceptor(TokenSessionService tokenSessionService) {
        this.tokenSessionService = tokenSessionService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request,
                                   ServerHttpResponse response,
                                   WebSocketHandler wsHandler,
                                   Map<String, Object> attributes) {
        URI uri = request.getURI();
        List<String> tokens = UriComponentsBuilder.fromUri(uri).build().getQueryParams().get("token");
        String token = tokens == null || tokens.isEmpty() ? "" : tokens.get(0);
        return tokenSessionService.resolveUser(token)
                .map(User::getId)
                .map(userId -> {
                    attributes.put(NotificationWebSocketHandler.USER_ID_ATTR, userId);
                    return true;
                })
                .orElseGet(() -> {
                    response.setStatusCode(HttpStatus.UNAUTHORIZED);
                    return false;
                });
    }

    @Override
    public void afterHandshake(ServerHttpRequest request,
                               ServerHttpResponse response,
                               WebSocketHandler wsHandler,
                               Exception exception) {
    }
}
