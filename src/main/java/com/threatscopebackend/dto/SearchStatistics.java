package com.threatscope.dto;

import lombok.Data;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SearchStatistics {
    
    private long totalSearches;
    
    private long searchesToday;
    
    private long searchesThisMonth;
    
    private List<TopSearchQuery> topQueries;
    
    private Map<String, Long> searchTypeBreakdown;
    
    private double averageExecutionTime;
    
    private LocalDateTime lastSearchTime;
    
    private long remainingSearches; // Based on subscription
    
    @Data
    @Builder
    public static class TopSearchQuery {
        private String query;
        private long count;
        private LocalDateTime lastSearched;
    }
}
