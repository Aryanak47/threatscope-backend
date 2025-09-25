package com.threatscopebackend.entity.postgresql;

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
@Table(name = "breach_alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(exclude = {"user", "monitoringItem"})
public class BreachAlert {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitoring_item_id")
    private MonitoringItem monitoringItem;
    
    @Column(name = "title", nullable = false)
    private String title;
    
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CommonEnums.AlertStatus status = CommonEnums.AlertStatus.NEW;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false)
    private CommonEnums.AlertSeverity severity = CommonEnums.AlertSeverity.MEDIUM;
    
    @Column(name = "breach_source")
    private String breachSource;
    
    @Column(name = "breach_date")
    private LocalDateTime breachDate;
    
    @Column(name = "breach_data", columnDefinition = "TEXT")
    private String breachData; // JSON string with breach details
    
    @Column(name = "affected_email")
    private String affectedEmail;
    
    @Column(name = "affected_domain")
    private String affectedDomain;
    
    @Column(name = "affected_username")
    private String affectedUsername;
    
    @Column(name = "data_types", columnDefinition = "TEXT")
    private String dataTypes; // JSON array of compromised data types
    
    @Column(name = "record_count")
    private Long recordCount;
    
    @Column(name = "is_verified", nullable = false)
    private Boolean isVerified = false;
    
    @Column(name = "is_false_positive", nullable = false)
    private Boolean isFalsePositive = false;
    
    @Column(name = "is_remediated", nullable = false)
    private Boolean isRemediated = false;
    
    @Column(name = "is_acknowledged", nullable = false)
    private Boolean isAcknowledged = false;
    
    @Column(name = "is_escalated", nullable = false)
    private Boolean isEscalated = false;
    
    @Column(name = "escalation_notes", columnDefinition = "TEXT")
    private String escalationNotes;
    
    @Column(name = "remediation_notes", columnDefinition = "TEXT")
    private String remediationNotes;
    
    @Column(name = "acknowledgment_notes", columnDefinition = "TEXT")
    private String acknowledgmentNotes;
    
    @Column(name = "custom_fields", columnDefinition = "TEXT")
    private String customFields; // JSON string for additional fields
    
    @Column(name = "risk_score")
    private Integer riskScore; // 0-100 risk assessment score
    
    @Column(name = "confidence_level")
    private Integer confidenceLevel; // 0-100 confidence in alert accuracy
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "viewed_at")
    private LocalDateTime viewedAt;
    
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;
    
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    
    @Column(name = "dismissed_at")
    private LocalDateTime dismissedAt;
    
    @Column(name = "escalated_at")
    private LocalDateTime escalatedAt;
    
    @Column(name = "notification_sent", nullable = false)
    private Boolean notificationSent = false;
    
    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;
    
    @Column(name = "email_notification_sent", nullable = false)
    private Boolean emailNotificationSent = false;
    
    @Column(name = "webhook_notification_sent", nullable = false)
    private Boolean webhookNotificationSent = false;
    
    // Helper methods
    public boolean isUnread() {
        return status == CommonEnums.AlertStatus.NEW;
    }
    
    public boolean isRead() {
        return viewedAt != null;
    }
    
    public boolean isResolved() {
        return status == CommonEnums.AlertStatus.RESOLVED;
    }
    
    public boolean isDismissed() {
        return status == CommonEnums.AlertStatus.DISMISSED;
    }
    
    public boolean isCritical() {
        return severity == CommonEnums.AlertSeverity.CRITICAL;
    }
    
    public boolean isHighPriority() {
        return severity == CommonEnums.AlertSeverity.HIGH || severity == CommonEnums.AlertSeverity.CRITICAL;
    }
    
    public void markAsViewed() {
        if (this.status == CommonEnums.AlertStatus.NEW) {
            this.status = CommonEnums.AlertStatus.VIEWED;
            this.viewedAt = LocalDateTime.now();
        }
    }
    
    public void acknowledge(String notes) {
        this.status = CommonEnums.AlertStatus.ACKNOWLEDGED;
        this.isAcknowledged = true;
        this.acknowledgmentNotes = notes;
        this.acknowledgedAt = LocalDateTime.now();
    }
    
    public void resolve(String notes) {
        this.status = CommonEnums.AlertStatus.RESOLVED;
        this.isRemediated = true;
        this.remediationNotes = notes;
        this.resolvedAt = LocalDateTime.now();
    }
    
    public void dismiss() {
        this.status = CommonEnums.AlertStatus.DISMISSED;
        this.dismissedAt = LocalDateTime.now();
    }
    
    public void escalate(String notes) {
        this.isEscalated = true;
        this.escalationNotes = notes;
        this.escalatedAt = LocalDateTime.now();
        // Escalated alerts typically become high priority
        if (this.severity == CommonEnums.AlertSeverity.LOW || this.severity == CommonEnums.AlertSeverity.MEDIUM) {
            this.severity = CommonEnums.AlertSeverity.HIGH;
        }
    }
    
    public void markAsFalsePositive(String reason) {
        this.isFalsePositive = true;
        this.status = CommonEnums.AlertStatus.DISMISSED;
        this.remediationNotes = "False Positive: " + (reason != null ? reason : "No reason provided");
        this.dismissedAt = LocalDateTime.now();
    }
    
    public String getSeverityDisplayName() {
        return severity.getDisplayName();
    }
    
    public String getStatusDisplayName() {
        return status.getDisplayName();
    }
    
    // Get alert age in hours
    public long getAgeInHours() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toHours();
    }
    
    // Get alert age in days
    public long getAgeInDays() {
        return java.time.Duration.between(createdAt, LocalDateTime.now()).toDays();
    }
    
    // Check if alert is stale (older than 30 days and unresolved)
    public boolean isStale() {
        return getAgeInDays() > 30 && !isResolved() && !isDismissed();
    }
    
    // Get time since last action
    public LocalDateTime getLastActionTime() {
        LocalDateTime latest = createdAt;
        if (viewedAt != null && viewedAt.isAfter(latest)) latest = viewedAt;
        if (acknowledgedAt != null && acknowledgedAt.isAfter(latest)) latest = acknowledgedAt;
        if (resolvedAt != null && resolvedAt.isAfter(latest)) latest = resolvedAt;
        if (escalatedAt != null && escalatedAt.isAfter(latest)) latest = escalatedAt;
        return latest;
    }
}
