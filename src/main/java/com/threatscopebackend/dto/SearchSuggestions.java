package com.threatscopebackend.dto;

import lombok.Data;
import lombok.Builder;

import java.util.List;

@Data
@Builder
public class SearchSuggestions {
    
    private List<String> emailSuggestions;
    
    private List<String> domainSuggestions;
    
    private List<String> sourceSuggestions;
    
    private List<String> tagSuggestions;
    
    private List<RecentSearch> recentSearches;
    
    @Data
    @Builder
    public static class RecentSearch {
        private String query;
        private SearchRequest.SearchType type;
        private long resultCount;
        private java.time.LocalDateTime searchedAt;
    }
}
