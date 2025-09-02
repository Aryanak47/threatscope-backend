package com.threatscopebackend.service.consultation;

import com.threatscopebackend.dto.consultation.response.ConsultationPlanResponse;
import com.threatscopebackend.entity.postgresql.ConsultationPlan;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.ConsultationPlanRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationPlanService {
    
    private final ConsultationPlanRepository consultationPlanRepository;
    
    /**
     * Get all active consultation plans
     */
    @Transactional(readOnly = true)
    public List<ConsultationPlanResponse> getActivePlans() {
        log.info("Fetching all active consultation plans");
        
        try {
            List<ConsultationPlan> plans = consultationPlanRepository.findByIsActiveTrueOrderBySortOrderAsc();
            log.info("Found {} active consultation plans", plans.size());
            
            if (plans.isEmpty()) {
                log.warn("No active consultation plans found in database");
                return List.of();
            }
            
            List<ConsultationPlanResponse> responses = plans.stream()
                    .map(plan -> {
                        try {
                            return ConsultationPlanResponse.fromEntity(plan);
                        } catch (Exception e) {
                            log.error("Error converting plan {} to response: {}", plan.getId(), e.getMessage());
                            throw e;
                        }
                    })
                    .collect(Collectors.toList());
                    
            log.info("Successfully converted {} plans to response objects", responses.size());
            return responses;
            
        } catch (Exception e) {
            log.error("Error fetching active consultation plans: {}", e.getMessage(), e);
            throw e;
        }
    }
    
    /**
     * Get popular consultation plans
     */
    @Transactional(readOnly = true)
    public List<ConsultationPlanResponse> getPopularPlans() {
        log.debug("Fetching popular consultation plans");
        
        List<ConsultationPlan> plans = consultationPlanRepository.findByIsActiveTrueAndIsPopularTrueOrderBySortOrderAsc();
        
        return plans.stream()
                .map(ConsultationPlanResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Get consultation plan by ID
     */
    @Transactional(readOnly = true)
    public ConsultationPlanResponse getPlanById(Long planId) {
        log.debug("Fetching consultation plan by ID: {}", planId);
        
        ConsultationPlan plan = consultationPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation plan not found"));
        
        if (!plan.getIsActive()) {
            throw new ResourceNotFoundException("Consultation plan is not available");
        }
        
        return ConsultationPlanResponse.fromEntity(plan);
    }
    
    /**
     * Get plans by price range
     */
    @Transactional(readOnly = true)
    public List<ConsultationPlanResponse> getPlansByPriceRange(BigDecimal minPrice, BigDecimal maxPrice) {
        log.debug("Fetching consultation plans by price range: {} - {}", minPrice, maxPrice);
        
        List<ConsultationPlan> plans = consultationPlanRepository.findByPriceRange(minPrice, maxPrice);
        
        return plans.stream()
                .map(ConsultationPlanResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Get plans by duration range
     */
    @Transactional(readOnly = true)
    public List<ConsultationPlanResponse> getPlansByDurationRange(Integer minDuration, Integer maxDuration) {
        log.debug("Fetching consultation plans by duration range: {} - {} minutes", minDuration, maxDuration);
        
        List<ConsultationPlan> plans = consultationPlanRepository.findByDurationRange(minDuration, maxDuration);
        
        return plans.stream()
                .map(ConsultationPlanResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Get all plans sorted by price
     */
    @Transactional(readOnly = true)
    public List<ConsultationPlanResponse> getPlansSortedByPrice() {
        log.debug("Fetching consultation plans sorted by price");
        
        Sort sort = Sort.by(Sort.Direction.ASC, "price");
        List<ConsultationPlan> plans = consultationPlanRepository.findByIsActiveTrue(sort);
        
        return plans.stream()
                .map(ConsultationPlanResponse::fromEntity)
                .collect(Collectors.toList());
    }
    
    /**
     * Validate plan availability and return entity
     */
    @Transactional(readOnly = true)
    public ConsultationPlan validateAndGetPlan(Long planId) {
        log.debug("Validating consultation plan: {}", planId);
        
        ConsultationPlan plan = consultationPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation plan not found"));
        
        if (!plan.isAvailableForPurchase()) {
            throw new IllegalArgumentException("Consultation plan is not available for purchase");
        }
        
        return plan;
    }
    
    /**
     * Get plan statistics for admin
     */
    @Transactional(readOnly = true)
    public Object getPlanStatistics() {
        log.debug("Fetching consultation plan statistics");
        
        Object[] stats = consultationPlanRepository.getPlanStatistics();
        long activeCount = consultationPlanRepository.countByIsActiveTrue();
        
        return new Object[]{
                stats[0], // count
                stats[1], // avg price
                stats[2], // min price
                stats[3], // max price
                activeCount // active count
        };
    }
    
    /**
     * Check if plan supports specific features
     */
    @Transactional(readOnly = true)
    public boolean planSupportsFeature(Long planId, String featureName) {
        ConsultationPlan plan = consultationPlanRepository.findById(planId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation plan not found"));
        
        String features = plan.getFeatures();
        return features != null && features.toLowerCase().contains(featureName.toLowerCase());
    }
    
    /**
     * Get recommended plan based on user's subscription
     */
    @Transactional(readOnly = true)
    public ConsultationPlanResponse getRecommendedPlan(String userPlanType) {
        log.debug("Getting recommended consultation plan for user plan: {}", userPlanType);
        
        List<ConsultationPlan> allPlans = consultationPlanRepository.findByIsActiveTrueOrderBySortOrderAsc();
        
        // Simple recommendation logic based on user's subscription plan
        ConsultationPlan recommendedPlan = switch (userPlanType.toUpperCase()) {
            case "FREE" -> allPlans.stream()
                    .filter(p -> p.getPrice().compareTo(BigDecimal.valueOf(50)) <= 0)
                    .findFirst()
                    .orElse(allPlans.get(0));
                    
            case "BASIC" -> allPlans.stream()
                    .filter(p -> p.getPrice().compareTo(BigDecimal.valueOf(100)) <= 0)
                    .findFirst()
                    .orElse(allPlans.get(allPlans.size() > 1 ? 1 : 0));
                    
            case "PROFESSIONAL", "ENTERPRISE" -> allPlans.stream()
                    .filter(p -> p.getIsPopular())
                    .findFirst()
                    .orElse(allPlans.get(allPlans.size() - 1));
                    
            default -> allPlans.get(0);
        };
        
        return ConsultationPlanResponse.fromEntity(recommendedPlan);
    }
}
