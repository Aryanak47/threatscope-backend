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
 * WebSocket configuration for real-time notifications and alerts
 * Provides secure, scalable WebSocket communication for instant delivery
 */
@Configuration
@EnableWebSocketMessageBroker
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Autowired(required = false)
    private ChannelInterceptor webSocketAuthInterceptor;

    /**
     * Configure message broker for routing messages
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Enable simple broker for /topic and /queue destinations
        config.enableSimpleBroker("/topic", "/queue", "/user")
              .setHeartbeatValue(new long[]{10000, 20000}) // Heartbeat every 10s/20s
              .setTaskScheduler(webSocketTaskScheduler());

        // Set application destination prefix
        config.setApplicationDestinationPrefixes("/app");
        
        // Set user destination prefix for personal notifications
        config.setUserDestinationPrefix("/user");
        
        log.info("✅ WebSocket message broker configured with heartbeat and user destinations");
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
                
        // Raw WebSocket endpoint (no SockJS)
        registry.addEndpoint("/ws/notifications-raw")
                .setAllowedOrigins("http://localhost:3000");
                
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
     * Configure client outbound channel for performance
     */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.taskExecutor()
                   .corePoolSize(10)
                   .maxPoolSize(50)
                   .queueCapacity(1000);
                   
        log.debug("WebSocket client outbound channel configured");
    }

    /**
     * Configure WebSocket transport settings
     */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registry) {
        registry.setMessageSizeLimit(64 * 1024) // 64KB message size limit
                .setSendBufferSizeLimit(512 * 1024) // 512KB send buffer
                .setSendTimeLimit(20000) // 20 second send timeout
                .setTimeToFirstMessage(30000); // 30 second connection timeout
                
        log.debug("WebSocket transport configured with size and timeout limits");
    }

    /**
     * Task scheduler for WebSocket heartbeat and maintenance
     */
    @Bean
    public org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler webSocketTaskScheduler() {
        org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler scheduler = 
            new org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler();
        scheduler.setPoolSize(10);
        scheduler.setThreadNamePrefix("WebSocket-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
}
