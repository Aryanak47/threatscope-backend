package com.threatscopebackend.controller.api;

import com.threatscopebackend.dto.consultation.response.ChatMessageResponse;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.service.consultation.ChatService;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.async.DeferredResult;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Long polling controller for chat messages only (WebSocket fallback)
 * Provides reliable chat message delivery when WebSocket fails
 */
@RestController
@RequestMapping("/api/consultation")
@RequiredArgsConstructor
@Slf4j
public class LongPollingController {
    
    private final ChatService chatService;
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(5);
    
    /**
     * Long polling endpoint for new chat messages
     * GET /api/consultation/{sessionId}/poll/messages?since=2024-01-01T10:00:00
     * 
     * This endpoint holds the connection open and only responds when:
     * 1. New chat messages arrive
     * 2. Timeout is reached (default 30 seconds)
     */
    @GetMapping("/{sessionId}/poll/messages")
    public DeferredResult<ResponseEntity<ApiResponse<List<ChatMessageResponse>>>> pollForNewMessages(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String since,
            @RequestParam(defaultValue = "30000") long timeout,
            @CurrentUser UserPrincipal currentUser) {
        
        log.debug("Long polling started for new messages in session {} from user {} (timeout: {}ms)", 
                 sessionId, currentUser.getId(), timeout);
        
        DeferredResult<ResponseEntity<ApiResponse<List<ChatMessageResponse>>>> deferredResult = 
            new DeferredResult<>(timeout);
        
        // Parse since timestamp
        LocalDateTime sinceTime = null;
        if (since != null) {
            try {
                sinceTime = LocalDateTime.parse(since);
            } catch (Exception e) {
                log.warn("Invalid 'since' parameter: {}", since);
                sinceTime = LocalDateTime.now().minusMinutes(5); // Default to 5 minutes ago
            }
        } else {
            sinceTime = LocalDateTime.now().minusMinutes(5); // Default to 5 minutes ago
        }
        
        final LocalDateTime pollSince = sinceTime;
        
        // Set timeout handler
        deferredResult.onTimeout(() -> {
            log.debug("Long polling timeout for session {} - no new messages", sessionId);
            deferredResult.setResult(ResponseEntity.ok(
                ApiResponse.success("No new messages", List.<ChatMessageResponse>of())
            ));
        });
        
        // Set error handler
        deferredResult.onError((error) -> {
            log.error("Long polling error for session {}: {}", sessionId, error);
            deferredResult.setResult(ResponseEntity.badRequest().body(
                ApiResponse.error("Polling error: " + error.getMessage())
            ));
        });
        
        // Start polling for new messages
        scheduler.scheduleAtFixedRate(() -> {
            try {
                if (deferredResult.isSetOrExpired()) {
                    return; // Stop polling if result is already set
                }
                
                // Check for new messages since the specified time
                List<ChatMessageResponse> newMessages = chatService.getRecentMessages(
                    sessionId, currentUser, pollSince
                );
                
                if (!newMessages.isEmpty()) {
                    log.info("Found {} new messages in session {} via long polling", 
                            newMessages.size(), sessionId);
                    
                    deferredResult.setResult(ResponseEntity.ok(
                        ApiResponse.success("New messages found", newMessages)
                    ));
                }
                
            } catch (Exception e) {
                log.error("Error checking for new messages in session {}: {}", sessionId, e.getMessage());
                if (!deferredResult.isSetOrExpired()) {
                    deferredResult.setErrorResult(e);
                }
            }
        }, 0, 2, TimeUnit.SECONDS); // Check every 2 seconds
        
        return deferredResult;
    }
    
    /**
     * Simple endpoint to get recent messages (no long polling)
     * GET /api/consultation/{sessionId}/messages/recent?since=2024-01-01T10:00:00
     */
    @GetMapping("/{sessionId}/messages/recent")
    public ResponseEntity<ApiResponse<List<ChatMessageResponse>>> getRecentMessages(
            @PathVariable Long sessionId,
            @RequestParam(required = false) String since,
            @CurrentUser UserPrincipal currentUser) {
        
        try {
            log.debug("Getting recent messages for session {} from user {}", sessionId, currentUser.getId());
            
            // Parse since timestamp
            LocalDateTime sinceTime = null;
            if (since != null) {
                try {
                    sinceTime = LocalDateTime.parse(since);
                } catch (Exception e) {
                    log.warn("Invalid 'since' parameter: {}", since);
                    sinceTime = LocalDateTime.now().minusMinutes(5);
                }
            } else {
                sinceTime = LocalDateTime.now().minusMinutes(5);
            }
            
            List<ChatMessageResponse> messages = chatService.getRecentMessages(
                sessionId, currentUser, sinceTime
            );
            
            return ResponseEntity.ok(ApiResponse.success("Recent messages retrieved", messages));
            
        } catch (Exception e) {
            log.error("Error getting recent messages for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest().body(
                ApiResponse.error("Failed to get recent messages: " + e.getMessage())
            );
        }
    }
    
    /**
     * Health check endpoint for long polling
     * GET /api/consultation/poll/health
     */
    @GetMapping("/poll/health")
    public ResponseEntity<ApiResponse<Map<String, Object>>> longPollingHealth() {
        Map<String, Object> health = new HashMap<>();
        health.put("service", "Long Polling Chat Fallback");
        health.put("status", "UP");
        health.put("schedulerActive", !scheduler.isShutdown());
        health.put("poolSize", 5);
        health.put("purpose", "WebSocket fallback for chat messages only");
        health.put("timestamp", System.currentTimeMillis());
        
        return ResponseEntity.ok(ApiResponse.success("Long polling service is healthy", health));
    }
    
    /**
     * Test endpoint to verify polling functionality
     * GET /api/consultation/{sessionId}/poll/test
     */
    @GetMapping("/{sessionId}/poll/test")
    public ResponseEntity<ApiResponse<Map<String, Object>>> testPolling(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal currentUser) {
        
        try {
            // Verify user has access to this session
            chatService.getSessionChat(sessionId, currentUser);
            
            Map<String, Object> testResult = Map.of(
                "sessionId", sessionId,
                "userId", currentUser.getId(),
                "pollingAvailable", true,
                "message", "Polling endpoint is working for this session",
                "timestamp", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(ApiResponse.success("Polling test successful", testResult));
            
        } catch (Exception e) {
            log.error("Polling test failed for session {}: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest().body(
                ApiResponse.error("Polling test failed: " + e.getMessage())
            );
        }
    }
}
