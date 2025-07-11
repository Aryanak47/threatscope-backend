package com.threatscopebackend.dto;

import lombok.Data;
import lombok.Builder;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class AdvancedSearchRequest {
    
    private List<SearchCriteria> criteria;
    
    private String operator = "AND"; // AND, OR
    
    private Map<String, Object> globalFilters;
    
    private int page = 0;
    
    private int size = 20;
    
    private String sortBy = "timestamp";
    
    private String sortDirection = "desc";
    
    @Data
    @Builder
    public static class SearchCriteria {
        private String field;
        private String operator; // equals, contains, startsWith, endsWith, range
        private Object value;
        private String logicalOperator = "AND"; // AND, OR, NOT
    }
}
