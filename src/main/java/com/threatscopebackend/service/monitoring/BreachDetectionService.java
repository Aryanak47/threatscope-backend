package com.threatscopebackend.service.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.repository.postgresql.ProcessedBreachRepository;
import com.threatscopebackend.service.SearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BreachDetectionService {

    private final SearchService searchService;
    private final AlertService alertService;
    private final MonitoringService monitoringService;
    private final ProcessedBreachRepository processedBreachRepository;

    /**
     * Check a monitoring item for new breaches
     * FIXED: Record check AFTER alerts are successfully created
     */
    public boolean checkMonitoringItem(MonitoringItem item) {
        log.debug("Checking monitoring item: {} ({})", item.getId(), item.getTargetValue());


        // ✅ FIXED: Store current lastChecked BEFORE updating it
        LocalDateTime previousCheck = item.getLastChecked();

        // Perform search based on monitor type
        List<Map<String, Object>> results = performSearch(item);

        // Process results using the ORIGINAL lastChecked time
        boolean alertsCreatedSuccessfully = processSearchResults(item, results, previousCheck);

        // ✅ FIXED: Only record check AFTER alerts are successfully created
        if (!results.isEmpty() && alertsCreatedSuccessfully) {
            monitoringService.recordCheck(item.getId());
            log.debug("Successfully processed and recorded check for monitoring item: {}", item.getId());
            return true;
        }
        log.debug("Alerts creation failed, NOT updating lastChecked for item: {}", item.getId());
        return false;

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
                        // ✅ FIXED: Store previous check time BEFORE updating
                        LocalDateTime previousCheck = item.getLastChecked();

                        // Get results for this specific item
                        List<Map<String, Object>> itemResults = bulkResults.getOrDefault(item.getTargetValue(), List.of());

                        // Process results using ORIGINAL lastChecked time
                        boolean alertsCreatedSuccessfully = processSearchResults(item, itemResults, previousCheck);

                        // ✅ FIXED: Only record check AFTER successful alert creation
                        if (alertsCreatedSuccessfully) {
                            monitoringService.recordCheck(item.getId());
                        }

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

    /**
     * Process search results and create alerts for new breaches
     * Return success status and use previousCheck time to prevent race condition
     */
    private boolean processSearchResults(MonitoringItem item, List<Map<String, Object>> results, LocalDateTime previousCheck) {
        if (results.isEmpty()) {
            return true; // No results to process = success
        }

        // Use previousCheck and pass monitoring item for duplicate tracking
        List<Map<String, Object>> newResults = filterNewResults(results, previousCheck, item);

        if (newResults.isEmpty()) {
            log.debug("No new results for monitoring item: {} (last check: {})", item.getId(), previousCheck);
            return true; // No new results = success
        }

        log.info("Found {} new breach results for monitoring item: {} (filtered using: {})",
                newResults.size(), item.getId(), previousCheck);

        boolean allAlertsCreatedSuccessfully = false;
        int successfulAlerts = 0;

        // Create alerts for new results
        for (Map<String, Object> result : newResults) {
            try {
                boolean alertCreated = alertService.createAlertFromResult(item, result, processedBreachRepository);
                if (alertCreated) {
                    successfulAlerts++;
                    allAlertsCreatedSuccessfully = true;
                }
            } catch (Exception e) {
                log.error("Failed to create alert for breach in item {}: {}", item.getId(), e.getMessage());
            }
        }

        log.info("Alert creation summary for item {}: {}/{} alerts created successfully",
                item.getId(), successfulAlerts, newResults.size());

        return allAlertsCreatedSuccessfully;
    }

    /**
     * Filter results using ProcessedBreach table for reliable duplicate prevention
     */
    private List<Map<String, Object>> filterNewResults(List<Map<String, Object>> results, LocalDateTime lastCheck, MonitoringItem item) {
        if (results.isEmpty()) {
            return results;
        }

        return results.stream()
                .filter(result -> {
                    try {
                        String breachId = (String) result.get("id");
                        
                        // Primary duplicate check: breach ID tracking (permanent blocking)
                        if (breachId != null && !breachId.trim().isEmpty()) {
                            // Check if we've EVER processed this breach ID for this monitoring item
                            if (processedBreachRepository.existsByMonitoringItemAndBreachId(item, breachId)) {
                                log.debug("Skipping already processed breach ID: {} for item: {}", breachId, item.getId());
                                return false;
                            }
                        }
                        return true; // Include this result
                        
                    } catch (Exception e) {
                        log.debug("Error filtering result, excluding: {}", e.getMessage());
                        return false; // Exclude problematic results
                    }
                })
                .toList();
    }



    /**
     * Cleanup old processed breach records to prevent table bloat
     * Should be called periodically (e.g., daily)
     */
    public int cleanupOldProcessedBreaches(int daysToKeep) {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
            int deleted = processedBreachRepository.deleteOldProcessedBreaches(cutoffDate);
            log.info("Cleaned up {} old processed breach records older than {} days", deleted, daysToKeep);
            return deleted;
        } catch (Exception e) {
            log.error("Error cleaning up old processed breaches: {}", e.getMessage());
            return 0;
        }
    }
}