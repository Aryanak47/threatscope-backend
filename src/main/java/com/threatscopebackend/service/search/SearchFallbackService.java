package com.threatscopebackend.service.search;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import com.threatscopebackend.config.ElasticsearchConfig;
import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.repository.mongo.StealerLogRepository;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.IndexOperations;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class SearchFallbackService {
    private final StealerLogRepository stealerLogRepository;
    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchClient elasticsearchClient;
    private final ElasticsearchConfig elasticsearchConfig;

    public SearchResponse searchInMongoDB(SearchRequest request, UserPrincipal user) {
        log.info("Falling back to MongoDB search for query: {}", request.getQuery());

        // Basic search in MongoDB based on query type
        List<StealerLog> results = switch (request.getSearchType()) {
            case EMAIL -> stealerLogRepository.findByLoginContainingIgnoreCase(request.getQuery());
            case DOMAIN -> stealerLogRepository.findByDomainContainingIgnoreCase(request.getQuery());
            case URL -> stealerLogRepository.findByUrlContainingIgnoreCase(request.getQuery());
            default -> stealerLogRepository.findByLoginContainingIgnoreCaseOrUrlContainingIgnoreCase(
                    request.getQuery(), request.getQuery());
        };

        // Convert to response format, filtering out any empty Optionals
        List<SearchResponse.SearchResult> searchResults = results.stream()
                .map(this::convertToSearchResult)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        // Async index the results in Elasticsearch
        if (!results.isEmpty()) {
            indexInElasticsearchAsync(results);
        }

        return SearchResponse.builder()
                .results(searchResults)
                .totalResults((long) searchResults.size())
                .currentPage(request.getPage())
                .pageSize(request.getSize())
                .query(request.getQuery())
                .searchType(request.getSearchType())
                .build();
    }

    @Async
    public void indexInElasticsearchAsync(List<StealerLog> logs) {
        if (logs == null || logs.isEmpty()) {
            return;
        }

        try {
            // Use the current month's index
            String indexName = elasticsearchConfig.getCurrentMonthIndex();
            IndexCoordinates indexCoordinates = IndexCoordinates.of(indexName);
            
            // Ensure the index exists with proper mapping
            ensureIndexExists(indexName);
            
            // Save all documents at once using ElasticsearchOperations
            elasticsearchOperations.save(logs, indexCoordinates);
            
            log.info("Successfully indexed {} documents in Elasticsearch index: {}", 
                    logs.size(), indexName);
                    
        } catch (Exception e) {
            log.error("Error during async indexing: {}", e.getMessage(), e);
        }
    }
    
    private void ensureIndexExists(String indexName) {
        try {
            IndexOperations indexOps = elasticsearchOperations.indexOps(IndexCoordinates.of(indexName));
            if (!indexOps.exists()) {
                indexOps.createWithMapping();
                log.info("Created index with mapping: {}", indexName);
            }
        } catch (Exception e) {
            log.error("Error ensuring index exists: {}", e.getMessage(), e);
            throw e;
        }
    }

    private Optional<SearchResponse.SearchResult> convertToSearchResult(StealerLog log) {
        return SearchResponse.SearchResult.fromStealerLog(log);
    }
}