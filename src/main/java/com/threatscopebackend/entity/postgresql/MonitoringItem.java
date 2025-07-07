package com.threatscope.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "monitoring_items")
@Data
@NoArgsConstructor
@EqualsAndHashCode(exclude = {"user"})
public class MonitoringItem {
    
    public enum Type {
        EMAIL,
        DOMAIN,
        KEYWORD,
        USERNAME,
        PHONE_NUMBER,
        CREDIT_CARD,
        IP_ADDRESS
    }
    
    public enum Status {
        ACTIVE,
        PAUSED,
        ERROR
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
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Type type;
    
    @Column(nullable = false)
    private String value;
    
    private String displayName;
    private String description;
    
    @Enumerated(EnumType.STRING)
    private Status status = Status.ACTIVE;
    
    @Enumerated(EnumType.STRING)
    private Severity severity = Severity.MEDIUM;
    
    private boolean isCaseSensitive = false;
    private boolean isRegex = false;
    private boolean isActive = true;
    private boolean notifyOnMatch = true;
    private boolean notifyOnNewBreach = true;
    private boolean notifyOnChange = false;
    private boolean notifyOnError = true;
    
    private String[] notificationEmails;
    private String[] notificationWebhooks;
    private String[] tags;
    
    @Column(columnDefinition = "TEXT")
    private String customNote;
    
    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    private LocalDateTime updatedAt;
    
    private LocalDateTime lastCheckedAt;
    private LocalDateTime lastMatchAt;
    private int matchCount = 0;
    private int breachCount = 0;
    
    @Column(columnDefinition = "TEXT")
    private String lastError;
    
    @Column(columnDefinition = "TEXT")
    private String metadata; // JSON string for additional data
}
