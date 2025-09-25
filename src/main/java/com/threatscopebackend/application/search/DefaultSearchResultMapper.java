package com.threatscopebackend.application.search;

import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.domain.search.SearchResultMapper;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.elasticsearch.BreachDataIndex;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Application layer implementation of SearchResultMapper
 * Clean Architecture: Application service
 * Single Responsibility: Only handles result mapping logic
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DefaultSearchResultMapper implements SearchResultMapper {
    
    private final UserRepository userRepository;
    
    @Override
    public Set<String> extractMongoIdsFromElasticsearch(List<BreachDataIndex> esResults) {
        return esResults.stream()
                .map(BreachDataIndex::getId)
                .collect(Collectors.toSet());
    }
    
    @Override
    public List<SearchResponse.SearchResult> mapMongoResults(
            List<StealerLog> mongoResults,
            UserPrincipal user) {
        
        CommonEnums.PlanType planType = getUserPlanType(user);
        
        return mongoResults.stream()
                .map(log -> SearchResponse.SearchResult.fromStealerLogWithPlan(log, planType))
                .filter(Optional::isPresent)
                .map(Optional::get)
                .collect(Collectors.toList());
    }
    
    @Override
    public CommonEnums.PlanType getUserPlanType(UserPrincipal user) {
        // Original logic from SearchService.convertToEnhancedSearchResult()
        CommonEnums.PlanType planType = CommonEnums.PlanType.FREE; // Default for anonymous/free users
        
        if (user != null && !"anonymous".equals(user.getId())) {
            try {
                // Fetch user's subscription plan exactly like original
                User fullUser = userRepository.findByIdWithRolesAndSubscription(user.getId()).orElse(null);
                if (fullUser != null && fullUser.getSubscription() != null) {
                    planType = fullUser.getSubscription().getPlanType();
                }
            } catch (Exception e) {
                log.warn("Failed to get user subscription for password masking: {}", e.getMessage());
            }
        }
        
        return planType;
    }
    
    @Override
    public void addSourceMetadata(SearchResponse.SearchResult result, String sourceName, String displayName) {
        if (result.getAdditionalData() == null) {
            result.setAdditionalData(new HashMap<>());
        }
        result.getAdditionalData().put("dataSource", sourceName);
        result.getAdditionalData().put("sourceDisplayName", displayName);
    }
    
    // No longer needed - using original Elasticsearch->MongoDB pattern instead
}