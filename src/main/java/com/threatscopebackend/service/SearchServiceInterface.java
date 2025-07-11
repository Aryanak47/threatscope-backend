package com.threatscopebackend.service;

import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.security.UserPrincipal;

import java.util.concurrent.CompletableFuture;

/**
 * Interface defining the contract for search operations in the ThreatScope application.
 */
public interface SearchServiceInterface {
    
    /**
     * Performs a search across multiple data sources based on the provided search request.
     *
     * @param request The search request containing query parameters
     * @param user The authenticated user making the request (can be null for public access)
     * @return CompletableFuture containing the search results
     */
    CompletableFuture<SearchResponse> search(SearchRequest request, UserPrincipal user);
    
    /**
     * Counts the number of search results without retrieving the actual documents.
     * Useful for pagination and performance optimization.
     *
     * @param request The search request containing query parameters
     * @param user The authenticated user making the request
     * @return The total count of search results
     */
    long countSearchResults(SearchRequest request, UserPrincipal user);
    
    /**
     * Gets search suggestions for autocomplete functionality.
     *
     * @param prefix The prefix to get suggestions for
     * @param searchType The type of search (EMAIL, DOMAIN, etc.)
     * @param limit Maximum number of suggestions to return
     * @return List of search suggestions
     */
    //List<String> getSearchSuggestions(String prefix, SearchRequest.SearchType searchType, int limit);
}
