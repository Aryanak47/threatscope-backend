package com.threatscopebackend.websocket;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Async;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.concurrent.CompletableFuture;

/**
 * Real-time notification service for instant WebSocket-based alerts
 * Provides immediate delivery of critical security notifications
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RealTimeNotificationService {

    private final SimpMessagingTemplate messagingTemplate;
    private final WebSocketSessionManager sessionManager;

    /**
     * Send real-time alert notification to user
     */
    @Async
    public CompletableFuture<Boolean> sendRealTimeAlert(BreachAlert alert) {
        try {
            User user = alert.getUser();
            
            // Check if user is online
            if (!sessionManager.isUserOnline(user.getId())) {
                log.debug("👤 User {} is offline, skipping real-time notification for alert {}", 
                         user.getId(), alert.getId());
                return CompletableFuture.completedFuture(false);
            }

            // Build notification payload
            Map<String, Object> notification = buildAlertNotification(alert);
            
            // Send to user's personal channel
            String destination = "/user/" + user.getId() + "/queue/alerts";
            messagingTemplate.convertAndSend(destination, notification);
            
            // Send to general alert channel (for dashboard updates)
            messagingTemplate.convertAndSend("/topic/alerts/" + user.getId(), notification);
            
            log.info("⚡ Real-time alert sent to user {} (alert: {}): {}", 
                    user.getId(), alert.getId(), alert.getTitle());
            
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            log.error("❌ Failed to send real-time alert {}: {}", alert.getId(), e.getMessage(), e);
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send real-time monitoring status update
     */
    @Async
    public CompletableFuture<Boolean> sendMonitoringUpdate(MonitoringItem item, String status, Map<String, Object> details) {
        try {
            User user = item.getUser();
            
            if (!sessionManager.isUserOnline(user.getId())) {
                return CompletableFuture.completedFuture(false);
            }

            Map<String, Object> update = buildMonitoringUpdate(item, status, details);
            
            // Send to monitoring updates channel
            String destination = "/user/" + user.getId() + "/queue/monitoring";
            messagingTemplate.convertAndSend(destination, update);
            
            log.debug("📊 Monitoring update sent to user {} (item: {}): {}", 
                     user.getId(), item.getId(), status);
            
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            log.error("❌ Failed to send monitoring update for item {}: {}", item.getId(), e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Send system notification to user
     */
    @Async
    public CompletableFuture<Boolean> sendSystemNotification(Long userId, String type, String title, String message, Map<String, Object> data) {
        try {
            if (!sessionManager.isUserOnline(userId)) {
                return CompletableFuture.completedFuture(false);
            }

            Map<String, Object> notification = Map.of(
                "type", "SYSTEM_NOTIFICATION",
                "notificationType", type,
                "title", title,
                "message", message,
                "data", data != null ? data : Map.of(),
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "id", System.currentTimeMillis() + "_" + userId
            );
            
            String destination = "/user/" + userId + "/queue/system";
            messagingTemplate.convertAndSend(destination, notification);
            
            log.info("📢 System notification sent to user {}: {}", userId, title);
            
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            log.error("❌ Failed to send system notification to user {}: {}", userId, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    /**
     * Broadcast alert to all online users (admin feature)
     */
    @Async
    public CompletableFuture<Integer> broadcastAlert(String title, String message, CommonEnums.AlertSeverity severity) {
        try {
            List<Long> onlineUsers = sessionManager.getOnlineUsers();
            
            Map<String, Object> broadcast = Map.of(
                "type", "BROADCAST_ALERT",
                "title", title,
                "message", message,
                "severity", severity.name(),
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                "id", "broadcast_" + System.currentTimeMillis()
            );
            
            // Send to global broadcast channel
            messagingTemplate.convertAndSend("/topic/broadcasts", broadcast);
            
            log.info("📢 Broadcast alert sent to {} online users: {}", onlineUsers.size(), title);
            
            return CompletableFuture.completedFuture(onlineUsers.size());
            
        } catch (Exception e) {
            log.error("❌ Failed to broadcast alert: {}", e.getMessage());
            return CompletableFuture.completedFuture(0);
        }
    }

    /**
     * Send real-time dashboard update
     */
    @Async  
    public CompletableFuture<Boolean> sendDashboardUpdate(Long userId, String updateType, Map<String, Object> data) {
        try {
            if (!sessionManager.isUserOnline(userId)) {
                return CompletableFuture.completedFuture(false);
            }

            Map<String, Object> update = Map.of(
                "type", "DASHBOARD_UPDATE",
                "updateType", updateType,
                "data", data,
                "timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            );
            
            String destination = "/user/" + userId + "/queue/dashboard";
            messagingTemplate.convertAndSend(destination, update);
            
            log.debug("📊 Dashboard update sent to user {}: {}", userId, updateType);
            
            return CompletableFuture.completedFuture(true);
            
        } catch (Exception e) {
            log.error("❌ Failed to send dashboard update to user {}: {}", userId, e.getMessage());
            return CompletableFuture.completedFuture(false);
        }
    }

    // Helper methods for building notification payloads

    /**
     * Build alert notification payload
     */
    private Map<String, Object> buildAlertNotification(BreachAlert alert) {
        Map<String, Object> notification = new HashMap<>();
        
        notification.put("type", "BREACH_ALERT");
        notification.put("id", alert.getId());
        notification.put("title", alert.getTitle());
        notification.put("description", alert.getDescription());
        notification.put("severity", alert.getSeverity().name());
        notification.put("breachSource", alert.getBreachSource());
        notification.put("breachDate", alert.getBreachDate() != null ? 
                        alert.getBreachDate().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        notification.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        notification.put("isRead", false);
        notification.put("requiresAction", alert.getSeverity() == CommonEnums.AlertSeverity.CRITICAL);
        
        // Add monitoring item context
        MonitoringItem item = alert.getMonitoringItem();
        if (item != null) {
            Map<String, Object> itemInfo = Map.of(
                "id", item.getId(),
                "targetValue", item.getTargetValue(),
                "monitorType", item.getMonitorType().name(),
                "monitorName", item.getMonitorName() != null ? item.getMonitorName() : ""
            );
            notification.put("monitoringItem", itemInfo);
        }
        
        // Add action buttons for frontend
        List<Map<String, String>> actions = new ArrayList<>();
        actions.add(Map.of("action", "mark_read", "label", "Mark as Read"));
        actions.add(Map.of("action", "view_details", "label", "View Details"));
        
        if (alert.getSeverity() == CommonEnums.AlertSeverity.CRITICAL) {
            actions.add(Map.of("action", "escalate", "label", "Escalate"));
        }
        
        notification.put("actions", actions);
        
        return notification;
    }

    /**
     * Build monitoring update payload
     */
    private Map<String, Object> buildMonitoringUpdate(MonitoringItem item, String status, Map<String, Object> details) {
        Map<String, Object> update = new HashMap<>();
        
        update.put("type", "MONITORING_UPDATE");
        update.put("itemId", item.getId());
        update.put("targetValue", item.getTargetValue());
        update.put("monitorType", item.getMonitorType().name());
        update.put("monitorName", item.getMonitorName());
        update.put("status", status);
        update.put("frequency", item.getFrequency().name());
        update.put("lastChecked", item.getLastChecked() != null ? 
                   item.getLastChecked().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null);
        update.put("timestamp", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        
        // Add details if provided
        if (details != null && !details.isEmpty()) {
            update.put("details", details);
        }
        
        // Add status indicators
        update.put("isActive", item.getIsActive());
        update.put("alertCount", item.getAlertCount());
        update.put("breachCount", item.getBreachCount());
        
        return update;
    }
    
    /**
     * Get connection statistics for monitoring
     */
    public Map<String, Object> getNotificationStats() {
        WebSocketSessionManager.ConnectionStats stats = sessionManager.getConnectionStats();
        
        return Map.of(
            "onlineUsers", stats.getCurrentConnections(),
            "totalConnections", stats.getTotalConnections(),
            "uniqueUsers", stats.getUniqueUsers(),
            "averageSessionsPerUser", stats.getAverageSessionsPerUser(),
            "lastConnectionTime", stats.getLastConnectionTime() != null ? 
                                 stats.getLastConnectionTime().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME) : null
        );
    }
}
