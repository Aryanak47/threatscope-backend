package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_configurations")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringConfiguration {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "config_key", nullable = false, unique = true, length = 100)
    private String configKey; // e.g., "real_time_interval", "hourly_interval", etc.
    
    @Column(name = "config_value", nullable = false, length = 500)
    private String configValue; // e.g., "300000" (5 minutes in milliseconds)
    
    @Column(name = "data_type", nullable = false, length = 20)
    private String dataType; // STRING, INTEGER, BOOLEAN, JSON
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "category", nullable = false, length = 50)
    private String category; // MONITORING, ALERTS, NOTIFICATIONS, CLEANUP
    
    @Column(name = "is_admin_configurable", nullable = false)
    private Boolean isAdminConfigurable = true;
    
    @Column(name = "requires_restart", nullable = false)
    private Boolean requiresRestart = false;
    
    @Column(name = "min_value", length = 100)
    private String minValue; // For validation
    
    @Column(name = "max_value", length = 100)
    private String maxValue; // For validation
    
    @Column(name = "default_value", length = 500)
    private String defaultValue;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "updated_by")
    private Long updatedBy; // Admin user ID who last updated
    
    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }
    
    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // Helper methods
    public Integer getIntValue() {
        try {
            return Integer.parseInt(configValue);
        } catch (NumberFormatException e) {
            return Integer.parseInt(defaultValue);
        }
    }
    
    public Long getLongValue() {
        try {
            return Long.parseLong(configValue);
        } catch (NumberFormatException e) {
            return Long.parseLong(defaultValue);
        }
    }
    
    public Boolean getBooleanValue() {
        return Boolean.parseBoolean(configValue);
    }
    
    public String getStringValue() {
        return configValue != null ? configValue : defaultValue;
    }
}
