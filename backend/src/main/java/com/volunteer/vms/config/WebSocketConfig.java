package com.volunteer.vms.config;

import com.volunteer.vms.notification.NotificationWebSocketHandler;
import com.volunteer.vms.notification.NotificationWebSocketInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final NotificationWebSocketHandler notificationWebSocketHandler;
    private final NotificationWebSocketInterceptor notificationWebSocketInterceptor;
    private final CorsProperties corsProperties;

    public WebSocketConfig(NotificationWebSocketHandler notificationWebSocketHandler,
                           NotificationWebSocketInterceptor notificationWebSocketInterceptor,
                           CorsProperties corsProperties) {
        this.notificationWebSocketHandler = notificationWebSocketHandler;
        this.notificationWebSocketInterceptor = notificationWebSocketInterceptor;
        this.corsProperties = corsProperties;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(notificationWebSocketHandler, "/ws/notifications")
                .addInterceptors(notificationWebSocketInterceptor)
                .setAllowedOriginPatterns(corsProperties.originPatterns());
    }
}
