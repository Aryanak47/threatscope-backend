package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "breach_alerts")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"user"})
public class BreachAlert {
    
    public enum Status {
        UNREAD,
        READ,
        ARCHIVED,
        IGNORED
    }
    
    public enum Severity {
        LOW,
        MEDIUM,
        HIGH,
        CRITICAL
    }
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "monitoring_item_id")
    private MonitoringItem monitoringItem;
    
    @Column(nullable = false)
    private String title;
    
    @Column(columnDefinition = "TEXT")
    private String description;
    
    @Enumerated(EnumType.STRING)
    private Status status = Status.UNREAD;
    
    @Enumerated(EnumType.STRING)
    private Severity severity = Severity.MEDIUM;
    
    @Column(name = "breach_source")
    private String breachSource;
    
    @Column(name = "breach_date")
    private LocalDateTime breachDate;
    
    @Column(name = "breach_data", columnDefinition = "TEXT")
    private String breachData; // JSON string with breach details
    
    @Column(name = "is_false_positive")
    private boolean isFalsePositive = false;
    
    @Column(name = "is_remediated")
    private boolean isRemediated = false;
    
    @Column(name = "is_acknowledged")
    private boolean isAcknowledged = false;
    
    @Column(name = "is_escalated")
    private boolean isEscalated = false;
    
    @Column(name = "escalation_notes", columnDefinition = "TEXT")
    private String escalationNotes;
    
    @Column(name = "remediation_notes", columnDefinition = "TEXT")
    private String remediationNotes;
    
    @Column(name = "custom_fields", columnDefinition = "TEXT")
    private String customFields; // JSON string for additional fields
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    private LocalDateTime readAt;
    private LocalDateTime archivedAt;
    private LocalDateTime resolvedAt;
    
    @Column(name = "notification_sent")
    private boolean notificationSent = false;
    
    @Column(name = "notification_sent_at")
    private LocalDateTime notificationSentAt;
}
