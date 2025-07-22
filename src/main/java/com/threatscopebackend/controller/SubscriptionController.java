package com.threatscopebackend.controller;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.entity.postgresql.Subscription;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.subscription.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/subscription")
@RequiredArgsConstructor
@Tag(name = "Subscription", description = "User subscription management")
@PreAuthorize("hasRole('USER')")
@Slf4j
public class SubscriptionController {
    
    private final SubscriptionService subscriptionService;
    
    @PostConstruct
    public void init() {
        log.info("🚀 SubscriptionController initialized successfully!");
    }
    
    @Operation(summary = "Get comprehensive subscription details including plan, limits, permissions, and usage")
    @GetMapping("/details")
    public ResponseEntity<ApiResponse<SubscriptionService.SubscriptionDetails>> getSubscriptionDetails(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam(required = false, defaultValue = "0") int currentMonitoringItems,
            @RequestParam(required = false, defaultValue = "0") int todaySearches) {
        
        log.info("📊 Getting subscription details - userPrincipal: {}, items: {}, searches: {}", 
                userPrincipal != null ? userPrincipal.getId() : "NULL", currentMonitoringItems, todaySearches);
        
        if (userPrincipal == null) {
            log.error("❌ @CurrentUser resolved to null - authentication issue!");
            return ResponseEntity.status(401)
                .body(ApiResponse.unauthorized("User not authenticated"));
        }
        
        // Get the User entity from UserPrincipal
        User user = userPrincipal.getUser();
        if (user == null) {
            log.error("❌ UserPrincipal.getUser() returned null for user ID: {}", userPrincipal.getId());
            return ResponseEntity.status(401)
                .body(ApiResponse.unauthorized("User details not available"));
        }
        
        try {
            SubscriptionService.SubscriptionDetails details = subscriptionService.getSubscriptionDetails(
                user, currentMonitoringItems, todaySearches);
            
            log.info("✅ Subscription details retrieved successfully for user: {}", user.getId());
            
            return ResponseEntity.ok(ApiResponse.success("Subscription details retrieved successfully", details));
        } catch (Exception e) {
            log.error("❌ Failed to get subscription details for user: {}", user.getId(), e);
            return ResponseEntity.status(500)
                .body(ApiResponse.serverError("Failed to retrieve subscription details: " + e.getMessage()));
        }
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
    
    @Operation(summary = "Upgrade subscription")
    @PostMapping("/upgrade")
    public ResponseEntity<ApiResponse<SubscriptionResponse>> upgradeSubscription(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam String planName) {
        
        User user = userPrincipal.getUser();
        if (user == null) {
            return ResponseEntity.status(401)
                .body(ApiResponse.unauthorized("User details not available"));
        }
        
        Subscription upgraded = subscriptionService.upgradeSubscription(user, planName);
        SubscriptionResponse response = SubscriptionResponse.fromEntity(upgraded);
        
        return ResponseEntity.ok(ApiResponse.success("Subscription upgraded successfully", response));
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
    public static class





    PlanResponse {
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
}
