package com.threatscopebackend.entity.postgresql;

import com.threatscopebackend.entity.enums.AlertActionType;
import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "alert_actions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "breachAlert"})
public class AlertAction {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "breach_alert_id", nullable = false)
    private BreachAlert breachAlert;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false)
    private AlertActionType actionType;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CommonEnums.AlertStatus status = CommonEnums.AlertStatus.NEW;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Column(name = "user_message", columnDefinition = "TEXT")
    private String userMessage; // User's additional details/requirements
    
    @Column(name = "admin_response", columnDefinition = "TEXT")
    private String adminResponse; // Admin's response to the request
    
    @Column(name = "contact_email")
    private String contactEmail;
    
    @Column(name = "contact_phone")
    private String contactPhone;
    
    @Column(name = "company_name")
    private String companyName;
    
    @Column(name = "urgency_level")
    private String urgencyLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    @Column(name = "estimated_budget")
    private String estimatedBudget;
    
    @Column(name = "preferred_timeline")
    private String preferredTimeline;
    
    @Column(name = "additional_context", columnDefinition = "TEXT")
    private String additionalContext;
    
    @Column(name = "is_processed", nullable = false)
    private Boolean isProcessed = false;
    
    @Column(name = "is_service_request", nullable = false)
    private Boolean isServiceRequest = false;
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "processed_at")
    private LocalDateTime processedAt;
    
    @Column(name = "scheduled_for")
    private LocalDateTime scheduledFor; // For consultations/meetings
    
    // Helper methods
    public boolean isPending() {
        return status == CommonEnums.AlertStatus.NEW;
    }
    
    public boolean isCompleted() {
        return status == CommonEnums.AlertStatus.RESOLVED;
    }
    
    public void markAsProcessed(String response) {
        this.isProcessed = true;
        this.adminResponse = response;
        this.processedAt = LocalDateTime.now();
        this.status = CommonEnums.AlertStatus.RESOLVED;
    }
    
    public void acknowledge() {
        this.status = CommonEnums.AlertStatus.ACKNOWLEDGED;
    }
    
    public String getStatusDisplayName() {
        return status.getDisplayName();
    }
    
    public String getActionDisplayName() {
        return actionType.getDisplayName();
    }
    
    public String getActionIcon() {
        return actionType.getIcon();
    }
    
    public boolean requiresFollowUp() {
        return actionType.isServiceRequest() && !isProcessed;
    }
    
    public boolean canBeModified() {
        return status == CommonEnums.AlertStatus.NEW || status == CommonEnums.AlertStatus.ACKNOWLEDGED;
    }
}
