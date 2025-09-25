package com.threatscopebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

/**
 * Configuration for RestTemplate used in external API integrations
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate restTemplate() {
        HttpComponentsClientHttpRequestFactory factory = new HttpComponentsClientHttpRequestFactory();
        
        // Configure timeouts
        factory.setConnectionRequestTimeout(Duration.ofMillis(5000));
        factory.setConnectTimeout(Duration.ofMillis(5000));
        
        RestTemplate restTemplate = new RestTemplate();
        restTemplate.setRequestFactory(factory);
        
        return restTemplate;
    }
}