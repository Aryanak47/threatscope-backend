package com.threatscopebackend.websocket;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages WebSocket sessions and user connections
 * This is a placeholder implementation - integrate with your existing WebSocket system
 */
@Component
@Slf4j
public class WebSocketSessionManager {
    
    // Mock storage for demonstration
    private final Map<Long, Set<String>> userSessions = new ConcurrentHashMap<>();
    private final Map<String, SessionInfo> sessionDetails = new ConcurrentHashMap<>();
    private final AtomicLong totalConnections = new AtomicLong(0);
    
    @Data
    public static class SessionInfo {
        private Long userId;
        private String sessionId;
        private LocalDateTime connectedAt;
        private LocalDateTime lastActivity;
        private String ipAddress;
        private String userAgent;
        private boolean isActive;
        
        public SessionInfo(Long userId, String sessionId) {
            this.userId = userId;
            this.sessionId = sessionId;
            this.connectedAt = LocalDateTime.now();
            this.lastActivity = LocalDateTime.now();
            this.isActive = true;
        }
    }
    
    @Data
    public static class ConnectionStats {
        private long totalConnections;
        private int activeConnections;
        private int totalUsers;
        private int activeSessions; // For consultation sessions
        private List<SessionInfo> recentConnections;
        private Map<String, Object> systemStats;
        
        public ConnectionStats() {
            this.recentConnections = new ArrayList<>();
            this.systemStats = new HashMap<>();
            this.activeSessions = 0;
        }
    }
    
    /**
     * Add a user session
     */
    public void addUserSession(Long userId, String sessionId) {
        log.debug("Adding session {} for user {}", sessionId, userId);
        
        userSessions.computeIfAbsent(userId, k -> ConcurrentHashMap.newKeySet()).add(sessionId);
        sessionDetails.put(sessionId, new SessionInfo(userId, sessionId));
        totalConnections.incrementAndGet();
        
        log.info("User {} connected with session {}", userId, sessionId);
    }
    
    /**
     * Remove a user session
     */
    public void removeUserSession(Long userId, String sessionId) {
        log.debug("Removing session {} for user {}", sessionId, userId);
        
        Set<String> sessions = userSessions.get(userId);
        if (sessions != null) {
            sessions.remove(sessionId);
            if (sessions.isEmpty()) {
                userSessions.remove(userId);
            }
        }
        
        SessionInfo sessionInfo = sessionDetails.remove(sessionId);
        if (sessionInfo != null) {
            sessionInfo.setActive(false);
        }
        
        log.info("User {} disconnected session {}", userId, sessionId);
    }
    
    /**
     * Check if user is online
     */
    public boolean isUserOnline(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        boolean isOnline = sessions != null && !sessions.isEmpty();
        log.debug("User {} online status: {}", userId, isOnline);
        return isOnline;
    }
    
    /**
     * Get user's active sessions
     */
    public List<String> getUserSessions(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        List<String> sessionList = sessions != null ? new ArrayList<>(sessions) : new ArrayList<>();
        log.debug("User {} has {} active sessions", userId, sessionList.size());
        return sessionList;
    }
    
    /**
     * Get all online users
     */
    public List<Long> getOnlineUsers() {
        List<Long> onlineUsers = new ArrayList<>(userSessions.keySet());
        log.debug("Found {} online users", onlineUsers.size());
        return onlineUsers;
    }
    
    /**
     * Disconnect all sessions for a user
     */
    public void disconnectUser(Long userId) {
        log.info("Disconnecting all sessions for user {}", userId);
        
        Set<String> sessions = userSessions.remove(userId);
        if (sessions != null) {
            for (String sessionId : sessions) {
                SessionInfo sessionInfo = sessionDetails.remove(sessionId);
                if (sessionInfo != null) {
                    sessionInfo.setActive(false);
                }
                
                // TODO: In real implementation, close the WebSocket connection
                log.debug("Closed session {} for user {}", sessionId, userId);
            }
        }
        
        log.info("Disconnected {} sessions for user {}", sessions != null ? sessions.size() : 0, userId);
    }
    
    /**
     * Update last activity for a session
     */
    public void updateLastActivity(String sessionId) {
        SessionInfo sessionInfo = sessionDetails.get(sessionId);
        if (sessionInfo != null) {
            sessionInfo.setLastActivity(LocalDateTime.now());
        }
    }
    
    /**
     * Clean up stale sessions (inactive for more than 30 minutes)
     */
    public int cleanupStaleSessions() {
        log.debug("Starting cleanup of stale sessions");
        
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        int cleanedCount = 0;
        
        Iterator<Map.Entry<String, SessionInfo>> iterator = sessionDetails.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, SessionInfo> entry = iterator.next();
            SessionInfo sessionInfo = entry.getValue();
            
            if (sessionInfo.getLastActivity().isBefore(cutoffTime)) {
                String sessionId = entry.getKey();
                Long userId = sessionInfo.getUserId();
                
                // Remove from user sessions
                Set<String> userSessionSet = userSessions.get(userId);
                if (userSessionSet != null) {
                    userSessionSet.remove(sessionId);
                    if (userSessionSet.isEmpty()) {
                        userSessions.remove(userId);
                    }
                }
                
                // Remove session details
                iterator.remove();
                cleanedCount++;
                
                log.debug("Cleaned up stale session {} for user {}", sessionId, userId);
            }
        }
        
        log.info("Cleaned up {} stale sessions", cleanedCount);
        return cleanedCount;
    }
    
    /**
     * Get connection statistics
     */
    public ConnectionStats getConnectionStats() {
        ConnectionStats stats = new ConnectionStats();
        
        stats.setTotalConnections(totalConnections.get());
        stats.setActiveConnections(sessionDetails.size());
        stats.setTotalUsers(userSessions.size());
        stats.setActiveSessions(sessionDetails.size()); // Each WebSocket session represents an active consultation session
        
        // Get recent connections (last 10)
        List<SessionInfo> recent = sessionDetails.values().stream()
                .sorted((a, b) -> b.getConnectedAt().compareTo(a.getConnectedAt()))
                .limit(10)
                .toList();
        stats.setRecentConnections(recent);
        
        // Enhanced system stats
        Map<String, Object> systemStats = new HashMap<>();
        systemStats.put("memoryUsage", Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory());
        systemStats.put("totalMemory", Runtime.getRuntime().totalMemory());
        systemStats.put("freeMemory", Runtime.getRuntime().freeMemory());
        systemStats.put("maxMemory", Runtime.getRuntime().maxMemory());
        systemStats.put("availableProcessors", Runtime.getRuntime().availableProcessors());
        systemStats.put("uptime", System.currentTimeMillis());
        
        // WebSocket specific stats
        systemStats.put("staleSessions", countStaleSessions());
        systemStats.put("activeUserSessions", userSessions.entrySet().stream()
                .mapToInt(entry -> entry.getValue().size())
                .sum());
        
        stats.setSystemStats(systemStats);
        
        log.debug("Generated connection statistics: {} active connections, {} users, {} consultation sessions", 
                 stats.getActiveConnections(), stats.getTotalUsers(), stats.getActiveSessions());
        
        return stats;
    }
    
    /**
     * Get session info by session ID
     */
    public SessionInfo getSessionInfo(String sessionId) {
        return sessionDetails.get(sessionId);
    }
    
    /**
     * Get total connection count
     */
    public long getTotalConnectionCount() {
        return totalConnections.get();
    }
    
    /**
     * Register a new session (alias for addUserSession)
     */
    public void registerSession(String sessionId, Long userId, String username) {
        log.debug("Registering session {} for user {} ({})", sessionId, userId, username);
        addUserSession(userId, sessionId);
        
        // Update session info with username
        SessionInfo sessionInfo = sessionDetails.get(sessionId);
        if (sessionInfo != null) {
            sessionInfo.setUserAgent(username); // Store username in userAgent field for now
        }
        
        log.info("Registered WebSocket session {} for user {} ({})", sessionId, userId, username);
    }
    
    /**
     * Unregister a session
     */
    public void unregisterSession(String sessionId) {
        log.debug("Unregistering session {}", sessionId);
        
        SessionInfo sessionInfo = sessionDetails.get(sessionId);
        if (sessionInfo != null) {
            Long userId = sessionInfo.getUserId();
            removeUserSession(userId, sessionId);
            log.info("Unregistered WebSocket session {} for user {}", sessionId, userId);
        } else {
            log.warn("Attempted to unregister unknown session: {}", sessionId);
        }
    }
    
    /**
     * Add session with additional info (IP address and user agent)
     */
    public void addUserSession(Long userId, String sessionId, String ipAddress, String userAgent) {
        addUserSession(userId, sessionId);
        
        SessionInfo sessionInfo = sessionDetails.get(sessionId);
        if (sessionInfo != null) {
            sessionInfo.setIpAddress(ipAddress);
            sessionInfo.setUserAgent(userAgent);
        }
    }
    
    /**
     * Get active consultation sessions count for a specific user
     */
    public int getActiveConsultationSessions(Long userId) {
        Set<String> sessions = userSessions.get(userId);
        return sessions != null ? sessions.size() : 0;
    }
    
    /**
     * Count stale sessions (for health monitoring)
     */
    private long countStaleSessions() {
        LocalDateTime cutoffTime = LocalDateTime.now().minusMinutes(30);
        return sessionDetails.values().stream()
                .filter(session -> session.getLastActivity().isBefore(cutoffTime))
                .count();
    }
    
    /**
     * Get session statistics for a specific user
     */
    public Map<String, Object> getUserSessionStats(Long userId) {
        Map<String, Object> stats = new HashMap<>();
        
        Set<String> userSessionSet = userSessions.get(userId);
        stats.put("isOnline", userSessionSet != null && !userSessionSet.isEmpty());
        stats.put("activeSessionCount", userSessionSet != null ? userSessionSet.size() : 0);
        stats.put("sessionIds", userSessionSet != null ? new ArrayList<>(userSessionSet) : List.of());
        
        if (userSessionSet != null) {
            List<SessionInfo> userSessionInfos = userSessionSet.stream()
                    .map(sessionDetails::get)
                    .filter(info -> info != null)
                    .toList();
            
            stats.put("sessionDetails", userSessionInfos);
            stats.put("lastActivity", userSessionInfos.stream()
                    .map(SessionInfo::getLastActivity)
                    .max(LocalDateTime::compareTo)
                    .orElse(null));
        }
        
        return stats;
    }
    
    /**
     * Get all users currently in consultation sessions
     */
    public Map<Long, Integer> getUsersInConsultations() {
        Map<Long, Integer> usersInConsultations = new HashMap<>();
        
        userSessions.forEach((userId, sessions) -> {
            if (!sessions.isEmpty()) {
                usersInConsultations.put(userId, sessions.size());
            }
        });
        
        return usersInConsultations;
    }
    
    /**
     * Health check for session manager
     */
    public Map<String, Object> healthCheck() {
        Map<String, Object> health = new HashMap<>();
        
        health.put("service", "WebSocket Session Manager");
        health.put("status", "UP");
        health.put("connectionStats", getConnectionStats());
        health.put("staleSessions", countStaleSessions());
        health.put("memoryFootprint", Map.of(
            "userSessions", userSessions.size(),
            "sessionDetails", sessionDetails.size(),
            "totalConnections", totalConnections.get()
        ));
        
        return health;
    }
}
