package com.threatscopebackend.controller;


import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.SearchService;
import com.threatscopebackend.service.LazyMetricsService;
import com.threatscopebackend.service.datasource.DataSourceManager;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/v1/search")
@RequiredArgsConstructor
@Slf4j
@Validated
public class SearchController {

    private final SearchService searchService;
    private final LazyMetricsService lazyMetricsService;
    private final DataSourceManager dataSourceManager;


    /**
     * Main search endpoint
     */
    @PostMapping
    public ResponseEntity<?> search(
            @Valid @RequestBody SearchRequest request,
            @CurrentUser UserPrincipal user,
            HttpServletRequest httpRequest) {

        // Handle anonymous users for demo/testing
        if (user == null) {
            user = UserPrincipal.anonymousUser();
        }

        log.info("Search request from user {}: {} (type: {})",
                user.getId(), request.getQuery(), request.getSearchType());

        try {
            // Add IP address to request context for rate limiting
            String clientIp = getClientIpAddress(httpRequest);

            // Log the search attempt
            log.debug("Processing search request - Query: {}, Type: {}, Page: {}, Size: {}",
                    request.getQuery(), request.getSearchType(), request.getPage(), request.getSize());

            SearchResponse response = searchService.search(request, user);

            // Log the search result
            log.debug("Search completed - Found {} results",
                    response != null ? response.getTotalResults() : 0);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid search request from user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Search failed for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "An error occurred while processing your search. Please try again later."
            ));
        }
    }

    /**
     * Quick search endpoint for simple queries
     */
    @GetMapping
    public ResponseEntity<?> quickSearch(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "Page size must be at least 1") @Max(value = 100, message = "Page size cannot exceed 100") int size,
            @RequestParam(defaultValue = "timestamp") @Pattern(regexp = "^(timestamp|login|url|password|metadata|_score)$",
                                                          message = "Invalid sort field. Must be one of: timestamp, login, url, password, metadata, or _score") String sortBy,
            @RequestParam(defaultValue = "desc") @Pattern(regexp = "^(?i)(asc|desc)$",
                                                       message = "Sort direction must be 'asc' or 'desc' (case insensitive)") String sortDirection,
            @RequestParam(defaultValue = "autoDetect") @Pattern(regexp = "^(?i)(Auto|email|url|password|username|domain|advanced)$",
                                                      message = "Search type must be one of: autoDetect, email, url, password, username, domain, or advanced (case insensitive)") String searchType,
            @CurrentUser UserPrincipal user) {
            
        // Handle anonymous users for demo/testing
        if (user == null) {
            user = UserPrincipal.anonymousUser();
        }

        try {
            // Validate query parameter
            if (q == null || q.trim().length() < 2) {
                return ResponseEntity.badRequest().body(Map.of(
                        "status", "error",
                        "message", "Search query must be at least 2 characters long"
                ));
            }

            log.info("Quick search request from user {}: {}", user.getId(), q);
            SearchRequest request = SearchRequest.builder()
                    .query(q.trim())
                    .searchType(SearchRequest.SearchType.valueOf(searchType.toUpperCase()))
                    .page(page)
                    .size(size)
                    .sortBy(sortBy)
                    .sortDirection(sortDirection)
                    .build();

            SearchResponse response = searchService.search(request, user);

            // Log the search result
            log.debug("Quick search completed - Found {} results for query: {}",
                    response.getTotalResults(), q);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Quick search failed for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "An error occurred while processing your search. Please try again later."
            ));
        }
    }

    /**
     * Multi-source search endpoint - searches across all enabled data sources
     */
    @PostMapping("/multi-source")
    public ResponseEntity<?> searchMultiSource(
            @Valid @RequestBody SearchRequest request,
            @CurrentUser UserPrincipal user,
            HttpServletRequest httpRequest) {

        // Handle anonymous users for demo/testing
        if (user == null) {
            user = UserPrincipal.anonymousUser();
        }

        log.info("Multi-source search request from user {}: {} (type: {})",
                user.getId(), request.getQuery(), request.getSearchType());

        try {
            // Add IP address to request context for rate limiting
            String clientIp = getClientIpAddress(httpRequest);

            // Log the search attempt
            log.debug("Processing multi-source search request - Query: {}, Type: {}, Page: {}, Size: {}",
                    request.getQuery(), request.getSearchType(), request.getPage(), request.getSize());

            SearchResponse response = searchService.searchMultiSource(request, user);

            // Log the search result
            log.debug("Multi-source search completed - Found {} results from {} sources",
                    response != null ? response.getTotalResults() : 0,
                    response != null && response.getMetadata() != null && response.getMetadata().getSourceBreakdown() != null ? 
                            response.getMetadata().getSourceBreakdown().size() : 0);

            return ResponseEntity.ok(response);

        } catch (IllegalArgumentException e) {
            log.warn("Invalid multi-source search request from user {}: {}", user.getId(), e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                    "status", "error",
                    "message", e.getMessage()
            ));
        } catch (Exception e) {
            log.error("Multi-source search failed for user {}: {}", user.getId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "An error occurred while processing your multi-source search. Please try again later."
            ));
        }
    }

    /**
     * Get information about available data sources
     */
    @GetMapping("/sources")
    public ResponseEntity<?> getDataSources(@CurrentUser UserPrincipal user) {
        
        // Handle anonymous users for demo/testing
        if (user == null) {
            user = UserPrincipal.anonymousUser();
        }

        try {
            log.debug("Data sources info request from user {}", user.getId());
            
            Map<String, Object> sourcesInfo = dataSourceManager.getDataSourceInfo();
            List<String> enabledSources = dataSourceManager.getEnabledSourceNames();
            
            Map<String, Object> response = Map.of(
                    "sources", sourcesInfo,
                    "enabledSources", enabledSources,
                    "totalSources", sourcesInfo.size()
            );
            
            return ResponseEntity.ok(response);

        } catch (Exception e) {
            log.error("Failed to get data sources info: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to retrieve data sources information"
            ));
        }
    }

    /**
     * Get search suggestions
     */
//    @GetMapping("/suggestions")
//    public ResponseEntity<SearchSuggestions> getSearchSuggestions(
//            @RequestParam(required = false) String q,
//            @CurrentUser UserPrincipal user) {
//
//        SearchSuggestions suggestions = searchService.getSearchSuggestions(q, user);
//
//        return ResponseEntity.ok(suggestions);
//    }

    /**
     * Get search statistics for dashboard
     */
//    @GetMapping("/statistics")
//    public ResponseEntity<SearchStatistics> getSearchStatistics(
//            @CurrentUser UserPrincipal user) {
//
//        SearchStatistics stats = searchService.getSearchStatistics(user);
//
//        return ResponseEntity.ok(stats);
//    }

    /**
     * Export search results
     */
//    @PostMapping("/export")
//    @PreAuthorize("hasRole('USER')")
//    public ResponseEntity<byte[]> exportSearchResults(
//            @Valid @RequestBody SearchRequest request,
//            @RequestParam(defaultValue = "CSV") String format,
//            @CurrentUser UserPrincipal user) {
//
//        log.info("Export request from user {}: {} results in {} format",
//                user.getId(), request.getQuery(), format);
//
//        // Check export limits based on subscription
//        searchService.checkExportLimits(user);
//
//        byte[] exportData = exportService.exportSearchResults(request, format, user);
//
//        String filename = generateExportFilename(request.getQuery(), format);
//        String contentType = getContentType(format);
//
//        return ResponseEntity.ok()
//                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
//                .contentType(MediaType.parseMediaType(contentType))
//                .body(exportData);
//    }

    /**
     * Get available search filters and their options
     */
//    @GetMapping("/filters")
//    public ResponseEntity<Map<String, Object>> getSearchFilters(
//            @CurrentUser UserPrincipal user) {
//
//        Map<String, Object> filters = searchService.getAvailableFilters();
//
//        return ResponseEntity.ok(filters);
//    }

    /**
     * Validate search query without executing
     */
//    @PostMapping("/validate")
//    public ResponseEntity<Map<String, Object>> validateQuery(
//            @Valid @RequestBody SearchRequest request,
//            @CurrentUser UserPrincipal user) {
//
//        Map<String, Object> validation = searchService.validateQuery(request);
//
//        return ResponseEntity.ok(validation);
//    }

    /**
     * Get search history for user
     */
//    @GetMapping("/history")
//    public ResponseEntity<List<SearchHistory>> getSearchHistory(
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @CurrentUser UserPrincipal user) {
//
//        Pageable pageable = PageRequest.of(page, size);
//        List<SearchHistory> history = searchService.getSearchHistory(user, pageable);
//
//        return ResponseEntity.ok(history);
//    }

    /**
     * Advanced search with complex filters
     */
//    @PostMapping("/advanced")
//    public ResponseEntity<SearchResponse> advancedSearch(
//            @Valid @RequestBody AdvancedSearchRequest request,
//            @CurrentUser UserPrincipal user) {
//
//        log.info("Advanced search request from user {}", user.getId());
//
//        SearchResponse response = searchService.performAdvancedSearch(request, user);
//
//        return ResponseEntity.ok(response);
//    }

    /**
     * Bulk search for multiple queries
     */
//    @PostMapping("/bulk")
//    @PreAuthorize("hasRole('ADMIN') or hasRole('MANAGER')")
//    public ResponseEntity<List<SearchResponse>> bulkSearch(
//            @Valid @RequestBody BulkSearchRequest request,
//            @CurrentUser UserPrincipal user) {
//
//        log.info("Bulk search request from user {} with {} queries",
//                user.getId(), request.getQueries().size());
//
//        List<SearchResponse> responses = searchService.performBulkSearch(request, user);
//
//        return ResponseEntity.ok(responses);
//    }

    /**
     * Get breach timeline for specific target
     */
//    @GetMapping("/timeline")
//    public ResponseEntity<List<TimelineEvent>> getBreachTimeline(
//            @RequestParam String target,
//            @RequestParam(defaultValue = "EMAIL") String targetType,
//            @RequestParam(required = false) String startDate,
//            @RequestParam(required = false) String endDate,
//            @CurrentUser UserPrincipal user) {
//
//        List<TimelineEvent> timeline = searchService.getBreachTimeline(
//                target, targetType, startDate, endDate, user);
//
//        return ResponseEntity.ok(timeline);
//    }

    /**
     * Search similar records based on a specific breach
     */
//    @GetMapping("/{breachId}/similar")
//    public ResponseEntity<SearchResponse> findSimilarBreaches(
//            @PathVariable String breachId,
//            @RequestParam(defaultValue = "0") int page,
//            @RequestParam(defaultValue = "20") int size,
//            @CurrentUser UserPrincipal user) {
//
//        SearchResponse response = searchService.findSimilarBreaches(breachId, page, size, user);
//
//        return ResponseEntity.ok(response);
//    }

    /**
     * Get detailed metrics for a specific source (lazy loaded)
     */
    @GetMapping("/metrics/{source}")
    public ResponseEntity<?> getSourceMetrics(
            @PathVariable String source,
            @CurrentUser UserPrincipal user) {
            
        // Handle anonymous users for demo/testing
        if (user == null) {
            user = UserPrincipal.anonymousUser();
        }

        try {
            log.info("Metrics request from user {} for source: {}", user.getId(), source);
            
            LazyMetricsService.SourceDetailedMetrics metrics = lazyMetricsService.calculateDetailedMetrics(source);
            
            return ResponseEntity.ok(metrics);

        } catch (Exception e) {
            log.error("Metrics calculation failed for source {}: {}", source, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "error",
                    "message", "Failed to calculate metrics for source: " + source
            ));
        }
    }

    // Helper methods
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty() && !"unknown".equalsIgnoreCase(xForwardedFor)) {
            return xForwardedFor.split(",")[0].trim();
        }

        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty() && !"unknown".equalsIgnoreCase(xRealIp)) {
            return xRealIp;
        }

        return request.getRemoteAddr();
    }

//    private String generateExportFilename(String query, String format) {
//        String sanitizedQuery = query.replaceAll("[^a-zA-Z0-9-_", "_");
//        String timestamp = java.time.LocalDateTime.now().format(
//                java.time.format.DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
//        return String.format("threatscope_search_%s_%s.%s",
//                sanitizedQuery, timestamp, format.toLowerCase());
//    }

    private String getContentType(String format) {
        return switch (format.toUpperCase()) {
            case "CSV" -> "text/csv";
            case "JSON" -> "application/json";
            case "PDF" -> "application/pdf";
            case "XLSX" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            default -> "application/octet-stream";
        };
    }
}
