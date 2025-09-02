package com.threatscopebackend.entity.postgresql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "plans")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Plan {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "plan_type", nullable = false)
    private CommonEnums.PlanType planType; // Use enum instead of string
    
    @Column(name = "display_name", nullable = false, length = 100)
    private String displayName;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "price", precision = 10, scale = 2)
    private BigDecimal price; // Monthly price
    
    @Column(name = "currency", length = 3)
    private String currency = "USD";
    
    @Enumerated(EnumType.STRING)
    @Column(name = "billing_cycle", nullable = false)
    private CommonEnums.BillingCycle billingCycle = CommonEnums.BillingCycle.MONTHLY;
    
    // Search limits
    @Column(name = "daily_searches", nullable = false)
    private Integer dailySearches;
    
    @Column(name = "monthly_searches", nullable = false)
    private Integer monthlySearches;
    
    // Monitoring limits
    @Column(name = "max_monitoring_items", nullable = false)
    private Integer maxMonitoringItems;
    
    @Column(name = "monitoring_frequencies", columnDefinition = "TEXT")
    private String monitoringFrequencies; // JSON array of allowed frequencies
    
    // Alert limits
    @Column(name = "max_alerts_per_day", nullable = false)
    private Integer maxAlertsPerDay;
    
    @Column(name = "alert_retention_days", nullable = false)
    private Integer alertRetentionDays;
    
    // Export limits
    @Column(name = "daily_exports", nullable = false)
    private Integer dailyExports;
    
    @Column(name = "monthly_exports", nullable = false)
    private Integer monthlyExports;
    
    // Features
    @Column(name = "api_access", nullable = false)
    private Boolean apiAccess = false;
    
    @Column(name = "real_time_monitoring", nullable = false)
    private Boolean realTimeMonitoring = false;
    
    @Column(name = "email_alerts", nullable = false)
    private Boolean emailAlerts = true;
    
    @Column(name = "in_app_alerts", nullable = false)
    private Boolean inAppAlerts = true;
    
    @Column(name = "webhook_alerts", nullable = false)
    private Boolean webhookAlerts = false;
    
    @Column(name = "priority_support", nullable = false)
    private Boolean prioritySupport = false;
    
    @Column(name = "custom_integrations", nullable = false)
    private Boolean customIntegrations = false;
    
    @Column(name = "advanced_analytics", nullable = false)
    private Boolean advancedAnalytics = false;
    
    // Plan settings
    @Column(name = "is_active", nullable = false)
    private Boolean isActive = true;
    
    @Column(name = "is_public", nullable = false)
    private Boolean isPublic = true; // Whether plan is publicly available
    
    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;
    
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "plan", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore // Prevent serialization of all subscriptions for this plan
    private List<Subscription> subscriptions;
    
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
    public boolean isFreePlan() {
        return planType == CommonEnums.PlanType.FREE;
    }
    
    public boolean allowsFrequency(CommonEnums.MonitorFrequency frequency) {
        if (monitoringFrequencies == null) return false;
        return monitoringFrequencies.contains(frequency.name());
    }
    
    public boolean hasFeature(String featureName) {
        return switch (featureName.toLowerCase()) {
            case "api_access" -> apiAccess;
            case "real_time_monitoring" -> realTimeMonitoring;
            case "email_alerts" -> emailAlerts;
            case "in_app_alerts" -> inAppAlerts;
            case "webhook_alerts" -> webhookAlerts;
            case "priority_support" -> prioritySupport;
            case "custom_integrations" -> customIntegrations;
            case "advanced_analytics" -> advancedAnalytics;
            default -> false;
        };
    }
    
    // Convenience method to get plan name
    public String getName() {
        return planType.name();
    }
    
    // Get allowed frequencies as enum list
    public List<CommonEnums.MonitorFrequency> getAllowedFrequencies() {
        if (monitoringFrequencies == null || monitoringFrequencies.isEmpty()) {
            return List.of(CommonEnums.MonitorFrequency.DAILY, CommonEnums.MonitorFrequency.WEEKLY);
        }
        
        return java.util.Arrays.stream(CommonEnums.MonitorFrequency.values())
                .filter(freq -> monitoringFrequencies.contains(freq.name()))
                .toList();
    }
}
