package com.threatscopebackend.entity.postgresql;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "consultation_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationSession {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "expert_id")
    private Expert expert;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "plan_id", nullable = false)
    private ConsultationPlan plan;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "triggering_alert_id")
    private BreachAlert triggeringAlert; // The alert that triggered this consultation
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private SessionStatus status = SessionStatus.PENDING;
    
    @Column(name = "session_price", precision = 10, scale = 2, nullable = false)
    private BigDecimal sessionPrice;
    
    @Column(name = "payment_status", nullable = false)
    @Enumerated(EnumType.STRING)
    private PaymentStatus paymentStatus = PaymentStatus.PENDING;
    
    @Column(name = "payment_intent_id")
    private String paymentIntentId; // For payment processing
    
    @Column(name = "session_notes", columnDefinition = "TEXT")
    private String sessionNotes; // User's initial problem description
    
    @Column(name = "expert_summary", columnDefinition = "TEXT")
    private String expertSummary; // Expert's session summary
    
    @Column(name = "deliverables_provided", columnDefinition = "TEXT")
    private String deliverablesProvided; // JSON array of what was delivered
    
    @Column(name = "user_rating")
    private Integer userRating; // 1-5 star rating
    
    @Column(name = "user_feedback", columnDefinition = "TEXT")
    private String userFeedback;
    
    @Column(name = "expert_feedback", columnDefinition = "TEXT")
    private String expertFeedback;
    
    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;
    
    @Column(name = "started_at")
    private LocalDateTime startedAt;
    
    @Column(name = "timer_started_at")
    private LocalDateTime timerStartedAt; // When the actual session timer starts (expert's first response)
    
    @Column(name = "completed_at")
    private LocalDateTime completedAt;
    
    @Column(name = "expires_at") 
    private LocalDateTime expiresAt; // When session expires if not started
    
    // Admin extension fields
    @Column(name = "admin_extended_until")
    private LocalDateTime adminExtendedUntil; // Admin can extend session beyond normal expiry
    
    @Column(name = "extended_by_admin_email")
    private String extendedByAdminEmail; // Track which admin extended the session
    
    @Column(name = "extension_reason", columnDefinition = "TEXT")
    private String extensionReason; // Why the session was extended
    
    @Column(name = "is_admin_managed")
    private Boolean isAdminManaged = false; // Allow null temporarily
    
    @CreationTimestamp
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ChatMessage> chatMessages;
    
    @OneToMany(mappedBy = "session", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JsonIgnore
    private List<ConsultationFile> files;
    
    // Enums
    public enum SessionStatus {
        PENDING,        // Payment pending or expert assignment pending
        ASSIGNED,       // Expert assigned, waiting to start
        ACTIVE,         // Session in progress
        COMPLETED,      // Session finished successfully
        CANCELLED,      // Session cancelled
        EXPIRED,        // Session expired without starting
        REFUNDED        // Session refunded
    }
    
    public enum PaymentStatus {
        PENDING,
        PAID,
        FAILED,
        REFUNDED,
        PARTIAL_REFUND
    }
    
    // Helper methods
    public boolean canStart() {
        return status == SessionStatus.ASSIGNED && 
               paymentStatus == PaymentStatus.PAID &&
               expert != null;
    }
    
    public boolean isActive() {
        return status == SessionStatus.ACTIVE;
    }
    
    public boolean isCompleted() {
        return status == SessionStatus.COMPLETED;
    }
    
    public boolean canBeRated() {
        return status == SessionStatus.COMPLETED && userRating == null;
    }
    
    public void startSession() {
        this.status = SessionStatus.ACTIVE;
        this.startedAt = LocalDateTime.now();
        // Timer will start when expert sends first message
    }
    
    public void startTimer() {
        if (this.timerStartedAt == null) {
            this.timerStartedAt = LocalDateTime.now();
        }
    }
    
    public void completeSession(String expertSummary, String deliverables) {
        this.status = SessionStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
        this.expertSummary = expertSummary;
        this.deliverablesProvided = deliverables;
    }
    
    public void assignExpert(Expert expert) {
        this.expert = expert;
        this.status = SessionStatus.ASSIGNED;
        expert.recordSession();
    }
    

    
    public long getDurationMinutes() {
        if (startedAt != null && completedAt != null) {
            return java.time.Duration.between(startedAt, completedAt).toMinutes();
        }
        return 0;
    }
    
    /**
     * ENHANCED: Check if session is expired with admin extension support
     */
    public boolean isExpired() {
        LocalDateTime now = LocalDateTime.now();
        
        // If admin has extended the session, use the extended time
        if (adminExtendedUntil != null) {
            return now.isAfter(adminExtendedUntil);
        }
        
        // If session is admin-managed, it doesn't expire automatically
        if (Boolean.TRUE.equals(isAdminManaged)) {
            return false;
        }
        
        // Normal expiration check
        return expiresAt != null && now.isAfter(expiresAt);
    }
    
    /**
     * NEW: Admin can extend session expiration
     */
    public void extendSession(int additionalHours, String adminEmail, String reason) {
        LocalDateTime baseTime = adminExtendedUntil != null ? adminExtendedUntil : 
                                (expiresAt != null ? expiresAt : LocalDateTime.now());
        
        this.adminExtendedUntil = baseTime.plusHours(additionalHours);
        this.extendedByAdminEmail = adminEmail;
        this.extensionReason = reason;
        this.isAdminManaged = true;
        
        // Add extension note to session notes
        String extensionNote = String.format("\n\n[ADMIN EXTENSION - %s by %s]: Extended by %d hours. Reason: %s", 
                LocalDateTime.now(), adminEmail, additionalHours, reason);
        this.sessionNotes = (this.sessionNotes != null ? this.sessionNotes : "") + extensionNote;
    }
    
    /**
     * NEW: Admin can set session as admin-managed (no auto-expiry)
     */
    public void setAdminManaged(boolean managed, String adminEmail, String reason) {
        this.isAdminManaged = managed;
        this.extendedByAdminEmail = adminEmail;
        this.extensionReason = reason;
        
        String managementNote = String.format("\n\n[ADMIN MANAGEMENT - %s by %s]: Session %s admin management. Reason: %s", 
                LocalDateTime.now(), adminEmail, managed ? "placed under" : "removed from", reason);
        this.sessionNotes = (this.sessionNotes != null ? this.sessionNotes : "") + managementNote;
    }
    
    /**
     * NEW: Get effective expiration time (considering admin extensions)
     */
    public LocalDateTime getEffectiveExpirationTime() {
        if (Boolean.TRUE.equals(isAdminManaged) && adminExtendedUntil == null) {
            return null; // Never expires
        }
        
        if (adminExtendedUntil != null) {
            return adminExtendedUntil;
        }
        
        return expiresAt;
    }
    
    /**
     * NEW: Check if session can be accessed by admin (even if expired)
     */
    public boolean canAdminAccess() {
        // Admins can always access sessions, even expired ones
        return true;
    }
    
    /**
     * NEW: Check if user can access session (respects expiration)
     */
    public boolean canUserAccess() {
        // Users cannot access expired sessions unless admin-managed
        if (isExpired() && !Boolean.TRUE.equals(isAdminManaged)) {
            return false;
        }
        
        return status != SessionStatus.CANCELLED;
    }
    
    /**
     * NEW: Get time until expiration (considering extensions)
     */
    public Long getMinutesUntilExpiration() {
        LocalDateTime effectiveExpiry = getEffectiveExpirationTime();
        
        if (effectiveExpiry == null) {
            return null; // Never expires
        }
        
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(effectiveExpiry)) {
            return 0L; // Already expired
        }
        
        return java.time.Duration.between(now, effectiveExpiry).toMinutes();
    }
    
    /**
     * NEW: Check if session needs admin attention
     */
    public boolean needsAdminAttention() {
        // Sessions in PENDING status for more than 1 hour need attention
        if (status == SessionStatus.PENDING && 
            createdAt.isBefore(LocalDateTime.now().minusHours(1))) {
            return true;
        }
        
        // Sessions that are about to expire need attention
        Long minutesLeft = getMinutesUntilExpiration();
        if (minutesLeft != null && minutesLeft <= 60 && status == SessionStatus.ASSIGNED) {
            return true;
        }
        
        return false;
    }
    
    @PrePersist
    protected void onCreate() {
        if (expiresAt == null) {
            // Sessions expire 24 hours after creation if not started
            expiresAt = LocalDateTime.now().plusHours(24);
        }
        
        // Ensure isAdminManaged is never null
        if (isAdminManaged == null) {
            isAdminManaged = false;
        }
    }
    
    // SAFE toString to prevent StackOverflow
    @Override
    public String toString() {
        return "ConsultationSession{" +
                "id=" + id +
                ", status=" + status +
                ", paymentStatus=" + paymentStatus +
                ", expertId=" + (expert != null ? expert.getId() : null) +
                ", userId=" + (user != null ? user.getId() : null) +
                ", isAdminManaged=" + isAdminManaged +
                ", effectiveExpiry=" + getEffectiveExpirationTime() +
                '}';
    }
}
