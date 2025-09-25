package com.threatscopebackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for data source REST clients with proper timeouts and connection pooling
 */
@Configuration
public class DataSourceConfig {
    
    @Value("${datasources.breach-vip.timeout:5000}")
    private int timeoutMs;
    
    /**
     * RestTemplate configured specifically for external data sources
     * with proper timeouts, connection pooling, and error handling
     */
    @Bean("dataSourceRestTemplate")
    public RestTemplate dataSourceRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(timeoutMs))
                .setReadTimeout(Duration.ofMillis(timeoutMs * 2)) // Read timeout slightly higher
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .build();
    }
    
    /**
     * Separate RestTemplate for health checks with shorter timeouts
     */
    @Bean("healthCheckRestTemplate") 
    public RestTemplate healthCheckRestTemplate(RestTemplateBuilder builder) {
        return builder
                .setConnectTimeout(Duration.ofMillis(2000)) // 2 seconds for health checks
                .setReadTimeout(Duration.ofMillis(3000))    // 3 seconds read timeout
                .requestFactory(HttpComponentsClientHttpRequestFactory.class)
                .build();
    }
}