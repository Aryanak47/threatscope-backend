package com.threatscopebackend.controller;

import com.threatscopebackend.dto.monitoring.AlertActionResponse;
import com.threatscopebackend.dto.monitoring.CreateAlertActionRequest;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.entity.enums.AlertActionType;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.monitoring.AlertActionService;
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

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/alerts")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Alerts", description = "Alert management and actions")
@PreAuthorize("hasRole('USER')")
public class AlertController {
    
    private final AlertActionService alertActionService;
    private final UserRepository userRepository;
    
    @Operation(summary = "Create alert action")
    @PostMapping("/{alertId}/actions")
    public ResponseEntity<ApiResponse<AlertActionResponse>> createAlertAction(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId,
            @Valid @RequestBody CreateAlertActionRequest request) {
        
        log.info("Creating alert action for user {} on alert {}: {}", 
                userPrincipal.getId(), alertId, request.getActionType());
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        AlertActionResponse response = alertActionService.createAlertAction(user, alertId, request);
        
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Alert action created successfully", response));
    }
    
    @Operation(summary = "Get alert actions for specific alert")
    @GetMapping("/{alertId}/actions")
    public ResponseEntity<ApiResponse<List<AlertActionResponse>>> getAlertActions(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        List<AlertActionResponse> actions = alertActionService.getAlertActions(user, alertId);
        
        return ResponseEntity.ok(ApiResponse.success("Alert actions retrieved successfully", actions));
    }
    
    @Operation(summary = "Get all user alert actions")
    @GetMapping("/actions")
    public ResponseEntity<ApiResponse<Page<AlertActionResponse>>> getUserAlertActions(
            @CurrentUser UserPrincipal userPrincipal,
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        Page<AlertActionResponse> actions = alertActionService.getUserAlertActions(user, page, size, sortBy, sortDir);
        
        return ResponseEntity.ok(ApiResponse.success("User alert actions retrieved successfully", actions));
    }
    
    @Operation(summary = "Get pending service requests")
    @GetMapping("/actions/service-requests")
    public ResponseEntity<ApiResponse<List<AlertActionResponse>>> getPendingServiceRequests(
            @CurrentUser UserPrincipal userPrincipal) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        List<AlertActionResponse> requests = alertActionService.getPendingServiceRequests(user);
        
        return ResponseEntity.ok(ApiResponse.success("Pending service requests retrieved successfully", requests));
    }
    
    @Operation(summary = "Get specific alert action")
    @GetMapping("/actions/{actionId}")
    public ResponseEntity<ApiResponse<AlertActionResponse>> getAlertAction(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long actionId) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        AlertActionResponse action = alertActionService.getAlertAction(user, actionId);
        
        return ResponseEntity.ok(ApiResponse.success("Alert action retrieved successfully", action));
    }
    
    @Operation(summary = "Cancel alert action")
    @DeleteMapping("/actions/{actionId}")
    public ResponseEntity<ApiResponse<Void>> cancelAlertAction(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long actionId) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        alertActionService.cancelAlertAction(user, actionId);
        
        return ResponseEntity.ok(ApiResponse.success("Alert action cancelled successfully", null));
    }
    
    @Operation(summary = "Get available action types")
    @GetMapping("/action-types")
    public ResponseEntity<ApiResponse<List<Map<String, Object>>>> getActionTypes() {
        
        List<Map<String, Object>> actionTypes = Arrays.stream(AlertActionType.values())
                .map(actionType -> Map.<String, Object>of(
                    "type", actionType.name(),
                    "displayName", actionType.getDisplayName(),
                    "description", actionType.getDescription(),
                    "icon", actionType.getIcon(),
                    "isServiceRequest", actionType.isServiceRequest(),
                    "isAlertManagement", actionType.isAlertManagement()
                ))
                .collect(Collectors.toList());
        
        return ResponseEntity.ok(ApiResponse.success("Action types retrieved successfully", actionTypes));
    }
    
    @Operation(summary = "Get alert action statistics")
    @GetMapping("/actions/statistics")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getActionStatistics(
            @CurrentUser UserPrincipal userPrincipal) {
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        long pendingActions = alertActionService.getPendingActionsCount(user);
        long serviceRequests = alertActionService.getServiceRequestsCount(user);
        
        Map<String, Object> statistics = Map.of(
            "pendingActions", pendingActions,
            "totalServiceRequests", serviceRequests,
            "hasServiceRequests", serviceRequests > 0,
            "hasPendingActions", pendingActions > 0
        );
        
        return ResponseEntity.ok(ApiResponse.success("Action statistics retrieved successfully", statistics));
    }
    
    // Quick action endpoints for common operations
    @Operation(summary = "Quick action: Mark alert as resolved")
    @PostMapping("/{alertId}/resolve")
    public ResponseEntity<ApiResponse<AlertActionResponse>> markAsResolved(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId,
            @RequestBody(required = false) Map<String, String> body) {
        
        CreateAlertActionRequest request = new CreateAlertActionRequest();
        request.setActionType(AlertActionType.MARK_RESOLVED);
        if (body != null && body.containsKey("message")) {
            request.setUserMessage(body.get("message"));
        }
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        AlertActionResponse response = alertActionService.createAlertAction(user, alertId, request);
        
        return ResponseEntity.ok(ApiResponse.success("Alert marked as resolved", response));
    }
    
    @Operation(summary = "Quick action: Mark alert as false positive")
    @PostMapping("/{alertId}/false-positive")
    public ResponseEntity<ApiResponse<AlertActionResponse>> markAsFalsePositive(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId,
            @RequestBody(required = false) Map<String, String> body) {
        
        CreateAlertActionRequest request = new CreateAlertActionRequest();
        request.setActionType(AlertActionType.MARK_FALSE_POSITIVE);
        if (body != null && body.containsKey("message")) {
            request.setUserMessage(body.get("message"));
        }
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        AlertActionResponse response = alertActionService.createAlertAction(user, alertId, request);
        
        return ResponseEntity.ok(ApiResponse.success("Alert marked as false positive", response));
    }
    
    @Operation(summary = "Quick action: Acknowledge alert")
    @PostMapping("/{alertId}/acknowledge")
    public ResponseEntity<ApiResponse<AlertActionResponse>> acknowledgeAlert(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId) {
        
        CreateAlertActionRequest request = new CreateAlertActionRequest();
        request.setActionType(AlertActionType.ACKNOWLEDGE);
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        AlertActionResponse response = alertActionService.createAlertAction(user, alertId, request);
        
        return ResponseEntity.ok(ApiResponse.success("Alert acknowledged", response));
    }
    
    @Operation(summary = "Quick action: Escalate alert")
    @PostMapping("/{alertId}/escalate")
    public ResponseEntity<ApiResponse<AlertActionResponse>> escalateAlert(
            @CurrentUser UserPrincipal userPrincipal,
            @PathVariable Long alertId,
            @RequestBody(required = false) Map<String, String> body) {
        
        CreateAlertActionRequest request = new CreateAlertActionRequest();
        request.setActionType(AlertActionType.ESCALATE);
        if (body != null && body.containsKey("message")) {
            request.setUserMessage(body.get("message"));
        }
        if (body != null && body.containsKey("urgencyLevel")) {
            request.setUrgencyLevel(body.get("urgencyLevel"));
        }
        
        User user = userRepository.findByIdWithRolesAndSubscription(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User", "id", userPrincipal.getId()));
        
        AlertActionResponse response = alertActionService.createAlertAction(user, alertId, request);
        
        return ResponseEntity.ok(ApiResponse.success("Alert escalated", response));
    }
}
