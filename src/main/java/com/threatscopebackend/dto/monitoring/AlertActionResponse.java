package com.threatscopebackend.dto.monitoring;

import com.threatscopebackend.entity.enums.AlertActionType;
import com.threatscopebackend.entity.enums.CommonEnums;
import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@Builder
public class AlertActionResponse {
    
    private Long id;
    private Long breachAlertId;
    private AlertActionType actionType;
    private CommonEnums.AlertStatus status;
    private String title;
    private String description;
    private String userMessage;
    private String adminResponse;
    private String contactEmail;
    private String contactPhone;
    private String companyName;
    private String urgencyLevel;
    private String estimatedBudget;
    private String preferredTimeline;
    private String additionalContext;
    private Boolean isProcessed;
    private Boolean isServiceRequest;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime processedAt;
    private LocalDateTime scheduledFor;
    
    // Display fields
    private String actionDisplayName;
    private String actionIcon;
    private String statusDisplayName;
    private Boolean requiresFollowUp;
    
    // Helper methods for frontend
    public boolean isPending() {
        return status == CommonEnums.AlertStatus.NEW;
    }
    
    public boolean isCompleted() {
        return status == CommonEnums.AlertStatus.RESOLVED;
    }
    
    public boolean canBeModified() {
        return isPending() && !isProcessed;
    }
}
