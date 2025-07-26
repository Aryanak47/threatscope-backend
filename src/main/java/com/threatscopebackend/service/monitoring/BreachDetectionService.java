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
import java.util.HashMap;
import com.threatscopebackend.websocket.RealTimeNotificationService;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BreachDetectionService {
    
    private final SearchService searchService;
    private final AlertService alertService;
    private final MonitoringService monitoringService;
    private final ObjectMapper objectMapper;
    private final RealTimeNotificationService realTimeNotificationService;
    
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
    
    /**
     * OPTIMIZED: Process multiple monitoring items in bulk for better performance
     */
    public void processBulkMonitoringItems(List<MonitoringItem> items) {
        log.debug("Processing {} monitoring items in bulk", items.size());
        
        try {
            // Group items by monitor type for bulk search optimization
            Map<CommonEnums.MonitorType, List<MonitoringItem>> groupedItems = items.stream()
                .collect(Collectors.groupingBy(MonitoringItem::getMonitorType));
            
            // Process each group with optimized bulk searches
            for (Map.Entry<CommonEnums.MonitorType, List<MonitoringItem>> entry : groupedItems.entrySet()) {
                CommonEnums.MonitorType monitorType = entry.getKey();
                List<MonitoringItem> typeItems = entry.getValue();
                
                log.debug("Processing {} items of type {}", typeItems.size(), monitorType);
                
                // Perform bulk search for this monitor type
                Map<String, List<Map<String, Object>>> bulkResults = performBulkSearch(typeItems, monitorType);
                
                // Process results for each item
                for (MonitoringItem item : typeItems) {
                    try {
                        // Record check
                        monitoringService.recordCheck(item.getId());
                        
                        // Get results for this specific item
                        List<Map<String, Object>> itemResults = bulkResults.getOrDefault(item.getTargetValue(), List.of());
                        
                        // Process results and create alerts
                        processSearchResults(item, itemResults);
                        
                    } catch (Exception e) {
                        log.error("Error processing bulk item {}: {}", item.getId(), e.getMessage());
                    }
                }
            }
            
            log.debug("Completed bulk processing for {} monitoring items", items.size());
            
        } catch (Exception e) {
            log.error("Error in bulk processing, falling back to individual processing: {}", e.getMessage());
            
            // Fallback to individual processing
            for (MonitoringItem item : items) {
                try {
                    checkMonitoringItem(item);
                } catch (Exception individualError) {
                    log.error("Error in individual fallback processing for item {}: {}", item.getId(), individualError.getMessage());
                }
            }
        }
    }
    
    /**
     * Perform bulk search for multiple monitoring items of the same type
     */
    private Map<String, List<Map<String, Object>>> performBulkSearch(List<MonitoringItem> items, CommonEnums.MonitorType monitorType) {
        Map<String, List<Map<String, Object>>> results = new HashMap<>();
        
        try {
            // Collect all target values for bulk search
            List<String> targetValues = items.stream()
                .map(MonitoringItem::getTargetValue)
                .distinct()
                .toList();
            
            // Build bulk search queries
            List<String> searchQueries = targetValues.stream()
                .map(targetValue -> buildSearchQueryForValue(targetValue, monitorType))
                .toList();
            
            // Perform bulk search using the search service
            Map<String, List<Map<String, Object>>> bulkSearchResults = searchService.bulkSearchForMonitoring(searchQueries, monitorType);
            
            // Map results back to target values
            for (int i = 0; i < targetValues.size(); i++) {
                String targetValue = targetValues.get(i);
                String searchQuery = searchQueries.get(i);
                
                List<Map<String, Object>> queryResults = bulkSearchResults.getOrDefault(searchQuery, List.of());
                results.put(targetValue, queryResults);
            }
            
        } catch (Exception e) {
            log.error("Error in bulk search for {} items: {}", items.size(), e.getMessage());
            
            // Fallback to individual searches
            for (MonitoringItem item : items) {
                try {
                    List<Map<String, Object>> individualResults = performSearch(item);
                    results.put(item.getTargetValue(), individualResults);
                } catch (Exception individualError) {
                    log.error("Error in individual search fallback for item {}: {}", item.getId(), individualError.getMessage());
                    results.put(item.getTargetValue(), List.of());
                }
            }
        }
        
        return results;
    }
    
    /**
     * Build search query for a specific target value and monitor type
     */
    private String buildSearchQueryForValue(String targetValue, CommonEnums.MonitorType monitorType) {
        return switch (monitorType) {
            case EMAIL -> targetValue;
            case DOMAIN -> "*@" + targetValue;
            case USERNAME -> targetValue;
            case KEYWORD -> targetValue;
            case IP_ADDRESS -> targetValue;
            case PHONE -> targetValue;
            case ORGANIZATION -> targetValue;
        };
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
        
        // FIXED: Better filtering for null breach dates to prevent duplicates
        return results.stream()
            .filter(result -> {
                try {
                    String breachId = (String) result.get("id");
                    
                    // If we have a breach ID, check if we've processed it recently
                    if (breachId != null && !breachId.trim().isEmpty()) {
                        // Check if this breach ID was processed in the last check cycle
                        // For now, we'll use a simple time-based approach
                        // TODO: Implement proper breach ID tracking in database
                        
                        String dateStr = (String) result.get("breach_date");
                        if (dateStr != null && !"null".equals(dateStr)) {
                            LocalDateTime breachDate = LocalDateTime.parse(dateStr);
                            return breachDate.isAfter(lastCheck);
                        } else {
                            // FIXED: For null dates, only include if not processed recently
                            // Conservative approach: skip null-date breaches if checked within last hour
                            return lastCheck.isBefore(LocalDateTime.now().minusHours(1));
                        }
                    }
                    
                    // If no breach ID, use date-based filtering
                    String dateStr = (String) result.get("breach_date");
                    if (dateStr != null && !"null".equals(dateStr)) {
                        LocalDateTime breachDate = LocalDateTime.parse(dateStr);
                        return breachDate.isAfter(lastCheck);
                    }
                    
                    // FIXED: Be very conservative with null dates - only include if last check was > 6 hours ago
                    return lastCheck.isBefore(LocalDateTime.now().minusHours(6));
                    
                } catch (Exception e) {
                    log.debug("Error filtering result, excluding: {}", e.getMessage());
                    return false; // Exclude problematic results
                }
            })
            .toList();
    }
    
    private void createAlertFromResult(MonitoringItem item, Map<String, Object> result) {
        try {
            // FIXED: Check for potential duplicates using breach content hash
            String breachId = (String) result.get("id");
            String contentHash = generateSimpleContentHash(item, result);
            
            // Quick duplicate check - if we have an alert with same content hash in last 24 hours, skip
            if (breachId != null && alertService.hasRecentAlertWithSimilarContent(item, contentHash)) {
                log.debug("Skipping potential duplicate alert for breach ID: {} (item: {})", breachId, item.getId());
                return;
            }
            
            String title = buildAlertTitle(item, result);
            String description = buildAlertDescription(item, result);
            CommonEnums.AlertSeverity severity = determineSeverity(item, result);
            String breachSource = (String) result.getOrDefault("source", "Unknown");
            LocalDateTime breachDate = parseBreachDate(result);
            String breachData = convertResultToJson(result);
            
            BreachAlert alert = alertService.createAlert(item, title, description, severity, breachSource, breachDate, breachData);
            
            // 🚀 REAL-TIME FEATURE: Send instant WebSocket notification for critical alerts
            if (severity == CommonEnums.AlertSeverity.CRITICAL) {
                try {
                    // Send immediate real-time notification for critical alerts
                    realTimeNotificationService.sendRealTimeAlert(alert).thenAccept(sent -> {
                        if (sent) {
                            log.info("⚡ CRITICAL alert sent via WebSocket immediately: {}", alert.getId());
                        } else {
                            log.debug("📱 User offline - CRITICAL alert will be delivered via email: {}", alert.getId());
                        }
                    });
                } catch (Exception e) {
                    log.error("❌ Failed to send real-time notification for alert {}: {}", alert.getId(), e.getMessage());
                    // Don't fail the entire alert creation if real-time notification fails
                }
            }
            
        } catch (Exception e) {
            log.error("Error creating alert for monitoring item {}: {}", item.getId(), e.getMessage());
        }
    }
    
    /**
     * Generate a simple content hash for duplicate detection
     */
    private String generateSimpleContentHash(MonitoringItem item, Map<String, Object> result) {
        try {
            StringBuilder hashInput = new StringBuilder();
            hashInput.append(item.getId()).append("|");
            hashInput.append(result.get("login")).append("|");
            hashInput.append(result.get("password")).append("|");
            hashInput.append(result.get("source")).append("|");
            hashInput.append(result.get("url"));
            
            // Simple hash using built-in hashCode (not cryptographic, but sufficient for duplicate detection)
            return String.valueOf(hashInput.toString().hashCode());
        } catch (Exception e) {
            log.debug("Error generating content hash: {}", e.getMessage());
            return String.valueOf(System.currentTimeMillis()); // Fallback to timestamp
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
