package com.volunteer.vms.notification;

/**
 * 外部通知适配端口：业务层只关心“发送某个通道的消息”，
 * 真实 SMTP/SMS 网关或课程版模拟实现都挂在该接口之后。
 */
public interface ExternalNotificationSender {
    ExternalNotificationDeliveryResult send(ExternalNotificationChannel channel,
                                            String recipient,
                                            String title,
                                            String content);
}
