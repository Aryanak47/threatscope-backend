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
        String source = cleanString((String) result.get("source"));
        String domain = extractDomainFromResult(result);
        
        if (isNotEmpty(source) && !"Unknown".equals(source)) {
            return String.format("🚨 New breach detected for %s in %s", item.getTargetValue(), source);
        } else if (isNotEmpty(domain)) {
            return String.format("🚨 New breach detected for %s on %s", item.getTargetValue(), domain);
        } else {
            return String.format("🚨 New breach detected for %s", item.getTargetValue());
        }
    }
    
    private String buildAlertDescription(MonitoringItem item, Map<String, Object> result) {
        StringBuilder description = new StringBuilder();
        
        // Header with emoji and clean formatting
        description.append("🔍 **Security Alert: New Data Breach Detected**\n\n");
        
        // Target information
        description.append("🎯 **Monitored Asset:** ").append(item.getTargetValue()).append("\n");
        description.append("💱 **Monitor Type:** ").append(item.getMonitorType().toString().toLowerCase().replace("_", " ")).append("\n\n");
        
        // Breach details (only show non-null, meaningful values)
        description.append("📄 **Breach Information:**\n");
        
        String source = cleanString((String) result.get("source"));
        if (isNotEmpty(source) && !"Unknown".equals(source)) {
            description.append("• **Source:** ").append(source).append("\n");
        }
        
        String breachDate = cleanString((String) result.get("breach_date"));
        if (isNotEmpty(breachDate) && !"null".equals(breachDate)) {
            description.append("• **Date:** ").append(formatBreachDate(breachDate)).append("\n");
        }
        
        String domain = extractDomainFromResult(result);
        if (isNotEmpty(domain)) {
            description.append("• **Associated Domain:** ").append(domain).append("\n");
        }
        
        String password = cleanString((String) result.get("password"));
        if (isNotEmpty(password)) {
            description.append("• **Password Exposed:** ").append(maskPassword(password)).append("\n");
        }
        
        String url = cleanString((String) result.get("url"));
        if (isNotEmpty(url) && !url.equals(domain)) {
            description.append("• **URL:** ").append(url).append("\n");
        }
        
        // Security recommendations
        description.append("\n🔒 **Recommended Actions:**\n");
        description.append("• Change passwords immediately for this account\n");
        description.append("• Enable two-factor authentication (2FA)\n");
        description.append("• Monitor account activity for suspicious behavior\n");
        description.append("• Consider using unique passwords for each service\n\n");
        
        description.append("⚠️ Please review this alert and take appropriate security measures.");
        
        return description.toString();
    }
    
    private CommonEnums.AlertSeverity determineSeverity(MonitoringItem item, Map<String, Object> result) {
        // Get cleaned values
        String password = cleanString((String) result.get("password"));
        String source = cleanString((String) result.get("source"));
        boolean hasPassword = isNotEmpty(password);
        boolean hasKnownSource = isNotEmpty(source) && !"Unknown".equals(source);
        
        // Critical severity for email breaches with passwords
        if (item.getMonitorType() == CommonEnums.MonitorType.EMAIL && hasPassword) {
            return CommonEnums.AlertSeverity.CRITICAL;
        }
        
        // High severity for domain breaches with passwords
        if (item.getMonitorType() == CommonEnums.MonitorType.DOMAIN && hasPassword) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        
        // High severity for IP address breaches
        if (item.getMonitorType() == CommonEnums.MonitorType.IP_ADDRESS) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        
        // High severity for username breaches with passwords
        if (item.getMonitorType() == CommonEnums.MonitorType.USERNAME && hasPassword) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        
        // Check if this is a recent breach (within last 30 days)
        LocalDateTime breachDate = parseBreachDate(result);
        if (breachDate != null && breachDate.isAfter(LocalDateTime.now().minusDays(30))) {
            return hasPassword ? CommonEnums.AlertSeverity.HIGH : CommonEnums.AlertSeverity.MEDIUM;
        }
        
        // Higher severity if we have known source
        if (hasKnownSource) {
            return hasPassword ? CommonEnums.AlertSeverity.MEDIUM : CommonEnums.AlertSeverity.LOW;
        }
        
        // Default severity based on whether password is exposed
        return hasPassword ? CommonEnums.AlertSeverity.MEDIUM : CommonEnums.AlertSeverity.LOW;
    }
    
    private LocalDateTime parseBreachDate(Map<String, Object> result) {
        try {
            Object dateObj = result.get("breach_date");
            if (dateObj == null) {
                return null;
            }
            
            // Handle different types of date objects
            if (dateObj instanceof LocalDateTime) {
                return (LocalDateTime) dateObj;
            } else if (dateObj instanceof String) {
                String dateStr = (String) dateObj;
                // Try different date formats
                try {
                    return LocalDateTime.parse(dateStr);
                } catch (Exception e) {
                    // Try with different formatter
                    return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
                }
            } else {
                log.debug("Unexpected date object type: {}", dateObj.getClass().getName());
                return null;
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
    
    // Helper methods for data cleaning and formatting
    
    private boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim());
    }
    
    private String cleanString(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }
    
    private String extractDomainFromResult(Map<String, Object> result) {
        String url = cleanString((String) result.get("url"));
        if (isNotEmpty(url)) {
            try {
                if (!url.startsWith("http")) {
                    url = "https://" + url;
                }
                java.net.URI uri = new java.net.URI(url);
                String domain = uri.getHost();
                return domain != null ? domain.toLowerCase() : null;
            } catch (Exception e) {
                // If URL parsing fails, try to extract domain pattern
                if (url.contains(".")) {
                    String[] parts = url.split("/")[0].split("@");
                    String domainPart = parts[parts.length - 1];
                    if (domainPart.contains(".")) {
                        return domainPart.toLowerCase();
                    }
                }
            }
        }
        
        // Try to extract domain from additional_data
        Object additionalData = result.get("additional_data");
        if (additionalData instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) additionalData;
            String domain = cleanString((String) dataMap.get("domain"));
            if (isNotEmpty(domain)) {
                return domain.toLowerCase();
            }
        }
        
        return null;
    }
    
    private String formatBreachDate(String dateStr) {
        if (!isNotEmpty(dateStr)) {
            return "Unknown";
        }
        
        try {
            LocalDateTime dateTime = LocalDateTime.parse(dateStr);
            return dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
        } catch (Exception e) {
            try {
                // Try parsing with different format
                LocalDateTime dateTime = LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_DATE_TIME);
                return dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
            } catch (Exception e2) {
                // If all parsing fails, return cleaned original
                return dateStr;
            }
        }
    }
    
    private String maskPassword(String password) {
        if (!isNotEmpty(password)) {
            return "[Not disclosed]";
        }
        
        if (password.length() <= 4) {
            return "****";
        }
        
        // Show first 2 and last 2 characters with asterisks in between
        int visibleChars = Math.min(2, password.length() / 3);
        String start = password.substring(0, visibleChars);
        String end = password.substring(password.length() - visibleChars);
        int maskedLength = Math.max(4, password.length() - (2 * visibleChars));
        String masked = "*".repeat(maskedLength);
        
        return start + masked + end;
    }
}
