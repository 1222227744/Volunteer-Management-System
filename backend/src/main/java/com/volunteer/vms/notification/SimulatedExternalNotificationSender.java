package com.volunteer.vms.notification;

/**
 * 课程版默认外部通知实现：不连接真实 SMTP/SMS 服务，只校验接收地址并返回发送结果。
 * 后续接入真实服务时替换该适配器即可，ExternalNotificationService 无需改动。
 */
public class SimulatedExternalNotificationSender implements ExternalNotificationSender {
    @Override
    public ExternalNotificationDeliveryResult send(ExternalNotificationChannel channel,
                                                   String recipient,
                                                   String title,
                                                   String content) {
        if (recipient == null || recipient.isBlank()) {
            return ExternalNotificationDeliveryResult.failed("缺少" + translateChannel(channel) + "接收地址");
        }
        return ExternalNotificationDeliveryResult.success();
    }

    private String translateChannel(ExternalNotificationChannel channel) {
        return channel == ExternalNotificationChannel.SMS ? "短信" : "邮件";
    }
}
