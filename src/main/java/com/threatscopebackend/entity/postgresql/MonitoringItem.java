package com.threatscopebackend.entity.postgresql;

import com.fasterxml.jackson.annotation.JsonBackReference;
import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_items")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MonitoringItem {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    @JsonBackReference // Prevent circular reference during JSON serialization
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "monitor_type", nullable = false)
    private CommonEnums.MonitorType monitorType;
    
    @Column(name = "query", nullable = false, length = 500)
    private String targetValue;
    
    @Column(name = "monitor_name", length = 100)
    private String monitorName;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private CommonEnums.MonitorFrequency frequency;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "email_alerts", nullable = false)
    private Boolean emailAlerts = true;
    
    @Column(name = "in_app_alerts", nullable = false)
    private Boolean inAppAlerts = true;
    
    @Column(name = "webhook_alerts", nullable = false)
    private Boolean webhookAlerts = false;
    
    @Column(name = "last_checked")
    private LocalDateTime lastChecked;
    
    @Column(name = "last_alert_sent")
    private LocalDateTime lastAlertSent;
    
    @Column(name = "alert_count", nullable = false)
    private Integer alertCount = 0;
    
    @Column(name = "breach_count", nullable = false)
    private Integer breachCount = 0;
    
    @Column(name = "match_count", nullable = false)
    private Integer matchCount = 0;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
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
    public boolean canSendAlert() {
        if (lastAlertSent == null) return true;
        
        return switch (frequency) {
            case REAL_TIME -> true; // Always allow real-time alerts
            case HOURLY -> lastAlertSent.isBefore(LocalDateTime.now().minusHours(1));
            case DAILY -> lastAlertSent.isBefore(LocalDateTime.now().minusDays(1));
            case WEEKLY -> lastAlertSent.isBefore(LocalDateTime.now().minusWeeks(1));
        };
    }
    
    public void recordCheck() {
        this.lastChecked = LocalDateTime.now();
    }
    
    public void recordAlert() {
        this.lastAlertSent = LocalDateTime.now();
        this.alertCount++;
    }
    
    public void recordBreach() {
        this.breachCount++;
        recordAlert(); // Also record as an alert
    }
    
    public void recordMatch() {
        this.matchCount++;
    }
    
    // Get display name for monitor type
    public String getMonitorTypeDisplayName() {
        return monitorType.getDisplayName();
    }
    
    // Get frequency display name
    public String getFrequencyDisplayName() {
        return frequency.getDisplayName();
    }
    
    // Check if this monitor type requires premium features
    public boolean requiresPremiumFrequency() {
        return frequency.requiresPremium();
    }
    
    // Get next check time based on frequency
    public LocalDateTime getNextCheckTime() {
        if (lastChecked == null) {
            return LocalDateTime.now();
        }
        
        return switch (frequency) {
            case REAL_TIME -> LocalDateTime.now(); // Always check real-time
            case HOURLY -> lastChecked.plusHours(1);
            case DAILY -> lastChecked.plusDays(1);
            case WEEKLY -> lastChecked.plusWeeks(1);
        };
    }
    
    // Check if monitoring item is due for a check
    public boolean isDueForCheck() {
        return getNextCheckTime().isBefore(LocalDateTime.now());
    }
    
    // Get days since last check
    public long getDaysSinceLastCheck() {
        if (lastChecked == null) return 0;
        return java.time.Duration.between(lastChecked, LocalDateTime.now()).toDays();
    }
    
    // Get days since created
    public long getDaysSinceCreated() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toDays();
    }
    
    // Check if has recent alerts (within last 7 days)
    public boolean hasRecentAlerts() {
        return lastAlertSent != null && lastAlertSent.isAfter(LocalDateTime.now().minusDays(7));
    }
    
    // Get status based on activity and checks
    public MonitoringStatus getStatus() {
        if (!isActive) return MonitoringStatus.INACTIVE;
        if (lastChecked == null) return MonitoringStatus.PENDING;
        if (getDaysSinceLastCheck() > 7) return MonitoringStatus.STALE;
        return MonitoringStatus.ACTIVE;
    }
    
    public enum MonitoringStatus {
        ACTIVE,
        INACTIVE,
        PENDING,
        STALE
    }
}
