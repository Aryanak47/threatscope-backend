package com.threatscopebackend.websocket;

import com.threatscopebackend.security.JwtTokenProvider;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.security.CustomUserDetailsService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;

/**
 * WebSocket authentication interceptor for securing real-time connections
 * Validates JWT tokens on WebSocket connect and maintains user context
 */
@Component
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {

    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    @Autowired
    private CustomUserDetailsService userDetailsService;
    
    @Autowired
    private ApplicationContext applicationContext;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            // Handle WebSocket connection authentication
            return handleConnect(message, accessor);
        } else if (accessor != null && StompCommand.DISCONNECT.equals(accessor.getCommand())) {
            // Handle WebSocket disconnection cleanup
            return handleDisconnect(message, accessor);
        }
        
        return message;
    }

    /**
     * Handle WebSocket CONNECT command with authentication
     */
    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        String sessionId = accessor.getSessionId();
        log.info("🔗 WebSocket connection attempt - Session: {}", sessionId);
        
        try {
            // Extract JWT token from headers
            String token = extractTokenFromHeaders(accessor);
            
            if (!StringUtils.hasText(token)) {
                log.warn("⚠️ WebSocket connection attempted without token - Session: {}", sessionId);
                log.warn("📋 Available headers: {}", accessor.toNativeHeaderMap());
                return message; // Let Spring Security handle the rejection
            }
            
            log.info("🔑 Found token for WebSocket connection - Session: {}, Token: {}...", 
                    sessionId, token.substring(0, Math.min(20, token.length())));

            // Validate JWT token
            if (!jwtTokenProvider.validateToken(token)) {
                log.warn("❌ WebSocket connection attempted with invalid token - Session: {}", sessionId);
                return message; // Let Spring Security handle the rejection
            }
            
            log.info("✅ Token validation successful - Session: {}", sessionId);

            // Extract user information
            Long userId = jwtTokenProvider.getUserIdFromToken(token);
            log.info("👤 Extracted user ID: {} - Session: {}", userId, sessionId);
            
            UserPrincipal userPrincipal = (UserPrincipal) userDetailsService.loadUserById(userId);
            log.info("👤 Loaded user principal: {} - Session: {}", userPrincipal.getUsername(), sessionId);

            // Create authentication
            Authentication authentication = new UsernamePasswordAuthenticationToken(
                userPrincipal, null, userPrincipal.getAuthorities());

            // Set authentication in accessor for this session
            accessor.setUser(authentication);
            SecurityContextHolder.getContext().setAuthentication(authentication);

            // Register session with user (get session manager lazily to avoid circular dependency)
            getWebSocketSessionManager().registerSession(sessionId, userId, userPrincipal.getUsername());

            log.info("✅ WebSocket connection authenticated for user: {} (session: {})", 
                    userPrincipal.getUsername(), sessionId);

        } catch (Exception e) {
            log.error("❌ WebSocket authentication failed - Session: {}, Error: {}", sessionId, e.getMessage(), e);
            // Continue without authentication - let security handle rejection
        }

        return message;
    }

    /**
     * Handle WebSocket DISCONNECT command with cleanup
     */
    private Message<?> handleDisconnect(Message<?> message, StompHeaderAccessor accessor) {
        try {
            String sessionId = accessor.getSessionId();
            Authentication auth = (Authentication) accessor.getUser();
            
            if (auth != null && auth.getPrincipal() instanceof UserPrincipal userPrincipal) {
                // Unregister session
                getWebSocketSessionManager().unregisterSession(sessionId);
                
                log.info("🔌 WebSocket disconnected for user: {} (session: {})", 
                        userPrincipal.getUsername(), sessionId);
            } else {
                log.debug("🔌 Anonymous WebSocket session disconnected: {}", sessionId);
            }

        } catch (Exception e) {
            log.error("❌ Error handling WebSocket disconnect: {}", e.getMessage());
        }

        return message;
    }

    /**
     * Extract JWT token from WebSocket headers
     */
    private String extractTokenFromHeaders(StompHeaderAccessor accessor) {
        log.debug("🔍 Extracting token from WebSocket headers...");
        
        // Try Authorization header first
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            log.debug("🔑 Found Authorization header: {}", authHeader != null ? authHeader.substring(0, Math.min(20, authHeader.length())) + "..." : "null");
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                log.debug("✅ Extracted token from Authorization header: {}...", token.substring(0, Math.min(20, token.length())));
                return token;
            }
        }

        // Try token parameter
        List<String> tokenHeaders = accessor.getNativeHeader("token");
        if (tokenHeaders != null && !tokenHeaders.isEmpty()) {
            String token = tokenHeaders.get(0);
            log.debug("✅ Found token in 'token' header: {}...", token.substring(0, Math.min(20, token.length())));
            return token;
        }

        // Try query parameter (for SockJS compatibility) - access_token
        List<String> tokenParams = accessor.getNativeHeader("access_token");
        if (tokenParams != null && !tokenParams.isEmpty()) {
            String token = tokenParams.get(0);
            log.debug("✅ Found token in 'access_token' header: {}...", token.substring(0, Math.min(20, token.length())));
            return token;
        }
        
        // Try SockJS URL parameters (this is crucial for SockJS connections)
        try {
            // Get the session attributes which may contain URL parameters
            Object sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                log.debug("📊 Session attributes available: {}", sessionAttributes);
            }
            
            // Try to get from SockJS info
            Map<String, Object> attributes = accessor.getSessionAttributes();
            if (attributes != null) {
                // Look for access_token in session attributes
                Object tokenObj = attributes.get("access_token");
                if (tokenObj != null) {
                    String token = tokenObj.toString();
                    log.debug("✅ Found token in session attributes: {}...", token.substring(0, Math.min(20, token.length())));
                    return token;
                }
            }
        } catch (Exception e) {
            log.debug("⚠️ Could not extract token from session attributes: {}", e.getMessage());
        }

        // Try X-User-ID header (custom header we send)
        List<String> userIdHeaders = accessor.getNativeHeader("X-User-ID");
        if (userIdHeaders != null && !userIdHeaders.isEmpty()) {
            log.debug("👤 Found X-User-ID header: {}", userIdHeaders.get(0));
        }

        log.warn("❌ No token found in any WebSocket headers or parameters");
        log.warn("📋 All available headers: {}", accessor.toNativeHeaderMap());
        log.warn("📋 Session attributes: {}", accessor.getSessionAttributes());
        return null;
    }

    /**
     * Get WebSocket session manager lazily to avoid circular dependency
     */
    private WebSocketSessionManager getWebSocketSessionManager() {
        return applicationContext.getBean(WebSocketSessionManager.class);
    }

    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        // Optional: Log successful message sends for debugging
        if (!sent) {
            log.debug("⚠️ WebSocket message failed to send: {}", message.getHeaders().get("stompCommand"));
        }
    }

    @Override
    public boolean preReceive(MessageChannel channel) {
        return true; // Allow all receives
    }

    @Override
    public Message<?> postReceive(Message<?> message, MessageChannel channel) {
        return message; // Pass through all received messages
    }
}
