package com.threatscopebackend.service.datasource;

import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.security.UserPrincipal;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for all data sources in the ThreatScope platform
 * Provides unified access to different breach data sources
 */
public interface DataSourceService {
    
    /**
     * Search for breach data from this specific data source
     * @param request The search request containing query and filters
     * @param user The authenticated user making the request
     * @return CompletableFuture containing list of search results
     */
    CompletableFuture<List<SearchResponse.SearchResult>> search(SearchRequest request, UserPrincipal user);
    
    /**
     * Get the unique identifier for this data source
     * @return Source name (e.g., "internal", "breach-vip", "haveibeenpwned")
     */
    String getSourceName();
    
    /**
     * Check if this data source is currently enabled
     * @return true if enabled, false otherwise
     */
    boolean isEnabled();
    
    /**
     * Get the priority of this data source (lower number = higher priority)
     * @return Priority value (0 = highest priority)
     */
    int getPriority();
    
    /**
     * Get the display name for this data source
     * @return Human-readable name for UI display
     */
    String getDisplayName();
    
    /**
     * Check if this data source supports the given search type
     * @param searchType The type of search being performed
     * @return true if supported, false otherwise
     */
    boolean supportsSearchType(SearchRequest.SearchType searchType);
    
    /**
     * Get the maximum number of results this source can return per request
     * @return Maximum results limit
     */
    int getMaxResultsLimit();
    
    /**
     * Check if this data source is currently healthy/available
     * @return true if healthy, false if experiencing issues
     */
    default boolean isHealthy() {
        return isEnabled();
    }
}