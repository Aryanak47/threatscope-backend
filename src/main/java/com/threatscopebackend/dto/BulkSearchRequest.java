package com.threatscope.dto;

import lombok.Data;
import lombok.Builder;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class BulkSearchRequest {
    
    @NotEmpty(message = "Queries list cannot be empty")
    private List<String> queries;
    
    private SearchRequest.SearchType searchType = SearchRequest.SearchType.AUTO;
    
    private Map<String, Object> commonFilters;
    
    private int maxResultsPerQuery = 100;
    
    private boolean includeEmpty = false;
}
