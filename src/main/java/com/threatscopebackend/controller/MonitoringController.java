package com.threatscopebackend.controller;

import com.threatscopebackend.dto.monitoring.*;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.service.monitoring.MonitoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/monitoring")
@RequiredArgsConstructor
@Tag(name = "Monitoring", description = "Monitoring items management")
@PreAuthorize("hasRole('USER')")
public class MonitoringController {
    
    private final MonitoringService monitoringService;
    
    @Operation(summary = "Create monitoring item")
    @PostMapping("/items")
    public ResponseEntity<ApiResponse<MonitoringItemResponse>> createMonitoringItem(
            @CurrentUser User user,
            @Valid @RequestBody CreateMonitoringItemRequest request) {
        
        MonitoringItemResponse response = monitoringService.createMonitoringItem(user, request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Monitoring item created successfully", response));
    }
    
    @Operation(summary = "Get monitoring items")
    @GetMapping("/items")
    public ResponseEntity<ApiResponse<Page<MonitoringItemResponse>>> getMonitoringItems(
            @CurrentUser User user,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {
        
        Page<MonitoringItemResponse> response = monitoringService.getMonitoringItems(user, page, size, sortBy, sortDir);
        return ResponseEntity.ok(ApiResponse.success("Monitoring items retrieved successfully", response));
    }
    
    @Operation(summary = "Get monitoring item by ID")
    @GetMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<MonitoringItemResponse>> getMonitoringItem(
            @CurrentUser User user,
            @PathVariable Long itemId) {
        
        MonitoringItemResponse response = monitoringService.getMonitoringItem(user, itemId);
        return ResponseEntity.ok(ApiResponse.success("Monitoring item retrieved successfully", response));
    }
    
    @Operation(summary = "Update monitoring item")
    @PutMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<MonitoringItemResponse>> updateMonitoringItem(
            @CurrentUser User user,
            @PathVariable Long itemId,
            @Valid @RequestBody UpdateMonitoringItemRequest request) {
        
        MonitoringItemResponse response = monitoringService.updateMonitoringItem(user, itemId, request);
        return ResponseEntity.ok(ApiResponse.success("Monitoring item updated successfully", response));
    }
    
    @Operation(summary = "Delete monitoring item")
    @DeleteMapping("/items/{itemId}")
    public ResponseEntity<ApiResponse<Void>> deleteMonitoringItem(
            @CurrentUser User user,
            @PathVariable Long itemId) {
        
        monitoringService.deleteMonitoringItem(user, itemId);
        return ResponseEntity.ok(ApiResponse.success("Monitoring item deleted successfully", null));
    }
    
    @Operation(summary = "Search monitoring items")
    @GetMapping("/items/search")
    public ResponseEntity<ApiResponse<Page<MonitoringItemResponse>>> searchMonitoringItems(
            @CurrentUser User user,
            @Parameter(description = "Search term") @RequestParam String query,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        
        Page<MonitoringItemResponse> response = monitoringService.searchMonitoringItems(user, query, page, size);
        return ResponseEntity.ok(ApiResponse.success("Search completed successfully", response));
    }
    
    @Operation(summary = "Get monitoring dashboard")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<MonitoringDashboardResponse>> getDashboard(@CurrentUser User user) {
        MonitoringDashboardResponse response = monitoringService.getDashboard(user);
        return ResponseEntity.ok(ApiResponse.success("Dashboard data retrieved successfully", response));
    }
    
    @Operation(summary = "Get monitoring statistics")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatistics(@CurrentUser User user) {
        Map<String, Object> response = monitoringService.getMonitoringStatistics(user);
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved successfully", response));
    }
}
