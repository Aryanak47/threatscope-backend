package com.threatscopebackend.service.data;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides index naming and management for Elasticsearch indices.
 * In the new Elasticsearch client, index management is typically done
 * through the ElasticsearchClient directly, but we keep this for compatibility
 * with any existing code that might use it.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class IndexNameProvider {
    private final ElasticsearchOperations elasticsearchOperations;
    
    public static final String BREACH_INDEX_PREFIX = "breaches-*";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    

    
    /**
     * Gets index name for a specific date (returns the main index in the new version)
     */
    public String getIndexForDate(LocalDateTime date) {
        return BREACH_INDEX_PREFIX;
    }
    
    /**
     * Gets all index patterns (returns the main index in the new version)
     */
    public String[] getAllIndicesPattern() {
        return new String[]{BREACH_INDEX_PREFIX};
    }
    
    /**
     * Gets indices for the last N months - uses wildcard pattern to avoid specific index issues
     */
    public String[] generateIndexNamesByMonth(int monthsBack) {
        // Use the wildcard pattern from @Document annotation instead of specific monthly indices
        log.debug("Using wildcard pattern {} for search (monthsBack: {})", BREACH_INDEX_PREFIX, monthsBack);
        return new String[]{BREACH_INDEX_PREFIX};
    }

    private boolean indexExists(String indexName) {
        try {
            return elasticsearchOperations.indexOps(IndexCoordinates.of(indexName)).exists();
        } catch (Exception e) {
            log.debug("Index check failed for {}: {}", indexName, e.getMessage());
            return false;
        }
    }
}
