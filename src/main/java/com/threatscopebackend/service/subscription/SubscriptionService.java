package com.threatscopebackend.service.subscription;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.entity.postgresql.Subscription;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.PlanRepository;
import com.threatscopebackend.repository.postgresql.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final PlanRepository planRepository;
    
    /**
     * Get user's current subscription or create a free one
     */
    public Subscription getUserSubscription(User user) {
        return subscriptionRepository.findByUser(user)
            .orElseGet(() -> createFreeSubscription(user));
    }
    
    /**
     * Create a free subscription for new users
     */
    @Transactional
    public Subscription createFreeSubscription(User user) {
        log.info("Creating free subscription for user: {}", user.getId());
        
        Plan freePlan = planRepository.findFreePlan()
            .orElseThrow(() -> new ResourceNotFoundException("Free plan not found"));
        
        Subscription subscription = new Subscription();
        subscription.setUser(user);
        subscription.setPlan(freePlan);
        subscription.setPlanType(CommonEnums.PlanType.FREE);
        subscription.setStatus(CommonEnums.SubscriptionStatus.ACTIVE);
        subscription.setBillingCycle(CommonEnums.BillingCycle.MONTHLY);
        subscription.setAmount(freePlan.getPrice());
        subscription.setCurrency(freePlan.getCurrency());
        subscription.setCurrentPeriodStart(LocalDateTime.now());
        subscription.setCurrentPeriodEnd(LocalDateTime.now().plusMonths(1));
        
        return subscriptionRepository.save(subscription);
    }
    
    /**
     * Check if user can create monitoring items
     */
    public boolean canCreateMonitoringItem(User user, int currentCount) {
        Subscription subscription = getUserSubscription(user);
        return subscription.canCreateMonitoringItems(currentCount);
    }
    
    /**
     * Check if user can perform searches
     */
    public boolean canPerformSearch(User user, int todaysSearches) {
        Subscription subscription = getUserSubscription(user);
        return subscription.canPerformSearches(todaysSearches);
    }
    
    /**
     * Check if user can use specific monitoring frequency
     */
    public boolean canUseMonitoringFrequency(User user, CommonEnums.MonitorFrequency frequency) {
        Subscription subscription = getUserSubscription(user);
        return subscription.canUseFrequency(frequency);
    }
    
    /**
     * Check if user has specific feature
     */
    public boolean hasFeature(User user, String featureName) {
        Subscription subscription = getUserSubscription(user);
        return subscription.hasFeature(featureName);
    }
    
    /**
     * Get user's plan limits
     */
    public Plan getUserPlan(User user) {
        Subscription subscription = getUserSubscription(user);
        return subscription.getPlan();
    }
    
    /**
     * Upgrade user subscription
     */
    @Transactional
    public Subscription upgradeSubscription(User user, String planName) {
        log.info("Upgrading subscription for user {} to plan: {}", user.getId(), planName);
        
        // Convert plan name string to enum and find plan
        Plan newPlan;
        try {
            CommonEnums.PlanType planType = CommonEnums.PlanType.valueOf(planName.toUpperCase());
            newPlan = planRepository.findByPlanType(planType)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planName));
        } catch (IllegalArgumentException e) {
            // If enum conversion fails, try finding by display name
            newPlan = planRepository.findByDisplayNameIgnoreCase(planName)
                .orElseThrow(() -> new ResourceNotFoundException("Plan not found: " + planName));
        }
        
        Subscription subscription = getUserSubscription(user);
        subscription.setPlan(newPlan);
        subscription.setPlanType(newPlan.getPlanType());
        subscription.setAmount(newPlan.getPrice());
        subscription.setBillingCycle(newPlan.getBillingCycle());
        
        return subscriptionRepository.save(subscription);
    }
    
    /**
     * Get all available plans
     */
    public List<Plan> getAvailablePlans() {
        return planRepository.findByIsPublicTrueAndIsActiveTrueOrderBySortOrder();
    }
    
    /**
     * Process expired trials
     */
    @Transactional
    public void processExpiredTrials() {
        List<Subscription> expiredTrials = subscriptionRepository.findExpiredTrials(LocalDateTime.now());
        
        Plan freePlan = planRepository.findFreePlan()
            .orElseThrow(() -> new ResourceNotFoundException("Free plan not found"));
        
        for (Subscription subscription : expiredTrials) {
            log.info("Processing expired trial for user: {}", subscription.getUser().getId());
            
            subscription.setPlan(freePlan);
            subscription.setPlanType(CommonEnums.PlanType.FREE);
            subscription.setStatus(CommonEnums.SubscriptionStatus.ACTIVE);
            subscription.setAmount(freePlan.getPrice());
            subscription.setTrialEndDate(null);
            
            subscriptionRepository.save(subscription);
        }
    }
    
    /**
     * Get subscription statistics
     */
    public List<Object[]> getSubscriptionStatistics() {
        return subscriptionRepository.getSubscriptionCountsByPlan();
    }
}
