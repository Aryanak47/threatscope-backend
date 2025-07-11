//package com.threatscopebackend.service;
//
//import com.threatscopebackend.dto.ExternalBreachData;
//import com.threatscopebackend.dto.SearchRequest;
//import com.threatscopebackend.dto.SearchResponse;
//import com.threatscopebackend.exception.SearchException;
//import com.threatscopebackend.repository.elasticsearch.BreachDataRepository;
//import com.threatscopebackend.repository.mongo.StealerLogRepository;
//import com.threatscopebackend.security.UserPrincipal;
//import com.threatscopebackend.service.data.IndexNameProvider;
//import com.threatscopebackend.service.external.ExternalBreachService;
//import com.threatscopebackend.service.security.RateLimitService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
//import org.springframework.data.elasticsearch.core.SearchHits;
//import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
//import org.springframework.data.elasticsearch.core.query.Criteria;
//import org.springframework.data.elasticsearch.core.query.CriteriaQuery;
//import org.springframework.data.elasticsearch.core.query.Query;
//import org.springframework.scheduling.annotation.Async;
//import org.springframework.stereotype.Service;
//import org.springframework.util.StringUtils;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.concurrent.CompletableFuture;
//import java.util.regex.Pattern;
//import java.util.stream.Collectors;
//
///**
// * Enhanced search service implementation that integrates multiple data sources
// */
//@Slf4j
///**
// * Implementation of SearchServiceInterface that provides enhanced search capabilities
// * across multiple data sources including Elasticsearch, MongoDB, and external APIs.
// */
//@Service
//@RequiredArgsConstructor
//public class EnhancedSearchServiceImpl implements SearchServiceInterface {
//
//    private final BreachDataRepository breachDataRepository;
//    private final StealerLogRepository stealerLogRepository;
//    private final ElasticsearchOperations elasticsearchOperations;
//    private final RateLimitService rateLimitService;
//    private final IndexNameProvider indexNameProvider;
////    private final ExternalBreachService externalBreachService;
//
//    private static final Pattern EMAIL_PATTERN = Pattern.compile(
//        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
//    );
//
//    @Override
//    @Async
//    public CompletableFuture<SearchResponse> search(SearchRequest request, UserPrincipal user) {
//        long startTime = System.currentTimeMillis();
//
//        try {
//            // Check rate limits for authenticated users
//            if (user != null) {
//                rateLimitService.checkSearchLimit(user.getId());
//            }
//
//            // 1. Search in Elasticsearch
//            SearchHits<Map> esHits = searchInElasticsearch(request);
//
//            // 2. Enrich with MongoDB data
//            List<SearchResponse.SearchResult> localResults = enrichWithMongoData(esHits);
//
//            // 3. Fetch from external breach API if it's an email search
//            List<ExternalBreachData> externalResults = Collections.emptyList();
//            if (isEmailSearch(request)) {
////                externalResults = externalBreachService.searchBreachData(request.getQuery());
//            }
//
//            // 4. Merge and format results
//            SearchResponse response = mergeResults(
//                localResults,
//                externalResults,
//                request,
//                System.currentTimeMillis() - startTime
//            );
//
//            // 5. Save search history for authenticated users
////            if (user != null) {
////                saveSearchHistory(request, response, user);
////            }
//
//            return CompletableFuture.completedFuture(response);
//
//        } catch (Exception e) {
//            log.error("Search failed: {}", e.getMessage(), e);
//            throw new SearchException("Search failed: " + e.getMessage(), e);
//        }
//    }
//
//    private boolean isEmailSearch(SearchRequest request) {
//        return request.getQuery() != null &&
//               EMAIL_PATTERN.matcher(request.getQuery().trim()).matches();
//    }
//
//    private SearchHits<Map> searchInElasticsearch(SearchRequest request) {
//        try {
//            // Create pageable for pagination with sort order
//            Pageable pageable = PageRequest.of(
//                request.getPage(),
//                request.getSize(),
//                getSortOrder(request.getSortBy(), request.getSortDirection())
//            );
//
//            // Determine search strategy based on request
//            switch (request.getSearchType()) {
//                case EMAIL:
//                    return searchByLogin(request, pageable, true);
//                case DOMAIN:
//                    return searchByDomain(request, pageable);
//                case PASSWORD:
//                    return searchByPassword(request, pageable);
//                case USERNAME:
//                    return searchByLogin(request, pageable, false);
//                case URL:
//                    return searchByUrl(request, pageable);
//                case ADVANCED:
//                    return performAdvancedSearch(request, pageable);
//                default:
//                    return performAutoDetectSearch(request, pageable);
//            }
//
//        } catch (Exception e) {
//            log.error("Elasticsearch search failed: {}", e.getMessage(), e);
//            throw new SearchException("Search failed in Elasticsearch", e);
//        }
//    }
//
//    private SearchHits<Map> searchByLogin(SearchRequest request, Pageable pageable, boolean emailOnly) {
//        String query = request.getQuery().toLowerCase().trim();
//
//        if (emailOnly && !EMAIL_PATTERN.matcher(query).matches()) {
//            throw new IllegalArgumentException("Invalid email format");
//        }
//
//        int monthsBack = getMonthsBackFromFilters(request.getFilters());
//        String[] indices = generateIndexNames(monthsBack);
//
//        // Using contains for wildcard-like behavior
//        Criteria criteria = new Criteria("login").contains(query);
//        Query searchQuery = new CriteriaQuery(criteria);
//        searchQuery.setPageable(pageable);
//
//        return elasticsearchOperations.search(searchQuery, Map.class, IndexCoordinates.of(indices));
//    }
//
//    private SearchHits<Map> searchByDomain(SearchRequest request, Pageable pageable) {
//        String domain = request.getQuery().toLowerCase().trim();
//        int monthsBack = getMonthsBackFromFilters(request.getFilters());
//        String[] indices = generateIndexNames(monthsBack);
//
//        // Using wildcard for domain search
//        Criteria criteria = new Criteria("domain").contains(domain);
//        Query searchQuery = new CriteriaQuery(criteria);
//        searchQuery.setPageable(pageable);
//
//        return elasticsearchOperations.search(searchQuery, Map.class, IndexCoordinates.of(indices));
//    }
//
//    private SearchHits<Map> searchByPassword(SearchRequest request, Pageable pageable) {
//        String password = request.getQuery().trim();
//        int monthsBack = getMonthsBackFromFilters(request.getFilters());
//        String[] indices = generateIndexNames(monthsBack);
//
//        // Exact match for password
//        Criteria criteria = new Criteria("password").is(password);
//        Query searchQuery = new CriteriaQuery(criteria);
//        searchQuery.setPageable(pageable);
//
//        return elasticsearchOperations.search(searchQuery, Map.class, IndexCoordinates.of(indices));
//    }
//
//    private SearchHits<Map> searchByUrl(SearchRequest request, Pageable pageable) {
//        String url = request.getQuery().trim();
//        int monthsBack = getMonthsBackFromFilters(request.getFilters());
//        String[] indices = generateIndexNames(monthsBack);
//
//        // Using contains for URL search
//        Criteria criteria = new Criteria("url").contains(url);
//        Query searchQuery = new CriteriaQuery(criteria);
//        searchQuery.setPageable(pageable);
//
//        return elasticsearchOperations.search(searchQuery, Map.class, IndexCoordinates.of(indices));
//    }
//
//    private SearchHits<Map> performAdvancedSearch(SearchRequest request, Pageable pageable) {
//        int monthsBack = getMonthsBackFromFilters(request.getFilters());
//        String[] indices = generateIndexNames(monthsBack);
//
//        Criteria criteria = buildFilterCriteria(request.getFilters());
//        if (criteria == null) {
//            criteria = new Criteria("*").exists();
//        }
//
//        Query searchQuery = new CriteriaQuery(criteria);
//        searchQuery.setPageable(pageable);
//
//        return elasticsearchOperations.search(searchQuery, Map.class, IndexCoordinates.of(indices));
//    }
//
//    private SearchHits<Map> performAutoDetectSearch(SearchRequest request, Pageable pageable) {
//        String query = request.getQuery().trim();
//
//        if (EMAIL_PATTERN.matcher(query).matches()) {
//            request.setSearchType(SearchRequest.SearchType.EMAIL);
//            return searchByLogin(request, pageable, true);
//        } else if (query.startsWith("http://") || query.startsWith("https://") || query.contains(".")) {
//            request.setSearchType(SearchRequest.SearchType.URL);
//            return searchByUrl(request, pageable);
//        } else {
//            request.setSearchType(SearchRequest.SearchType.USERNAME);
//            return searchByLogin(request, pageable, false);
//        }
//    }
//
//    private Criteria buildFilterCriteria(Map<String, Object> filters) {
//        if (filters == null || filters.isEmpty()) {
//            return null;
//        }
//
//        Criteria criteria = null;
//
//        for (Map.Entry<String, Object> entry : filters.entrySet()) {
//            String key = entry.getKey();
//            Object value = entry.getValue();
//
//            if (value == null) continue;
//
//            Criteria filterCriteria = switch (key) {
//                case "dateFrom" -> new Criteria("timestamp").greaterThanEqual(value);
//                case "dateTo" -> new Criteria("timestamp").lessThanEqual(value);
//                case "hasPassword" -> Boolean.TRUE.equals(value) ?
//                        new Criteria("password").exists() : null;
//                case "metadata" -> buildMetadataCriteria(value);
//                default -> null;
//            };
//
//            if (filterCriteria != null) {
//                criteria = criteria == null ? filterCriteria : criteria.and(filterCriteria);
//            }
//        }
//
//        return criteria;
//    }
//
//    private Criteria buildMetadataCriteria(Object metadataValue) {
//        if (!(metadataValue instanceof Map)) {
//            return null;
//        }
//
//        Map<?, ?> metadataMap = (Map<?, ?>) metadataValue;
//        Criteria criteria = null;
//
//        for (Map.Entry<?, ?> entry : metadataMap.entrySet()) {
//            Criteria metaCriteria = new Criteria("metadata." + entry.getKey()).is(entry.getValue());
//            criteria = criteria == null ? metaCriteria : criteria.and(metaCriteria);
//        }
//
//        return criteria;
//    }
//
//    private List<SearchResponse.SearchResult> enrichWithMongoData(SearchHits<Map> esHits) {
//        if (esHits == null || esHits.isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        // Get all MongoDB document IDs from Elasticsearch results
//        Set<String> mongoIds = esHits.getSearchHits().stream()
//            .map(hit -> (String) hit.getContent().get("id"))
//            .filter(Objects::nonNull)
//            .collect(Collectors.toSet());
//
//        // Fetch full documents from MongoDB
//        Map<String, Object> mongoDocs = stealerLogRepository.findByIdIn(mongoIds).stream()
//            .collect(Collectors.toMap(
//                doc -> doc.getId().toString(),
//                doc -> doc,
//                (existing, replacement) -> existing
//            ));
//
//        // Merge Elasticsearch and MongoDB data
//        return esHits.getSearchHits().stream()
//            .map(hit -> convertToSearchResult(hit.getContent(), mongoDocs.get(hit.getContent().get("id"))))
//            .filter(Objects::nonNull)
//            .collect(Collectors.toList());
//    }
//
//    private SearchResponse.SearchResult convertToSearchResult(Map<String, Object> esData, Object mongoData) {
//        SearchResponse.SearchResult result = new SearchResponse.SearchResult();
//
//        try {
//            // Map common fields from Elasticsearch
//            if (esData != null) {
//                result.setId(String.valueOf(esData.getOrDefault("id", "")));
//                result.setEmail((String) esData.get("email"));
//                result.setUrl((String) esData.get("url"));
//                result.setSource((String) esData.get("source"));
//                result.setDomain((String) esData.get("domain"));
////                result.setMalwareFamily((String) esData.get("malwareFamily"));
////                result.setIpAddress((String) esData.get("ipAddress"));
////                result.setCountry((String) esData.get("country"));
////                result.setCity((String) esData.get("city"));
//                result.setSeverity((String) esData.get("severity"));
//
//                // Map dates
//                if (esData.get("timestamp") != null) {
//                    result.setTimestamp(parseDate(esData.get("timestamp").toString()));
//                } else if (esData.get("dateCompromised") != null) {
//                    LocalDateTime dateCompromised = parseDate(esData.get("dateCompromised").toString());
//                    result.setDateCompromised(dateCompromised);
//                    result.setTimestamp(dateCompromised); // Fallback to dateCompromised if timestamp not available
//                }
//
//                // Map boolean flags
////                result.setVerified(Boolean.TRUE.equals(esData.get("verified")));
//                result.setHasPassword(Boolean.TRUE.equals(esData.get("hasPassword")));
////                result.setHasCookies(Boolean.TRUE.equals(esData.get("hasCookies")));
////                result.setHasFormData(Boolean.TRUE.equals(esData.get("hasFormData")));
//
//                // Map numeric fields
//                if (esData.get("riskScore") != null) {
////                    result.setRiskScore(Integer.parseInt(esData.get("riskScore").toString()));
//                }
//            }
//
//            // Enrich with MongoDB data if available
//            if (mongoData != null && mongoData instanceof Map) {
//                Map<String, Object> mongoMap = (Map<String, Object>) mongoData;
//                Map<String, Object> additionalData = new HashMap<>();
//
//                // Add all MongoDB fields to additionalData
//                mongoMap.forEach((key, value) -> {
//                    if (value != null && !key.startsWith("_")) {
//                        additionalData.put(key, value);
//                    }
//                });
//
//                if (!additionalData.isEmpty()) {
//                    result.setAdditionalData(additionalData);
//                }
//            }
//
//            return result;
//
//        } catch (Exception e) {
//            log.error("Error converting search result: {}", e.getMessage(), e);
//            return null;
//        }
//    }
//
//    private LocalDateTime parseDate(String dateStr) {
//        if (dateStr == null || dateStr.isEmpty()) {
//            return null;
//        }
//
//        try {
//            // Try ISO-8601 format first
//            if (dateStr.contains("T")) {
//                return LocalDateTime.parse(dateStr.replace("Z", ""));
//            }
//            // Try simple date format
//            return LocalDateTime.parse(dateStr + "T00:00:00");
//        } catch (Exception e) {
//            log.warn("Failed to parse date: {}", dateStr, e);
//            return null;
//        }
//    }
//
//    // Helper methods for search functionality
//    private int getMonthsBackFromFilters(Map<String, Object> filters) {
//        if (filters != null && filters.containsKey("monthsBack")) {
//            try {
//                return Integer.parseInt(filters.get("monthsBack").toString());
//            } catch (NumberFormatException e) {
//                log.warn("Invalid monthsBack value: {}", filters.get("monthsBack"));
//            }
//        }
//        return 12; // Default to 12 months
//    }
//
//    private String[] generateIndexNames(int monthsBack) {
//        List<String> indices = new ArrayList<>();
//        LocalDateTime now = LocalDateTime.now();
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");
//
//        for (int i = 0; i < monthsBack; i++) {
//            String month = now.minusMonths(i).format(formatter);
//            indices.add("breaches-" + month);
//        }
//
//        return indices.toArray(new String[0]);
//    }
//
//    private Sort getSortOrder(String sortBy, String sortDirection) {
//        String field = StringUtils.hasText(sortBy) ? sortBy : "timestamp";
//        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
//            ? Sort.Direction.ASC
//            : Sort.Direction.DESC;
//        return Sort.by(direction, field);
//    }
//
//    private SearchResponse mergeResults(
//        List<SearchResponse.SearchResult> localResults,
//        List<ExternalBreachData> externalResults,
//        SearchRequest request,
//        long executionTimeMs
//    ) {
//        // Map external results to our response format
//        List<SearchResponse.SearchResult> externalMapped = externalResults.stream()
//            .map(this::mapExternalToSearchResult)
//            .collect(Collectors.toList());
//
//        // Combine and deduplicate results
//        List<SearchResponse.SearchResult> allResults = new ArrayList<>();
//        Set<String> seenEmails = new HashSet<>();
//
//        // Add local results first
//        for (SearchResponse.SearchResult result : localResults) {
//            if (result.getEmail() != null && !seenEmails.contains(result.getEmail())) {
//                allResults.add(result);
//                seenEmails.add(result.getEmail());
//            }
//        }
//
//        // Add external results (skip if we already have a local result with the same email)
//        for (SearchResponse.SearchResult result : externalMapped) {
//            if (result.getEmail() != null && !seenEmails.contains(result.getEmail())) {
//                allResults.add(result);
//                seenEmails.add(result.getEmail());
//            }
//        }
//
//        // Sort results by compromise date (newest first)
//        allResults.sort((a, b) -> {
//            LocalDateTime dateA = a.getDateCompromised() != null ? a.getDateCompromised() : LocalDateTime.MIN;
//            LocalDateTime dateB = b.getDateCompromised() != null ? b.getDateCompromised() : LocalDateTime.MIN;
//            return dateB.compareTo(dateA); // Descending order
//        });
//
//        // Apply pagination
//        int page = request.getPage();
//        int size = request.getSize();
//        int totalResults = allResults.size();
//        int totalPages = (int) Math.ceil((double) totalResults / size);
//
//        // Get paginated results
//        int fromIndex = page * size;
//        int toIndex = Math.min(fromIndex + size, allResults.size());
//
//        List<SearchResponse.SearchResult> paginatedResults = allResults.subList(
//            Math.min(fromIndex, allResults.size()),
//            Math.min(toIndex, allResults.size())
//        );
//
//        // Build and return response
//        return SearchResponse.builder()
//            .results(paginatedResults)
//            .totalResults(totalResults)
//            .currentPage(page)
//            .pageSize(size)
//            .totalPages(totalPages)
//            .executionTimeMs(executionTimeMs)
//            .query(request.getQuery())
//            .searchType(request.getSearchType())
//            .build();
//    }
//
//    private SearchResponse.SearchResult mapExternalToSearchResult(ExternalBreachData externalData) {
//        SearchResponse.SearchResult result = new SearchResponse.SearchResult();
//
//        result.setId("ext-" + externalData.getId());
//        result.setEmail(externalData.getEmail());
//        result.setDomain(externalData.getDomain());
//        result.setSource(externalData.getSource());
//        result.setDateCompromised(externalData.getBreachDate());
////        result.setVerified(externalData.isVerified());
//
//        // Set additional data
//        Map<String, Object> additionalData = new HashMap<>();
//        additionalData.put("title", externalData.getTitle());
//        additionalData.put("description", externalData.getDescription());
//        additionalData.put("dataClasses", externalData.getDataClasses());
//        additionalData.put("isFabricated", externalData.isFabricated());
//        additionalData.put("isSensitive", externalData.isSensitive());
//        additionalData.put("isRetired", externalData.isRetired());
//        additionalData.put("logoPath", externalData.getLogoPath());
//
//        // Add all additional info
//        if (externalData.getAdditionalInfo() != null) {
//            additionalData.putAll(externalData.getAdditionalInfo());
//        }
//
//        result.setAdditionalData(additionalData);
//
//        return result;
//    }
//
//    @Override
//    public long countSearchResults(SearchRequest request, UserPrincipal user) {
//        try {
//            // Create a basic query with search text if provided
//            Criteria criteria = StringUtils.hasText(request.getQuery())
//                ? new Criteria().expression("*" + request.getQuery() + "*")
//                : new Criteria("*").exists();
//
//            // Apply search type specific criteria
//            if (request.getSearchType() != null) {
//                switch (request.getSearchType()) {
//                    case EMAIL:
//                        criteria = criteria.and("email").contains(request.getQuery());
//                        break;
//                    case DOMAIN:
//                        criteria = criteria.and("domain").contains(request.getQuery());
//                        break;
//                    case PASSWORD:
//                        criteria = criteria.and("password").is(request.getQuery());
//                        break;
//                    case URL:
//                        criteria = criteria.and("url").contains(request.getQuery());
//                        break;
//                    // Other search types can be added as needed
//                }
//            }
//
//            // Apply additional filters if any
//            if (request.getFilters() != null && !request.getFilters().isEmpty()) {
//                Criteria filterCriteria = buildFilterCriteria(request.getFilters());
//                if (filterCriteria != null) {
//                    criteria = criteria.and(filterCriteria);
//                }
//            }
//
//            // Execute count query
//            Query countQuery = new CriteriaQuery(criteria);
//            int monthsBack = getMonthsBackFromFilters(request.getFilters());
//            String[] indices = generateIndexNames(monthsBack);
//
//            return elasticsearchOperations.count(countQuery, Map.class, IndexCoordinates.of(indices));
//
//        } catch (Exception e) {
//            log.error("Failed to count search results: {}", e.getMessage(), e);
//            throw new SearchException("Failed to count search results", e);
//        }
//    }
//
////    private void saveSearchHistory(SearchRequest request, SearchResponse response, UserPrincipal user) {
////        try {
////            if (user == null) {
////                return; // Don't save history for anonymous users
////            }
////
////            SearchHistory history = new SearchHistory();
////            history.setUserId(user.getId());
////            history.setQuery(request.getQuery());
////            history.setSearchType(request.getSearchType());
////            history.setResultCount(response.getTotalResults());
////            history.setExecutionTimeMs(response.getExecutionTimeMs());
////            history.setCreatedAt(LocalDateTime.now());
////
////            // Uncomment when SearchHistoryRepository is available
////            // searchHistoryRepository.save(history);
////
////        } catch (Exception e) {
////            log.warn("Failed to save search history for user {}: {}",
////                user != null ? user.getId() : "anonymous", e.getMessage());
////        }
//    }
//
//
