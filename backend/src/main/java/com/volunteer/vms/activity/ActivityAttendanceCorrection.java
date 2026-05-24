package com.volunteer.vms.activity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "activity_attendance_corrections")
public class ActivityAttendanceCorrection {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long activityId;

    @Column(nullable = false)
    private Long registrationId;

    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private AttendanceCorrectionAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus beforeStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RegistrationStatus afterStatus;

    private LocalDateTime beforeCheckInAt;

    private LocalDateTime afterCheckInAt;

    private LocalDateTime beforeCheckOutAt;

    private LocalDateTime afterCheckOutAt;

    @Column(nullable = false, length = 500)
    private String reason;

    @Column(nullable = false)
    private Long correctedBy;

    @Column(nullable = false, length = 50)
    private String correctedByName;

    @Column(nullable = false)
    private LocalDateTime correctedAt;

    @PrePersist
    public void prePersist() {
        if (correctedAt == null) {
            correctedAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public Long getActivityId() {
        return activityId;
    }

    public void setActivityId(Long activityId) {
        this.activityId = activityId;
    }

    public Long getRegistrationId() {
        return registrationId;
    }

    public void setRegistrationId(Long registrationId) {
        this.registrationId = registrationId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public AttendanceCorrectionAction getAction() {
        return action;
    }

    public void setAction(AttendanceCorrectionAction action) {
        this.action = action;
    }

    public RegistrationStatus getBeforeStatus() {
        return beforeStatus;
    }

    public void setBeforeStatus(RegistrationStatus beforeStatus) {
        this.beforeStatus = beforeStatus;
    }

    public RegistrationStatus getAfterStatus() {
        return afterStatus;
    }

    public void setAfterStatus(RegistrationStatus afterStatus) {
        this.afterStatus = afterStatus;
    }

    public LocalDateTime getBeforeCheckInAt() {
        return beforeCheckInAt;
    }

    public void setBeforeCheckInAt(LocalDateTime beforeCheckInAt) {
        this.beforeCheckInAt = beforeCheckInAt;
    }

    public LocalDateTime getAfterCheckInAt() {
        return afterCheckInAt;
    }

    public void setAfterCheckInAt(LocalDateTime afterCheckInAt) {
        this.afterCheckInAt = afterCheckInAt;
    }

    public LocalDateTime getBeforeCheckOutAt() {
        return beforeCheckOutAt;
    }

    public void setBeforeCheckOutAt(LocalDateTime beforeCheckOutAt) {
        this.beforeCheckOutAt = beforeCheckOutAt;
    }

    public LocalDateTime getAfterCheckOutAt() {
        return afterCheckOutAt;
    }

    public void setAfterCheckOutAt(LocalDateTime afterCheckOutAt) {
        this.afterCheckOutAt = afterCheckOutAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getCorrectedBy() {
        return correctedBy;
    }

    public void setCorrectedBy(Long correctedBy) {
        this.correctedBy = correctedBy;
    }

    public String getCorrectedByName() {
        return correctedByName;
    }

    public void setCorrectedByName(String correctedByName) {
        this.correctedByName = correctedByName;
    }

    public LocalDateTime getCorrectedAt() {
        return correctedAt;
    }
}
