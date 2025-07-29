package com.threatscopebackend.dto.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.service.monitoring.PasswordMaskingService;
import lombok.Data;
import lombok.Builder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.stereotype.Component;

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
    
    // Password visibility info for frontend
    private Boolean canViewFullPasswords;
    private String passwordMaskingInfo;
    
    /**
     * Create response with subscription-based password masking
     */
    public static BreachAlertResponse fromEntityWithUser(BreachAlert alert, User user, PasswordMaskingService passwordMaskingService) {
        if (alert == null) return null;
        
        // Get user's plan type for password masking
        CommonEnums.PlanType planType = user != null && user.getSubscription() != null ? 
            user.getSubscription().getPlanType() : CommonEnums.PlanType.FREE;
            
        // Mask sensitive data based on subscription
        String maskedBreachData = passwordMaskingService != null ? 
            passwordMaskingService.maskSensitiveDataBasedOnPlan(alert.getBreachData(), planType) : 
            alert.getBreachData();
            
        boolean canViewPasswords = passwordMaskingService != null && 
            passwordMaskingService.canViewFullPasswords(planType);
            
        String maskingInfo = passwordMaskingService != null ? 
            passwordMaskingService.getMaskingDescription(planType) : 
            "Password visibility depends on subscription plan.";
        
        return BreachAlertResponse.builder()
            .id(alert.getId())
            .title(alert.getTitle())
            .description(alert.getDescription())
            .status(alert.getStatus())
            .severity(alert.getSeverity())
            .breachSource(alert.getBreachSource())
            .breachDate(alert.getBreachDate())
            .breachData(maskedBreachData)  // ✅ Backend masked based on subscription
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
            .canViewFullPasswords(canViewPasswords)
            .passwordMaskingInfo(maskingInfo)
            .build();
    }
    
    /**
     * Legacy method for backward compatibility
     * @deprecated Use fromEntityWithUser instead for proper password masking
     */
    @Deprecated
    public static BreachAlertResponse fromEntity(BreachAlert alert) {
        // Use the new method with no user (defaults to FREE plan masking)
        return fromEntityWithUser(alert, null, null);
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
