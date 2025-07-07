package com.threatscopebackend.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResponse {
    
    private List<SearchResult> results;
    
    private long totalResults;
    
    private int currentPage;
    
    private int totalPages;
    
    private int pageSize;
    
    private long executionTimeMs;
    
    private String query;
    
    private SearchRequest.SearchType searchType;
    
    private Map<String, Object> aggregations;
    
    private List<String> suggestions;
    
    private SearchMetadata metadata;
    
    @Data
    @Builder
    public static class SearchMetadata {
        private Map<String, Long> sourceBreakdown;
        private Map<String, Long> countryBreakdown;
        private Map<String, Long> severityBreakdown;
        private Map<String, Long> dateBreakdown;
        private long verifiedCount;
        private long unverifiedCount;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchResult {
        private String id;
        private String email;
        private String domain;
        private String url;
        private String source;
        private String malwareFamily;
        private LocalDateTime dateDiscovered;
        private LocalDateTime dateCompromised;
        private String ipAddress;
        private String country;
        private String city;
        private String severity;
        private Boolean verified;
        private List<String> tags;
        private boolean hasPassword;
        private boolean hasCookies;
        private boolean hasFormData;
        private Integer riskScore;
        private Map<String, List<String>> highlights;
        private Map<String, Object> additionalData;
    }
}
