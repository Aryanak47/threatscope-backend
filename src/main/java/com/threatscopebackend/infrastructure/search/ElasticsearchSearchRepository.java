package com.threatscopebackend.infrastructure.search;

import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.domain.search.SearchRepository;
import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.elasticsearch.BreachDataIndex;
import com.threatscopebackend.repository.mongo.StealerLogRepository;
import com.threatscopebackend.service.search.MultiIndexSearchService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Set;

/**
 * Infrastructure layer implementation of SearchRepository
 * Clean Architecture: Infrastructure layer
 * Single Responsibility: Only handles data access
 */
@Repository
@RequiredArgsConstructor
@Slf4j
public class ElasticsearchSearchRepository implements SearchRepository {
    
    private final MultiIndexSearchService multiIndexSearchService;
    private final StealerLogRepository stealerLogRepository;
    
    @Override
    public Page<BreachDataIndex> searchElasticsearch(SearchRequest request) {
        try {
            String query = request.getQuery().toLowerCase().trim();
            int monthsBack = 36; // Default months back
            
            Sort sort = Sort.by(Sort.Direction.DESC, "timestamp");
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize(), sort);
            
            return switch (request.getSearchType()) {
                case EMAIL -> {
                    if (request.getSearchMode() == SearchRequest.SearchMode.EXACT) {
                        yield multiIndexSearchService.searchLoginAcrossIndices(query, pageable, monthsBack);
                    } else {
                        yield multiIndexSearchService.searchLoginAcrossIndices(query, pageable, monthsBack);
                    }
                }
                case URL -> multiIndexSearchService.searchUrlAcrossIndices(query, pageable, monthsBack);
                case DOMAIN -> multiIndexSearchService.searchUrlAcrossIndices(query, pageable, monthsBack);
                default -> multiIndexSearchService.searchLoginAcrossIndices(query, pageable, monthsBack);
            };
            
        } catch (Exception e) {
            log.warn("Elasticsearch search failed: {}", e.getMessage());
            return Page.empty();
        }
    }
    
    @Override
    public List<StealerLog> searchMongoDB(SearchRequest request) {
        try {
            String query = request.getQuery().toLowerCase().trim();
            Pageable pageable = PageRequest.of(request.getPage(), request.getSize());
            
            return switch (request.getSearchType()) {
                case EMAIL -> stealerLogRepository.findByLoginContainingIgnoreCaseOrUrlContainingIgnoreCase(query, query);
                case USERNAME -> stealerLogRepository.findByLoginIgnoreCase(query);
                case DOMAIN -> stealerLogRepository.findByDomainContainingIgnoreCase(query);
                case URL -> stealerLogRepository.findByUrlIgnoreCase(query);
                case PASSWORD -> stealerLogRepository.findByLoginIgnoreCase(query); // No password search for security
                default -> stealerLogRepository.findByLoginIgnoreCase(query);
            };
            
        } catch (Exception e) {
            log.warn("MongoDB search failed: {}", e.getMessage());
            return List.of();
        }
    }
    
    @Override
    public boolean isElasticsearchHealthy() {
        try {
            // Simple health check - could be enhanced
            return multiIndexSearchService != null;
        } catch (Exception e) {
            log.warn("Elasticsearch health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public boolean isMongoDBHealthy() {
        try {
            // Simple health check - could be enhanced
            return stealerLogRepository != null;
        } catch (Exception e) {
            log.warn("MongoDB health check failed: {}", e.getMessage());
            return false;
        }
    }
    
    @Override
    public List<StealerLog> findMongoDocumentsByIds(Set<String> ids) {
        return stealerLogRepository.findByIdIn(ids);
    }
}