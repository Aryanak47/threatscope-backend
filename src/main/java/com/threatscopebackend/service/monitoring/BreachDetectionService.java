package com.threatscopebackend.service.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class BreachDetectionService {
    
    private final SearchService searchService;
    private final AlertService alertService;
    private final MonitoringService monitoringService;
    private final ObjectMapper objectMapper;
    
    /**
     * Check a monitoring item for new breaches
     */
    public void checkMonitoringItem(MonitoringItem item) {
        log.debug("Checking monitoring item: {} ({})", item.getId(), item.getTargetValue());
        
        try {
            // Record that we're checking this item
            monitoringService.recordCheck(item.getId());
            
            // Perform search based on monitor type
            List<Map<String, Object>> results = performSearch(item);
            
            // Process results and create alerts if needed
            processSearchResults(item, results);
            
            log.debug("Completed check for monitoring item: {}", item.getId());
            
        } catch (Exception e) {
            log.error("Error checking monitoring item {}: {}", item.getId(), e.getMessage(), e);
        }
    }
    
    private List<Map<String, Object>> performSearch(MonitoringItem item) {
        String searchQuery = buildSearchQuery(item);
        
        try {
            // Use the existing search service to look for breaches
            // This will search through the Elasticsearch index
            return searchService.searchForMonitoring(searchQuery, item.getMonitorType());
            
        } catch (Exception e) {
            log.error("Error performing search for monitoring item {}: {}", item.getId(), e.getMessage());
            return List.of(); // Return empty list on error
        }
    }
    
    private String buildSearchQuery(MonitoringItem item) {
        return switch (item.getMonitorType()) {
            case EMAIL -> item.getTargetValue();
            case DOMAIN -> "*@" + item.getTargetValue();
            case USERNAME -> item.getTargetValue();
            case KEYWORD -> item.getTargetValue();
            case IP_ADDRESS -> item.getTargetValue();
            case PHONE -> item.getTargetValue();
            case ORGANIZATION -> item.getTargetValue();
        };
    }
    
    private void processSearchResults(MonitoringItem item, List<Map<String, Object>> results) {
        if (results.isEmpty()) {
            return;
        }
        
        // Filter results to only include new ones since last check
        LocalDateTime lastCheck = item.getLastChecked();
        List<Map<String, Object>> newResults = filterNewResults(results, lastCheck);
        
        if (newResults.isEmpty()) {
            log.debug("No new results for monitoring item: {}", item.getId());
            return;
        }
        
        log.info("Found {} new breach results for monitoring item: {}", newResults.size(), item.getId());
        
        // Create alerts for new results
        for (Map<String, Object> result : newResults) {
            createAlertFromResult(item, result);
        }
    }
    
    private List<Map<String, Object>> filterNewResults(List<Map<String, Object>> results, LocalDateTime lastCheck) {
        if (lastCheck == null) {
            // If this is the first check, return all results
            return results;
        }
        
        // Filter results based on breach date or creation date
        return results.stream()
            .filter(result -> {
                try {
                    String dateStr = (String) result.get("breach_date");
                    if (dateStr != null) {
                        LocalDateTime breachDate = LocalDateTime.parse(dateStr);
                        return breachDate.isAfter(lastCheck);
                    }
                    return true; // Include if no date information
                } catch (Exception e) {
                    return true; // Include if date parsing fails
                }
            })
            .toList();
    }
    
    private void createAlertFromResult(MonitoringItem item, Map<String, Object> result) {
        try {
            String title = buildAlertTitle(item, result);
            String description = buildAlertDescription(item, result);
            CommonEnums.AlertSeverity severity = determineSeverity(item, result);
            String breachSource = (String) result.getOrDefault("source", "Unknown");
            LocalDateTime breachDate = parseBreachDate(result);
            String breachData = convertResultToJson(result);
            
            alertService.createAlert(item, title, description, severity, breachSource, breachDate, breachData);
            
        } catch (Exception e) {
            log.error("Error creating alert for monitoring item {}: {}", item.getId(), e.getMessage());
        }
    }
    
    private String buildAlertTitle(MonitoringItem item, Map<String, Object> result) {
        String source = (String) result.getOrDefault("source", "Unknown Source");
        return String.format("New breach detected for %s in %s", 
            item.getTargetValue(), source);
    }
    
    private String buildAlertDescription(MonitoringItem item, Map<String, Object> result) {
        StringBuilder description = new StringBuilder();
        
        description.append("A new data breach has been detected for your monitored ");
        description.append(item.getMonitorType().toString().toLowerCase()).append(": ");
        description.append(item.getTargetValue()).append("\n\n");
        
        description.append("Breach Details:\n");
        description.append("- Source: ").append(result.getOrDefault("source", "Unknown")).append("\n");
        description.append("- Date: ").append(result.getOrDefault("breach_date", "Unknown")).append("\n");
        
        if (result.containsKey("password")) {
            description.append("- Password: ").append(result.get("password")).append("\n");
        }
        
        if (result.containsKey("additional_data")) {
            description.append("- Additional Data: ").append(result.get("additional_data")).append("\n");
        }
        
        description.append("\nPlease review this alert and take appropriate security measures.");
        
        return description.toString();
    }
    
    private CommonEnums.AlertSeverity determineSeverity(MonitoringItem item, Map<String, Object> result) {
        // Determine severity based on various factors
        
        // Critical severity for email breaches with passwords
        if (item.getMonitorType() == CommonEnums.MonitorType.EMAIL && result.containsKey("password")) {
            return CommonEnums.AlertSeverity.CRITICAL;
        }
        
        // High severity for domain breaches
        if (item.getMonitorType() == CommonEnums.MonitorType.DOMAIN) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        
        // High severity for IP address breaches
        if (item.getMonitorType() == CommonEnums.MonitorType.IP_ADDRESS) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        
        // Medium severity for username breaches with passwords
        if (item.getMonitorType() == CommonEnums.MonitorType.USERNAME && result.containsKey("password")) {
            return CommonEnums.AlertSeverity.MEDIUM;
        }
        
        // Check if this is a recent breach (within last 30 days)
        LocalDateTime breachDate = parseBreachDate(result);
        if (breachDate != null && breachDate.isAfter(LocalDateTime.now().minusDays(30))) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        
        // Default to medium severity
        return CommonEnums.AlertSeverity.MEDIUM;
    }
    
    private LocalDateTime parseBreachDate(Map<String, Object> result) {
        try {
            String dateStr = (String) result.get("breach_date");
            if (dateStr != null) {
                // Try different date formats
                try {
                    return LocalDateTime.parse(dateStr);
                } catch (Exception e) {
                    // Try with different formatter
                    return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
                }
            }
        } catch (Exception e) {
            log.debug("Could not parse breach date: {}", e.getMessage());
        }
        
        return null;
    }
    
    private String convertResultToJson(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (Exception e) {
            log.error("Error converting result to JSON: {}", e.getMessage());
            return result.toString();
        }
    }
}
