package com.threatscopebackend.controller.websocket;

import com.threatscopebackend.dto.consultation.request.SendMessageRequest;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.ChatMessage;
import com.threatscopebackend.entity.postgresql.ConsultationSession;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.repository.postgresql.ChatMessageRepository;
import com.threatscopebackend.repository.postgresql.ConsultationSessionRepository;
import com.threatscopebackend.repository.postgresql.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.HashMap;

/**
 * WebSocket controller for real-time chat + admin notifications
 * 
 * FEATURES:
 * 1. Real-time chat messages (bidirectional)
 * 2. Admin notifications (timer, extensions, status updates)
 * 3. Connection testing
 */
@Controller
@RequiredArgsConstructor
@Slf4j
public class SimpleChatController {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatMessageRepository chatMessageRepository;
    private final ConsultationSessionRepository consultationSessionRepository;
    private final UserRepository userRepository;
    
    /**
     * Real-time chat messages with database persistence
     * Client sends: /app/chat.sendMessage
     * Broadcasts to: /topic/consultation/{sessionId}/chat
     */
    @MessageMapping("/chat.sendMessage")
    @Transactional
    public void sendChatMessage(@Payload Map<String, Object> chatMessage) {
        try {
            Long sessionId = Long.valueOf(chatMessage.get("sessionId").toString());
            String content = chatMessage.get("content").toString();
            String sender = chatMessage.get("sender").toString();
            String senderName = chatMessage.get("senderName") != null ? chatMessage.get("senderName").toString() : sender;

            log.info("📤 Broadcasting real-time chat message: sessionId={}, sender={}, content={}", 
                    sessionId, senderName, content.substring(0, Math.min(content.length(), 50)) + "...");
            // Broadcast to chat topic for real-time delivery
            messagingTemplate.convertAndSend("/topic/consultation/" + sessionId + "/chat", chatMessage);
            log.info("✅ Message broadcasted to /topic/consultation/{}/chat", sessionId);
            
        } catch (Exception e) {
            log.error("❌ Error broadcasting chat message: {}", e.getMessage(), e);
        }
    }

    
    /**
     * Admin notifications (timer start, extensions, etc.)
     * Client sends: /app/consultation.notify
     * Broadcasts to: /topic/consultation/{sessionId}
     */
    @MessageMapping("/consultation.notify")
    public void sendConsultationNotification(@Payload Map<String, Object> notification) {
        try {
            Long sessionId = Long.valueOf(notification.get("sessionId").toString());
            log.info("Broadcasting admin notification: type={}, sessionId={}", 
                    notification.get("type"), sessionId);
            
            // Add timestamp if not present
            if (!notification.containsKey("timestamp")) {
                notification.put("timestamp", System.currentTimeMillis());
            }
            
            // Broadcast to consultation topic
            messagingTemplate.convertAndSend("/topic/consultation/" + sessionId, notification);
            
        } catch (Exception e) {
            log.error("Error broadcasting consultation notification: {}", e.getMessage());
        }
    }
    
    /**
     * Session status updates
     */
    @MessageMapping("/session.status")
    public void updateSessionStatus(@Payload Map<String, Object> statusUpdate) {
        try {
            Long sessionId = Long.valueOf(statusUpdate.get("sessionId").toString());
            String newStatus = statusUpdate.get("newStatus").toString();
            
            log.info("Broadcasting session status update: sessionId={}, status={}", sessionId, newStatus);
            
            // Add timestamp
            statusUpdate.put("timestamp", System.currentTimeMillis());
            
            // Broadcast to both topics
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", statusUpdate);
            messagingTemplate.convertAndSend("/topic/consultation/" + sessionId, statusUpdate);
            
        } catch (Exception e) {
            log.error("Error broadcasting session status update: {}", e.getMessage());
        }
    }
    
    /**
     * Typing indicators for chat
     */
    @MessageMapping("/typing.indicator")
    public void handleTypingIndicator(@Payload Map<String, Object> typingData) {
        try {
            Long sessionId = Long.valueOf(typingData.get("sessionId").toString());
            String sender = typingData.get("sender").toString();
            Boolean isTyping = Boolean.valueOf(typingData.get("isTyping").toString());
            
            log.debug("Broadcasting typing indicator: sessionId={}, sender={}, typing={}", 
                     sessionId, sender, isTyping);
            
            // Add timestamp
            typingData.put("timestamp", System.currentTimeMillis());
            
            // Broadcast to chat topic
            messagingTemplate.convertAndSend("/topic/consultation/" + sessionId + "/typing", typingData);
            
        } catch (Exception e) {
            log.error("Error broadcasting typing indicator: {}", e.getMessage());
        }
    }
    
    /**
     * Connection test endpoint
     */
    @MessageMapping("/test.connection")
    @SendTo("/topic/test")
    public Map<String, Object> testConnection(@Payload Map<String, Object> testData) {
        try {
            log.info("Connection test received from client");
            
            return Map.of(
                "type", "CONNECTION_TEST_SUCCESS",
                "message", "WebSocket connection is working",
                "serverTime", LocalDateTime.now().toString(),
                "timestamp", System.currentTimeMillis(),
                "clientData", testData
            );
            
        } catch (Exception e) {
            log.error("Connection test failed: {}", e.getMessage());
            return Map.of(
                "type", "CONNECTION_TEST_FAILED",
                "message", "Connection test failed: " + e.getMessage(),
                "timestamp", System.currentTimeMillis()
            );
        }
    }
    
    // ===== PROGRAMMATIC METHODS (Called by backend services) =====
    
    /**
     * Send admin notifications (timer start, extension, completion)
     */
    public void sendNotificationToSession(Long sessionId, String type, String title, String message, Map<String, Object> data) {
        try {
            Map<String, Object> notification = new HashMap<>();
            notification.put("type", type);
            notification.put("title", title);
            notification.put("message", message);
            notification.put("sessionId", sessionId);
            notification.put("timestamp", System.currentTimeMillis());
            if (data != null) {
                notification.putAll(data);
            }
            
            // Send to consultation topic
            messagingTemplate.convertAndSend("/topic/consultation/" + sessionId, notification);
            
            log.info("Admin notification sent to session {}: {} - {}", sessionId, type, title);
            
        } catch (Exception e) {
            log.error("Failed to send notification to session {}: {}", sessionId, e.getMessage());
        }
    }
    
    /**
     * Send session status update
     */
    public void sendSessionStatusUpdate(Long sessionId, String oldStatus, String newStatus) {
        try {
            Map<String, Object> statusUpdate = Map.of(
                "type", "SESSION_STATUS_UPDATE",
                "sessionId", sessionId,
                "oldStatus", oldStatus,
                "newStatus", newStatus,
                "timestamp", System.currentTimeMillis()
            );
            
            // Broadcast to both topics
            messagingTemplate.convertAndSend("/topic/session/" + sessionId + "/status", statusUpdate);
            messagingTemplate.convertAndSend("/topic/consultation/" + sessionId, statusUpdate);
            
            log.info("Session status update sent: sessionId={}, {} -> {}", sessionId, oldStatus, newStatus);
            
        } catch (Exception e) {
            log.error("Failed to send session status update: {}", e.getMessage());
        }
    }
}
