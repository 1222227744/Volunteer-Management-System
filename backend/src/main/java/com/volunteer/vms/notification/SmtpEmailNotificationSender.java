package com.volunteer.vms.notification;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.mail.MailException;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Properties;

/**
 * 真实邮件发送适配器：配置 vms.external.email.enabled=true 后，
 * EMAIL 通道会通过 SMTP 发送；未启用时由路由发送器回退到本地通道。
 */
@Component
public class SmtpEmailNotificationSender {
    private final EmailNotificationProperties properties;

    public SmtpEmailNotificationSender(EmailNotificationProperties properties) {
        this.properties = properties;
    }

    public boolean enabled() {
        return properties.enabled();
    }

    public ExternalNotificationDeliveryResult send(String recipient, String title, String content) {
        if (recipient == null || recipient.isBlank()) {
            return ExternalNotificationDeliveryResult.failed("缺少邮件接收地址");
        }
        if (recipient.endsWith("@example.local")) {
            return ExternalNotificationDeliveryResult.failed("用户未配置真实邮箱地址");
        }
        if (!properties.enabled()) {
            return ExternalNotificationDeliveryResult.failed("真实邮件发送未启用");
        }
        if (isBlank(properties.host()) || isBlank(properties.from())) {
            return ExternalNotificationDeliveryResult.failed("邮件服务未配置 host 或 from");
        }
        try {
            JavaMailSenderImpl mailSender = buildMailSender();
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                    message,
                    false,
                    StandardCharsets.UTF_8.name()
            );
            helper.setFrom(properties.from(), emptyToDefault(properties.fromName(), properties.from()));
            helper.setTo(recipient.trim());
            helper.setSubject(title);
            helper.setText(content, false);
            mailSender.send(message);
            return ExternalNotificationDeliveryResult.success();
        } catch (MessagingException | MailException ex) {
            return ExternalNotificationDeliveryResult.failed("邮件发送失败：" + ex.getMessage());
        } catch (java.io.UnsupportedEncodingException ex) {
            return ExternalNotificationDeliveryResult.failed("发件人名称编码失败：" + ex.getMessage());
        }
    }

    private JavaMailSenderImpl buildMailSender() {
        JavaMailSenderImpl mailSender = new JavaMailSenderImpl();
        mailSender.setHost(properties.host());
        mailSender.setPort(properties.resolvedPort());
        mailSender.setUsername(emptyToNull(properties.username()));
        mailSender.setPassword(emptyToNull(properties.password()));
        mailSender.setDefaultEncoding(StandardCharsets.UTF_8.name());
        mailSender.setJavaMailProperties(mailProperties());
        return mailSender;
    }

    private Properties mailProperties() {
        Properties values = new Properties();
        values.put("mail.smtp.auth", String.valueOf(properties.auth()));
        values.put("mail.smtp.ssl.enable", String.valueOf(properties.sslEnabled()));
        values.put("mail.smtp.starttls.enable", String.valueOf(properties.startTlsEnabled()));
        values.put("mail.smtp.connectiontimeout", String.valueOf(properties.resolvedConnectionTimeoutMillis()));
        values.put("mail.smtp.timeout", String.valueOf(properties.resolvedTimeoutMillis()));
        values.put("mail.smtp.writetimeout", String.valueOf(properties.resolvedWriteTimeoutMillis()));
        return values;
    }

    private String emptyToNull(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private String emptyToDefault(String value, String fallback) {
        return isBlank(value) ? fallback : value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
