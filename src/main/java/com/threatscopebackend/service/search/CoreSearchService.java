package com.threatscopebackend.service.search;

import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.domain.search.SearchRepository;
import com.threatscopebackend.domain.search.SearchResultMapper;
import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.elasticsearch.BreachDataIndex;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * Core search service using Clean Architecture principles
 * Application Service that orchestrates domain and infrastructure layers
 * 
 * SOLID Principles Applied:
 * - Single Responsibility: Only handles search orchestration
 * - Open/Closed: Extensible through repository and mapper interfaces
 * - Liskov Substitution: Can substitute different repository/mapper implementations
 * - Interface Segregation: Uses focused interfaces
 * - Dependency Inversion: Depends on abstractions, not concretions
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CoreSearchService {
    
    private final SearchRepository searchRepository;
    private final SearchResultMapper searchResultMapper;
    
    /**
     * Perform internal search using Elasticsearch first, MongoDB as fallback
     * This is the core search logic shared across multiple services
     */
    public List<SearchResponse.SearchResult> performInternalSearch(SearchRequest request, UserPrincipal user) {
        log.debug("Performing core internal search for query: {}", request.getQuery());
        
        try {
            // Step 1: Try Elasticsearch first using Clean Architecture
            Page<BreachDataIndex> esResults = searchRepository.searchElasticsearch(request);
            
            if (esResults != null && !esResults.isEmpty()) {
                log.debug("Found {} results from Elasticsearch", esResults.getTotalElements());
                
                // Original pattern: Extract MongoDB IDs from Elasticsearch results
                Set<String> mongoIds = searchResultMapper.extractMongoIdsFromElasticsearch(esResults.getContent());
                
                // Fetch full documents from MongoDB
                List<StealerLog> fullDocuments = searchRepository.findMongoDocumentsByIds(mongoIds);
                
                // Apply original fromStealerLogWithPlan logic
                return searchResultMapper.mapMongoResults(fullDocuments, user);
            }
            
            // Step 2: Fallback to MongoDB if no Elasticsearch results
            log.debug("No Elasticsearch results, trying MongoDB fallback");
            List<StealerLog> mongoResults = searchRepository.searchMongoDB(request);
            
            if (!mongoResults.isEmpty()) {
                log.debug("Found {} results from MongoDB fallback", mongoResults.size());
                return searchResultMapper.mapMongoResults(mongoResults, user);
            }
            
            log.debug("No results found in either Elasticsearch or MongoDB");
            return List.of();
            
        } catch (Exception e) {
            log.error("Error in core internal search: {}", e.getMessage(), e);
            return List.of();
        }
    }
    
}