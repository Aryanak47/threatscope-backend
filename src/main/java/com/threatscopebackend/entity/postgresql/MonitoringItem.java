package com.threatscopebackend.entity.postgresql;

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
    private User user;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "monitor_type", nullable = false)
    private MonitorType monitorType;
    
    @Column(name = "target_value", nullable = false, length = 500)
    private String targetValue;
    
    @Column(name = "monitor_name", length = 100)
    private String monitorName;
    
    @Column(name = "description", length = 500)
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "frequency", nullable = false)
    private MonitorFrequency frequency;
    
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "email_alerts", nullable = false)
    private Boolean emailAlerts = true;
    
    @Column(name = "in_app_alerts", nullable = false)
    private Boolean inAppAlerts = true;
    
    @Column(name = "last_checked")
    private LocalDateTime lastChecked;
    
    @Column(name = "last_alert_sent")
    private LocalDateTime lastAlertSent;
    
    @Column(name = "alert_count", nullable = false)
    private Integer alertCount = 0;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    public enum MonitorType {
        EMAIL,          // Monitor specific email addresses
        DOMAIN,         // Monitor entire domains
        USERNAME,       // Monitor usernames across platforms
        KEYWORD,        // Monitor specific keywords/terms
        IP_ADDRESS,     // Monitor IP addresses
        PHONE,          // Monitor phone numbers
        ORGANIZATION    // Monitor organization names
    }
    
    public enum MonitorFrequency {
        REAL_TIME,      // Check immediately when new data arrives
        HOURLY,         // Check every hour
        DAILY,          // Check once per day
        WEEKLY          // Check once per week
    }
    
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
}