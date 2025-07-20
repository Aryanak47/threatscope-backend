package com.threatscopebackend.controller;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.entity.postgresql.Subscription;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.service.subscription.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.Builder;
import lombok.Data;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscription")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "User subscription management")
@PreAuthorize("hasRole('USER')")
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    
    @Operation(summary = "Get current user subscription")
    @GetMapping("/current")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> getCurrentSubscription(@CurrentUser User user) {
        Subscription subscription = subscriptionService.getUserSubscription(user);
        SubscriptionResponse response = SubscriptionResponse.fromEntity(subscription);
        return ResponseEntity.ok(ApiResponse.success("Subscription retrieved successfully", response));
    }
    
    @Operation(summary = "Get available subscription plans")
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<PlanResponse>>> getAvailablePlans() {
        List<Plan> plans = subscriptionService.getAvailablePlans();
        List<PlanResponse> responses = plans.stream()
                .map(PlanResponse::fromEntity)
                .toList();
        return ResponseEntity.ok(ApiResponse.success("Plans retrieved successfully", responses));
    }
    
    @Operation(summary = "Get user's plan limits and usage")
    @GetMapping("/limits")
    public ResponseEntity<ApiResponse<PlanLimitsResponse>> getPlanLimits(@CurrentUser User user) {
        Plan plan = subscriptionService.getUserPlan(user);
        Subscription subscription = subscriptionService.getUserSubscription(user);
        
        PlanLimitsResponse response = PlanLimitsResponse.builder()
            .planName(plan.getName())
            .displayName(plan.getDisplayName())
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
            .hasPrioritySupport(plan.getPrioritySupport())
            .hasCustomIntegrations(plan.getCustomIntegrations())
            .hasAdvancedAnalytics(plan.getAdvancedAnalytics())
            .allowedFrequencies(parseAllowedFrequencies(plan.getMonitoringFrequencies()))
            .build();
        
        return ResponseEntity.ok(ApiResponse.success("Plan limits retrieved successfully", response));
    }
    
    @Operation(summary = "Check if user can perform specific action")
    @GetMapping("/can-perform")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> canPerformActions(
            @CurrentUser User user,
            @RequestParam(required = false, defaultValue = "0") int currentMonitoringItems,
            @RequestParam(required = false, defaultValue = "0") int todaySearches) {
        
        Map<String, Boolean> permissions = Map.of(
            "canCreateMonitoringItem", subscriptionService.canCreateMonitoringItem(user, currentMonitoringItems),
            "canPerformSearch", subscriptionService.canPerformSearch(user, todaySearches),
            "hasApiAccess", subscriptionService.hasFeature(user, "api_access"),
            "hasRealTimeMonitoring", subscriptionService.hasFeature(user, "real_time_monitoring"),
            "hasPrioritySupport", subscriptionService.hasFeature(user, "priority_support"),
            "hasAdvancedAnalytics", subscriptionService.hasFeature(user, "advanced_analytics")
        );
        
        return ResponseEntity.ok(ApiResponse.success("Permissions retrieved successfully", permissions));
    }
    
    @Operation(summary = "Upgrade subscription")
    @PostMapping("/upgrade")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> upgradeSubscription(
            @CurrentUser User user,
            @RequestParam String planName) {
        
        Subscription upgraded = subscriptionService.upgradeSubscription(user, planName);
        SubscriptionResponse response = SubscriptionResponse.fromEntity(upgraded);
        
        return ResponseEntity.ok(ApiResponse.success("Subscription upgraded successfully", response));
    }
    
    private List<String> parseAllowedFrequencies(String frequenciesJson) {
        // Parse the JSON string to extract allowed frequencies
        // This is a simple implementation - you might want to use Jackson for proper JSON parsing
        if (frequenciesJson == null || frequenciesJson.isEmpty()) {
            return List.of("DAILY", "WEEKLY"); // Default for free plans
        }
        
        // Remove brackets and split by comma
        String cleaned = frequenciesJson.replaceAll("[\\[\\]\"]", "");
        return List.of(cleaned.split(","));
    }
    
    // DTOs for responses
    @Data
    public static class SubscriptionResponse {
        private Long id;
        private String planName;
        private String displayName;
        private String status;
        private String billingCycle;
        private java.math.BigDecimal amount;
        private String currency;
        private java.time.LocalDateTime currentPeriodStart;
        private java.time.LocalDateTime currentPeriodEnd;
        private java.time.LocalDateTime trialEndDate;
        private Boolean isActive;
        private Boolean isTrial;
        
        public static SubscriptionResponse fromEntity(Subscription subscription) {
            SubscriptionResponse response = new SubscriptionResponse();
            response.id = subscription.getId();
            response.planName = subscription.getPlan().getName();
            response.displayName = subscription.getPlan().getDisplayName();
            response.status = subscription.getStatus().toString();
            response.billingCycle = subscription.getBillingCycle() != null ? 
                subscription.getBillingCycle().toString() : null;
            response.amount = subscription.getAmount();
            response.currency = subscription.getCurrency();
            response.currentPeriodStart = subscription.getCurrentPeriodStart();
            response.currentPeriodEnd = subscription.getCurrentPeriodEnd();
            response.trialEndDate = subscription.getTrialEndDate();
            response.isActive = subscription.isActive();
            response.isTrial = subscription.isTrial();
            return response;
        }
    }
    
    @Data
    public static class PlanResponse {
        private Long id;
        private String name;
        private String displayName;
        private String description;
        private java.math.BigDecimal price;
        private String currency;
        private String billingCycle;
        private Integer dailySearches;
        private Integer monthlySearches;
        private Integer maxMonitoringItems;
        private Integer maxAlertsPerDay;
        private Integer dailyExports;
        private Integer monthlyExports;
        private Boolean apiAccess;
        private Boolean realTimeMonitoring;
        private Boolean emailAlerts;
        private Boolean inAppAlerts;
        private Boolean prioritySupport;
        private Boolean customIntegrations;
        private Boolean advancedAnalytics;
        private List<String> allowedFrequencies;
        
        public static PlanResponse fromEntity(Plan plan) {
            PlanResponse response = new PlanResponse();
            response.id = plan.getId();
            response.name = plan.getName();
            response.displayName = plan.getDisplayName();
            response.description = plan.getDescription();
            response.price = plan.getPrice();
            response.currency = plan.getCurrency();
            response.billingCycle = plan.getBillingCycle().toString();
            response.dailySearches = plan.getDailySearches();
            response.monthlySearches = plan.getMonthlySearches();
            response.maxMonitoringItems = plan.getMaxMonitoringItems();
            response.maxAlertsPerDay = plan.getMaxAlertsPerDay();
            response.dailyExports = plan.getDailyExports();
            response.monthlyExports = plan.getMonthlyExports();
            response.apiAccess = plan.getApiAccess();
            response.realTimeMonitoring = plan.getRealTimeMonitoring();
            response.emailAlerts = plan.getEmailAlerts();
            response.inAppAlerts = plan.getInAppAlerts();
            response.prioritySupport = plan.getPrioritySupport();
            response.customIntegrations = plan.getCustomIntegrations();
            response.advancedAnalytics = plan.getAdvancedAnalytics();
            
            // Parse allowed frequencies
            if (plan.getMonitoringFrequencies() != null && !plan.getMonitoringFrequencies().isEmpty()) {
                String cleaned = plan.getMonitoringFrequencies().replaceAll("[\\[\\]\"]", "");
                response.allowedFrequencies = List.of(cleaned.split(","));
            } else {
                response.allowedFrequencies = List.of("DAILY", "WEEKLY");
            }
            
            return response;
        }
    }
    
    @Data
    @Builder
    public static class PlanLimitsResponse {
        private String planName;
        private String displayName;
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
        private Boolean hasPrioritySupport;
        private Boolean hasCustomIntegrations;
        private Boolean hasAdvancedAnalytics;
        private List<String> allowedFrequencies;
    }
}
