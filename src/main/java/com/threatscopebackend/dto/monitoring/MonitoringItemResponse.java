package com.threatscopebackend.dto.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
public class MonitoringItemResponse {
    
    private Long id;
    private CommonEnums.MonitorType monitorType;
    private String targetValue;
    private String monitorName;
    private String description;
    private CommonEnums.MonitorFrequency frequency;
    private Boolean isActive;
    private Boolean emailAlerts;
    private Boolean inAppAlerts;
    private Boolean webhookAlerts;
    private LocalDateTime lastChecked;
    private LocalDateTime lastAlertSent;
    private Integer alertCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // Computed fields
    private Long daysSinceLastCheck;
    private Long daysSinceCreated;
    private Boolean hasRecentAlerts;
    private String status; // "ACTIVE", "INACTIVE", "PENDING", "STALE"
    
    // Display fields
    private String monitorTypeDisplayName;
    private String frequencyDisplayName;
    private String monitorTypeDescription;
    
    public static MonitoringItemResponse fromEntity(com.threatscopebackend.entity.postgresql.MonitoringItem item) {
        if (item == null) return null;
        
        LocalDateTime now = LocalDateTime.now();
        
        return MonitoringItemResponse.builder()
            .id(item.getId())
            .monitorType(item.getMonitorType())
            .targetValue(item.getTargetValue())
            .monitorName(item.getMonitorName())
            .description(item.getDescription())
            .frequency(item.getFrequency())
            .isActive(item.getIsActive())
            .emailAlerts(item.getEmailAlerts())
            .inAppAlerts(item.getInAppAlerts())
            .webhookAlerts(item.getWebhookAlerts())
            .lastChecked(item.getLastChecked())
            .lastAlertSent(item.getLastAlertSent())
            .alertCount(item.getAlertCount())
            .createdAt(item.getCreatedAt())
            .updatedAt(item.getUpdatedAt())
            .daysSinceLastCheck(item.getDaysSinceLastCheck())
            .daysSinceCreated(item.getDaysSinceCreated())
            .hasRecentAlerts(item.hasRecentAlerts())
            .status(item.getStatus().name())
            .monitorTypeDisplayName(item.getMonitorTypeDisplayName())
            .frequencyDisplayName(item.getFrequencyDisplayName())
            .monitorTypeDescription(item.getMonitorType().getDescription())
            .build();
    }
}
