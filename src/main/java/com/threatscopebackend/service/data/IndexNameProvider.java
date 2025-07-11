package com.threatscopebackend.service.data;

import org.springframework.stereotype.Component;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Provides index naming and management for Elasticsearch indices.
 * In the new Elasticsearch client, index management is typically done
 * through the ElasticsearchClient directly, but we keep this for compatibility
 * with any existing code that might use it.
 */
@Component
public class IndexNameProvider {
    
    public static final String BREACH_INDEX_PREFIX = "breach-data";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM");
    
    /**
     * Gets the current index name (using a single index in the new version)
     */
    public String getCurrentMonthIndex() {
        return BREACH_INDEX_PREFIX;
    }
    
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
     * Gets indices for the last N months (returns the main index in the new version)
     */
    public String[] getIndicesForLastMonths(int monthsBack) {
        return new String[]{BREACH_INDEX_PREFIX};
    }
}
