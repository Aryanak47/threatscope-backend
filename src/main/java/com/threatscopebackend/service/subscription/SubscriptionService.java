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
        log.debug("Getting subscription for user: {} (user.subscription = {})", 
                 user.getId(), user.getSubscription() != null ? "exists" : "null");
        
        // First check if user already has subscription loaded
        if (user.getSubscription() != null) {
            log.debug("Found loaded subscription: {}", user.getSubscription().getPlanType());
            return user.getSubscription();
        }
        
        // Otherwise fetch from repository
        return subscriptionRepository.findByUser(user)
            .orElseGet(() -> {
                log.info("No subscription found for user: {}, creating free subscription", user.getId());
                return createFreeSubscription(user);
            });
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
        log.debug("Checking if user {} can create monitoring item (currentCount: {})", user.getId(), currentCount);
        Subscription subscription = getUserSubscription(user);
        
        if (subscription == null) {
            log.warn("No subscription found for user: {}", user.getId());
            return false;
        }
        
        if (subscription.getPlan() == null) {
            log.warn("No plan found for user subscription: {}", user.getId());
            return false;
        }
        
        boolean canCreate = subscription.canCreateMonitoringItems(currentCount);
        log.debug("User {} subscription check: plan={}, maxItems={}, currentCount={}, canCreate={}", 
                 user.getId(), subscription.getPlan().getPlanType(), 
                 subscription.getPlan().getMaxMonitoringItems(), currentCount, canCreate);
        
        return canCreate;
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
     * Get all active plans (public method for API)
     */
    public List<Plan> getAllActivePlans() {
        return getAvailablePlans();
    }
    
    /**
     * Get plan by ID
     */
    public Plan getPlanById(Long planId) {
        return planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plan not found with ID: " + planId));
    }
    
    /**
     * Get plans comparison data
     */
    public Object getPlansComparison() {
        List<Plan> plans = getAllActivePlans();
        
        // Create a comparison structure
        return plans.stream().map(plan -> {
            return java.util.Map.of(
                "id", plan.getId(),
                "name", plan.getDisplayName(),
                "planType", plan.getPlanType().name(),
                "price", plan.getPrice(),
                "dailySearches", plan.getDailySearches(),
                "monthlySearches", plan.getMonthlySearches(),
                "maxMonitoringItems", plan.getMaxMonitoringItems(),
                "features", java.util.Map.of(
                    "apiAccess", plan.getApiAccess(),
                    "realTimeMonitoring", plan.getRealTimeMonitoring(),
                    "emailAlerts", plan.getEmailAlerts(),
                    "inAppAlerts", plan.getInAppAlerts(),
                    "webhookAlerts", plan.getWebhookAlerts(),
                    "prioritySupport", plan.getPrioritySupport(),
                    "customIntegrations", plan.getCustomIntegrations(),
                    "advancedAnalytics", plan.getAdvancedAnalytics()
                )
            );
        }).toList();
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
    
    /**
     * Get comprehensive subscription details including plan, limits, permissions, and usage
     */
    public SubscriptionDetails getSubscriptionDetails(User user, int currentMonitoringItems, int todaySearches) {
        log.debug("Getting comprehensive subscription details for user: {}", user.getId());
        
        Subscription subscription = getUserSubscription(user);
        Plan plan = subscription.getPlan();
        
        // Build subscription details
        SubscriptionDetails details = new SubscriptionDetails();
        
        // Basic subscription info
        SubscriptionInfo.SubscriptionInfoBuilder builder = SubscriptionInfo.builder()
            .id(subscription.getId())
            .planName(plan.getName())
            .displayName(plan.getDisplayName())
            .planType(subscription.getPlanType() != null ? subscription.getPlanType().name() : null)
            .status(subscription.getStatus() != null ? subscription.getStatus().name() : null);
            
        // Set optional fields with null checks
        if (subscription.getBillingCycle() != null) {
            builder.billingCycle(subscription.getBillingCycle().name());
        }
        
        builder.amount(subscription.getAmount())
            .currency(subscription.getCurrency())
            .currentPeriodStart(subscription.getCurrentPeriodStart())
            .currentPeriodEnd(subscription.getCurrentPeriodEnd())
            .trialEndDate(subscription.getTrialEndDate())
            .isActive(subscription.isActive())
            .isTrial(subscription.isTrial());
            
        details.setSubscription(builder.build());
        
        // Plan limits and features
        details.setPlanLimits(PlanLimits.builder()
            .dailySearches(plan.getDailySearches())
            .monthlySearches(plan.getMonthlySearches())
            .maxMonitoringItems(plan.getMaxMonitoringItems())
            .maxAlertsPerDay(plan.getMaxAlertsPerDay())
            .dailyExports(plan.getDailyExports())
            .monthlyExports(plan.getMonthlyExports())
            .hasApiAccess(plan.getApiAccess())
            .hasRealTimeMonitoring(plan.getRealTimeMonitoring())
            .hasEmailAlerts(plan.getEmailAlerts())
            .hasInAppAlerts(plan.getInAppAlerts())
            .hasWebhookAlerts(plan.getWebhookAlerts())
            .hasPrioritySupport(plan.getPrioritySupport())
            .hasCustomIntegrations(plan.getCustomIntegrations())
            .hasAdvancedAnalytics(plan.getAdvancedAnalytics())
            .allowedFrequencies(parseAllowedFrequencies(plan.getMonitoringFrequencies()))
            .build());
        
        // Current permissions based on usage
        details.setPermissions(UserPermissions.builder()
            .canCreateMonitoringItem(canCreateMonitoringItem(user, currentMonitoringItems))
            .canPerformSearch(canPerformSearch(user, todaySearches))
            .hasApiAccess(hasFeature(user, "api_access"))
            .hasRealTimeMonitoring(hasFeature(user, "real_time_monitoring"))
            .hasPrioritySupport(hasFeature(user, "priority_support"))
            .hasAdvancedAnalytics(hasFeature(user, "advanced_analytics"))
            .build());
        
        // Current usage
        details.setCurrentUsage(CurrentUsage.builder()
            .monitoringItems(currentMonitoringItems)
            .todaySearches(todaySearches)
            .build());
        
        return details;
    }
    
    private java.util.List<String> parseAllowedFrequencies(String frequenciesJson) {
        if (frequenciesJson == null || frequenciesJson.isEmpty()) {
            return java.util.List.of("DAILY", "WEEKLY"); // Default for free plans
        }
        
        // Remove brackets and quotes, split by comma
        String cleaned = frequenciesJson.replaceAll("[\\[\\]\"]", "");
        return java.util.List.of(cleaned.split(","));
    }
    
    // Inner classes for comprehensive response
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SubscriptionDetails {
        private SubscriptionInfo subscription;
        private PlanLimits planLimits;
        private UserPermissions permissions;
        private CurrentUsage currentUsage;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class SubscriptionInfo {
        private Long id;
        private String planName;
        private String displayName;
        private String planType;
        private String status;
        private String billingCycle;
        private java.math.BigDecimal amount;
        private String currency;
        private java.time.LocalDateTime currentPeriodStart;
        private java.time.LocalDateTime currentPeriodEnd;
        private java.time.LocalDateTime trialEndDate;
        private Boolean isActive;
        private Boolean isTrial;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class PlanLimits {
        private Integer dailySearches;
        private Integer monthlySearches;
        private Integer maxMonitoringItems;
        private Integer maxAlertsPerDay;
        private Integer dailyExports;
        private Integer monthlyExports;
        private Boolean hasApiAccess;
        private Boolean hasRealTimeMonitoring;
        private Boolean hasEmailAlerts;
        private Boolean hasInAppAlerts;
        private Boolean hasWebhookAlerts;
        private Boolean hasPrioritySupport;
        private Boolean hasCustomIntegrations;
        private Boolean hasAdvancedAnalytics;
        private java.util.List<String> allowedFrequencies;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class UserPermissions {
        private Boolean canCreateMonitoringItem;
        private Boolean canPerformSearch;
        private Boolean hasApiAccess;
        private Boolean hasRealTimeMonitoring;
        private Boolean hasPrioritySupport;
        private Boolean hasAdvancedAnalytics;
    }
    
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class CurrentUsage {
        private Integer monitoringItems;
        private Integer todaySearches;
    }
}
