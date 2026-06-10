package com.volunteer.vms.ops;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "incident_records")
public class IncidentRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String title;

    @Column(nullable = false, length = 1000)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentStatus status;

    @Column(length = 1000)
    private String handlingMeasure;

    @Column(length = 1000)
    private String result;

    @Column(nullable = false)
    private Long createdBy;

    @Column(nullable = false, length = 50)
    private String createdByName;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;

    @PrePersist
    public void prePersist() {
        if (severity == null) {
            severity = IncidentSeverity.MEDIUM;
        }
        if (status == null) {
            status = IncidentStatus.OPEN;
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

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public IncidentSeverity getSeverity() {
        return severity;
    }

    public void setSeverity(IncidentSeverity severity) {
        this.severity = severity;
    }

    public IncidentStatus getStatus() {
        return status;
    }

    public void setStatus(IncidentStatus status) {
        this.status = status;
    }

    public String getHandlingMeasure() {
        return handlingMeasure;
    }

    public void setHandlingMeasure(String handlingMeasure) {
        this.handlingMeasure = handlingMeasure;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(LocalDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
