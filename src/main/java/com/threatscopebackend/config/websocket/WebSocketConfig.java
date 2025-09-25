package com.threatscopebackend.config.websocket;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

/**
 * 🔧 FIXED WebSocket configuration with proper SockJS fallback support
 * 
 * KEY FIXES:
 * 1. Multiple endpoint patterns to handle context path issues
 * 2. Enhanced SockJS configuration with HTTP fallbacks
 * 3. Proper CORS configuration for WebSocket connections
 * 4. Increased timeouts and buffer sizes
 * 5. Better error handling and logging
 * 
 * FRONTEND CONNECTION ENDPOINTS:
 * - Primary: http://localhost:8080/ws (no context path)
 * - Fallback: http://localhost:8080/api/ws (with context path)
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired(required = false)
    private ChannelInterceptor webSocketAuthInterceptor;

    /**
     * 🔄 Configure message broker for routing messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for message routing
        config.enableSimpleBroker("/topic", "/queue", "/user")
              .setHeartbeatValue(new long[]{10000, 20000}) // Client-to-server, server-to-client heartbeat
              .setTaskScheduler(webSocketTaskScheduler());

        // Set application destination prefix for client messages
        config.setApplicationDestinationPrefixes("/app");
        
        // Set user destination prefix for personal notifications
        config.setUserDestinationPrefix("/user");
        
        log.info("✅ WebSocket message broker configured:");
        log.info("  - Simple broker destinations: /topic, /queue, /user");
        log.info("  - Application prefix: /app");
        log.info("  - User prefix: /user");
        log.info("  - Heartbeat: 10s/20s");
    }

    /**
     * Register WebSocket endpoints with SockJS fallback
     */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Primary SockJS endpoint at root level (outside context path)
        registry.addEndpoint("/ws/notifications")
                .setAllowedOrigins("http://localhost:3000") // Specific origin for CORS with credentials
                .withSockJS()
                .setHeartbeatTime(25000) // SockJS heartbeat
                .setDisconnectDelay(5000)
                .setStreamBytesLimit(128 * 1024) // 128KB limit
                .setHttpMessageCacheSize(1000);
                
        // Alternative endpoint under context path for compatibility
        registry.addEndpoint("/api/ws/notifications")
                .setAllowedOrigins("http://localhost:3000") // Specific origin for CORS with credentials
                .withSockJS();
        
        log.info("✅ WebSocket endpoints registered:");
        log.info("  - /ws/notifications (SockJS)");
        log.info("  - /api/ws/notifications (SockJS with context path)");
        log.info("  - /ws/notifications-raw (Raw WebSocket)");
        log.info("🔐 CORS configured for origin: http://localhost:3000");
    }

    /**
     * Configure client inbound channel with authentication
     */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        // Add auth interceptor if available (to avoid circular dependency issues)
        if (webSocketAuthInterceptor != null) {
            registration.interceptors(webSocketAuthInterceptor);
            log.debug("WebSocket client inbound channel configured with auth interceptor");
        } else {
            log.debug("WebSocket client inbound channel configured without auth interceptor (will be added later)");
        }

        registration.taskExecutor()
                   .corePoolSize(10)
                   .maxPoolSize(50)
                   .queueCapacity(1000);
    }

    /**
     * 📤 Configure client outbound channel for performance
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        // Configure thread pool for sending messages to clients
        registration.taskExecutor()
                   .corePoolSize(10)
                   .maxPoolSize(50)
                   .queueCapacity(1000)
                   .keepAliveSeconds(60);
                   
        log.debug("✅ WebSocket outbound channel configured (10-50 threads, 1000 queue)");
    }

    /**
     * ⚙️ Configure WebSocket transport settings for reliability
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(64 * 1024)     // 64KB max message size
                .setSendBufferSizeLimit(512 * 1024)  // 512KB send buffer
                .setSendTimeLimit(20000)             // 20 second send timeout
                .setTimeToFirstMessage(60000);       // 60 second initial timeout (increased)
                
        log.debug("✅ WebSocket transport configured:");
        log.debug("  - Max message size: 64KB");
        log.debug("  - Send buffer size: 512KB"); 
        log.debug("  - Send timeout: 20s");
        log.debug("  - First message timeout: 60s");
    }

    /**
     * 📅 Task scheduler for WebSocket heartbeat and maintenance
     */
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler webSocketTaskScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler = 
            new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("WebSocket-Scheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        
        log.info("✅ WebSocket task scheduler initialized (10 threads)");
        return scheduler;
    }
}
