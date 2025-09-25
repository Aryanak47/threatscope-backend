package com.threatscopebackend.service;

import com.threatscopebackend.service.datasource.DataSourceService;
import com.threatscopebackend.service.datasource.BreachVipDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Monitors data source health, performance, and availability
 * Provides metrics for circuit breaker decisions and system monitoring
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class DataSourceMonitoringService {
    
    private final List<DataSourceService> dataSources;
    
    // Health tracking
    private final Map<String, HealthMetrics> healthMetrics = new ConcurrentHashMap<>();
    
    // Performance tracking
    private final Map<String, PerformanceMetrics> performanceMetrics = new ConcurrentHashMap<>();
    
    /**
     * Health check all data sources every 2 minutes
     */
    @Scheduled(fixedRate = 120000) // 2 minutes
    public void performHealthChecks() {
        log.debug("Performing scheduled health checks for {} data sources", dataSources.size());
        
        for (DataSourceService dataSource : dataSources) {
            if (!dataSource.isEnabled()) {
                continue;
            }
            
            String sourceName = dataSource.getSourceName();
            long startTime = System.currentTimeMillis();
            
            try {
                boolean isHealthy = dataSource.isHealthy();
                long responseTime = System.currentTimeMillis() - startTime;
                
                updateHealthMetrics(sourceName, isHealthy, responseTime, null);
                
                log.debug("Health check for {}: {} ({}ms)", sourceName, 
                         isHealthy ? "HEALTHY" : "UNHEALTHY", responseTime);
                
            } catch (Exception e) {
                long responseTime = System.currentTimeMillis() - startTime;
                updateHealthMetrics(sourceName, false, responseTime, e.getMessage());
                
                log.warn("Health check failed for {}: {} ({}ms)", 
                        sourceName, e.getMessage(), responseTime);
            }
        }
    }
    
    /**
     * Record search operation metrics
     */
    public void recordSearchOperation(String sourceName, long durationMs, boolean success, int resultCount) {
        PerformanceMetrics metrics = performanceMetrics.computeIfAbsent(sourceName, 
                k -> new PerformanceMetrics());
        
        synchronized (metrics) {
            metrics.totalRequests++;
            metrics.totalResponseTime += durationMs;
            metrics.lastRequestTime = LocalDateTime.now();
            
            if (success) {
                metrics.successfulRequests++;
                metrics.totalResults += resultCount;
            } else {
                metrics.failedRequests++;
            }
            
            // Track response time percentiles (simple implementation)
            metrics.responseTimes.add(durationMs);
            if (metrics.responseTimes.size() > 100) {
                metrics.responseTimes.remove(0); // Keep only last 100 measurements
            }
        }
        
        log.debug("Recorded search operation for {}: {}ms, success={}, results={}", 
                 sourceName, durationMs, success, resultCount);
    }
    
    /**
     * Get health status for a specific data source
     */
    public HealthStatus getHealthStatus(String sourceName) {
        HealthMetrics health = healthMetrics.get(sourceName);
        PerformanceMetrics performance = performanceMetrics.get(sourceName);
        
        if (health == null) {
            return new HealthStatus(sourceName, false, "No health data available", null, null);
        }
        
        return new HealthStatus(
                sourceName,
                health.isHealthy,
                health.lastError,
                health.lastCheckTime,
                performance
        );
    }
    
    /**
     * Get health status for all data sources
     */
    public Map<String, HealthStatus> getAllHealthStatuses() {
        Map<String, HealthStatus> statuses = new HashMap<>();
        
        for (DataSourceService dataSource : dataSources) {
            String sourceName = dataSource.getSourceName();
            statuses.put(sourceName, getHealthStatus(sourceName));
        }
        
        return statuses;
    }
    
    /**
     * Get detailed metrics for a specific data source
     */
    public Map<String, Object> getDetailedMetrics(String sourceName) {
        Map<String, Object> details = new HashMap<>();
        
        HealthMetrics health = healthMetrics.get(sourceName);
        PerformanceMetrics performance = performanceMetrics.get(sourceName);
        
        // Basic info
        details.put("sourceName", sourceName);
        details.put("timestamp", LocalDateTime.now());
        
        // Health metrics
        if (health != null) {
            details.put("isHealthy", health.isHealthy);
            details.put("lastHealthCheck", health.lastCheckTime);
            details.put("healthCheckCount", health.checkCount);
            details.put("healthyCount", health.healthyCount);
            details.put("unhealthyCount", health.unhealthyCount);
            details.put("averageHealthCheckTime", health.getTotalCheckTime() / Math.max(1, health.checkCount));
            details.put("lastError", health.lastError);
        }
        
        // Performance metrics
        if (performance != null) {
            details.put("totalRequests", performance.totalRequests);
            details.put("successfulRequests", performance.successfulRequests);
            details.put("failedRequests", performance.failedRequests);
            details.put("successRate", performance.getSuccessRate());
            details.put("averageResponseTime", performance.getAverageResponseTime());
            details.put("totalResults", performance.totalResults);
            details.put("lastRequestTime", performance.lastRequestTime);
            
            if (!performance.responseTimes.isEmpty()) {
                List<Long> sortedTimes = new ArrayList<>(performance.responseTimes);
                Collections.sort(sortedTimes);
                
                details.put("minResponseTime", sortedTimes.get(0));
                details.put("maxResponseTime", sortedTimes.get(sortedTimes.size() - 1));
                details.put("medianResponseTime", sortedTimes.get(sortedTimes.size() / 2));
                details.put("p95ResponseTime", sortedTimes.get((int) (sortedTimes.size() * 0.95)));
            }
        }
        
        // Additional details for resilient data sources
        dataSources.stream()
                .filter(ds -> ds.getSourceName().equals(sourceName))
                .findFirst()
                .ifPresent(ds -> {
                    if (ds instanceof BreachVipDataSource) {
                        BreachVipDataSource breachVipDs = (BreachVipDataSource) ds;
                        details.putAll(breachVipDs.getHealthDetails());
                    }
                });
        
        return details;
    }
    
    /**
     * Get system-wide data source summary
     */
    public Map<String, Object> getSystemSummary() {
        Map<String, Object> summary = new HashMap<>();
        
        int totalSources = dataSources.size();
        int enabledSources = (int) dataSources.stream().filter(DataSourceService::isEnabled).count();
        int healthySources = (int) dataSources.stream()
                .filter(DataSourceService::isEnabled)
                .filter(DataSourceService::isHealthy)
                .count();
        
        summary.put("totalDataSources", totalSources);
        summary.put("enabledDataSources", enabledSources);
        summary.put("healthyDataSources", healthySources);
        summary.put("unhealthyDataSources", enabledSources - healthySources);
        
        // Aggregate performance metrics
        long totalRequests = performanceMetrics.values().stream()
                .mapToLong(pm -> pm.totalRequests)
                .sum();
        long totalSuccessful = performanceMetrics.values().stream()
                .mapToLong(pm -> pm.successfulRequests)
                .sum();
        
        summary.put("totalRequests", totalRequests);
        summary.put("totalSuccessfulRequests", totalSuccessful);
        summary.put("overallSuccessRate", totalRequests > 0 ? (double) totalSuccessful / totalRequests : 0.0);
        
        return summary;
    }
    
    private void updateHealthMetrics(String sourceName, boolean isHealthy, long responseTime, String error) {
        HealthMetrics metrics = healthMetrics.computeIfAbsent(sourceName, k -> new HealthMetrics());
        
        synchronized (metrics) {
            metrics.isHealthy = isHealthy;
            metrics.lastCheckTime = LocalDateTime.now();
            metrics.checkCount++;
            metrics.totalCheckTime += responseTime;
            metrics.lastError = error;
            
            if (isHealthy) {
                metrics.healthyCount++;
            } else {
                metrics.unhealthyCount++;
            }
        }
    }
    
    // Data classes
    public static class HealthStatus {
        public final String sourceName;
        public final boolean isHealthy;
        public final String lastError;
        public final LocalDateTime lastCheckTime;
        public final PerformanceMetrics performance;
        
        public HealthStatus(String sourceName, boolean isHealthy, String lastError, 
                          LocalDateTime lastCheckTime, PerformanceMetrics performance) {
            this.sourceName = sourceName;
            this.isHealthy = isHealthy;
            this.lastError = lastError;
            this.lastCheckTime = lastCheckTime;
            this.performance = performance;
        }
    }
    
    private static class HealthMetrics {
        volatile boolean isHealthy = true;
        volatile LocalDateTime lastCheckTime = LocalDateTime.now();
        volatile long checkCount = 0;
        volatile long healthyCount = 0;
        volatile long unhealthyCount = 0;
        volatile long totalCheckTime = 0;
        volatile String lastError = null;
        
        public long getTotalCheckTime() {
            return totalCheckTime;
        }
    }
    
    private static class PerformanceMetrics {
        volatile long totalRequests = 0;
        volatile long successfulRequests = 0;
        volatile long failedRequests = 0;
        volatile long totalResponseTime = 0;
        volatile long totalResults = 0;
        volatile LocalDateTime lastRequestTime = null;
        final List<Long> responseTimes = Collections.synchronizedList(new ArrayList<>());
        
        public double getSuccessRate() {
            return totalRequests > 0 ? (double) successfulRequests / totalRequests : 0.0;
        }
        
        public double getAverageResponseTime() {
            return totalRequests > 0 ? (double) totalResponseTime / totalRequests : 0.0;
        }
    }
}