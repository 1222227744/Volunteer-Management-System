package com.volunteer.vms.service;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 核心实体：对应 v4 对 SRS FR-04 的补强，用于保存服务记录更正申请、
 * 审核结果和新旧数据快照，避免直接覆盖服务记录导致缺少追溯依据。
 */
@Entity
@Table(name = "service_record_corrections")
public class ServiceRecordCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long serviceRecordId;

    @Column(nullable = false)
    private Long activityId;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long requesterId;

    @Column(nullable = false, length = 50)
    private String requesterName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ServiceRecordCorrectionStatus status;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal oldHours;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal newHours;

    @Column(nullable = false, length = 1000)
    private String oldAchievement;

    @Column(nullable = false, length = 1000)
    private String newAchievement;

    @Column(length = 500)
    private String oldEvidenceUrl;

    @Column(length = 500)
    private String newEvidenceUrl;

    private Long oldEvidenceFileId;

    private Long newEvidenceFileId;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(length = 500)
    private String reviewComment;

    private Long reviewedBy;

    @Column(length = 50)
    private String reviewedByName;

    @Column(nullable = false)
    private LocalDateTime requestedAt;

    private LocalDateTime reviewedAt;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = ServiceRecordCorrectionStatus.PENDING;
        }
        if (requestedAt == null) {
            requestedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getServiceRecordId() {
        return serviceRecordId;
    }

    public void setServiceRecordId(Long serviceRecordId) {
        this.serviceRecordId = serviceRecordId;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public Long getRequesterId() {
        return requesterId;
    }

    public void setRequesterId(Long requesterId) {
        this.requesterId = requesterId;
    }

    public String getRequesterName() {
        return requesterName;
    }

    public void setRequesterName(String requesterName) {
        this.requesterName = requesterName;
    }

    public ServiceRecordCorrectionStatus getStatus() {
        return status;
    }

    public void setStatus(ServiceRecordCorrectionStatus status) {
        this.status = status;
    }

    public BigDecimal getOldHours() {
        return oldHours;
    }

    public void setOldHours(BigDecimal oldHours) {
        this.oldHours = oldHours;
    }

    public BigDecimal getNewHours() {
        return newHours;
    }

    public void setNewHours(BigDecimal newHours) {
        this.newHours = newHours;
    }

    public String getOldAchievement() {
        return oldAchievement;
    }

    public void setOldAchievement(String oldAchievement) {
        this.oldAchievement = oldAchievement;
    }

    public String getNewAchievement() {
        return newAchievement;
    }

    public void setNewAchievement(String newAchievement) {
        this.newAchievement = newAchievement;
    }

    public String getOldEvidenceUrl() {
        return oldEvidenceUrl;
    }

    public void setOldEvidenceUrl(String oldEvidenceUrl) {
        this.oldEvidenceUrl = oldEvidenceUrl;
    }

    public String getNewEvidenceUrl() {
        return newEvidenceUrl;
    }

    public void setNewEvidenceUrl(String newEvidenceUrl) {
        this.newEvidenceUrl = newEvidenceUrl;
    }

    public Long getOldEvidenceFileId() {
        return oldEvidenceFileId;
    }

    public void setOldEvidenceFileId(Long oldEvidenceFileId) {
        this.oldEvidenceFileId = oldEvidenceFileId;
    }

    public Long getNewEvidenceFileId() {
        return newEvidenceFileId;
    }

    public void setNewEvidenceFileId(Long newEvidenceFileId) {
        this.newEvidenceFileId = newEvidenceFileId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getReviewComment() {
        return reviewComment;
    }

    public void setReviewComment(String reviewComment) {
        this.reviewComment = reviewComment;
    }

    public Long getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(Long reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getReviewedByName() {
        return reviewedByName;
    }

    public void setReviewedByName(String reviewedByName) {
        this.reviewedByName = reviewedByName;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}
