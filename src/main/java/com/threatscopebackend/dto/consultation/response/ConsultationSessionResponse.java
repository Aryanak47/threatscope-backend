package com.threatscopebackend.dto.consultation.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationSessionResponse {
    private Long id;
    private String status;
    private String paymentStatus;
    private BigDecimal sessionPrice;
    private String sessionNotes;
    private String expertSummary;
    private Integer userRating;
    private String userFeedback;
    private LocalDateTime scheduledAt;
    private LocalDateTime startedAt;
    private LocalDateTime timerStartedAt; // NEW: When the actual consultation timer started
    private LocalDateTime completedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    
    // Admin extension fields
    private LocalDateTime adminExtendedUntil;
    private String extendedByAdminEmail;
    private String extensionReason;
    private Boolean isAdminManaged;
    private LocalDateTime effectiveExpirationTime;
    
    // Related objects
    private UserSummary user; // Add user information
    private ConsultationPlanResponse plan;
    private ExpertResponse expert;
    private BreachAlertSummary triggeringAlert;
    
    // Session statistics
    private Long durationMinutes;
    private Long messageCount;
    private Long fileCount;
    private Boolean canStart;
    private Boolean canRate;
    private Boolean isExpired;
    
    public static ConsultationSessionResponse fromEntity(com.threatscopebackend.entity.postgresql.ConsultationSession session) {
        return ConsultationSessionResponse.builder()
            .id(session.getId())
            .status(session.getStatus().toString())
            .paymentStatus(session.getPaymentStatus().toString())
            .sessionPrice(session.getSessionPrice())
            .sessionNotes(session.getSessionNotes())
            .expertSummary(session.getExpertSummary())
            .userRating(session.getUserRating())
            .userFeedback(session.getUserFeedback())
            .scheduledAt(session.getScheduledAt())
            .startedAt(session.getStartedAt())
            .timerStartedAt(session.getTimerStartedAt()) // NEW: Include timer start time
            .completedAt(session.getCompletedAt())
            .expiresAt(session.getExpiresAt())
            .createdAt(session.getCreatedAt())
            // Admin extension fields
            .adminExtendedUntil(session.getAdminExtendedUntil())
            .extendedByAdminEmail(session.getExtendedByAdminEmail())
            .extensionReason(session.getExtensionReason())
            .isAdminManaged(session.getIsAdminManaged())
            .effectiveExpirationTime(session.getEffectiveExpirationTime())
            // Related objects
            .user(session.getUser() != null ? UserSummary.fromEntity(session.getUser()) : null) // Add user mapping
            .plan(session.getPlan() != null ? ConsultationPlanResponse.fromEntity(session.getPlan()) : null)
            .expert(session.getExpert() != null ? ExpertResponse.fromEntity(session.getExpert()) : null)
            .triggeringAlert(session.getTriggeringAlert() != null ? 
                BreachAlertSummary.fromEntity(session.getTriggeringAlert()) : null)
            .durationMinutes(session.getDurationMinutes())
            .canStart(session.canStart())
            .canRate(session.canBeRated())
            .isExpired(session.isExpired())
            .build();
    }
}
