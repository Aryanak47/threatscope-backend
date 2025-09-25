package com.threatscopebackend.service.datasource;

import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.DataSourceMonitoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

/**
 * Manages all data sources and coordinates multi-source searches
 * Handles parallel execution, result aggregation, and source coordination
 */
@Service
@Slf4j
public class DataSourceManager {
    
    private final List<DataSourceService> dataSources;
    private final DataSourceMonitoringService monitoringService;
    
    public DataSourceManager(List<DataSourceService> dataSources, DataSourceMonitoringService monitoringService) {
        this.dataSources = dataSources;
        this.monitoringService = monitoringService;
        
        // Debug: Log all discovered data sources on startup
        log.info("DataSourceManager initialized with {} data sources:", 
                dataSources != null ? dataSources.size() : 0);
        
        if (dataSources != null) {
            for (DataSourceService source : dataSources) {
                log.info("  - {} ({}): enabled={}, healthy={}, priority={}", 
                        source.getSourceName(), 
                        source.getDisplayName(),
                        source.isEnabled(),
                        source.isHealthy(),
                        source.getPriority());
            }
        } else {
            log.error("❌ CRITICAL: No data sources injected! Multi-source search will not work.");
        }
    }
    
    // Dedicated thread pool with proper sizing to prevent thread exhaustion
    private final ExecutorService multiSourceExecutor = Executors.newFixedThreadPool(8, r -> {
        Thread t = new Thread(r, "MultiSource-Worker");
        t.setDaemon(true);
        return t;
    });
    
    /**
     * Search across all enabled data sources and aggregate results
     * @param request The search request
     * @param user The authenticated user
     * @return Aggregated search results from all sources
     */
    public CompletableFuture<List<SearchResponse.SearchResult>> searchAllSources(
            SearchRequest request, UserPrincipal user) {
        
        log.debug("Starting multi-source search for query: {}", request.getQuery());
        
        // Get enabled sources that support this search type
        List<DataSourceService> enabledSources = getEnabledSources(request);
        
        if (enabledSources.isEmpty()) {
            log.warn("No enabled data sources found for search type: {}", request.getSearchType());
            return CompletableFuture.completedFuture(List.of());
        }
        
        log.debug("Searching {} enabled data sources: {}", 
                enabledSources.size(), 
                enabledSources.stream().map(DataSourceService::getSourceName).collect(Collectors.toList()));
        
        // Execute searches in parallel with proper error isolation and monitoring
        List<CompletableFuture<SearchSourceResult>> futures = enabledSources.stream()
                .map(source -> {
                    log.debug("Starting search on source: {}", source.getSourceName());
                    return CompletableFuture.supplyAsync(() -> {
                        LocalDateTime startTime = LocalDateTime.now();
                        String sourceName = source.getSourceName();
                        
                        try {
                            List<SearchResponse.SearchResult> results = source.search(request, user).get(10, TimeUnit.SECONDS);
                            long durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();
                            

                            log.debug("Source {} completed successfully: {} results in {}ms", 
                                    sourceName, results.size(), durationMs);
                            
                            return new SearchSourceResult(sourceName, results, true, null, durationMs);
                            
                        } catch (TimeoutException e) {
                            long durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();
                            log.warn("Source {} timed out after {}ms", sourceName, durationMs);
                            monitoringService.recordSearchOperation(sourceName, durationMs, false, 0);
                            return new SearchSourceResult(sourceName, List.of(), false, "Timeout after " + durationMs + "ms", durationMs);
                            
                        } catch (Exception e) {
                            long durationMs = Duration.between(startTime, LocalDateTime.now()).toMillis();
                            log.error("Source {} failed after {}ms: {}", sourceName, durationMs, e.getMessage());
                            monitoringService.recordSearchOperation(sourceName, durationMs, false, 0);
                            return new SearchSourceResult(sourceName, List.of(), false, e.getMessage(), durationMs);
                        }
                    }, multiSourceExecutor);
                })
                .toList();
        
        // Wait for all searches to complete with overall timeout protection
        return CompletableFuture.supplyAsync(() -> {
            List<SearchResponse.SearchResult> aggregatedResults = new ArrayList<>();
            Map<String, SearchSourceResult> sourceResults = new HashMap<>();
            
            try {
                // Wait for all futures with global timeout
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                        .get(15, TimeUnit.SECONDS); // Global timeout for all sources
                
                // Collect results from all sources
                for (CompletableFuture<SearchSourceResult> future : futures) {
                    try {
                        SearchSourceResult result = future.get(100, TimeUnit.MILLISECONDS); // Should be ready
                        sourceResults.put(result.sourceName, result);
                        
                        if (result.success && !result.results.isEmpty()) {
                            aggregatedResults.addAll(result.results);
                            log.debug("Source {} contributed {} results", result.sourceName, result.results.size());
                        } else if (!result.success) {
                            log.warn("Source {} failed: {}", result.sourceName, result.error);
                        }
                        
                    } catch (Exception e) {
                        log.error("Error collecting results from future: {}", e.getMessage());
                    }
                }
                
            } catch (TimeoutException e) {
                log.warn("Global timeout reached for multi-source search after 15 seconds");
                // Cancel any remaining futures
                for (CompletableFuture<SearchSourceResult> future : futures) {
                    future.cancel(true);
                }
            } catch (Exception e) {
                log.error("Error during multi-source search aggregation: {}", e.getMessage(), e);
            }
            
            // Remove duplicates and sort by priority
            List<SearchResponse.SearchResult> deduplicatedResults = deduplicateResults(aggregatedResults);
            List<SearchResponse.SearchResult> sortedResults = sortResultsBySourcePriority(deduplicatedResults);
            
            // Log summary
            logSearchSummary(sourceResults, aggregatedResults.size(), sortedResults.size());
            
            return sortedResults;
        }, multiSourceExecutor);
    }
    
    /**
     * Search a specific data source by name
     * @param sourceName The name of the data source
     * @param request The search request
     * @param user The authenticated user
     * @return Results from the specific source
     */
    public CompletableFuture<List<SearchResponse.SearchResult>> searchSource(
            String sourceName, SearchRequest request, UserPrincipal user) {
        
        Optional<DataSourceService> source = dataSources.stream()
                .filter(ds -> ds.getSourceName().equals(sourceName))
                .filter(DataSourceService::isEnabled)
                .findFirst();
        
        if (source.isPresent()) {
            log.debug("Searching specific source: {}", sourceName);
            return source.get().search(request, user);
        } else {
            log.warn("Data source not found or disabled: {}", sourceName);
            return CompletableFuture.completedFuture(List.of());
        }
    }
    
    /**
     * Get all enabled data sources that support the given search type
     */
    private List<DataSourceService> getEnabledSources(SearchRequest request) {
        if (dataSources == null || dataSources.isEmpty()) {
            log.error("❌ CRITICAL: dataSources is null or empty! Available sources: {}", 
                     dataSources != null ? dataSources.size() : "null");
            return List.of();
        }
        
        log.debug("Filtering {} total data sources for search type: {}", 
                 dataSources.size(), request.getSearchType());
        
        List<DataSourceService> enabledSources = dataSources.stream()
                .filter(source -> {
                    boolean enabled = source.isEnabled();
                    log.debug("Source {}: enabled={}", source.getSourceName(), enabled);
                    return enabled;
                })
                .filter(source -> {
                    boolean healthy = source.isHealthy();
                    log.debug("Source {}: healthy={}", source.getSourceName(), healthy);
                    return healthy;
                })
                .filter(source -> {
                    boolean supports = source.supportsSearchType(request.getSearchType());
                    log.debug("Source {}: supports {}={}", source.getSourceName(), request.getSearchType(), supports);
                    return supports;
                })
                .sorted(Comparator.comparingInt(DataSourceService::getPriority))
                .collect(Collectors.toList());
        
        log.debug("Found {} enabled sources after filtering", enabledSources.size());
        return enabledSources;
    }
    
    /**
     * Remove duplicate results based on email address
     * Priority: Internal > BreachVIP > Others
     */
    private List<SearchResponse.SearchResult> deduplicateResults(List<SearchResponse.SearchResult> results) {
        Map<String, SearchResponse.SearchResult> uniqueResults = new HashMap<>();
        
        // Sort by source priority first (internal has priority 0, others higher)
        results.stream()
                .sorted((a, b) -> {
                    int priorityA = getSourcePriority(a);
                    int priorityB = getSourcePriority(b);
                    return Integer.compare(priorityA, priorityB);
                })
                .forEach(result -> {
                    // Use composite key: email + source + domain to preserve results from different sources
                    String email = result.getEmail() != null ? result.getEmail() : "";
                    String source = result.getSource() != null ? result.getSource() : "";
                    String domain = result.getDomain() != null ? result.getDomain() : "";
                    String key = email + "|" + source + "|" + domain;
                    
                    if (!uniqueResults.containsKey(key)) {
                        uniqueResults.put(key, result);
                        log.debug("Added result with key: {} from source: {}", key, source);
                    } else {
                        log.debug("Deduplicated result with key: {} from source: {}", key, source);
                    }
                });
        
        return new ArrayList<>(uniqueResults.values());
    }
    
    /**
     * Get source priority from result metadata
     */
    private int getSourcePriority(SearchResponse.SearchResult result) {
        if (result.getAdditionalData() != null) {
            String sourceType = (String) result.getAdditionalData().get("dataSource");
            if ("internal".equals(sourceType)) return 0;
            if ("breach-vip".equals(sourceType)) return 1;
        }
        return 999; // Unknown sources get lowest priority
    }
    
    /**
     * Sort results by source priority and then by timestamp
     */
    private List<SearchResponse.SearchResult> sortResultsBySourcePriority(List<SearchResponse.SearchResult> results) {
        return results.stream()
                .sorted((a, b) -> {
                    // First sort by source priority
                    int priorityCompare = Integer.compare(getSourcePriority(a), getSourcePriority(b));
                    if (priorityCompare != 0) return priorityCompare;
                    
                    // Then sort by timestamp (newest first)
                    if (a.getTimestamp() != null && b.getTimestamp() != null) {
                        return b.getTimestamp().compareTo(a.getTimestamp());
                    }
                    
                    return 0;
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Get information about all data sources
     * @return Map of source information
     */
    public Map<String, Object> getDataSourceInfo() {
        Map<String, Object> info = new HashMap<>();
        
        for (DataSourceService source : dataSources) {
            Map<String, Object> sourceInfo = new HashMap<>();
            sourceInfo.put("displayName", source.getDisplayName());
            sourceInfo.put("enabled", source.isEnabled());
            sourceInfo.put("healthy", source.isHealthy());
            sourceInfo.put("priority", source.getPriority());
            sourceInfo.put("maxResults", source.getMaxResultsLimit());
            
            info.put(source.getSourceName(), sourceInfo);
        }
        
        return info;
    }
    
    /**
     * Get list of enabled source names
     * @return List of enabled source names
     */
    public List<String> getEnabledSourceNames() {
        if (dataSources == null || dataSources.isEmpty()) {
            log.warn("No data sources available for getEnabledSourceNames()");
            return List.of();
        }
        
        return dataSources.stream()
                .filter(DataSourceService::isEnabled)
                .filter(DataSourceService::isHealthy)
                .map(DataSourceService::getSourceName)
                .collect(Collectors.toList());
    }
    
    /**
     * Get debug information about all data sources (for troubleshooting)
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debug = new HashMap<>();
        debug.put("totalSources", dataSources != null ? dataSources.size() : 0);
        debug.put("dataSourcesNull", dataSources == null);
        
        if (dataSources != null) {
            Map<String, Object> sourceDetails = new HashMap<>();
            for (DataSourceService source : dataSources) {
                try {
                    Map<String, Object> details = new HashMap<>();
                    details.put("className", source.getClass().getSimpleName());
                    details.put("sourceName", source.getSourceName());
                    details.put("displayName", source.getDisplayName());
                    details.put("enabled", source.isEnabled());
                    details.put("healthy", source.isHealthy());
                    details.put("priority", source.getPriority());
                    sourceDetails.put(source.getSourceName(), details);
                } catch (Exception e) {
                    sourceDetails.put("error_" + source.getClass().getSimpleName(), e.getMessage());
                }
            }
            debug.put("sources", sourceDetails);
        }
        
        return debug;
    }
    
    /**
     * Log search operation summary with performance metrics
     */
    private void logSearchSummary(Map<String, SearchSourceResult> sourceResults, 
                                 int totalResults, int finalResults) {
        
        StringBuilder summary = new StringBuilder("\n=== Multi-Source Search Summary ===\n");
        
        for (SearchSourceResult result : sourceResults.values()) {
            String status = result.success ? "✅ SUCCESS" : "❌ FAILED";
            summary.append(String.format("  %s (%dms): %s - %d results%s\n",
                    result.sourceName,
                    result.durationMs,
                    status,
                    result.results.size(),
                    result.error != null ? " [" + result.error + "]" : ""
            ));
        }
        
        summary.append(String.format("Total: %d → Final: %d (after deduplication)\n", 
                totalResults, finalResults));
        summary.append("=====================================");
        
        log.info(summary.toString());
    }
    
    /**
     * Internal class to hold search results from individual sources
     */
    private static class SearchSourceResult {
        final String sourceName;
        final List<SearchResponse.SearchResult> results;
        final boolean success;
        final String error;
        final long durationMs;
        
        SearchSourceResult(String sourceName, List<SearchResponse.SearchResult> results, 
                          boolean success, String error, long durationMs) {
            this.sourceName = sourceName;
            this.results = results;
            this.success = success;
            this.error = error;
            this.durationMs = durationMs;
        }
    }
}