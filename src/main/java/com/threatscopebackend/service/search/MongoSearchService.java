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
public class MongoSearchService {
    private final StealerLogRepository stealerLogRepository;

    public SearchResponse searchInMongoDB(SearchRequest request, UserPrincipal user) {
        log.info("Falling back to MongoDB search for query: {}", request.getQuery());

        // Basic search in MongoDB based on query type
        List<StealerLog> results = switch (request.getSearchType()) {
            case EMAIL , USERNAME -> stealerLogRepository.findByLoginIgnoreCase(request.getQuery());
            case DOMAIN -> stealerLogRepository.findByDomainContainingIgnoreCase(request.getQuery());
            case URL -> stealerLogRepository.findByUrlIgnoreCase(request.getQuery());
            default -> throw new IllegalArgumentException("Unsupported search type: " + request.getSearchType());
        };

        // Convert to response format, filtering out any empty Optionals
        List<SearchResponse.SearchResult> searchResults = results.stream()
                .map(this::convertToSearchResult)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());

        return SearchResponse.builder()
                .results(searchResults)
                .totalResults((long) searchResults.size())
                .currentPage(request.getPage())
                .pageSize(request.getSize())
                .query(request.getQuery())
                .searchType(request.getSearchType())
                .build();
    }


    private Optional<SearchResponse.SearchResult> convertToSearchResult(StealerLog log) {
        return SearchResponse.SearchResult.fromStealerLog(log);
    }
}