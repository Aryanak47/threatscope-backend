package com.threatscopebackend.entity.postgresql;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "audit_logs")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false)
    private AuditAction action;

    @Column(name = "resource_type")
    private String resourceType;

    @Column(name = "resource_id")
    private String resourceId;

    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues; // JSON string

    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues; // JSON string

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    @Column(name = "success")
    private Boolean success = true;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public enum AuditAction {
        USER_CREATED,
        USER_UPDATED,
        USER_DELETED,
        USER_LOGIN,
        USER_LOGOUT,
        SEARCH_PERFORMED,
        MONITORING_CREATED,
        MONITORING_UPDATED,
        MONITORING_DELETED,
        ALERT_CREATED,
        ALERT_READ,
        ALERT_DISMISSED,
        SUBSCRIPTION_CREATED,
        SUBSCRIPTION_UPDATED,
        SUBSCRIPTION_CANCELLED,
        EXPORT_GENERATED,
        API_KEY_CREATED,
        API_KEY_DELETED,
        ADMIN_ACTION
    }

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
