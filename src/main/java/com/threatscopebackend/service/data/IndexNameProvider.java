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
    
    public static final String BREACH_INDEX_PREFIX = "breaches-";
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
    public String[] generateIndexNames(int monthsBack) {
        List<String> indices = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now();
        DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM");

        // Always include the latest index pattern
        String currentMonthIndex = "breaches-" + now.format(formatter);
        indices.add(currentMonthIndex);

        // Check how many months back we have data
        boolean previousMonthExists = true;
        int monthsChecked = 1;

        // Check up to the requested number of months or until we can't find an index
        while (previousMonthExists && monthsChecked < monthsBack) {
            String monthIndex = "breaches-" + now.minusMonths(monthsChecked).format(formatter);
            if (indexExists(monthIndex)) {
                indices.add(monthIndex);
                monthsChecked++;
            } else {
                previousMonthExists = false;
            }
        }

        log.debug("Generated {} indices for search: {}", indices.size(), indices);
        return indices.toArray(new String[0]);
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
