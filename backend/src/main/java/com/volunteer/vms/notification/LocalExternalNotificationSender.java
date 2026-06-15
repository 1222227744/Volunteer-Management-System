package com.volunteer.vms.notification;

/**
 * 本地外部通知实现：不连接第三方短信服务；邮件未启用 SMTP 时仅完成接收地址校验并返回处理结果。
 * 后续接入短信服务商时替换该适配器即可，ExternalNotificationService 无需改动。
 */
public class LocalExternalNotificationSender implements ExternalNotificationSender {
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
