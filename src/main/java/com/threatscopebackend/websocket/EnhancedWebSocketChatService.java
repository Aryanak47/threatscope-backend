package com.threatscopebackend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.threatscopebackend.entity.postgresql.ChatMessage;
import com.threatscopebackend.entity.postgresql.ConsultationSession;
import com.threatscopebackend.entity.postgresql.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Enhanced Real-time Chat Service using WebSocket/STOMP
 * Handles all real-time communication for consultation sessions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class EnhancedWebSocketChatService {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionManager sessionManager;
    private final ObjectMapper objectMapper;
    
    // Statistics tracking
    private final AtomicLong totalChatMessagesSent = new AtomicLong(0);
    private final AtomicLong sessionStatusUpdatesSent = new AtomicLong(0);
    private final AtomicLong notificationsSent = new AtomicLong(0);
    
    /**
     * Send real-time chat message to consultation participants
     */
    public CompletableFuture<Boolean> sendChatMessage(ConsultationSession session, ChatMessage message) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending real-time chat message {} in session {}", message.getId(), session.getId());
                
                // Prepare message payload
                Map<String, Object> payload = createChatMessagePayload(message, session);
                
                // Send to user (always)
                String userDestination = "/user/" + session.getUser().getId() + "/queue/chat";
                messagingTemplate.convertAndSend(userDestination, payload);
                log.debug("Sent chat message to user: {}", userDestination);
                
                // Send to expert (if assigned and online)
                if (session.getExpert() != null) {
                    String expertDestination = "/user/" + session.getExpert().getId() + "/queue/chat";
                    messagingTemplate.convertAndSend(expertDestination, payload);
                    log.debug("Sent chat message to expert: {}", expertDestination);
                }
                
                // Send to session-specific topic (for multiple admin viewers)
                String sessionTopic = "/topic/session/" + session.getId() + "/chat";
                messagingTemplate.convertAndSend(sessionTopic, payload);
                
                totalChatMessagesSent.incrementAndGet();
                log.info("Real-time chat message sent: session={}, messageId={}, sender={}", 
                        session.getId(), message.getId(), message.getSender());
                
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send real-time chat message {}: {}", message.getId(), e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Send session status update to all participants
     */
    public CompletableFuture<Boolean> sendSessionStatusUpdate(ConsultationSession session, String oldStatus, String newStatus) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending session status update: {} -> {} for session {}", oldStatus, newStatus, session.getId());
                
                Map<String, Object> payload = Map.of(
                    "type", "SESSION_STATUS_UPDATE",
                    "sessionId", session.getId(),
                    "oldStatus", oldStatus,
                    "newStatus", newStatus,
                    "session", createSessionSummary(session),
                    "timestamp", System.currentTimeMillis()
                );
                
                // Send to user
                String userDestination = "/user/" + session.getUser().getId() + "/queue/session-updates";
                messagingTemplate.convertAndSend(userDestination, payload);
                
                // Send to expert (if assigned)
                if (session.getExpert() != null) {
                    String expertDestination = "/user/" + session.getExpert().getId() + "/queue/session-updates";
                    messagingTemplate.convertAndSend(expertDestination, payload);
                }
                
                // Send to session topic
                String sessionTopic = "/topic/session/" + session.getId() + "/updates";
                messagingTemplate.convertAndSend(sessionTopic, payload);
                
                sessionStatusUpdatesSent.incrementAndGet();
                log.info("Session status update sent: session={}, status=({} -> {})", 
                        session.getId(), oldStatus, newStatus);
                
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send session status update: {}", e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Send expert assignment notification
     */
    public CompletableFuture<Boolean> sendExpertAssignmentNotification(ConsultationSession session) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Sending expert assignment notification for session {}", session.getId());
                
                Map<String, Object> payload = Map.of(
                    "type", "EXPERT_ASSIGNED",
                    "sessionId", session.getId(),
                    "expert", Map.of(
                        "id", session.getExpert().getId(),
                        "name", session.getExpert().getName(),
                        "specialization", session.getExpert().getSpecialization()
                    ),
                    "session", createSessionSummary(session),
                    "timestamp", System.currentTimeMillis()
                );
                
                // Send to user
                String userDestination = "/user/" + session.getUser().getId() + "/queue/notifications";
                messagingTemplate.convertAndSend(userDestination, payload);
                
                // Send to expert
                String expertDestination = "/user/" + session.getExpert().getId() + "/queue/notifications";
                messagingTemplate.convertAndSend(expertDestination, payload);
                
                notificationsSent.incrementAndGet();
                log.info("Expert assignment notification sent: session={}, expert={}", 
                        session.getId(), session.getExpert().getName());
                
                return true;
                
            } catch (Exception e) {
                log.error("Failed to send expert assignment notification: {}", e.getMessage());
                return false;
            }
        });
    }
    
    /**
     * Send typing indicator to other participant
     */
    public void sendTypingIndicator(Long sessionId, Long senderId, String senderType, boolean isTyping) {
        try {
            Map<String, Object> payload = Map.of(
                "type", "TYPING_INDICATOR",
                "sessionId", sessionId,
                "senderId", senderId,
                "senderType", senderType, // USER or EXPERT
                "isTyping", isTyping,
                "timestamp", System.currentTimeMillis()
            );
            
            // Send to session topic (will reach other participant)
            String sessionTopic = "/topic/session/" + sessionId + "/typing";
            messagingTemplate.convertAndSend(sessionTopic, payload);
            
            log.debug("Typing indicator sent: session={}, sender={}, typing={}", 
                     sessionId, senderType, isTyping);
            
        } catch (Exception e) {
            log.error("Failed to send typing indicator: {}", e.getMessage());
        }
    }
    
    /**
     * Send connection test message
     */
    public void sendConnectionTest(Long userId) {
        try {
            Map<String, Object> payload = Map.of(
                "type", "CONNECTION_TEST",
                "message", "WebSocket connection test successful",
                "timestamp", System.currentTimeMillis(),
                "userId", userId
            );
            
            String destination = "/user/" + userId + "/queue/test";
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("Connection test message sent to user {}", userId);
            
        } catch (Exception e) {
            log.error("Failed to send connection test: {}", e.getMessage());
        }
    }
    
    /**
     * Send system notification to user
     */
    public void sendSystemNotification(Long userId, String type, String title, String description, Map<String, Object> data) {
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("type", "SYSTEM_NOTIFICATION");
            payload.put("notificationType", type);
            payload.put("title", title);
            payload.put("message", description != null ? description : "");
            payload.put("timestamp", System.currentTimeMillis());
            payload.put("userId", userId);
            
            // Add additional data if provided
            if (data != null) {
                payload.putAll(data);
            }
            
            // Send to user's notification queue
            String destination = "/user/" + userId + "/queue/notifications";
            messagingTemplate.convertAndSend(destination, payload);
            
            log.info("System notification sent to user {}: {} - Type: {}", userId, title, type);
            
        } catch (Exception e) {
            log.error("Failed to send system notification to user {}: {}", userId, e.getMessage());
        }
    }
    
    /**
     * Create chat message payload for WebSocket transmission
     */
    private Map<String, Object> createChatMessagePayload(ChatMessage message, ConsultationSession session) {
        Map<String, Object> payload = new HashMap<>();
        
        payload.put("type", "CHAT_MESSAGE");
        payload.put("sessionId", session.getId());
        payload.put("message", Map.of(
            "id", message.getId(),
            "content", message.getContent(),
            "sender", message.getSender().toString(),
            "messageType", message.getType().toString(),
            "isSystemMessage", message.getIsSystemMessage(),
            "createdAt", message.getCreatedAt().toString(),
            "senderName", getSenderDisplayName(message, session)
        ));
        payload.put("timestamp", System.currentTimeMillis());
        
        return payload;
    }
    
    /**
     * Create session summary for WebSocket payloads
     */
    private Map<String, Object> createSessionSummary(ConsultationSession session) {
        Map<String, Object> summary = new HashMap<>();
        
        summary.put("id", session.getId());
        summary.put("status", session.getStatus().toString());
        summary.put("paymentStatus", session.getPaymentStatus().toString());
        summary.put("plan", Map.of(
            "name", session.getPlan().getName(),
            "displayName", session.getPlan().getDisplayName(),
            "duration", session.getPlan().getSessionDurationMinutes()
        ));
        
        if (session.getExpert() != null) {
            summary.put("expert", Map.of(
                "id", session.getExpert().getId(),
                "name", session.getExpert().getName(),
                "specialization", session.getExpert().getSpecialization()
            ));
        }
        
        if (session.getStartedAt() != null) {
            summary.put("startedAt", session.getStartedAt().toString());
        }
        
        if (session.getTimerStartedAt() != null) {
            summary.put("timerStartedAt", session.getTimerStartedAt().toString());
        }
        
        return summary;
    }
    
    /**
     * Get display name for message sender
     */
    private String getSenderDisplayName(ChatMessage message, ConsultationSession session) {
        switch (message.getSender()) {
            case USER:
                User user = session.getUser();
                return user.getFirstName() + " " + user.getLastName();
            case EXPERT:
                return session.getExpert() != null ? session.getExpert().getName() : "Security Expert";
            case SYSTEM:
                return "System";
            default:
                return message.getSender().toString();
        }
    }
    
    /**
     * Check if user is online and can receive real-time messages
     */
    public boolean isUserOnline(Long userId) {
        return sessionManager.isUserOnline(userId);
    }
    
    /**
     * Get active chat sessions for a user
     */
    public int getActiveSessionCount(Long userId) {
        if (userId == null) {
            // Return total active sessions across all users
            return sessionManager.getConnectionStats().getActiveConnections();
        }
        return sessionManager.getUserSessions(userId).size();
    }
    
    /**
     * Get WebSocket service statistics
     */
    public Map<String, Object> getServiceStats() {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalChatMessagesSent", totalChatMessagesSent.get());
        stats.put("sessionStatusUpdatesSent", sessionStatusUpdatesSent.get());
        stats.put("notificationsSent", notificationsSent.get());
        stats.put("onlineUsers", sessionManager.getOnlineUsers().size());
        stats.put("activeConnections", sessionManager.getConnectionStats().getActiveConnections());
        stats.put("uptime", System.currentTimeMillis());
        stats.put("status", "ACTIVE");
        
        return stats;
    }
    
    /**
     * Reset service statistics
     */
    public void resetStats() {
        totalChatMessagesSent.set(0);
        sessionStatusUpdatesSent.set(0);
        notificationsSent.set(0);
        log.info("WebSocket chat service statistics reset");
    }
    
    /**
     * Health check for WebSocket service
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        health.put("service", "WebSocket Chat Service");
        health.put("status", "UP");
        health.put("timestamp", LocalDateTime.now().toString());
        health.put("messagingTemplate", messagingTemplate != null ? "AVAILABLE" : "NOT_AVAILABLE");
        health.put("sessionManager", sessionManager != null ? "AVAILABLE" : "NOT_AVAILABLE");
        health.put("activeConnections", sessionManager.getConnectionStats().getActiveConnections());
        
        return health;
    }
}
