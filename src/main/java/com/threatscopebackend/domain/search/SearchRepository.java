package com.threatscopebackend.domain.search;

import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.elasticsearch.BreachDataIndex;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.Set;

/**
 * Domain interface for search repository operations
 * Clean Architecture: Domain layer interface
 */
public interface SearchRepository {
    
    /**
     * Search in Elasticsearch
     */
    Page<BreachDataIndex> searchElasticsearch(SearchRequest request);
    
    /**
     * Search in MongoDB as fallback
     */
    List<StealerLog> searchMongoDB(SearchRequest request);
    
    /**
     * Check if Elasticsearch is available
     */
    boolean isElasticsearchHealthy();
    
    /**
     * Check if MongoDB is available  
     */
    boolean isMongoDBHealthy();
    
    /**
     * Fetch MongoDB documents by IDs (original Elasticsearch->MongoDB pattern)
     */
    List<StealerLog> findMongoDocumentsByIds(Set<String> ids);
}