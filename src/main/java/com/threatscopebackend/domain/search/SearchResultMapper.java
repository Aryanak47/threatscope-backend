package com.threatscopebackend.domain.search;

import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.elasticsearch.BreachDataIndex;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.security.UserPrincipal;

import java.util.List;
import java.util.Set;

/**
 * Domain interface for mapping search results
 * Clean Architecture: Domain service interface
 * Single Responsibility: Only handles result mapping
 */
public interface SearchResultMapper {
    
    /**
     * Extract MongoDB IDs from Elasticsearch results (original pattern)
     */
    Set<String> extractMongoIdsFromElasticsearch(List<BreachDataIndex> esResults);
    
    /**
     * Map MongoDB results to domain objects  
     */
    List<SearchResponse.SearchResult> mapMongoResults(
            List<StealerLog> mongoResults,
            UserPrincipal user);
    
    /**
     * Get user's plan type for business logic
     */
    CommonEnums.PlanType getUserPlanType(UserPrincipal user);
    
    /**
     * Add source metadata to results
     */
    void addSourceMetadata(SearchResponse.SearchResult result, String sourceName, String displayName);
}