package com.volunteer.vms.notification;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "external_notification_tasks")
public class ExternalNotificationTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExternalNotificationChannel channel;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(length = 200)
    private String recipient;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ExternalNotificationStatus status;

    @Column(nullable = false)
    private Integer retryCount;

    @Column(nullable = false)
    private Integer maxRetries;

    @Column(length = 1000)
    private String lastError;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime lastTriedAt;

    private LocalDateTime sentAt;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = ExternalNotificationStatus.PENDING;
        }
        if (retryCount == null) {
            retryCount = 0;
        }
        if (maxRetries == null) {
            maxRetries = 3;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public ExternalNotificationChannel getChannel() {
        return channel;
    }

    public void setChannel(ExternalNotificationChannel channel) {
        this.channel = channel;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getRecipient() {
        return recipient;
    }

    public void setRecipient(String recipient) {
        this.recipient = recipient;
    }

    public ExternalNotificationStatus getStatus() {
        return status;
    }

    public void setStatus(ExternalNotificationStatus status) {
        this.status = status;
    }

    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    public Integer getMaxRetries() {
        return maxRetries;
    }

    public void setMaxRetries(Integer maxRetries) {
        this.maxRetries = maxRetries;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getLastTriedAt() {
        return lastTriedAt;
    }

    public void setLastTriedAt(LocalDateTime lastTriedAt) {
        this.lastTriedAt = lastTriedAt;
    }

    public LocalDateTime getSentAt() {
        return sentAt;
    }

    public void setSentAt(LocalDateTime sentAt) {
        this.sentAt = sentAt;
    }
}
