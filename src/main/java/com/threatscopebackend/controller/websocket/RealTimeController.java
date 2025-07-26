package com.threatscopebackend.controller.websocket;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.websocket.RealTimeNotificationService;
import com.threatscopebackend.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * REST controller for WebSocket and real-time notification management
 * Provides admin capabilities and user notification features
 */
@RestController
@RequestMapping("/api/realtime")
@RequiredArgsConstructor
@Slf4j
public class RealTimeController {

    private final RealTimeNotificationService realTimeNotificationService;
    private final WebSocketSessionManager sessionManager;

    /**
     * Get WebSocket connection statistics (Admin only)
     */
    @GetMapping("/stats")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> getWebSocketStats() {
        try {
            Map<String, Object> stats = realTimeNotificationService.getNotificationStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting WebSocket stats: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get detailed session information (Admin only)
     */
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<WebSocketSessionManager.ConnectionStats> getDetailedStats() {
        try {
            WebSocketSessionManager.ConnectionStats stats = sessionManager.getConnectionStats();
            return ResponseEntity.ok(stats);
        } catch (Exception e) {
            log.error("Error getting session details: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Get list of online users (Admin only)
     */
    @GetMapping("/online-users")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Long>> getOnlineUsers() {
        try {
            List<Long> onlineUsers = sessionManager.getOnlineUsers();
            return ResponseEntity.ok(onlineUsers);
        } catch (Exception e) {
            log.error("Error getting online users: {}", e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Check if current user is online
     */
    @GetMapping("/status")
    public ResponseEntity<Map<String, Object>> getConnectionStatus(@AuthenticationPrincipal UserPrincipal user) {
        try {
            boolean isOnline = sessionManager.isUserOnline(user.getId());
            List<String> sessions = sessionManager.getUserSessions(user.getId());
            
            Map<String, Object> status = Map.of(
                "isOnline", isOnline,
                "sessionCount", sessions.size(),
                "lastActivity", System.currentTimeMillis()
            );
            
            return ResponseEntity.ok(status);
        } catch (Exception e) {
            log.error("Error getting connection status for user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.internalServerError().build();
        }
    }

    /**
     * Send test notification to current user
     */
    @PostMapping("/test-notification")
    public ResponseEntity<Map<String, Object>> sendTestNotification(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, String> request) {
        try {
            String message = request.getOrDefault("message", "Test notification from ThreatScope");
            
            CompletableFuture<Boolean> result = realTimeNotificationService.sendSystemNotification(
                user.getId(), 
                "TEST", 
                "Test Notification", 
                message, 
                Map.of("timestamp", System.currentTimeMillis())
            );
            
            boolean sent = result.get();
            
            Map<String, Object> response = Map.of(
                "sent", sent,
                "message", sent ? "Test notification sent successfully" : "User is offline or notification failed"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending test notification to user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to send test notification"));
        }
    }

    /**
     * Send system broadcast to all online users (Admin only)
     */
    @PostMapping("/broadcast")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> sendBroadcast(@RequestBody Map<String, String> request) {
        try {
            String title = request.getOrDefault("title", "System Announcement");
            String message = request.getOrDefault("message", "System announcement from ThreatScope");
            String severityStr = request.getOrDefault("severity", "MEDIUM");
            
            CommonEnums.AlertSeverity severity;
            try {
                severity = CommonEnums.AlertSeverity.valueOf(severityStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                severity = CommonEnums.AlertSeverity.MEDIUM;
            }
            
            CompletableFuture<Integer> result = realTimeNotificationService.broadcastAlert(title, message, severity);
            int recipientCount = result.get();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "recipientCount", recipientCount,
                "message", "Broadcast sent to " + recipientCount + " online users"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending broadcast: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to send broadcast"));
        }
    }

    /**
     * Force disconnect a user's sessions (Admin only)
     */
    @PostMapping("/disconnect/{userId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, String>> disconnectUser(@PathVariable Long userId) {
        try {
            sessionManager.disconnectUser(userId);
            
            Map<String, String> response = Map.of(
                "success", "true",
                "message", "User " + userId + " has been disconnected"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error disconnecting user {}: {}", userId, e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to disconnect user"));
        }
    }

    /**
     * Clean up stale sessions (Admin only)
     */
    @PostMapping("/cleanup")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Map<String, Object>> cleanupStaleSessions() {
        try {
            int cleanedCount = sessionManager.cleanupStaleSessions();
            
            Map<String, Object> response = Map.of(
                "success", true,
                "cleanedSessions", cleanedCount,
                "message", "Cleaned up " + cleanedCount + " stale sessions"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error cleaning up stale sessions: {}", e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to cleanup sessions"));
        }
    }

    /**
     * Send dashboard update to current user
     */
    @PostMapping("/dashboard-update")
    public ResponseEntity<Map<String, Object>> sendDashboardUpdate(
            @AuthenticationPrincipal UserPrincipal user,
            @RequestBody Map<String, Object> request) {
        try {
            String updateType = (String) request.getOrDefault("updateType", "GENERAL");
            @SuppressWarnings("unchecked")
            Map<String, Object> data = (Map<String, Object>) request.getOrDefault("data", Map.of());
            
            CompletableFuture<Boolean> result = realTimeNotificationService.sendDashboardUpdate(
                user.getId(), updateType, data
            );
            
            boolean sent = result.get();
            
            Map<String, Object> response = Map.of(
                "sent", sent,
                "message", sent ? "Dashboard update sent" : "User is offline"
            );
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error sending dashboard update to user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.internalServerError()
                .body(Map.of("error", "Failed to send dashboard update"));
        }
    }
}
