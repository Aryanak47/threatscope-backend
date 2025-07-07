package com.threatscopebackend.service;

import com.stripe.service.SubscriptionService;
import com.threatscope.document.StealerLog;

import com.threatscope.elasticsearch.BreachDataIndex;
import com.threatscope.entity.SearchHistory;
import com.threatscope.exception.SearchException;


import com.fasterxml.jackson.databind.ObjectMapper;


import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.repository.elasticsearch.BreachDataRepository;
import com.threatscopebackend.repository.mongo.StealerLogRepository;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.data.IndexNameProvider;
import com.threatscopebackend.service.search.MultiIndexSearchService;
import com.threatscopebackend.service.security.RateLimitService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.*;
import org.springframework.data.elasticsearch.core.*;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.*;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SearchService {
    private final BreachDataRepository breachDataRepository;
    private final StealerLogRepository stealerLogRepository;
//    private final SearchHistoryRepository searchHistoryRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final MultiIndexSearchService multiIndexSearchService;
    private final RateLimitService rateLimitService;
    private final IndexNameProvider indexNameProvider;
//    private final UserService userService;
//    private final SubscriptionService subscriptionService;
    private final ObjectMapper objectMapper;

    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );

    /**
     * Main search method - uses ES structure first, then fetches from MongoDB
     */
    public SearchResponse search(SearchRequest request, UserPrincipal user) {
        long startTime = System.currentTimeMillis();

        // Check rate limits
        rateLimitService.checkSearchLimit(user.getId());

        try {
            // Step 1: Search in Elasticsearch
            Page<BreachDataIndex> esResults = performElasticsearchSearch(request);

            // Step 2: Extract MongoDB IDs from Elasticsearch results
            Set<String> mongoIds = esResults.getContent().stream()
                    .map(BreachDataIndex::getId)
                    .collect(Collectors.toSet());

            // Step 3: Fetch full documents from MongoDB
            List<StealerLog> fullDocuments = stealerLogRepository.findByIdIn(mongoIds);

            // Step 4: Convert to response format
            List<SearchResponse.SearchResult> results = fullDocuments.stream()
                    .map(this::convertToSearchResult)
                    .collect(Collectors.toList());

            // Step 5: Build response
            SearchResponse response = SearchResponse.builder()
                    .results(results)
                    .totalResults(esResults.getTotalElements())
                    .currentPage(esResults.getNumber())
                    .totalPages(esResults.getTotalPages())
                    .pageSize(esResults.getSize())
                    .executionTimeMs(System.currentTimeMillis() - startTime)
                    .query(request.getQuery())
                    .searchType(request.getSearchType())
                    .build();

            // Step 6: Save search history
//            saveSearchHistory(request, user, response);

            return response;

        } catch (Exception e) {
            log.error("Search failed for user {}: {}", user.getId(), e.getMessage(), e);
            throw new SearchException("Search failed: " + e.getMessage(), e);
        }
    }

    /**
     * Perform Elasticsearch search based on the request
     */
    private Page<BreachDataIndex> performElasticsearchSearch(SearchRequest request) {
        Pageable pageable = PageRequest.of(
                request.getPage(),
                request.getSize(),
                getSortOrder(request.getSortBy(), request.getSortDirection())
        );

        // Determine search strategy based on request
        return switch (request.getSearchType()) {
            case EMAIL -> searchByLogin(request, pageable, true);
            case DOMAIN -> searchByDomain(request, pageable);
            case PASSWORD -> searchByPassword(request, pageable);
            case USERNAME -> searchByLogin(request, pageable, false);
            case URL -> searchByUrl(request, pageable);
            case ADVANCED -> performAdvancedSearch(request, pageable);
            default -> performAutoDetectSearch(request, pageable);
        };
    }

    /**
     * Search by login field (handles both email and username)
     * REFACTORED: Using Criteria API instead of QueryBuilders
     */
    private Page<BreachDataIndex> searchByLogin(SearchRequest request, Pageable pageable, boolean emailOnly) {
        String query = request.getQuery().toLowerCase().trim();

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

        Criteria criteria = new Criteria("password").matches(password);
        Query searchQuery = new CriteriaQuery(criteria);
        searchQuery.setPageable(pageable);

        return executeSearch(searchQuery, indices, pageable);
    }

    /**
     * Search by domain
     */
    private Page<BreachDataIndex> searchByDomain(SearchRequest request, Pageable pageable) {
        String domain = request.getQuery().toLowerCase().trim();
        int monthsBack = getMonthsBackFromFilters(request.getFilters());
        return multiIndexSearchService.searchUrlAcrossIndices("*" + domain + "*", pageable, monthsBack);
    }

    /**
     * Auto-detect search type based on query pattern
     */
    private Page<BreachDataIndex> performAutoDetectSearch(SearchRequest request, Pageable pageable) {
        String query = request.getQuery().trim();

        if (EMAIL_PATTERN.matcher(query).matches()) {
            request.setSearchType(SearchRequest.SearchType.EMAIL);
            return searchByLogin(request, pageable, true);
        } else if (query.startsWith("http://") || query.startsWith("https://") || query.contains(".")) {
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
        String[] indices = generateIndexNames(monthsBack);

        // Using contains for wildcard-like behavior
        Criteria criteria = new Criteria("login").contains(pattern);
        Query searchQuery = new CriteriaQuery(criteria);
        searchQuery.setPageable(pageable);

        return executeSearch(searchQuery, indices, pageable);
    }

    /**
     * Exact URL search
     * REFACTORED: Using Criteria API for exact phrase matching
     */
    private Page<BreachDataIndex> performExactUrlSearch(String url, Pageable pageable, int monthsBack) {
        String[] indices = generateIndexNames(monthsBack);

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
     * Generate index names for the last N months
     */
    private String[] generateIndexNames(int monthsBack) {
        List<String> indices = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        for (int i = 0; i < monthsBack; i++) {
            String month = now.minusMonths(i).format(formatter);
            indices.add("breaches-" + month);
        }

        return indices.toArray(new String[0]);
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
     * Convert StealerLog to SearchResult DTO
     */
    private SearchResponse.SearchResult convertToSearchResult(StealerLog log) {
        return SearchResponse.SearchResult.builder()
                .id(log.getId())
                .email(log.getLogin())
                .url(log.getUrl())
                .domain(log.getDomain())
                .hasPassword(log.getPassword() != null && !log.getPassword().isEmpty())
                .build();
    }

    /**
     * Save search history
     */
    private void saveSearchHistory(SearchRequest request, UserPrincipal user, SearchResponse response) {
        try {
            SearchHistory history = new SearchHistory();
//            history.setUser(userService.findById(user.getId()));
            history.setSearchType(SearchHistory.SearchType.valueOf(request.getSearchType().name()));
            history.setQuery(request.getQuery());
            history.setFilters(request.getFilters() != null ?
                    objectMapper.writeValueAsString(request.getFilters()) : null);
            history.setResultsCount(response.getTotalResults());
            history.setExecutionTimeMs(response.getExecutionTimeMs());
            history.setCreatedAt(LocalDateTime.now());

//            searchHistoryRepository.save(history);
        } catch (Exception e) {
            log.warn("Failed to save search history for user {}: {}", user.getId(), e.getMessage());
        }
    }

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