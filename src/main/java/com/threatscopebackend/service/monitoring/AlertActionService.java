package com.threatscopebackend.service.monitoring;

import com.threatscopebackend.dto.monitoring.AlertActionResponse;
import com.threatscopebackend.dto.monitoring.CreateAlertActionRequest;
import com.threatscopebackend.entity.enums.AlertActionType;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.AlertAction;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.AlertActionRepository;
import com.threatscopebackend.repository.postgresql.BreachAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertActionService {
    
    private final AlertActionRepository alertActionRepository;
    private final BreachAlertRepository breachAlertRepository;
    
    @Transactional
    public AlertActionResponse createAlertAction(User user, Long breachAlertId, CreateAlertActionRequest request) {
        log.info("Creating alert action for user {} on alert {}: {}", 
                user.getId(), breachAlertId, request.getActionType());
        
        // Validate the breach alert exists and belongs to the user
        BreachAlert breachAlert = breachAlertRepository.findByIdAndUserId(breachAlertId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", breachAlertId));
        
        // Check if user already has this action type for this alert (prevent duplicates)
        if (alertActionRepository.existsByUserIdAndBreachAlertIdAndActionType(
                user.getId(), breachAlertId, request.getActionType())) {
            throw new IllegalStateException("Action of this type already exists for this alert");
        }
        
        // Build the alert action
        AlertAction alertAction = AlertAction.builder()
                .user(user)
                .breachAlert(breachAlert)
                .actionType(request.getActionType())
                .status(CommonEnums.AlertStatus.NEW)
                .title(buildActionTitle(request.getActionType(), breachAlert))
                .description(buildActionDescription(request.getActionType(), breachAlert))
                .userMessage(request.getUserMessage())
                .contactEmail(request.getContactEmail() != null ? request.getContactEmail() : user.getEmail())
                .contactPhone(request.getContactPhone())
                .companyName(request.getCompanyName())
                .urgencyLevel(request.getUrgencyLevel() != null ? request.getUrgencyLevel() : "MEDIUM")
                .estimatedBudget(request.getEstimatedBudget())
                .preferredTimeline(request.getPreferredTimeline())
                .additionalContext(request.getAdditionalContext())
                .isServiceRequest(request.getActionType().isServiceRequest())
                .build();
        
        alertAction = alertActionRepository.save(alertAction);
        
        // Handle different action types
        handleActionType(alertAction, breachAlert);
        
        log.info("Created alert action with ID: {}", alertAction.getId());
        
        return convertToResponse(alertAction);
    }
    
    private void handleActionType(AlertAction alertAction, BreachAlert breachAlert) {
        switch (alertAction.getActionType()) {
            case MARK_RESOLVED:
                breachAlert.resolve("Marked as resolved by user");
                breachAlertRepository.save(breachAlert);
                alertAction.markAsProcessed("Alert marked as resolved");
                alertActionRepository.save(alertAction);
                break;
                
            case MARK_FALSE_POSITIVE:
                breachAlert.markAsFalsePositive("Marked as false positive by user");
                breachAlertRepository.save(breachAlert);
                alertAction.markAsProcessed("Alert marked as false positive");
                alertActionRepository.save(alertAction);
                break;
                
            case ACKNOWLEDGE:
                breachAlert.acknowledge("Acknowledged by user");
                breachAlertRepository.save(breachAlert);
                alertAction.acknowledge();
                alertActionRepository.save(alertAction);
                break;
                
            case ESCALATE:
                breachAlert.escalate("Escalated by user");
                breachAlertRepository.save(breachAlert);
                break;
                
            default:
                // Service requests - no immediate action, will be processed by admin
                log.info("Service request created: {}", alertAction.getActionType());
                break;
        }
    }
    
    private String buildActionTitle(AlertActionType actionType, BreachAlert breachAlert) {
        return switch (actionType) {
            case SECURITY_ASSESSMENT -> "Security Assessment Request for " + breachAlert.getAffectedEmail();
            case CODE_INVESTIGATION -> "Code Investigation Request for " + breachAlert.getBreachSource();
            case INCIDENT_RESPONSE -> "Incident Response Request for " + breachAlert.getTitle();
            case EXPERT_CONSULTATION -> "Expert Consultation Request";
            case COMPLIANCE_CHECK -> "Compliance Assessment Request";
            case SECURITY_TRAINING -> "Security Training Request";
            case MARK_RESOLVED -> "Alert Resolution";
            case MARK_FALSE_POSITIVE -> "False Positive Report";
            case ACKNOWLEDGE -> "Alert Acknowledgment";
            case ESCALATE -> "Alert Escalation";
        };
    }
    
    private String buildActionDescription(AlertActionType actionType, BreachAlert breachAlert) {
        return switch (actionType) {
            case SECURITY_ASSESSMENT -> 
                "Request comprehensive security assessment following breach detection for " + breachAlert.getAffectedEmail();
            case CODE_INVESTIGATION -> 
                "Request code review and vulnerability assessment for the affected system";
            case INCIDENT_RESPONSE -> 
                "Request expert assistance with incident response and breach mitigation";
            case EXPERT_CONSULTATION -> 
                "Schedule consultation with cybersecurity expert to discuss security concerns";
            case COMPLIANCE_CHECK -> 
                "Request compliance assessment to ensure regulatory requirements are met";
            case SECURITY_TRAINING -> 
                "Request security awareness training for team members";
            case MARK_RESOLVED -> 
                "Mark this alert as resolved and no longer requiring attention";
            case MARK_FALSE_POSITIVE -> 
                "Report this alert as a false positive";
            case ACKNOWLEDGE -> 
                "Acknowledge receipt and review of this security alert";
            case ESCALATE -> 
                "Escalate this alert for urgent attention from security team";
        };
    }
    
    public Page<AlertActionResponse> getUserAlertActions(User user, int page, int size, String sortBy, String sortDir) {
        Pageable pageable = PageRequest.of(page, size);
        Page<AlertAction> actions = alertActionRepository.findByUserIdOrderByCreatedAtDesc(user.getId(), pageable);
        
        return actions.map(this::convertToResponse);
    }
    
    public List<AlertActionResponse> getAlertActions(User user, Long breachAlertId) {
        // Verify the alert belongs to the user
        breachAlertRepository.findByIdAndUserId(breachAlertId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Alert", "id", breachAlertId));
        
        List<AlertAction> actions = alertActionRepository.findByBreachAlertIdOrderByCreatedAtDesc(breachAlertId);
        
        return actions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    public List<AlertActionResponse> getPendingServiceRequests(User user) {
        List<AlertAction> actions = alertActionRepository.findPendingServiceRequestsByUserId(user.getId());
        
        return actions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    public AlertActionResponse getAlertAction(User user, Long actionId) {
        AlertAction action = alertActionRepository.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert Action", "id", actionId));
        
        // Verify the action belongs to the user
        if (!action.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Alert Action", "id", actionId);
        }
        
        return convertToResponse(action);
    }
    
    @Transactional
    public void cancelAlertAction(User user, Long actionId) {
        AlertAction action = alertActionRepository.findById(actionId)
                .orElseThrow(() -> new ResourceNotFoundException("Alert Action", "id", actionId));
        
        // Verify the action belongs to the user
        if (!action.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Alert Action", "id", actionId);
        }
        
        // Only allow cancellation of pending service requests
        if (!action.canBeModified()) {
            throw new IllegalStateException("This action cannot be cancelled");
        }
        
        action.setStatus(CommonEnums.AlertStatus.DISMISSED);
        action.setIsProcessed(true);
        action.setProcessedAt(LocalDateTime.now());
        action.setAdminResponse("Cancelled by user");
        
        alertActionRepository.save(action);
        
        log.info("Alert action {} cancelled by user {}", actionId, user.getId());
    }
    
    // Statistics and dashboard methods
    public long getPendingActionsCount(User user) {
        return alertActionRepository.countByUserIdAndIsProcessedFalse(user.getId());
    }
    
    public long getServiceRequestsCount(User user) {
        return alertActionRepository.countByUserIdAndIsServiceRequestTrue(user.getId());
    }
    
    private AlertActionResponse convertToResponse(AlertAction action) {
        return AlertActionResponse.builder()
                .id(action.getId())
                .breachAlertId(action.getBreachAlert().getId())
                .actionType(action.getActionType())
                .status(action.getStatus())
                .title(action.getTitle())
                .description(action.getDescription())
                .userMessage(action.getUserMessage())
                .adminResponse(action.getAdminResponse())
                .contactEmail(action.getContactEmail())
                .contactPhone(action.getContactPhone())
                .companyName(action.getCompanyName())
                .urgencyLevel(action.getUrgencyLevel())
                .estimatedBudget(action.getEstimatedBudget())
                .preferredTimeline(action.getPreferredTimeline())
                .additionalContext(action.getAdditionalContext())
                .isProcessed(action.getIsProcessed())
                .isServiceRequest(action.getIsServiceRequest())
                .createdAt(action.getCreatedAt())
                .updatedAt(action.getUpdatedAt())
                .processedAt(action.getProcessedAt())
                .scheduledFor(action.getScheduledFor())
                .actionDisplayName(action.getActionDisplayName())
                .actionIcon(action.getActionIcon())
                .statusDisplayName(action.getStatusDisplayName())
                .requiresFollowUp(action.requiresFollowUp())
                .build();
    }
}
