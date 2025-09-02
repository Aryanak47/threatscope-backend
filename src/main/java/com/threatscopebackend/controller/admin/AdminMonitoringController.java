package com.threatscopebackend.controller.admin;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.entity.postgresql.MonitoringConfiguration;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.repository.postgresql.PlanRepository;
import com.threatscopebackend.service.admin.MonitoringConfigurationService;
import com.threatscopebackend.service.subscription.SubscriptionService;
import com.threatscopebackend.scheduler.OptimizedMonitoringScheduler;
import com.threatscopebackend.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin management endpoints")
@PreAuthorize("hasRole('ADMIN')")
public class AdminMonitoringController {
    
    private final MonitoringConfigurationService configService;
    private final SubscriptionService subscriptionService;
    private final PlanRepository planRepository;
    private final OptimizedMonitoringScheduler optimizedMonitoringScheduler;
    
    // =============== MONITORING CONFIGURATION MANAGEMENT ===============
    
    @Operation(summary = "Get all monitoring configurations")
    @GetMapping("/monitoring/configurations")
    public ResponseEntity<ApiResponse<List<MonitoringConfiguration>>> getAllConfigurations() {
        List<MonitoringConfiguration> configs = configService.getAdminConfigurableSettings();
        return ResponseEntity.ok(ApiResponse.success("Configurations retrieved successfully", configs));
    }
    
    @Operation(summary = "Get configurations by category")
    @GetMapping("/monitoring/configurations/category/{category}")
    public ResponseEntity<ApiResponse<List<MonitoringConfiguration>>> getConfigurationsByCategory(
            @PathVariable String category) {
        List<MonitoringConfiguration> configs = configService.getConfigurationsByCategory(category);
        return ResponseEntity.ok(ApiResponse.success("Configurations retrieved successfully", configs));
    }
    
    @Operation(summary = "Get configurations grouped by category")
    @GetMapping("/monitoring/configurations/grouped")
    public ResponseEntity<ApiResponse<Map<String, List<MonitoringConfiguration>>>> getConfigurationsGrouped() {
        Map<String, List<MonitoringConfiguration>> grouped = configService.getConfigurationsGroupedByCategory();
        return ResponseEntity.ok(ApiResponse.success("Configurations retrieved successfully", grouped));
    }
    
    @Operation(summary = "Update monitoring configuration")
    @PutMapping("/monitoring/configurations/{key}")
    public ResponseEntity<ApiResponse<MonitoringConfiguration>> updateConfiguration(
            @CurrentUser User admin,
            @PathVariable String key,
            @RequestParam String value) {
        
        MonitoringConfiguration updated = configService.updateConfiguration(key, value, admin.getId());
        
        // Reload scheduling if monitoring configuration changed
        if (key.startsWith("monitoring.")) {
            optimizedMonitoringScheduler.reloadOptimizedSchedulingConfiguration();
        }
        
        return ResponseEntity.ok(ApiResponse.success("Configuration updated successfully", updated));
    }
    
    @Operation(summary = "Create new monitoring configuration")
    @PostMapping("/monitoring/configurations")
    public ResponseEntity<ApiResponse<MonitoringConfiguration>> createConfiguration(
            @CurrentUser User admin,
            @Valid @RequestBody MonitoringConfiguration config) {
        
        MonitoringConfiguration created = configService.createConfiguration(config, admin.getId());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Configuration created successfully", created));
    }
    
    @Operation(summary = "Delete monitoring configuration")
    @DeleteMapping("/monitoring/configurations/{key}")
    public ResponseEntity<ApiResponse<Void>> deleteConfiguration(
            @CurrentUser User admin,
            @PathVariable String key) {
        
        configService.deleteConfiguration(key, admin.getId());
        return ResponseEntity.ok(ApiResponse.success("Configuration deleted successfully", null));
    }
    
    @Operation(summary = "Reset configuration to default")
    @PutMapping("/monitoring/configurations/{key}/reset")
    public ResponseEntity<ApiResponse<MonitoringConfiguration>> resetConfigurationToDefault(
            @CurrentUser User admin,
            @PathVariable String key) {
        
        MonitoringConfiguration reset = configService.resetToDefault(key, admin.getId());
        return ResponseEntity.ok(ApiResponse.success("Configuration reset to default", reset));
    }
    
    @Operation(summary = "Bulk update configurations")
    @PutMapping("/monitoring/configurations/bulk")
    public ResponseEntity<ApiResponse<String>> bulkUpdateConfigurations(
            @CurrentUser User admin,
            @RequestBody Map<String, String> updates) {
        
        configService.bulkUpdateConfigurations(updates, admin.getId());
        
        // Reload scheduling if any monitoring configuration changed
        boolean hasMonitoringConfig = updates.keySet().stream()
                .anyMatch(key -> key.startsWith("monitoring."));
        
        if (hasMonitoringConfig) {
            optimizedMonitoringScheduler.reloadOptimizedSchedulingConfiguration();
        }
        
        return ResponseEntity.ok(ApiResponse.success(
                "Bulk configuration update completed for " + updates.size() + " items", null));
    }
    
    @Operation(summary = "Get current monitoring intervals")
    @GetMapping("/monitoring/intervals")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getMonitoringIntervals() {
        Map<String, Long> intervals = configService.getMonitoringIntervals();
        return ResponseEntity.ok(ApiResponse.success("Monitoring intervals retrieved", intervals));
    }
    
    @Operation(summary = "Reload monitoring scheduler configuration")
    @PostMapping("/monitoring/reload-scheduler")
    public ResponseEntity<ApiResponse<String>> reloadScheduler(@CurrentUser User admin) {
        optimizedMonitoringScheduler.reloadOptimizedSchedulingConfiguration();
        return ResponseEntity.ok(ApiResponse.success("Optimized monitoring scheduler reloaded successfully", null));
    }
    
    // =============== SUBSCRIPTION PLAN MANAGEMENT ===============
    
    @Operation(summary = "Get all subscription plans")
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<Plan>>> getAllPlans() {
        List<Plan> plans = planRepository.findAll(Sort.by("sortOrder"));
        return ResponseEntity.ok(ApiResponse.success("Plans retrieved successfully", plans));
    }
    
    @Operation(summary = "Get plan by ID")
    @GetMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<Plan>> getPlan(@PathVariable Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));
        return ResponseEntity.ok(ApiResponse.success("Plan retrieved successfully", plan));
    }
    
    @Operation(summary = "Create new subscription plan")
    @PostMapping("/plans")
    public ResponseEntity<ApiResponse<Plan>> createPlan(@Valid @RequestBody Plan plan) {
        Plan created = planRepository.save(plan);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Plan created successfully", created));
    }
    
    @Operation(summary = "Update subscription plan")
    @PutMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<Plan>> updatePlan(
            @PathVariable Long planId,
            @Valid @RequestBody Plan planUpdate) {
        
        Plan existing = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));
        
        // Update fields
        existing.setDisplayName(planUpdate.getDisplayName());
        existing.setDescription(planUpdate.getDescription());
        existing.setPrice(planUpdate.getPrice());
        existing.setCurrency(planUpdate.getCurrency());
        existing.setBillingCycle(planUpdate.getBillingCycle());
        existing.setDailySearches(planUpdate.getDailySearches());
        existing.setMonthlySearches(planUpdate.getMonthlySearches());
        existing.setMaxMonitoringItems(planUpdate.getMaxMonitoringItems());
        existing.setMonitoringFrequencies(planUpdate.getMonitoringFrequencies());
        existing.setMaxAlertsPerDay(planUpdate.getMaxAlertsPerDay());
        existing.setAlertRetentionDays(planUpdate.getAlertRetentionDays());
        existing.setDailyExports(planUpdate.getDailyExports());
        existing.setMonthlyExports(planUpdate.getMonthlyExports());
        existing.setApiAccess(planUpdate.getApiAccess());
        existing.setRealTimeMonitoring(planUpdate.getRealTimeMonitoring());
        existing.setEmailAlerts(planUpdate.getEmailAlerts());
        existing.setInAppAlerts(planUpdate.getInAppAlerts());
        existing.setPrioritySupport(planUpdate.getPrioritySupport());
        existing.setCustomIntegrations(planUpdate.getCustomIntegrations());
        existing.setAdvancedAnalytics(planUpdate.getAdvancedAnalytics());
        existing.setIsActive(planUpdate.getIsActive());
        existing.setIsPublic(planUpdate.getIsPublic());
        existing.setSortOrder(planUpdate.getSortOrder());
        
        Plan updated = planRepository.save(existing);
        return ResponseEntity.ok(ApiResponse.success("Plan updated successfully", updated));
    }
    
    @Operation(summary = "Delete subscription plan")
    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<Void>> deletePlan(@PathVariable Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));
        
        // Don't allow deletion of plans with active subscriptions
        // You might want to add this check
        
        planRepository.delete(plan);
        return ResponseEntity.ok(ApiResponse.success("Plan deleted successfully", null));
    }
    
    @Operation(summary = "Toggle plan active status")
    @PutMapping("/plans/{planId}/toggle-active")
    public ResponseEntity<ApiResponse<Plan>> togglePlanActive(@PathVariable Long planId) {
        Plan plan = planRepository.findById(planId)
                .orElseThrow(() -> new RuntimeException("Plan not found"));
        
        plan.setIsActive(!plan.getIsActive());
        Plan updated = planRepository.save(plan);
        
        return ResponseEntity.ok(ApiResponse.success("Plan status updated", updated));
    }
    
    // =============== SUBSCRIPTION STATISTICS ===============
    
    @Operation(summary = "Get subscription statistics")
    @GetMapping("/subscriptions/statistics")
    public ResponseEntity<ApiResponse<List<Object[]>>> getSubscriptionStatistics() {
        List<Object[]> stats = subscriptionService.getSubscriptionStatistics();
        return ResponseEntity.ok(ApiResponse.success("Subscription statistics retrieved", stats));
    }
    
    @Operation(summary = "Get available plans for public view")
    @GetMapping("/plans/public")
    public ResponseEntity<ApiResponse<List<Plan>>> getPublicPlans() {
        List<Plan> plans = subscriptionService.getAvailablePlans();
        return ResponseEntity.ok(ApiResponse.success("Public plans retrieved", plans));
    }
}
