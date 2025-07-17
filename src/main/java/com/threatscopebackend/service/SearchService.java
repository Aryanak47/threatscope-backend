package com.threatscopebackend.service;

import com.threatscopebackend.config.ElasticsearchConfig;
import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.elasticsearch.BreachDataIndex;
import com.threatscopebackend.repository.elasticsearch.BreachDataRepository;
import com.threatscopebackend.repository.mongo.StealerLogRepository;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.data.IndexNameProvider;
import com.threatscopebackend.service.search.MultiIndexSearchService;
import com.threatscopebackend.service.search.SearchFallbackService;
import com.threatscopebackend.service.security.RateLimitService;
import com.threatscopebackend.service.core.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.Criteria;
import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
import org.springframework.data.elasticsearch.core.query.Query;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchService {
    private final BreachDataRepository breachDataRepository;
    private final StealerLogRepository stealerLogRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final MultiIndexSearchService multiIndexSearchService;
    private final RateLimitService rateLimitService;
    private final SearchFallbackService searchFallbackService;
    private final ElasticsearchConfig elasticsearchConfig;
    private final IndexNameProvider indexNameProvider;
    private final UsageService usageService;


    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final int MIN_QUERY_LENGTH = 2;

    /**
     * Main search method - uses ES structure first, then falls back to MongoDB if needed
     */
    public SearchResponse search(SearchRequest request, UserPrincipal user) {
        long startTime = System.currentTimeMillis();

        // Validate query
        if (request.getQuery() == null || request.getQuery().trim().length() < MIN_QUERY_LENGTH) {
            throw new IllegalArgumentException("Search query must be at least " + MIN_QUERY_LENGTH + " characters long");
        }

        // Check rate limits
//        rateLimitService.checkSearchLimit(user.getId());

        try {
            SearchResponse response = null;
            
            // Step 1: First try Elasticsearch
            try {
                Page<BreachDataIndex> esResults = performElasticsearchSearch(request);
                
                if (esResults != null && !esResults.isEmpty()) {
                    // If we have results from Elasticsearch, process and return them
                    response = buildSearchResponse(esResults, request, startTime);
                } else {
                    log.info("No results found in Elasticsearch, falling back to MongoDB");
                }
            } catch (Exception e) {
                log.warn("Elasticsearch search failed, falling back to MongoDB: {}", e.getMessage());
            }

            // Step 2: Fallback to MongoDB search if no ES results
            if (response == null) {
                response = searchFallbackService.searchInMongoDB(request, user);
                response.setExecutionTimeMs(System.currentTimeMillis() - startTime);
            }

            // ✅ **CRITICAL FIX**: Record usage after successful search
            if (response != null && !user.getId().equals("anonymous")) {
                try {
                    usageService.recordUsage(user, UsageService.UsageType.SEARCH);
                    log.info("✅ Recorded search usage for user: {}", user.getId());
                } catch (Exception e) {
                    log.error("❌ Failed to record usage for user {}: {}", user.getId(), e.getMessage(), e);
                    // Don't fail the search if usage recording fails
                }
            }

            return response;

        } catch (Exception e) {
            log.error("Search failed for user {}: {}", user.getId(), e.getMessage(), e);
            throw new RuntimeException("Search failed. Please try again later.", e);
        }
    }
    
    /**
     * Build search response from Elasticsearch results with enhanced metrics
     */
    private SearchResponse buildSearchResponse(Page<BreachDataIndex> esResults, SearchRequest request, long startTime) {
        // Extract MongoDB IDs from Elasticsearch results
        Set<String> mongoIds = esResults.getContent().stream()
                .map(BreachDataIndex::getId)
                .collect(Collectors.toSet());

        // Fetch full documents from MongoDB
        List<StealerLog> fullDocuments = stealerLogRepository.findByIdIn(mongoIds);
        // Convert full documents to search results
        List<SearchResponse.SearchResult> results = fullDocuments.stream()
                .map(this::convertToEnhancedSearchResult) // Pass null or appropriate metrics
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());


        // Build response with comprehensive data
        return SearchResponse.builder()
                .results(results)
                .totalResults(esResults.getTotalElements())
                .currentPage(esResults.getNumber())
                .totalPages(esResults.getTotalPages())
                .pageSize(esResults.getSize())
                .executionTimeMs(System.currentTimeMillis() - startTime)
                .query(request.getQuery())
                .searchType(request.getSearchType())
                .build();
    }

    /**
     * Perform Elasticsearch search based on the request
     */
    private Page<BreachDataIndex> performElasticsearchSearch(SearchRequest request) {
        Sort sort = getSortOrder(request.getSortBy(), request.getSortDirection());
        log.debug("Using sort field: {}", sort);
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                sort
        );


        // Determine search strategy based on request
        return switch (request.getSearchType()) {
            case EMAIL -> {
                request.setSearchMode(SearchRequest.SearchMode.EXACT);
                yield searchByLogin(request, pageable, true);
            }
            case DOMAIN -> searchByDomain(request, pageable);
            case PASSWORD -> searchByPassword(request, pageable);
            case USERNAME -> {
                request.setSearchMode(SearchRequest.SearchMode.EXACT);
                yield searchByLogin(request, pageable, false);
            }
            case URL -> searchByUrl(request, pageable);
            case ADVANCED -> performAdvancedSearch(request, pageable);
            case AUTO -> performAutoDetectSearch(request, pageable);
            default ->
                    throw new IllegalArgumentException("Unsupported search type: " );
        };
    }

    /**
     * Search by login field (handles both email and username)
     * REFACTORED: Using Criteria API instead of QueryBuilders
     */
    private Page<BreachDataIndex> searchByLogin(SearchRequest request, Pageable pageable, boolean emailOnly) {
        // Convert query to lowercase to match Elasticsearch storage
        String query = request.getQuery().toLowerCase().trim();
        log.info("Searching for login (case-insensitive): {}", query);

        if (emailOnly && !EMAIL_PATTERN.matcher(query).matches()) {
            throw new IllegalArgumentException("Invalid email format");
        }

        int monthsBack = getMonthsBackFromFilters(request.getFilters());

        if (request.getSearchMode() == SearchRequest.SearchMode.EXACT) {
            return multiIndexSearchService.searchLoginAcrossIndices(query, pageable, monthsBack);
        } else {
            return searchLoginWithWildcard(query, pageable, monthsBack);
        }
    }

    /**
     * Search by URL
     */
    private Page<BreachDataIndex> searchByUrl(SearchRequest request, Pageable pageable) {
        String query = request.getQuery().trim();
        int monthsBack = getMonthsBackFromFilters(request.getFilters());

        if (request.getSearchMode() == SearchRequest.SearchMode.EXACT) {
            return performExactUrlSearch(query, pageable, monthsBack);
        } else {
            return multiIndexSearchService.searchUrlAcrossIndices(query, pageable, monthsBack);
        }
    }

    /**
     * Search by password
     * REFACTORED: Using Criteria API instead of NativeSearchQueryBuilder
     */
    private Page<BreachDataIndex> searchByPassword(SearchRequest request, Pageable pageable) {
        String password = request.getQuery().trim();
        String[] indices = indexNameProvider.getAllIndicesPattern();

        try {
            Criteria criteria = new Criteria("password").matches(password);
            Query searchQuery = new CriteriaQuery(criteria);
            searchQuery.setPageable(pageable);

            return executeSearch(searchQuery, indices, pageable);
        } catch (Exception e) {
            log.error("Error in password search: {}", e.getMessage(), e);
            return Page.empty();
        }
    }

    /**
     * Search by domain
     */
    private Page<BreachDataIndex> searchByDomain(SearchRequest request, Pageable pageable) {
        String domain = request.getQuery().toLowerCase().trim();
        int monthsBack = getMonthsBackFromFilters(request.getFilters());
        try {
            return multiIndexSearchService.searchUrlAcrossIndices(  domain , pageable, monthsBack);
        } catch (Exception e) {
            log.error("Error in domain search: {}", e.getMessage(), e);
            return Page.empty();
        }
    }

    /**
     * Auto-detect search type based on query pattern
     */
    private Page<BreachDataIndex> performAutoDetectSearch(SearchRequest request, Pageable pageable) {
        String query = request.getQuery().trim();

        if (EMAIL_PATTERN.matcher(query).matches()) {
            request.setSearchType(SearchRequest.SearchType.EMAIL);
            return searchByLogin(request, pageable, true);
        } else if (query.startsWith("http://") || query.startsWith("https://") || query.contains(".")) { // needs  to add other protocols like app://, android:// etc.
            request.setSearchType(SearchRequest.SearchType.URL);
            return searchByUrl(request, pageable);
        } else {
            request.setSearchType(SearchRequest.SearchType.USERNAME);
            return searchByLogin(request, pageable, false);
        }
    }

    /**
     * Advanced search with multiple criteria
     * REFACTORED: Using Criteria API with complex conditions
     */
    private Page<BreachDataIndex> performAdvancedSearch(SearchRequest request, Pageable pageable) {
        String[] indices = indexNameProvider.getAllIndicesPattern();

        // Start with main query criteria
        Criteria mainCriteria = null;

        // Add main query if exists
        if (request.getQuery() != null && !request.getQuery().trim().isEmpty()) {
            String query = request.getQuery().trim();

            // Multi-field search with boosting using OR conditions
            mainCriteria = new Criteria("login").matches(query).boost(2.0f)
                    .or(new Criteria("url").matches(query).boost(1.5f))
                    .or(new Criteria("password").matches(query).boost(1.0f));
        }

        // Add filters
        Criteria filterCriteria = buildFilterCriteria(request.getFilters());

        // Combine main criteria with filters
        Criteria finalCriteria;
        if (mainCriteria != null && filterCriteria != null) {
            finalCriteria = mainCriteria.and(filterCriteria);
        } else if (mainCriteria != null) {
            finalCriteria = mainCriteria;
        } else if (filterCriteria != null) {
            finalCriteria = filterCriteria;
        } else {
            // If no criteria, create a simple match-all equivalent
            finalCriteria = new Criteria("*").exists();
        }

        Query searchQuery = new CriteriaQuery(finalCriteria);
        searchQuery.setPageable(pageable);

        return executeSearch(searchQuery, indices, pageable);
    }

    /**
     * Build filter criteria from request filters
     * REFACTORED: Using Criteria API instead of BoolQueryBuilder
     */
    private Criteria buildFilterCriteria(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return null;
        }

        Criteria criteria = null;

        for (Map.Entry<String, Object> entry : filters.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();

            if (value == null) continue;

            Criteria filterCriteria = switch (key) {
                case "dateFrom" -> new Criteria("timestamp").greaterThanEqual(value);
                case "dateTo" -> new Criteria("timestamp").lessThanEqual(value);
                case "hasPassword" -> Boolean.TRUE.equals(value) ?
                        new Criteria("password").exists() : null;
                case "metadata" -> buildMetadataCriteria(value);
                default -> null;
            };

            if (filterCriteria != null) {
                criteria = criteria == null ? filterCriteria : criteria.and(filterCriteria);
            }
        }

        return criteria;
    }

    /**
     * Build metadata criteria for nested fields
     */
    private Criteria buildMetadataCriteria(Object metadataValue) {
        if (!(metadataValue instanceof Map)) {
            return null;
        }

        Map<?, ?> metadataMap = (Map<?, ?>) metadataValue;
        Criteria criteria = null;

        for (Map.Entry<?, ?> entry : metadataMap.entrySet()) {
            Criteria metaCriteria = new Criteria("metadata." + entry.getKey()).is(entry.getValue());
            criteria = criteria == null ? metaCriteria : criteria.and(metaCriteria);
        }

        return criteria;
    }

    /**
     * Search login with wildcard pattern
     * REFACTORED: Using Criteria API instead of NativeSearchQueryBuilder
     */
    private Page<BreachDataIndex> searchLoginWithWildcard(String pattern, Pageable pageable, int monthsBack) {
        String[] indices = indexNameProvider.generateIndexNames(monthsBack);

        // Using contains for wildcard-like behavior
        Criteria criteria = new Criteria("login").matches(pattern);
        Query searchQuery = new CriteriaQuery(criteria);
        searchQuery.setPageable(pageable);

        return executeSearch(searchQuery, indices, pageable);
    }

    /**
     * Exact URL search
     * REFACTORED: Using Criteria API for exact phrase matching
     */
    private Page<BreachDataIndex> performExactUrlSearch(String url, Pageable pageable, int monthsBack) {
        String[] indices = indexNameProvider.generateIndexNames(monthsBack);

        // Using exact match for URLs
        Criteria criteria = new Criteria("url.keyword").is(url);
        Query searchQuery = new CriteriaQuery(criteria);
        searchQuery.setPageable(pageable);

        return executeSearch(searchQuery, indices, pageable);
    }

    /**
     * Centralized search execution method
     * NEW: DRY principle implementation for consistent search execution
     */
    private Page<BreachDataIndex> executeSearch(Query searchQuery, String[] indices, Pageable pageable) {
        try {
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indices);
            
            // Log the query and sort details
            log.debug("Executing search on indices: {}", (Object) indices);
            log.debug("Sort: {}", pageable.getSort());

            SearchHits<BreachDataIndex> searchHits = elasticsearchOperations.search(
                    searchQuery, BreachDataIndex.class, indexCoordinates);

            List<BreachDataIndex> content = searchHits.stream()
                    .map(SearchHit::getContent)
                    .collect(Collectors.toList());

            log.debug("Search executed successfully, found {} results", content.size());

            return new PageImpl<>(content, pageable, searchHits.getTotalHits());

        } catch (Exception e) {
            log.error("Search execution failed for indices: {}, error: {}",
                    String.join(",", indices), e.getMessage(), e);

            // Return empty page instead of throwing exception
            return new PageImpl<>(List.of(), pageable, 0);
        }
    }

    /**
     * Get months back from filters or return default (12)
     */
    private int getMonthsBackFromFilters(Map<String, Object> filters) {
        if (filters != null && filters.containsKey("monthsBack")) {
            try {
                return Integer.parseInt(filters.get("monthsBack").toString());
            } catch (NumberFormatException e) {
                log.warn("Invalid monthsBack value: {}", filters.get("monthsBack"));
            }
        }
        return 12; // Default to 12 months
    }


    /**
     * Get sort order from parameters
     */
    private Sort getSortOrder(String sortBy, String sortDirection) {
        String field = StringUtils.hasText(sortBy) ? sortBy : "timestamp";
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection) ?
                Sort.Direction.ASC : Sort.Direction.DESC;
        return Sort.by(direction, field);
    }

    /**
     * Convert StealerLog to enhanced SearchResult DTO
     */
    private Optional<SearchResponse.SearchResult> convertToEnhancedSearchResult(StealerLog log) {
        return SearchResponse.SearchResult.fromStealerLog(log);
    }

    /**
     * Convert StealerLog to BreachDataIndex for Elasticsearch
     */

    // Helper method to extract domain from URL
    private String extractDomain(String url) {
        if (url == null || url.isEmpty()) {
            return "";
        }
        try {
            URI uri = new URI(url);
            String domain = uri.getHost();
            return domain != null ? domain : "";
        } catch (Exception e) {
            log.warn("Failed to extract domain from URL: {}", url, e);
            return "";
        }
    }

    /**
     * Save search history
     */
//    private void saveSearchHistory(SearchRequest request, UserPrincipal user, SearchResponse response) {
//        try {
//            SearchHistory history = new SearchHistory();
////            history.setUser(userService.findById(user.getId()));
//            history.setSearchType(SearchHistory.SearchType.valueOf(request.getSearchType().name()));
//            history.setQuery(request.getQuery());
//            history.setFilters(request.getFilters() != null ?
//                    new ObjectMapper().writeValueAsString(request.getFilters()) : null);
////            history.setResultsCount(response.getTotalResults());
//            history.setExecutionTimeMs(response.getExecutionTimeMs());
//            history.setCreatedAt(LocalDateTime.now());
//
////            searchHistoryRepository.save(history);
//        } catch (Exception e) {
//            log.warn("Failed to save search history for user {}: {}", user.getId(), e.getMessage());
//        }
//    }

    /**
     * NEW: Count search results without retrieving documents
     * Useful for pagination and performance optimization
     */
    public long countSearchResults(SearchRequest request, UserPrincipal user) {
        try {
            // Build the same criteria as the search
            Criteria criteria = buildSearchCriteria(request);
            if (criteria == null) {
                return 0;
            }

            Query countQuery = new CriteriaQuery(criteria);
            String[] indices = indexNameProvider.getAllIndicesPattern();
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indices);

            return elasticsearchOperations.count(countQuery, BreachDataIndex.class, indexCoordinates);

        } catch (Exception e) {
            log.error("Count search failed for user {}: {}", user.getId(), e.getMessage(), e);
            return 0;
        }
    }

    /**
     * NEW: Build search criteria based on request type
     * Helper method for code reuse between search and count operations
     */
    private Criteria buildSearchCriteria(SearchRequest request) {
        String query = request.getQuery();
        if (query == null || query.trim().isEmpty()) {
            // For empty queries, search everything using exists on a common field
            return new Criteria("login").exists();
        }

        query = query.trim();

        return switch (request.getSearchType()) {
            case EMAIL -> new Criteria("login.keyword").is(query.toLowerCase());
            case USERNAME -> new Criteria("login").matches(query.toLowerCase());
            case PASSWORD -> new Criteria("password").matches(query);
            case URL -> new Criteria("url").contains(query);
            case DOMAIN -> new Criteria("url").contains(query.toLowerCase());
            case ADVANCED -> {
                Criteria mainCriteria = new Criteria("login").matches(query).boost(2.0f)
                        .or(new Criteria("url").matches(query).boost(1.5f))
                        .or(new Criteria("password").matches(query).boost(1.0f));

                Criteria filterCriteria = buildFilterCriteria(request.getFilters());
                yield filterCriteria != null ? mainCriteria.and(filterCriteria) : mainCriteria;
            }
            default -> new Criteria("login").matches(query)
                    .or(new Criteria("url").matches(query))
                    .or(new Criteria("password").matches(query));
        };
    }

    /**
     * NEW: Suggest search terms for autocomplete
     * Performance optimized suggestion system
     */
    public List<String> getSearchSuggestions(String prefix, SearchRequest.SearchType searchType, int limit) {
        try {
            if (prefix == null || prefix.trim().length() < 2) {
                return List.of();
            }

            String field = switch (searchType) {
                case EMAIL, USERNAME -> "login";
                case URL, DOMAIN -> "url";
                default -> "login";
            };

            Criteria criteria = new Criteria(field).startsWith(prefix.trim().toLowerCase());
            Query suggestionQuery = new CriteriaQuery(criteria);

            // Use pagination instead of setMaxResults
            Pageable pageable = PageRequest.of(0, limit);
            suggestionQuery.setPageable(pageable);

            String[] indices = indexNameProvider.getAllIndicesPattern();
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indices);

            SearchHits<BreachDataIndex> searchHits = elasticsearchOperations.search(
                    suggestionQuery, BreachDataIndex.class, indexCoordinates);

            return searchHits.stream()
                    .map(hit -> {
                        BreachDataIndex content = hit.getContent();
                        return switch (searchType) {
                            case EMAIL, USERNAME -> content.getLogin();
                            case URL, DOMAIN -> content.getUrl();
                            default -> content.getLogin();
                        };
                    })
                    .filter(Objects::nonNull)
                    .distinct()
                    .limit(limit)
                    .collect(Collectors.toList());

        } catch (Exception e) {
            log.error("Suggestion search failed: {}", e.getMessage(), e);
            return List.of();
        }
    }
}