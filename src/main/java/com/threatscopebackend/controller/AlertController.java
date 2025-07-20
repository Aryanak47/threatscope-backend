package com.threatscopebackend.controller;

import com.threatscopebackend.dto.monitoring.BreachAlertResponse;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.service.monitoring.AlertService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Tag(name = "Alerts", description = "Breach alerts management")
@PreAuthorize("hasRole('USER')")
public class AlertController {
    
    private final AlertService alertService;
    
    @Operation(summary = "Get alerts")
    @GetMapping
    public ResponseEntity<ApiResponse<Page<BreachAlertResponse>>> getAlerts(
            @CurrentUser User user,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by status") @RequestParam(required = false) CommonEnums.AlertStatus status,
            @Parameter(description = "Filter by severity") @RequestParam(required = false) CommonEnums.AlertSeverity severity) {
        
        Page<BreachAlertResponse> response = alertService.getAlerts(user, page, size, sortBy, sortDir, status, severity);
        return ResponseEntity.ok(ApiResponse.success("Alerts retrieved successfully", response));
    }
    
    @Operation(summary = "Get alert by ID")
    @GetMapping("/{alertId}")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> getAlert(
            @CurrentUser User user,
            @PathVariable Long alertId) {
        
        BreachAlertResponse response = alertService.getAlert(user, alertId);
        return ResponseEntity.ok(ApiResponse.success("Alert retrieved successfully", response));
    }
    
    @Operation(summary = "Mark alert as read")
    @PutMapping("/{alertId}/read")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> markAsRead(
            @CurrentUser User user,
            @PathVariable Long alertId) {
        
        BreachAlertResponse response = alertService.markAsRead(user, alertId);
        return ResponseEntity.ok(ApiResponse.success("Alert marked as read", response));
    }
    
    @Operation(summary = "Mark alert as archived")
    @PutMapping("/{alertId}/archive")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> markAsArchived(
            @CurrentUser User user,
            @PathVariable Long alertId) {
        
        BreachAlertResponse response = alertService.markAsArchived(user, alertId);
        return ResponseEntity.ok(ApiResponse.success("Alert archived", response));
    }
    
    @Operation(summary = "Mark alert as false positive")
    @PutMapping("/{alertId}/false-positive")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> markAsFalsePositive(
            @CurrentUser User user,
            @PathVariable Long alertId) {
        
        BreachAlertResponse response = alertService.markAsFalsePositive(user, alertId);
        return ResponseEntity.ok(ApiResponse.success("Alert marked as false positive", response));
    }
    
    @Operation(summary = "Mark alert as remediated")
    @PutMapping("/{alertId}/remediate")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> markAsRemediated(
            @CurrentUser User user,
            @PathVariable Long alertId,
            @Parameter(description = "Remediation notes") @RequestParam(required = false) String notes) {
        
        BreachAlertResponse response = alertService.markAsRemediated(user, alertId, notes);
        return ResponseEntity.ok(ApiResponse.success("Alert marked as remediated", response));
    }
    
    @Operation(summary = "Escalate alert")
    @PutMapping("/{alertId}/escalate")
    public ResponseEntity<ApiResponse<BreachAlertResponse>> escalateAlert(
            @CurrentUser User user,
            @PathVariable Long alertId,
            @Parameter(description = "Escalation notes") @RequestParam String notes) {
        
        BreachAlertResponse response = alertService.escalateAlert(user, alertId, notes);
        return ResponseEntity.ok(ApiResponse.success("Alert escalated", response));
    }
    
    @Operation(summary = "Bulk mark alerts as read")
    @PutMapping("/bulk/read")
    public ResponseEntity<ApiResponse<Integer>> bulkMarkAsRead(
            @CurrentUser User user,
            @RequestBody List<Long> alertIds) {
        
        int updated = alertService.bulkMarkAsRead(user, alertIds);
        return ResponseEntity.ok(ApiResponse.success("Alerts marked as read", updated));
    }
    
    @Operation(summary = "Mark all alerts as read")
    @PutMapping("/read-all")
    public ResponseEntity<ApiResponse<Integer>> markAllAsRead(@CurrentUser User user) {
        int updated = alertService.markAllAsRead(user);
        return ResponseEntity.ok(ApiResponse.success("All alerts marked as read", updated));
    }
    
    @Operation(summary = "Get unread alert count")
    @GetMapping("/unread/count")
    public ResponseEntity<ApiResponse<Long>> getUnreadCount(@CurrentUser User user) {
        long count = alertService.getUnreadCount(user);
        return ResponseEntity.ok(ApiResponse.success("Unread count retrieved", count));
    }
    
    @Operation(summary = "Get recent alerts")
    @GetMapping("/recent")
    public ResponseEntity<ApiResponse<List<BreachAlertResponse>>> getRecentAlerts(
            @CurrentUser User user,
            @Parameter(description = "Number of days") @RequestParam(defaultValue = "7") int days) {
        
        List<BreachAlertResponse> response = alertService.getRecentAlerts(user, days);
        return ResponseEntity.ok(ApiResponse.success("Recent alerts retrieved", response));
    }
    
    @Operation(summary = "Get high priority alerts")
    @GetMapping("/high-priority")
    public ResponseEntity<ApiResponse<List<BreachAlertResponse>>> getHighPriorityAlerts(@CurrentUser User user) {
        List<BreachAlertResponse> response = alertService.getHighPriorityAlerts(user);
        return ResponseEntity.ok(ApiResponse.success("High priority alerts retrieved", response));
    }
    
    @Operation(summary = "Search alerts")
    @GetMapping("/search")
    public ResponseEntity<ApiResponse<Page<BreachAlertResponse>>> searchAlerts(
            @CurrentUser User user,
            @Parameter(description = "Search term") @RequestParam String query,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "10") int size) {
        
        Page<BreachAlertResponse> response = alertService.searchAlerts(user, query, page, size);
        return ResponseEntity.ok(ApiResponse.success("Search completed", response));
    }
    
    @Operation(summary = "Get alert statistics")
    @GetMapping("/statistics")
    public ResponseEntity<ApiResponse<Map<String, Long>>> getStatistics(@CurrentUser User user) {
        Map<String, Long> response = alertService.getAlertStatistics(user);
        return ResponseEntity.ok(ApiResponse.success("Statistics retrieved", response));
    }
}
