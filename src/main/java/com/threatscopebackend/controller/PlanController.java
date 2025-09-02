package com.threatscopebackend.controller;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.service.subscription.SubscriptionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/plans")
@RequiredArgsConstructor
@Tag(name = "Plans", description = "Subscription plans management")
public class PlanController {
    
    private final SubscriptionService subscriptionService;
    
    @Operation(summary = "Get all active plans")
    @GetMapping
    public ResponseEntity<ApiResponse<List<Plan>>> getAllPlans() {
        List<Plan> plans = subscriptionService.getAllActivePlans();
        return ResponseEntity.ok(ApiResponse.success("Plans retrieved successfully", plans));
    }
    
    @Operation(summary = "Get plan by ID")
    @GetMapping("/{planId}")
    public ResponseEntity<ApiResponse<Plan>> getPlanById(@PathVariable Long planId) {
        Plan plan = subscriptionService.getPlanById(planId);
        return ResponseEntity.ok(ApiResponse.success("Plan retrieved successfully", plan));
    }
    
    @Operation(summary = "Get plan features comparison")
    @GetMapping("/comparison")
    public ResponseEntity<ApiResponse<Object>> getPlansComparison() {
        Object comparison = subscriptionService.getPlansComparison();
        return ResponseEntity.ok(ApiResponse.success("Plans comparison retrieved successfully", comparison));
    }
}
