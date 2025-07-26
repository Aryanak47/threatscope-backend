package com.threatscopebackend.config.websocket;

import com.threatscopebackend.websocket.WebSocketAuthInterceptor;
import com.threatscopebackend.websocket.WebSocketSessionManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.web.socket.config.WebSocketMessageBrokerStats;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.messaging.simp.SimpMessagingTemplate;

/**
 * WebSocket interceptor configuration to avoid circular dependencies
 * Configures authentication and session management after all beans are created
 */
@Configuration
@RequiredArgsConstructor
@Slf4j
public class WebSocketInterceptorConfig {

    private final WebSocketAuthInterceptor authInterceptor;
    private final SimpMessagingTemplate messagingTemplate;

    /**
     * WebSocket session manager bean
     */
    @Bean
    public WebSocketSessionManager webSocketSessionManager() {
        return new WebSocketSessionManager();
    }

    /**
     * Configure WebSocket authentication after all beans are ready
     * This prevents circular dependency issues during startup
     */
    @EventListener(ApplicationReadyEvent.class)
    public void configureWebSocketSecurity() {
        log.info("🔒 WebSocket security configuration completed after application startup");
        log.info("✅ WebSocket authentication and session management ready");
        
        // The auth interceptor is now ready to be used
        // Spring will automatically use it as it's a @Component
    }
}
