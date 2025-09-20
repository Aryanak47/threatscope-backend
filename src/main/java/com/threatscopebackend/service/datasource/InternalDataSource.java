package com.threatscopebackend.service.datasource;

import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.search.CoreSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Internal data source implementation using shared CoreSearchService
 * Follows DRY principle by reusing the same search logic as SearchService
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class InternalDataSource implements DataSourceService {
    
    private final CoreSearchService coreSearchService;
    
    @Value("${datasources.internal.enabled:true}")
    private boolean enabled;
    
    @Value("${datasources.internal.priority:0}")
    private int priority;
    
    @Value("${datasources.internal.max-results:10000}")
    private int maxResultsLimit;
    
    @Override
    public CompletableFuture<List<SearchResponse.SearchResult>> search(SearchRequest request, UserPrincipal user) {
        return CompletableFuture.supplyAsync(() -> {
            try {
                log.debug("Searching internal data source for query: {}", request.getQuery());
                
                // Use shared core search service (DRY principle)
                List<SearchResponse.SearchResult> results = coreSearchService.performInternalSearch(request, user);
                
                // Add internal source metadata to all results
                results.forEach(this::addInternalSourceMetadata);
                    
                log.debug("Internal data source returned {} results", results.size());
                return results;
                
            } catch (Exception e) {
                log.error("Error searching internal data source: {}", e.getMessage(), e);
                return List.of();
            }
        });
    }
    
    /**
     * Add internal source metadata to search results
     * Single Responsibility: Only handles metadata addition
     */
    private void addInternalSourceMetadata(SearchResponse.SearchResult result) {
        if (result.getAdditionalData() == null) {
            result.setAdditionalData(new HashMap<>());
        }
        result.getAdditionalData().put("dataSource", getSourceName());
        result.getAdditionalData().put("sourceDisplayName", getDisplayName());
    }
    
    @Override
    public String getSourceName() {
        return "internal";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public String getDisplayName() {
        return "ThreatScope Internal";
    }
    
    @Override
    public boolean supportsSearchType(SearchRequest.SearchType searchType) {
        // Internal source supports all search types
        return true;
    }
    
    @Override
    public int getMaxResultsLimit() {
        return maxResultsLimit;
    }
    
    @Override
    public boolean isHealthy() {
        // For now, assume internal source is always healthy if enabled
        // In the future, we could check Elasticsearch/MongoDB health here
        return isEnabled();
    }
}