package com.volunteer.vms.ops;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_configs", uniqueConstraints = {
        @UniqueConstraint(name = "uk_system_configs_key", columnNames = "config_key")
})
public class SystemConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "config_key", nullable = false, length = 80)
    private String configKey;

    @Column(nullable = false, length = 500)
    private String configValue;

    @Column(nullable = false, length = 120)
    private String configName;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false)
    private Boolean editable;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Long updatedBy;

    @Column(nullable = false, length = 50)
    private String updatedByName;

    @PrePersist
    public void prePersist() {
        if (editable == null) {
            editable = true;
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Boolean getEditable() {
        return editable;
    }

    public void setEditable(Boolean editable) {
        this.editable = editable;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public Long getUpdatedBy() {
        return updatedBy;
    }

    public void setUpdatedBy(Long updatedBy) {
        this.updatedBy = updatedBy;
    }

    public String getUpdatedByName() {
        return updatedByName;
    }

    public void setUpdatedByName(String updatedByName) {
        this.updatedByName = updatedByName;
    }
}
