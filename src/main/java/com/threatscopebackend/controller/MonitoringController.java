package com.threatscopebackend.controller;

import com.threatscopebackend.dto.monitoring.*;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.monitoring.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/monitoring")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Monitoring", description = "Monitoring items management")
@PreAuthorize("hasRole('USER')")
public class MonitoringController {
    
    private final MonitoringService monitoringService;
    private final UserRepository userRepository;
    
    @Operation(summary = "Create monitoring item")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<MonitoringItemResponse>> createMonitoringItem(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody CreateMonitoringItemRequest request) {
        
        log.info("🔄 Creating monitoring item for user ID: {} with type: {}", 
                userPrincipal.getId(), request.getMonitorType());
        
        // Debug: Log the full request
        log.info("🔍 Request details: monitorType={}, targetValue={}, monitorName={}, frequency={}, emailAlerts={}, inAppAlerts={}",
                request.getMonitorType(), request.getTargetValue(), request.getMonitorName(), 
                request.getFrequency(), request.getEmailAlerts(), request.getInAppAlerts());
        
        // Get the full User entity from the repository
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        log.info("🔍 Found user: {} with plan: {}", 
                user.getEmail(), 
                user.getSubscription() != null ? user.getSubscription().getPlanType() : "No subscription");
        
        MonitoringItemResponse response = monitoringService.createMonitoringItem(user, request);
        
        log.info("✅ Successfully created monitoring item with ID: {}", response.getId());
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Monitoring item created successfully", response));
    }
    
    @Operation(summary = "Get monitoring items")
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<Page<MonitoringItemResponse>>> getMonitoringItems(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        Page<MonitoringItemResponse> response = monitoringService.getMonitoringItems(user, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Monitoring items retrieved successfully", response));
    }
    
    @Operation(summary = "Get monitoring item by ID")
    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<MonitoringItemResponse>> getMonitoringItem(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long itemId) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        MonitoringItemResponse response = monitoringService.getMonitoringItem(user, itemId);
        return ResponseEntity.ok(ApiResponse.success("Monitoring item retrieved successfully", response));
    }
    
    @Operation(summary = "Update monitoring item")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<MonitoringItemResponse>> updateMonitoringItem(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateMonitoringItemRequest request) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        MonitoringItemResponse response = monitoringService.updateMonitoringItem(user, itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Monitoring item updated successfully", response));
    }
    
    @Operation(summary = "Delete monitoring item")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteMonitoringItem(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long itemId) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        monitoringService.deleteMonitoringItem(user, itemId);
        return ResponseEntity.ok(ApiResponse.success("Monitoring item deleted successfully", null));
    }
    
    @Operation(summary = "Search monitoring items")
    @GetMapping("/items/search")
    public ResponseEntity<ApiResponse<Page<MonitoringItemResponse>>> searchMonitoringItems(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "Search term") @RequestParam String query,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        Page<MonitoringItemResponse> response = monitoringService.searchMonitoringItems(user, query, page, size);
        return ResponseEntity.ok(ApiResponse.success("Search completed successfully", response));
    }
    
    @Operation(summary = "Get monitoring dashboard")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<MonitoringDashboardResponse>> getDashboard(
            @CurrentUser UserPrincipal userPrincipal) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        MonitoringDashboardResponse response = monitoringService.getDashboard(user);
        return ResponseEntity.ok(ApiResponse.success("Dashboard data retrieved successfully", response));
    }
    
    @Operation(summary = "Get monitoring statistics")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics(
            @CurrentUser UserPrincipal userPrincipal) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        Map<String, Object> response = monitoringService.getMonitoringStatistics(user);
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", response));
    }
}
