package com.threatscopebackend.websocket;

import com.threatscopebackend.entity.postgresql.ChatMessage;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.ConsultationSession;
import com.threatscopebackend.entity.enums.CommonEnums;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Service for sending real-time notifications via WebSocket
 * Now integrated with the enhanced WebSocket chat service
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeNotificationService {
    
    private final EnhancedWebSocketChatService chatService;
    
    // Statistics tracking
    private final AtomicLong totalNotificationsSent = new AtomicLong(0);
    private final AtomicLong alertNotificationsSent = new AtomicLong(0);
    private final AtomicLong chatNotificationsSent = new AtomicLong(0);
    private final AtomicLong systemNotificationsSent = new AtomicLong(0);
    

    

    
    /**
     * Send real-time alert notification to user
     */
    public void sendRealTimeAlert(BreachAlert alert) {
        log.debug("Sending real-time alert notification for alert ID: {}", alert.getId());
        
        try {
            // Send real-time alert notification using messaging template
            Long userId = alert.getUser().getId();
            String userEmail = alert.getUser().getEmail();
            
            Map<String, Object> payload = Map.of(
                "type", "BREACH_ALERT",
                "alertId", alert.getId(),
                "severity", alert.getSeverity().toString(),
                "title", alert.getTitle(),
                "description", alert.getDescription() != null ? alert.getDescription() : "",
                "affectedEmail", alert.getAffectedEmail() != null ? alert.getAffectedEmail() : "",
                "timestamp", alert.getCreatedAt().toString(),
                "requiresAction", !alert.getIsAcknowledged() && !alert.isResolved()
            );
            
            // Send via enhanced chat service's system notification method
            chatService.sendSystemNotification(userId, "BREACH_ALERT", alert.getTitle(), alert.getDescription(), payload);
            
            alertNotificationsSent.incrementAndGet();
            totalNotificationsSent.incrementAndGet();
            
            log.info("Real-time alert notification sent to {} for alert {}", userEmail, alert.getId());
            
        } catch (Exception e) {
            log.error("Failed to send real-time alert notification for alert {}: {}", 
                     alert.getId(), e.getMessage());
        }
    }
    
    /**
     * Send session status update notification
     */
    public void sendSessionStatusUpdate(ConsultationSession session, String oldStatus, String newStatus) {
        log.debug("Sending session status update for session {}: {} -> {}", session.getId(), oldStatus, newStatus);
        
        try {
            // Use the enhanced chat service for session status updates
            chatService.sendSessionStatusUpdate(session, oldStatus, newStatus).thenAccept(success -> {
                if (success) {
                    systemNotificationsSent.incrementAndGet();
                    totalNotificationsSent.incrementAndGet();
                    log.info("Session status update sent: session={}, status=({} -> {})", 
                            session.getId(), oldStatus, newStatus);
                } else {
                    log.error("Failed to send session status update for session {}", session.getId());
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to send session status update: {}", e.getMessage());
        }
    }
    
    /**
     * Send system notification to user
     */
    public CompletableFuture<Boolean> sendSystemNotification(Long userId, String type, String title, String description, Map<String, Object> data) {
        log.debug("Sending system notification to user {}: {}", userId, title);
        
        return CompletableFuture.supplyAsync(() -> {
            try {
                // Use enhanced chat service's system notification method
                chatService.sendSystemNotification(userId, type, title, description, data);
                
                systemNotificationsSent.incrementAndGet();
                totalNotificationsSent.incrementAndGet();
                
                log.info("System notification sent to user {}: {} - Type: {}", userId, title, type);
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send system notification to user {}: {}", userId, e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Send expert assignment notification
     */
    public void sendExpertAssignmentNotification(ConsultationSession session) {
        log.debug("Sending expert assignment notification for session {}", session.getId());
        
        try {
            chatService.sendExpertAssignmentNotification(session).thenAccept(success -> {
                if (success) {
                    systemNotificationsSent.incrementAndGet();
                    totalNotificationsSent.incrementAndGet();
                    log.info("Expert assignment notification sent for session {}", session.getId());
                }
            });
        } catch (Exception e) {
            log.error("Failed to send expert assignment notification: {}", e.getMessage());
        }
    }
    
    /**
     * Send typing indicator
     */
    public void sendTypingIndicator(Long sessionId, Long senderId, String senderType, boolean isTyping) {
        try {
            chatService.sendTypingIndicator(sessionId, senderId, senderType, isTyping);
        } catch (Exception e) {
            log.error("Failed to send typing indicator: {}", e.getMessage());
        }
    }
    
    /**
     * Check if user is online
     */
    public boolean isUserOnline(Long userId) {
        return chatService.isUserOnline(userId);
    }
    
    /**
     * Send chat message notification via WebSocket
     */
    public void sendChatMessage(String recipientEmail, Long sessionId, ChatMessage message) {
        log.debug("Delegating chat message notification to enhanced service for session {}", sessionId);
        
        try {
            // Get the session from the message to avoid extra queries
            ConsultationSession session = message.getSession();
            
            // Use the enhanced chat service for real-time messaging
            chatService.sendChatMessage(session, message).thenAccept(success -> {
                if (success) {
                    chatNotificationsSent.incrementAndGet();
                    totalNotificationsSent.incrementAndGet();
                    log.info("Chat message notification sent via enhanced service: session={}, messageId={}", 
                            sessionId, message.getId());
                } else {
                    log.error("Enhanced chat service failed to send message for session {}", sessionId);
                }
            });
            
        } catch (Exception e) {
            log.error("Failed to send chat message notification via enhanced service: {}", e.getMessage());
        }
    }
    
    /**
    * Send monitoring update to user only for important events
    */
    public void sendMonitoringUpdate(MonitoringItem item, String status, Map<String, Object> data) {
    log.debug("Sending monitoring update for item {} to user {}: {}", 
    item.getId(), item.getUser().getId(), status);
    
    try {
    // Only send notifications for important events, not routine checks
    if (!shouldSendMonitoringNotification(status)) {
        log.debug("Skipping notification for routine status: {} for item {}", status, item.getId());
        return;
    }
    
    // Use enhanced chat service for monitoring updates
    Long userId = item.getUser().getId();
    boolean isOnline = chatService.isUserOnline(userId);
    
    if (isOnline) {
    Map<String, Object> payload = new HashMap<>(data != null ? data : new HashMap<>());
    payload.put("type", "MONITORING_UPDATE");
    payload.put("itemId", item.getId());
    payload.put("targetValue", item.getTargetValue());
    payload.put("monitorType", item.getMonitorType().toString());
    payload.put("status", status);
    payload.put("timestamp", System.currentTimeMillis());
    
    // Send via enhanced chat service system notification
    String title = getMonitoringUpdateTitle(status, item);
    String message = getMonitoringUpdateMessage(status, item);
    
    chatService.sendSystemNotification(userId, "MONITORING_UPDATE", title, message, payload);
        
    systemNotificationsSent.incrementAndGet();
        totalNotificationsSent.incrementAndGet();
        
            log.info("Monitoring update sent to user {} for item {}: {}", 
                userId, item.getId(), status);
    } else {
            log.debug("User {} is offline, monitoring update queued for item {}", userId, item.getId());
            }
            
        } catch (Exception e) {
            log.error("Failed to send monitoring update for item {}: {}", 
                     item.getId(), e.getMessage());
        }
    }
    
    /**
     * Determine if a monitoring status update should trigger a notification
     * CRITICAL: Prevents spam by only notifying for important events
     */
    private boolean shouldSendMonitoringNotification(String status) {
        return switch (status.toUpperCase()) {
            // IMPORTANT - User should know about security threats only
            case "BREACH_FOUND", "NEW_BREACH", "CRITICAL_ALERT", "HIGH_ALERT" -> true;
            
            // NOT IMPORTANT - Don't spam user (includes config changes they made themselves)
            case "CHECKED", "NO_BREACH", "SCAN_COMPLETED", "PROCESSING" -> false;
            case "SCHEDULED", "QUEUED", "IN_PROGRESS" -> false;
            case "ERROR", "FAILED", "CONNECTION_ERROR", "QUOTA_EXCEEDED" -> false;
            case "MONITORING_ENABLED", "MONITORING_DISABLED", "FREQUENCY_CHANGED" -> false;
            case "ITEM_CREATED", "ITEM_DELETED", "ITEM_UPDATED" -> false;
            
            default -> {
                log.debug("Unknown monitoring status: {}. Defaulting to not send notification.", status);
                yield false;
            }
        };
    }
    
    /**
     * Get appropriate title for monitoring update notification (security alerts only)
     */
    private String getMonitoringUpdateTitle(String status, MonitoringItem item) {
        return switch (status.toUpperCase()) {
            case "BREACH_FOUND", "NEW_BREACH" -> "New Breach Detected";
            case "CRITICAL_ALERT" -> "Critical Security Alert";
            case "HIGH_ALERT" -> "High Priority Alert";
            default -> "Security Alert";
        };
    }
    
    /**
     * Get appropriate message for monitoring update notification (security alerts only)
     */
    private String getMonitoringUpdateMessage(String status, MonitoringItem item) {
        String target = item.getTargetValue();
        String type = item.getMonitorType().toString().toLowerCase().replace("_", " ");
        
        return switch (status.toUpperCase()) {
            case "BREACH_FOUND", "NEW_BREACH" -> 
                String.format("A new security breach was detected for your %s monitoring of %s. Please review immediately.", type, target);
            case "CRITICAL_ALERT" -> 
                String.format("Critical security alert for %s monitoring of %s. Immediate action required.", type, target);
            case "HIGH_ALERT" -> 
                String.format("High priority security alert for %s monitoring of %s.", type, target);
            default -> 
                String.format("Security alert for %s monitoring of %s.", type, target);
        };
    }
    
    /**
     * Send connection test to user
     */
    public void sendConnectionTest(Long userId) {
        chatService.sendConnectionTest(userId);
    }
    
    /**
     * Get comprehensive notification statistics
     */
    public Map<String, Object> getNotificationStats() {
        Map<String, Object> stats = new HashMap<>();
        
        // Our stats
        stats.put("totalNotificationsSent", totalNotificationsSent.get());
        stats.put("alertNotificationsSent", alertNotificationsSent.get());
        stats.put("chatNotificationsSent", chatNotificationsSent.get());
        stats.put("systemNotificationsSent", systemNotificationsSent.get());
        
        // Enhanced service stats
        Map<String, Object> chatServiceStats = chatService.getServiceStats();
        stats.put("chatService", chatServiceStats);
        
        stats.put("uptime", System.currentTimeMillis());
        stats.put("status", "ACTIVE");
        
        return stats;
    }
    
    /**
     * Health check for the notification service
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        health.put("service", "Real-time Notification Service");
        health.put("status", "UP");
        health.put("chatServiceHealth", chatService.healthCheck());
        health.put("statistics", getNotificationStats());
        
        return health;
    }
}
