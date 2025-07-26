package com.threatscopebackend.websocket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

/**
 * Manages WebSocket sessions and user connections for real-time notifications
 * Thread-safe session tracking with user mapping and connection statistics
 */
@Component
@Slf4j
public class WebSocketSessionManager {

    // Map: sessionId -> SessionInfo
    private final ConcurrentMap<String, SessionInfo> sessions = new ConcurrentHashMap<>();
    
    // Map: userId -> List of sessionIds (for multi-device users)
    private final ConcurrentMap<Long, List<String>> userSessions = new ConcurrentHashMap<>();
    
    // Connection statistics
    private volatile long totalConnections = 0;
    private volatile long currentConnections = 0;
    private volatile LocalDateTime lastConnectionTime;
    private volatile LocalDateTime lastDisconnectionTime;

    /**
     * Register a new WebSocket session
     */
    public void registerSession(String sessionId, Long userId, String username) {
        if (sessionId == null || userId == null) {
            log.warn("⚠️ Cannot register session with null sessionId or userId");
            return;
        }

        SessionInfo sessionInfo = new SessionInfo(sessionId, userId, username, LocalDateTime.now());
        
        // Register session
        sessions.put(sessionId, sessionInfo);
        
        // Add to user's session list
        userSessions.computeIfAbsent(userId, k -> new ArrayList<>()).add(sessionId);
        
        // Update statistics
        totalConnections++;
        currentConnections++;
        lastConnectionTime = LocalDateTime.now();
        
        log.info("✅ Registered WebSocket session: {} for user: {} ({}). Total active: {}", 
                sessionId, username, userId, currentConnections);
    }

    /**
     * Unregister a WebSocket session
     */
    public void unregisterSession(String sessionId) {
        if (sessionId == null) {
            return;
        }

        SessionInfo sessionInfo = sessions.remove(sessionId);
        
        if (sessionInfo != null) {
            // Remove from user's session list
            List<String> userSessionList = userSessions.get(sessionInfo.getUserId());
            if (userSessionList != null) {
                userSessionList.remove(sessionId);
                
                // Clean up empty user session lists
                if (userSessionList.isEmpty()) {
                    userSessions.remove(sessionInfo.getUserId());
                }
            }
            
            // Update statistics
            currentConnections--;
            lastDisconnectionTime = LocalDateTime.now();
            
            log.info("🔌 Unregistered WebSocket session: {} for user: {} ({}). Total active: {}", 
                    sessionId, sessionInfo.getUsername(), sessionInfo.getUserId(), currentConnections);
        } else {
            log.debug("🔌 Attempted to unregister unknown session: {}", sessionId);
        }
    }

    /**
     * Get all session IDs for a specific user
     */
    public List<String> getUserSessions(Long userId) {
        return userSessions.getOrDefault(userId, new ArrayList<>());
    }

    /**
     * Check if a user has any active sessions
     */
    public boolean isUserOnline(Long userId) {
        List<String> sessions = userSessions.get(userId);
        return sessions != null && !sessions.isEmpty();
    }

    /**
     * Get session information
     */
    public SessionInfo getSessionInfo(String sessionId) {
        return sessions.get(sessionId);
    }

    /**
     * Get all active sessions
     */
    public Map<String, SessionInfo> getAllSessions() {
        return new ConcurrentHashMap<>(sessions);
    }

    /**
     * Get all online users
     */
    public List<Long> getOnlineUsers() {
        return new ArrayList<>(userSessions.keySet());
    }

    /**
     * Get connection statistics
     */
    public ConnectionStats getConnectionStats() {
        return ConnectionStats.builder()
                .currentConnections(currentConnections)
                .totalConnections(totalConnections)
                .uniqueUsers(userSessions.size())
                .lastConnectionTime(lastConnectionTime)
                .lastDisconnectionTime(lastDisconnectionTime)
                .sessionsPerUser(userSessions.entrySet().stream()
                    .collect(java.util.stream.Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().size())))
                .build();
    }

    /**
     * Clean up stale sessions (called by scheduled task)
     */
    public int cleanupStaleSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(1); // 1 hour timeout
        int cleanedCount = 0;

        List<String> staleSessionIds = new ArrayList<>();
        
        for (Map.Entry<String, SessionInfo> entry : sessions.entrySet()) {
            if (entry.getValue().getConnectedAt().isBefore(cutoffTime)) {
                staleSessionIds.add(entry.getKey());
            }
        }

        for (String sessionId : staleSessionIds) {
            unregisterSession(sessionId);
            cleanedCount++;
        }

        if (cleanedCount > 0) {
            log.info("🧹 Cleaned up {} stale WebSocket sessions", cleanedCount);
        }

        return cleanedCount;
    }

    /**
     * Force disconnect all sessions for a user
     */
    public void disconnectUser(Long userId) {
        List<String> userSessionIds = getUserSessions(userId);
        
        for (String sessionId : userSessionIds) {
            unregisterSession(sessionId);
        }
        
        log.info("🔌 Force disconnected all sessions for user: {} ({} sessions)", userId, userSessionIds.size());
    }

    /**
     * Get user information from session ID
     */
    public Long getUserIdFromSession(String sessionId) {
        SessionInfo sessionInfo = sessions.get(sessionId);
        return sessionInfo != null ? sessionInfo.getUserId() : null;
    }

    /**
     * Session information holder
     */
    @lombok.Data
    @lombok.AllArgsConstructor
    public static class SessionInfo {
        private String sessionId;
        private Long userId;
        private String username;
        private LocalDateTime connectedAt;
    }

    /**
     * Connection statistics holder
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class ConnectionStats {
        private long currentConnections;
        private long totalConnections;
        private long uniqueUsers;
        private LocalDateTime lastConnectionTime;
        private LocalDateTime lastDisconnectionTime;
        private Map<Long, Integer> sessionsPerUser;
        
        public double getAverageSessionsPerUser() {
            return uniqueUsers > 0 ? (double) currentConnections / uniqueUsers : 0.0;
        }
    }
}
