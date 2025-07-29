package com.threatscopebackend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * WebSocket controller for testing real-time communications
 */
@Controller
@Slf4j
public class WebSocketTestController {

    @Autowired
    private SimpMessageSendingOperations messagingTemplate;

    /**
     * Test endpoint to check if WebSocket is working
     */
    @GetMapping("/api/ws/test")
    @ResponseBody
    public Map<String, Object> testWebSocket() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "WebSocket endpoint is running");
        response.put("timestamp", LocalDateTime.now());
        response.put("endpoints", new String[]{
            "/ws/notifications (SockJS)",
            "/api/ws/notifications (SockJS with context)",
            "/ws/notifications-raw (Raw WebSocket)"
        });
        
        log.info("📡 WebSocket test endpoint called");
        
        // Send a test broadcast message
        try {
            Map<String, Object> testMessage = new HashMap<>();
            testMessage.put("type", "system_notification");
            testMessage.put("title", "WebSocket Test");
            testMessage.put("message", "WebSocket connection is working!");
            testMessage.put("timestamp", LocalDateTime.now());
            testMessage.put("priority", "low");
            
            messagingTemplate.convertAndSend("/topic/broadcasts", testMessage);
            log.info("📤 Test broadcast message sent");
            
            response.put("testMessageSent", true);
        } catch (Exception e) {
            log.error("❌ Failed to send test message: {}", e.getMessage());
            response.put("testMessageSent", false);
            response.put("error", e.getMessage());
        }
        
        return response;
    }

    /**
     * Handle test messages from clients
     */
    @MessageMapping("/test")
    @SendTo("/topic/broadcasts")
    public Map<String, Object> handleTestMessage(@Payload Map<String, Object> message, Principal principal) {
        log.info("📥 Received test message from client: {}", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "test_response");
        response.put("title", "Test Response");
        response.put("message", "Server received your test message!");
        response.put("timestamp", LocalDateTime.now());
        response.put("originalMessage", message);
        response.put("userId", principal != null ? principal.getName() : "anonymous");
        
        return response;
    }

    /**
     * Send a test notification to a specific user
     */
    @MessageMapping("/test/user")
    @SendToUser("/queue/alerts")
    public Map<String, Object> handleUserTestMessage(@Payload Map<String, Object> message, Principal principal) {
        log.info("📥 Received user test message: {}", message);
        
        Map<String, Object> response = new HashMap<>();
        response.put("type", "breach");
        response.put("title", "Test User Alert");
        response.put("message", "This is a test alert for your user account");
        response.put("priority", "medium");
        response.put("timestamp", LocalDateTime.now());
        response.put("data", Map.of("testData", "userSpecificAlert"));
        
        return response;
    }
}
