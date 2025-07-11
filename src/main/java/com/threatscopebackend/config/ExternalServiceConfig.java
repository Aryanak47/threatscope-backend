//package com.threatscopebackend.config;
//
//import org.apache.hc.client5.http.config.RequestConfig;
//import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
//import org.apache.hc.client5.http.impl.classic.HttpClients;
//import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
//import org.apache.hc.core5.util.Timeout;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
//import org.springframework.retry.annotation.EnableRetry;
//import org.springframework.web.client.RestTemplate;
//
///**
// * Configuration for external service integrations
// */
//@Configuration
//@EnableRetry
//public class ExternalServiceConfig {
//
//    @Bean
//    public RestTemplate restTemplate() {
//        // Create connection manager
//        PoolingHttpClientConnectionManager connectionManager = new PoolingHttpClientConnectionManager();
//        connectionManager.setMaxTotal(100);
//        connectionManager.setDefaultMaxPerRoute(20);
//
//        // Configure timeouts using Apache HttpClient 5
//        RequestConfig requestConfig = RequestConfig.custom()
//                .setConnectTimeout(Timeout.ofSeconds(5))     // Connection timeout
//                .setResponseTimeout(Timeout.ofSeconds(10))   // Response read timeout
//                .build();
//
//        // Build Apache HttpClient
//        CloseableHttpClient httpClient = HttpClients.custom()
//                .setConnectionManager(connectionManager)
//                .setDefaultRequestConfig(requestConfig)
//                .build();
//
//        // Use Apache HttpClient 5 with Spring's request factory
//        HttpComponentsClientHttpRequestFactory factory =
//                new HttpComponentsClientHttpRequestFactory(httpClient);
//
//        // ✅ REMOVE these lines, as they're meant for older HttpClient (version 4)
//        // factory.setConnectTimeout(5000);
//        // factory.setReadTimeout(10000);
//
//        return new RestTemplate(factory);
//    }
//}
