package com.volunteer.vms.notification;

import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/**
 * 外部通知路由适配器：EMAIL 可切换为真实 SMTP，SMS 使用本地通道结果记录。
 */
@Primary
@Component
public class RoutingExternalNotificationSender implements ExternalNotificationSender {
    private final SmtpEmailNotificationSender emailSender;
    private final LocalExternalNotificationSender localSender = new LocalExternalNotificationSender();

    public RoutingExternalNotificationSender(SmtpEmailNotificationSender emailSender) {
        this.emailSender = emailSender;
    }

    @Override
    public ExternalNotificationDeliveryResult send(ExternalNotificationChannel channel,
                                                   String recipient,
                                                   String title,
                                                   String content) {
        if (channel == ExternalNotificationChannel.EMAIL && emailSender.enabled()) {
            return emailSender.send(recipient, title, content);
        }
        return localSender.send(channel, recipient, title, content);
    }
}
