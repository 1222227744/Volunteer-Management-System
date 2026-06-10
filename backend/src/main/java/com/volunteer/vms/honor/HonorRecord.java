package com.volunteer.vms.honor;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "honor_records")
public class HonorRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private HonorType honorType;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String reason;

    @Column(length = 2000)
    private String showcaseText;

    private Long relatedActivityId;

    @Column(nullable = false)
    private Integer pointsAwarded;

    @Column(nullable = false)
    private Long awardedBy;

    @Column(nullable = false)
    private LocalDateTime awardedAt;

    @Column(nullable = false)
    private Boolean publicVisible;

    @PrePersist
    public void prePersist() {
        if (awardedAt == null) {
            awardedAt = LocalDateTime.now();
        }
        if (publicVisible == null) {
            publicVisible = true;
        }
        if (pointsAwarded == null) {
            pointsAwarded = 0;
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

    public HonorType getHonorType() {
        return honorType;
    }

    public void setHonorType(HonorType honorType) {
        this.honorType = honorType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getShowcaseText() {
        return showcaseText;
    }

    public void setShowcaseText(String showcaseText) {
        this.showcaseText = showcaseText;
    }

    public Long getRelatedActivityId() {
        return relatedActivityId;
    }

    public void setRelatedActivityId(Long relatedActivityId) {
        this.relatedActivityId = relatedActivityId;
    }

    public Integer getPointsAwarded() {
        return pointsAwarded;
    }

    public void setPointsAwarded(Integer pointsAwarded) {
        this.pointsAwarded = pointsAwarded;
    }

    public Long getAwardedBy() {
        return awardedBy;
    }

    public void setAwardedBy(Long awardedBy) {
        this.awardedBy = awardedBy;
    }

    public LocalDateTime getAwardedAt() {
        return awardedAt;
    }

    public void setAwardedAt(LocalDateTime awardedAt) {
        this.awardedAt = awardedAt;
    }

    public Boolean getPublicVisible() {
        return publicVisible;
    }

    public void setPublicVisible(Boolean publicVisible) {
        this.publicVisible = publicVisible;
    }
}
