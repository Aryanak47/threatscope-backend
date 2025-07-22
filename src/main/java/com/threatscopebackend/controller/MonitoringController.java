package com.threatscopebackend.controller;

import com.threatscopebackend.dto.monitoring.*;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.repository.postgresql.BreachAlertRepository;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.monitoring.MonitoringService;
import com.threatscopebackend.service.monitoring.BreachDetectionService;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.enums.CommonEnums;
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
    private final BreachDetectionService breachDetectionService;
    private final UserRepository userRepository;
    private final BreachAlertRepository breachAlertRepository;
    
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
    
    @Operation(summary = "Get breach alerts")
    @GetMapping("/alerts")
    public ResponseEntity<ApiResponse<Page<BreachAlertResponse>>> getBreachAlerts(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by status") @RequestParam(required = false) CommonEnums.AlertStatus status,
            @Parameter(description = "Filter by severity") @RequestParam(required = false) CommonEnums.AlertSeverity severity) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        org.springframework.data.domain.Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            org.springframework.data.domain.Sort.by(sortBy).descending() : 
            org.springframework.data.domain.Sort.by(sortBy).ascending();
        
        org.springframework.data.domain.Pageable pageable = 
            org.springframework.data.domain.PageRequest.of(page, size, sort);
        
        Page<BreachAlert> alertsPage;
        if (status != null && severity != null) {
            alertsPage = breachAlertRepository.findByUserAndStatusAndSeverity(user, status, severity, pageable);
        } else if (status != null) {
            alertsPage = breachAlertRepository.findByUserAndStatus(user, status, pageable);
        } else if (severity != null) {
            alertsPage = breachAlertRepository.findByUserAndSeverity(user, severity, pageable);
        } else {
            alertsPage = breachAlertRepository.findByUser(user, pageable);
        }
        
        Page<BreachAlertResponse> responsePage = alertsPage.map(BreachAlertResponse::fromEntity);
        return ResponseEntity.ok(ApiResponse.success("Breach alerts retrieved successfully", responsePage));
    }
    
    @Operation(summary = "Get unread alerts count")
    @GetMapping("/alerts/unread-count")
    public ResponseEntity<ApiResponse<Long>> getUnreadAlertsCount(@CurrentUser UserPrincipal userPrincipal) {
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        long unreadCount = breachAlertRepository.countByUserAndStatus(user, CommonEnums.AlertStatus.NEW);
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved successfully", unreadCount));
    }
    
    @Operation(summary = "Get specific breach alert")
    @GetMapping("/alerts/{alertId}")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> getBreachAlert(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        BreachAlert alert = breachAlertRepository.findByIdAndUserId(alertId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Breach Alert", "id", alertId));
        
        BreachAlertResponse response = BreachAlertResponse.fromEntity(alert);
        return ResponseEntity.ok(ApiResponse.success("Breach alert retrieved successfully", response));
    }
    
    @Operation(summary = "Mark alert as viewed")
    @PostMapping("/alerts/{alertId}/view")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> markAlertAsViewed(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        BreachAlert alert = breachAlertRepository.findByIdAndUserId(alertId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Breach Alert", "id", alertId));
        
        if (alert.getStatus() == CommonEnums.AlertStatus.NEW) {
            alert.setStatus(CommonEnums.AlertStatus.VIEWED);
            alert.setViewedAt(java.time.LocalDateTime.now());
            breachAlertRepository.save(alert);
        }
        
        BreachAlertResponse response = BreachAlertResponse.fromEntity(alert);
        return ResponseEntity.ok(ApiResponse.success("Alert marked as viewed", response));
    }

//    TODO:REMOVE
    @Operation(summary = "Manually trigger monitoring check for testing")
    @PostMapping("/trigger-check")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerManualCheck(
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.info("🔄 Manual monitoring check triggered by user: {}", userPrincipal.getId());
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        // Get user's active monitoring items
        var items = monitoringService.getMonitoringItems(user, 0, 100, "createdAt", "desc");
        
        int totalItems = items.getContent().size();
        int checkedItems = 0;
        int alertsGenerated = 0;
        
        log.info("📋 Found {} monitoring items to check", totalItems);
        
        // Manually trigger checks for all user's monitoring items
        for (var itemResponse : items.getContent()) {
            try {
                // Get the actual monitoring item entity (itemResponse.getId() is already a Long)
                Long itemId = itemResponse.getId();
                
                log.info("🔍 Checking monitoring item: {} ({})", 
                        itemResponse.getMonitorName(), itemResponse.getTargetValue());
                
                // We need to get the actual MonitoringItem entity to pass to breach detection
                // For now, let's create a simple method to get the entity
                var actualEntity = getMonitoringItemEntity(user, itemId);
                
                if (actualEntity != null) {
                    log.info("🔬 Running REAL breach detection for: {}", itemResponse.getTargetValue());
                    
                    // Call the actual breach detection service
                    breachDetectionService.checkMonitoringItem(actualEntity);
                    
                    // Check if any alerts were generated by looking at the updated entity
                    // (The breach detection service will create alerts if breaches are found)
                    log.info("✅ Completed breach detection for: {}", itemResponse.getTargetValue());
                } else {
                    log.warn("⚠️ Could not find entity for monitoring item: {}", itemId);
                }
                
                checkedItems++;
                
            } catch (Exception e) {
                log.error("❌ Error checking monitoring item {}: {}", itemResponse.getId(), e.getMessage());
            }
        }
        
        Map<String, Object> result = Map.of(
            "totalItems", totalItems,
            "checkedItems", checkedItems,
            "alertsGenerated", alertsGenerated,
            "message", "Manual monitoring check completed"
        );
        
        log.info("✅ Manual check completed: {} items checked, {} alerts generated", 
                checkedItems, alertsGenerated);
        
        return ResponseEntity.ok(ApiResponse.success("Manual monitoring check completed", result));
    }
    
    /**
     * Helper method to get the actual MonitoringItem entity
     * This is needed because the breach detection service requires the entity, not the DTO
     */
    private com.threatscopebackend.entity.postgresql.MonitoringItem getMonitoringItemEntity(User user, Long itemId) {
        try {
            // Use the monitoring service to find the actual entity
            return monitoringService.getMonitoringItemEntity(user, itemId);
        } catch (Exception e) {
            log.error("❌ Failed to get monitoring item entity {}: {}", itemId, e.getMessage());
            return null;
        }
    }
}
