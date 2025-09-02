package com.threatscopebackend.service.notification;

import com.threatscopebackend.entity.postgresql.ConsultationSession;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.websocket.RealTimeNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * Service for handling only CRITICAL notifications that users should not miss
 * Replaces excessive system notifications with targeted important events only
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CriticalNotificationService {
    
    private final RealTimeNotificationService realTimeNotificationService;
    
    /**
     * Send notification when expert approves payment - CRITICAL for user to know
     */
    public void notifyPaymentApproved(ConsultationSession session) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("amount", session.getSessionPrice());
            data.put("expertName", session.getExpert() != null ? session.getExpert().getName() : "ThreatScope Expert");
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "PAYMENT_APPROVED",
                "💳 Payment Approved - Session Ready",
                String.format("Your consultation payment of $%.2f has been approved. Your session with %s is now ready to begin.", 
                    session.getSessionPrice(), 
                    session.getExpert() != null ? session.getExpert().getName() : "our expert"),
                data
            );
            
            log.info("✅ CRITICAL: Payment approved notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send payment approval notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when admin starts consultation timer - CRITICAL for billing transparency
     */
    public void notifyTimerStarted(ConsultationSession session, String adminEmail) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("timerStartedAt", session.getTimerStartedAt());
            data.put("adminEmail", adminEmail);
            data.put("sessionDuration", session.getPlan() != null ? session.getPlan().getSessionDurationMinutes() : 30);
            
            // Notify user
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "TIMER_STARTED",
                "⏰ Consultation Timer Started",
                String.format("Your consultation timer has started! You now have %d minutes of expert consultation time. Make the most of your session!", 
                    session.getPlan() != null ? session.getPlan().getSessionDurationMinutes() : 30),
                data
            );
            
            // Notify expert if available (using sendSystemNotification for expert too)
            if (session.getExpert() != null) {
                realTimeNotificationService.sendSystemNotification(
                    session.getExpert().getId(),
                    "TIMER_STARTED",
                    "⏰ Session Timer Started",
                    String.format("Consultation timer started for session with %s %s. Time is now being tracked.", 
                        session.getUser().getFirstName() != null ? session.getUser().getFirstName() : "User", 
                        session.getUser().getLastName() != null ? session.getUser().getLastName() : ""),
                    data
                );
            }
            
            log.info("✅ CRITICAL: Timer started notification sent for session {}", session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send timer start notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when admin stops consultation timer - CRITICAL for billing transparency
     */
    public void notifyTimerStopped(ConsultationSession session, String adminEmail, long durationMinutes) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("durationMinutes", durationMinutes);
            data.put("adminEmail", adminEmail);
            data.put("completedAt", session.getCompletedAt());
            
            // Notify user
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "TIMER_STOPPED",
                "⏹️ Consultation Timer Stopped",
                String.format("Your consultation session has been completed! Total time: %d minutes. Please review the session summary and rate your experience.", 
                    durationMinutes),
                data
            );
            
            // Notify expert if available
            if (session.getExpert() != null) {
                realTimeNotificationService.sendSystemNotification(
                    session.getExpert().getId(),
                    "TIMER_STOPPED",
                    "⏹️ Session Timer Stopped",
                    String.format("Session with %s %s completed. Duration: %d minutes. Session marked as complete.", 
                        session.getUser().getFirstName() != null ? session.getUser().getFirstName() : "User", 
                        session.getUser().getLastName() != null ? session.getUser().getLastName() : "", 
                        durationMinutes),
                    data
                );
            }
            
            log.info("✅ CRITICAL: Timer stopped notification sent for session {} - Duration: {} minutes", 
                    session.getId(), durationMinutes);
            
        } catch (Exception e) {
            log.error("❌ Failed to send timer stop notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when expert is assigned - IMPORTANT for user to know who they'll work with
     */
    public void notifyExpertAssigned(ConsultationSession session) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("expertName", session.getExpert().getName());
            data.put("expertSpecialization", session.getExpert().getSpecialization());
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "EXPERT_ASSIGNED",
                "👨‍💼 Expert Assigned to Your Session",
                String.format("%s has been assigned to your consultation. They specialize in %s and will help you with your security concerns.", 
                    session.getExpert().getName(), 
                    session.getExpert().getSpecialization()),
                data
            );
            
            log.info("✅ CRITICAL: Expert assigned notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send expert assignment notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when session is completed - CRITICAL for user to provide feedback
     */
    public void notifySessionCompleted(ConsultationSession session) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("expertName", session.getExpert() != null ? session.getExpert().getName() : "Expert");
            data.put("canRate", session.canBeRated());
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "SESSION_COMPLETED",
                "✅ Consultation Session Completed",
                String.format("Your consultation session has been completed. Please review the deliverables and rate your experience with %s.", 
                    session.getExpert() != null ? session.getExpert().getName() : "our expert"),
                data
            );
            
            log.info("✅ CRITICAL: Session completed notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send session completion notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when payment fails - CRITICAL for user to resolve
     */
    public void notifyPaymentFailed(ConsultationSession session, String failureReason) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("amount", session.getSessionPrice());
            data.put("failureReason", failureReason);
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "PAYMENT_FAILED",
                "❌ Payment Failed - Action Required",
                String.format("Payment of $%.2f for your consultation failed. Please update your payment method to proceed with your session.", 
                    session.getSessionPrice()),
                data
            );
            
            log.info("✅ CRITICAL: Payment failed notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send payment failure notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when session is cancelled - CRITICAL for user to know
     */
    public void notifySessionCancelled(ConsultationSession session, String reason, boolean refundIssued) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("reason", reason);
            data.put("refundIssued", refundIssued);
            data.put("amount", session.getSessionPrice());
            
            String message = refundIssued 
                ? String.format("Your consultation session has been cancelled. A refund of $%.2f has been issued. Reason: %s", 
                                session.getSessionPrice(), reason)
                : String.format("Your consultation session has been cancelled. Reason: %s", reason);
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "SESSION_CANCELLED",
                "⚠️ Session Cancelled",
                message,
                data
            );
            
            log.info("✅ CRITICAL: Session cancelled notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send session cancellation notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when admin extends session - IMPORTANT for user to know about extension
     */
    public void notifySessionExtended(ConsultationSession session, int additionalHours, String reason) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("additionalHours", additionalHours);
            data.put("reason", reason);
            data.put("newExpiryTime", session.getEffectiveExpirationTime());
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "SESSION_EXTENDED",
                "⏰ Session Extended",
                String.format("Your consultation session has been extended by %d hours. You can continue your session until %s. Reason: %s", 
                    additionalHours, 
                    session.getEffectiveExpirationTime() != null ? session.getEffectiveExpirationTime().toString() : "further notice",
                    reason),
                data
            );
            
            log.info("✅ CRITICAL: Session extended notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send session extension notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when admin changes session management - IMPORTANT for transparency
     */
    public void notifySessionManagementChanged(ConsultationSession session, boolean managed, String reason) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("managed", managed);
            data.put("reason", reason);
            
            String message = managed 
                ? String.format("Your consultation session is now under admin management and will not expire automatically. Reason: %s", reason)
                : String.format("Your consultation session has been returned to normal operation. Reason: %s", reason);
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "SESSION_MANAGEMENT_CHANGED",
                "🔧 Session Management Updated",
                message,
                data
            );
            
            log.info("✅ CRITICAL: Session management changed notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send session management notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when admin reactivates expired session - CRITICAL for user to know
     */
    public void notifySessionReactivated(ConsultationSession session, String reason) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("sessionId", session.getId());
            data.put("reason", reason);
            data.put("newExpiryTime", session.getEffectiveExpirationTime());
            
            realTimeNotificationService.sendSystemNotification(
                session.getUser().getId(),
                "SESSION_REACTIVATED",
                "🔄 Session Reactivated",
                String.format("Your expired consultation session has been reactivated and is now available for 24 more hours. Reason: %s", 
                    reason),
                data
            );
            
            log.info("✅ CRITICAL: Session reactivated notification sent to user {} for session {}", 
                    session.getUser().getId(), session.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send session reactivation notification for session {}: {}", 
                     session.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when subscription is about to expire - CRITICAL for service continuity
     */
    public void notifySubscriptionExpiring(User user, int daysRemaining) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("daysRemaining", daysRemaining);
            data.put("planType", user.getSubscription() != null ? user.getSubscription().getPlanType() : "FREE");
            
            realTimeNotificationService.sendSystemNotification(
                user.getId(),
                "SUBSCRIPTION_EXPIRING",
                "⏰ Subscription Expiring Soon",
                String.format("Your %s subscription expires in %d days. Renew now to continue accessing premium features.", 
                    user.getSubscription() != null ? user.getSubscription().getPlanType() : "subscription", 
                    daysRemaining),
                data
            );
            
            log.info("✅ CRITICAL: Subscription expiring notification sent to user {}", user.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send subscription expiring notification for user {}: {}", 
                     user.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification when account is suspended - CRITICAL for user to know
     */
    public void notifyAccountSuspended(User user, String reason) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("reason", reason);
            data.put("supportEmail", "support@threatscope.com");
            
            realTimeNotificationService.sendSystemNotification(
                user.getId(),
                "ACCOUNT_SUSPENDED",
                "🚫 Account Suspended",
                String.format("Your account has been suspended. Reason: %s. Contact support@threatscope.com for assistance.", 
                    reason),
                data
            );
            
            log.info("✅ CRITICAL: Account suspended notification sent to user {}", user.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send account suspension notification for user {}: {}", 
                     user.getId(), e.getMessage());
        }
    }
    
    /**
     * Send notification for security breach alerts - CRITICAL for user safety
     */
    public void notifySecurityBreach(User user, String breachDetails, String recommendedActions) {
        try {
            Map<String, Object> data = new HashMap<>();
            data.put("breachDetails", breachDetails);
            data.put("recommendedActions", recommendedActions);
            data.put("timestamp", System.currentTimeMillis());
            
            realTimeNotificationService.sendSystemNotification(
                user.getId(),
                "SECURITY_BREACH",
                "🔴 SECURITY ALERT - Immediate Action Required",
                String.format("Security breach detected: %s. Recommended actions: %s", 
                    breachDetails, recommendedActions),
                data
            );
            
            log.info("✅ CRITICAL: Security breach notification sent to user {}", user.getId());
            
        } catch (Exception e) {
            log.error("❌ Failed to send security breach notification for user {}: {}", 
                     user.getId(), e.getMessage());
        }
    }
    
    // ===== NOTIFICATION FILTERING RULES =====
    
    /**
     * Determines if a notification should be sent based on criticality
     * ONLY send notifications for events that require USER ACTION or are FINANCIALLY SIGNIFICANT
     */
    public boolean shouldSendNotification(String notificationType, Object context) {
        return switch (notificationType) {
            // CRITICAL - User must take action
            case "PAYMENT_APPROVED", "PAYMENT_FAILED", "SESSION_CANCELLED", 
                 "EXPERT_ASSIGNED", "SESSION_COMPLETED", "SUBSCRIPTION_EXPIRING", 
                 "ACCOUNT_SUSPENDED", "SECURITY_BREACH", "SESSION_EXTENDED",
                 "SESSION_MANAGEMENT_CHANGED", "SESSION_REACTIVATED", 
                 "TIMER_STARTED", "TIMER_STOPPED" -> true;
            
            // NON-CRITICAL - Don't spam user with these
            case "SESSION_CREATED", "EXPERT_VIEWING", "ADMIN_LOGGED_IN", 
                 "SYSTEM_MAINTENANCE", "GENERAL_UPDATE" -> false;
            
            default -> {
                log.warn("⚠️ Unknown notification type: {}. Defaulting to not send.", notificationType);
                yield false;
            }
        };
    }
}
