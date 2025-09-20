package com.threatscopebackend.controller;

import com.threatscopebackend.service.DataSourceMonitoringService;
import com.threatscopebackend.service.datasource.DataSourceManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * REST API for monitoring data source health and performance
 */
@RestController
@RequestMapping("/v1/datasources")
@RequiredArgsConstructor
@Slf4j
public class DataSourceMonitoringController {
    
    private final DataSourceMonitoringService monitoringService;
    private final DataSourceManager dataSourceManager;
    
    /**
     * Get health status of all data sources
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        try {
            Map<String, Object> healthData = Map.of(
                "summary", monitoringService.getSystemSummary(),
                "sources", monitoringService.getAllHealthStatuses()
            );
            
            return ResponseEntity.ok(healthData);
            
        } catch (Exception e) {
            log.error("Error getting data source health status: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get health status"
            ));
        }
    }
    
    /**
     * Get detailed metrics for a specific data source
     */
    @GetMapping("/health/{sourceName}")
    public ResponseEntity<Map<String, Object>> getSourceHealth(@PathVariable String sourceName) {
        try {
            Map<String, Object> metrics = monitoringService.getDetailedMetrics(sourceName);
            
            if (metrics.isEmpty() || !metrics.containsKey("sourceName")) {
                return ResponseEntity.notFound().build();
            }
            
            return ResponseEntity.ok(metrics);
            
        } catch (Exception e) {
            log.error("Error getting metrics for source {}: {}", sourceName, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get source metrics"
            ));
        }
    }
    
    /**
     * Get basic information about all configured data sources
     */
    @GetMapping("/info")
    public ResponseEntity<Map<String, Object>> getDataSourceInfo() {
        try {
            return ResponseEntity.ok(dataSourceManager.getDataSourceInfo());
        } catch (Exception e) {
            log.error("Error getting data source info: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get data source information"
            ));
        }
    }
    
    /**
     * Get list of enabled source names
     */
    @GetMapping("/enabled")
    public ResponseEntity<Map<String, Object>> getEnabledSources() {
        try {
            return ResponseEntity.ok(Map.of(
                "enabledSources", dataSourceManager.getEnabledSourceNames(),
                "timestamp", System.currentTimeMillis()
            ));
        } catch (Exception e) {
            log.error("Error getting enabled sources: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Failed to get enabled sources"
            ));
        }
    }
}