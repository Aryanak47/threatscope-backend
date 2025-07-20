package com.threatscopebackend.dto.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
public class BreachAlertResponse {
    
    private Long id;
    private String title;
    private String description;
    private CommonEnums.AlertStatus status;
    private CommonEnums.AlertSeverity severity;
    private String breachSource;
    private LocalDateTime breachDate;
    private String breachData;
    private String affectedEmail;
    private String affectedDomain;
    private String affectedUsername;
    private String dataTypes;
    private Long recordCount;
    private Boolean isVerified;
    private Boolean isFalsePositive;
    private Boolean isRemediated;
    private Boolean isAcknowledged;
    private Boolean isEscalated;
    private String escalationNotes;
    private String remediationNotes;
    private String acknowledgmentNotes;
    private Integer riskScore;
    private Integer confidenceLevel;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime viewedAt;
    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime dismissedAt;
    private LocalDateTime escalatedAt;
    private Boolean notificationSent;
    private LocalDateTime notificationSentAt;
    private Boolean emailNotificationSent;
    private Boolean webhookNotificationSent;
    
    // Related monitoring item info
    private Long monitoringItemId;
    private String monitoringItemName;
    private CommonEnums.MonitorType monitorType;
    
    // Display fields
    private String statusDisplayName;
    private String severityDisplayName;
    private String monitorTypeDisplayName;
    
    // Computed fields
    private Long ageInHours;
    private Long ageInDays;
    private String priorityLevel;
    private Boolean isRecent;
    private Boolean isHighPriority;
    private Boolean isStale;
    private LocalDateTime lastActionTime;
    
    public static BreachAlertResponse fromEntity(BreachAlert alert) {
        if (alert == null) return null;
        
        return BreachAlertResponse.builder()
            .id(alert.getId())
            .title(alert.getTitle())
            .description(alert.getDescription())
            .status(alert.getStatus())
            .severity(alert.getSeverity())
            .breachSource(alert.getBreachSource())
            .breachDate(alert.getBreachDate())
            .breachData(alert.getBreachData())
            .affectedEmail(alert.getAffectedEmail())
            .affectedDomain(alert.getAffectedDomain())
            .affectedUsername(alert.getAffectedUsername())
            .dataTypes(alert.getDataTypes())
            .recordCount(alert.getRecordCount())
            .isVerified(alert.getIsVerified())
            .isFalsePositive(alert.getIsFalsePositive())
            .isRemediated(alert.getIsRemediated())
            .isAcknowledged(alert.getIsAcknowledged())
            .isEscalated(alert.getIsEscalated())
            .escalationNotes(alert.getEscalationNotes())
            .remediationNotes(alert.getRemediationNotes())
            .acknowledgmentNotes(alert.getAcknowledgmentNotes())
            .riskScore(alert.getRiskScore())
            .confidenceLevel(alert.getConfidenceLevel())
            .createdAt(alert.getCreatedAt())
            .updatedAt(alert.getUpdatedAt())
            .viewedAt(alert.getViewedAt())
            .acknowledgedAt(alert.getAcknowledgedAt())
            .resolvedAt(alert.getResolvedAt())
            .dismissedAt(alert.getDismissedAt())
            .escalatedAt(alert.getEscalatedAt())
            .notificationSent(alert.getNotificationSent())
            .notificationSentAt(alert.getNotificationSentAt())
            .emailNotificationSent(alert.getEmailNotificationSent())
            .webhookNotificationSent(alert.getWebhookNotificationSent())
            .monitoringItemId(alert.getMonitoringItem() != null ? 
                alert.getMonitoringItem().getId() : null)
            .monitoringItemName(alert.getMonitoringItem() != null ? 
                alert.getMonitoringItem().getMonitorName() : null)
            .monitorType(alert.getMonitoringItem() != null ? 
                alert.getMonitoringItem().getMonitorType() : null)
            .statusDisplayName(alert.getStatusDisplayName())
            .severityDisplayName(alert.getSeverityDisplayName())
            .monitorTypeDisplayName(alert.getMonitoringItem() != null ? 
                alert.getMonitoringItem().getMonitorTypeDisplayName() : null)
            .ageInHours(alert.getAgeInHours())
            .ageInDays(alert.getAgeInDays())
            .priorityLevel(determinePriorityLevel(alert.getSeverity()))
            .isRecent(alert.getAgeInDays() < 7)
            .isHighPriority(alert.isHighPriority())
            .isStale(alert.isStale())
            .lastActionTime(alert.getLastActionTime())
            .build();
    }
    
    private static String determinePriorityLevel(CommonEnums.AlertSeverity severity) {
        return switch (severity) {
            case CRITICAL -> "URGENT";
            case HIGH -> "HIGH";
            case MEDIUM -> "NORMAL";
            case LOW -> "LOW";
        };
    }
}
