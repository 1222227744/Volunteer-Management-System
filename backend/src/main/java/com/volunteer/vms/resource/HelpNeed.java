package com.volunteer.vms.resource;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "help_needs")
public class HelpNeed {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 120)
    private String requester;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(nullable = false)
    private Integer quantity;

    @Column(length = 40)
    private String unit;

    @Column(nullable = false, length = 200)
    private String location;

    private LocalDateTime requiredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NeedStatus status;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    public void prePersist() {
        if (status == null) {
            status = NeedStatus.OPEN;
        }
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getRequester() {
        return requester;
    }

    public void setRequester(String requester) {
        this.requester = requester;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getLocation() {
        return location;
    }

    public void setLocation(String location) {
        this.location = location;
    }

    public LocalDateTime getRequiredAt() {
        return requiredAt;
    }

    public void setRequiredAt(LocalDateTime requiredAt) {
        this.requiredAt = requiredAt;
    }

    public NeedStatus getStatus() {
        return status;
    }

    public void setStatus(NeedStatus status) {
        this.status = status;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
