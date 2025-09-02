package com.threatscopebackend.controller.consultation;

import com.threatscopebackend.dto.consultation.request.*;
import com.threatscopebackend.dto.consultation.response.*;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.consultation.ConsultationPlanService;
import com.threatscopebackend.service.consultation.ConsultationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/v1/consultation")
@RequiredArgsConstructor
@Slf4j
public class ConsultationController {
    
    private final ConsultationService consultationService;
    private final ConsultationPlanService consultationPlanService;
    
    // ===== CONSULTATION PLANS =====
    
    @GetMapping("/plans")
    public ResponseEntity<ApiResponse<List<ConsultationPlanResponse>>> getConsultationPlans() {
        log.info("🎯 API: Fetching consultation plans");
        
        try {
            List<ConsultationPlanResponse> plans = consultationPlanService.getActivePlans();
            log.info("🎯 API: Retrieved {} consultation plans", plans.size());
            
            return ResponseEntity.ok(ApiResponse.success("Consultation plans retrieved successfully", plans));
        } catch (Exception e) {
            log.error("🚨 API: Error fetching consultation plans: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<List<ConsultationPlanResponse>>error("Failed to fetch consultation plans: " + e.getMessage()));
        }
    }
    
    // ===== TEST ENDPOINTS =====
    
    @GetMapping("/test/create-mock-session")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> createMockSession(
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.info("🧪 TEST: Creating mock consultation session for user: {}", userPrincipal.getEmail());
        
        try {
            // Get first available consultation plan
            ConsultationPlanResponse plan = consultationPlanService.getActivePlans().get(0);
            
            // Create mock session request
            CreateConsultationSessionRequest request = new CreateConsultationSessionRequest();
            request.setPlanId(Long.valueOf(plan.getId()));
            request.setSessionNotes("Test consultation session - This is a mock session created for testing the consultation flow.");
            request.setConsultationType("general");
            request.setConsultationCategory("general-security");
            // Note: alertId is optional for general consultations
            
            ConsultationSessionResponse session = consultationService.createConsultationSession(userPrincipal, request);
            
            // Auto-assign expert and mark as paid for testing
            consultationService.processSuccessfulPayment(Long.valueOf(session.getId()), "mock_payment_" + session.getId());
            
            // Fetch updated session
            session = consultationService.getUserSession(Long.valueOf(session.getId()), userPrincipal);
            
            log.info("✅ Created mock session: {} with status: {}", session.getId(), session.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success("Mock consultation session created successfully", session));
        } catch (Exception e) {
            log.error("❌ Error creating mock session: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to create mock session: " + e.getMessage()));
        }
    }
    
    @GetMapping("/plans/test")
    public ResponseEntity<ApiResponse<String>> testPlansEndpoint() {
        log.info("🧪 TEST: Testing consultation plans endpoint at /v1/consultation/plans/test");
        
        try {
            // Simple database connectivity test
            List<ConsultationPlanResponse> plans = consultationPlanService.getActivePlans();
            return ResponseEntity.ok(ApiResponse.success("Test successful", "Total active plans: " + plans.size()));
        } catch (Exception e) {
            log.error("🚨 TEST: Error testing plans: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError()
                    .body(ApiResponse.<String>error("Test failed: " + e.getMessage()));
        }
    }
    
    @GetMapping("/plans/popular")
    public ResponseEntity<ApiResponse<List<ConsultationPlanResponse>>> getPopularPlans() {
        log.debug("Fetching popular consultation plans");
        
        List<ConsultationPlanResponse> plans = consultationPlanService.getPopularPlans();
        
        return ResponseEntity.ok(ApiResponse.success("Popular consultation plans retrieved successfully", plans));
    }
    
    @GetMapping("/plans/{planId}")
    public ResponseEntity<ApiResponse<ConsultationPlanResponse>> getConsultationPlan(@PathVariable Long planId) {
        log.debug("Fetching consultation plan: {}", planId);
        
        ConsultationPlanResponse plan = consultationPlanService.getPlanById(planId);
        
        return ResponseEntity.ok(ApiResponse.success("Consultation plan retrieved successfully", plan));
    }
    
    @GetMapping("/plans/recommended")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsultationPlanResponse>> getRecommendedPlan(@CurrentUser UserPrincipal userPrincipal) {
        log.debug("Fetching recommended plan for user: {}", userPrincipal.getEmail());
        
        // Get user's subscription plan type (simplified - could be enhanced)
        String userPlanType = "FREE"; // Default - could be fetched from user's subscription
        
        ConsultationPlanResponse plan = consultationPlanService.getRecommendedPlan(userPlanType);
        
        return ResponseEntity.ok(ApiResponse.success("Recommended consultation plan retrieved", plan));
    }
    
    // ===== CONSULTATION SESSIONS =====
    
    @PostMapping("/sessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> createConsultationSession(
            @CurrentUser UserPrincipal userPrincipal,
            @Valid @RequestBody CreateConsultationSessionRequest request) {
        
        log.info("Creating consultation session for user: {}", userPrincipal.getEmail());
        
        ConsultationSessionResponse session = consultationService.createConsultationSession(userPrincipal, request);
        
        return ResponseEntity.ok(ApiResponse.success("Consultation session created successfully", session));
    }
    
    @GetMapping("/sessions")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<Page<ConsultationSessionResponse>>> getUserSessions(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        
        log.debug("Fetching sessions for user: {}", userPrincipal.getEmail());
        
        Page<ConsultationSessionResponse> sessions = consultationService.getUserSessions(userPrincipal, page, size);
        
        return ResponseEntity.ok(ApiResponse.success("User sessions retrieved successfully", sessions));
    }
    
    @GetMapping("/sessions/active")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<List<ConsultationSessionResponse>>> getActiveSessions(
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.debug("Fetching active sessions for user: {}", userPrincipal.getEmail());
        
        List<ConsultationSessionResponse> sessions = consultationService.getActiveSessionsForUser(userPrincipal);
        
        return ResponseEntity.ok(ApiResponse.success("Active sessions retrieved successfully", sessions));
    }
    
    @GetMapping("/sessions/{sessionId}")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> getSession(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.info("🔍 DEBUG: Fetching session: {} for user: {} (ID: {})", 
                sessionId, userPrincipal.getEmail(), userPrincipal.getId());
        
        try {
            ConsultationSessionResponse session;
            
            // Check if user is admin - admins can access any session
            if (userPrincipal.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))) {
                log.info("🔑 ADMIN: Admin user accessing session {}", sessionId);
                session = consultationService.getSessionById(sessionId);
            } else {
                // Regular user - must own the session
                session = consultationService.getUserSession(sessionId, userPrincipal);
            }
            
            log.info("✅ Successfully retrieved session: {} with status: {}", 
                    sessionId, session.getStatus());
            return ResponseEntity.ok(ApiResponse.success("Session retrieved successfully", session));
        } catch (Exception e) {
            log.error("❌ Error fetching session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to fetch session: " + e.getMessage()));
        }
    }
    
    @PostMapping("/sessions/{sessionId}/start")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> startSession(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.info("Starting session: {} for user: {}", sessionId, userPrincipal.getEmail());
        
        try {
            ConsultationSessionResponse session;
            
            // Check if user is admin - admins can start any session
            if (userPrincipal.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))) {
                log.info("🔑 ADMIN: Admin starting session {}", sessionId);
                session = consultationService.startSessionById(sessionId);
            } else {
                // Regular user - must own the session
                session = consultationService.startSession(sessionId, userPrincipal);
            }
            
            return ResponseEntity.ok(ApiResponse.success("Session started successfully", session));
        } catch (Exception e) {
            log.error("❌ Error starting session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to start session: " + e.getMessage()));
        }
    }
    

    
    @PostMapping("/sessions/{sessionId}/cancel")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> cancelSession(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal) {
        
        log.info("Cancelling session: {} for user: {}", sessionId, userPrincipal.getEmail());
        
        ConsultationSessionResponse session = consultationService.cancelSession(sessionId, userPrincipal);
        
        return ResponseEntity.ok(ApiResponse.success("Session cancelled successfully", session));
    }
    
    @PostMapping("/sessions/{sessionId}/payment")
    @PreAuthorize("hasRole('USER')")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> processPayment(
            @PathVariable Long sessionId,
            @CurrentUser UserPrincipal userPrincipal,
            @RequestBody(required = false) Object paymentData) {
        
        log.info("🎭 Processing payment for session: {} for user: {} (ID: {})", 
                sessionId, userPrincipal.getEmail(), userPrincipal.getId());
        
        try {
            // For testing purposes, simulate successful payment
            String mockPaymentIntentId = "mock_payment_" + sessionId + "_" + System.currentTimeMillis();
            
            // Process the payment
            consultationService.processSuccessfulPayment(sessionId, mockPaymentIntentId);
            
            // Fetch updated session - allow admin access
            ConsultationSessionResponse session;
            if (userPrincipal.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"))) {
                session = consultationService.getSessionById(sessionId);
            } else {
                session = consultationService.getUserSession(sessionId, userPrincipal);
            }
            
            log.info("✅ Payment processed successfully for session: {} - Status: {}", 
                    sessionId, session.getStatus());
            
            return ResponseEntity.ok(ApiResponse.success("Payment processed successfully", session));
            
        } catch (Exception e) {
            log.error("❌ Payment processing failed for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Payment processing failed: " + e.getMessage()));
        }
    }
    
    // ===== PAYMENT WEBHOOKS =====
    
    @PostMapping("/payment/webhook")
    public ResponseEntity<ApiResponse<Void>> handlePaymentWebhook(
            @RequestParam String sessionId,
            @RequestParam String paymentIntentId,
            @RequestParam String status) {
        
        log.info("Received payment webhook for session: {}, status: {}", sessionId, status);
        
        try {
            if ("succeeded".equals(status)) {
                consultationService.processSuccessfulPayment(Long.valueOf(sessionId), paymentIntentId);
            }
            
            return ResponseEntity.ok(ApiResponse.<Void>success("Payment webhook processed successfully", null));
            
        } catch (Exception e) {
            log.error("Failed to process payment webhook: {}", e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Void>error("Failed to process payment webhook: " + e.getMessage()));
        }
    }
    
    // ===== STATISTICS =====
    
    @GetMapping("/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<ConsultationStatistics>> getStatistics() {
        log.debug("Fetching consultation statistics");
        
        ConsultationStatistics statistics = consultationService.getConsultationStatistics();
        
        return ResponseEntity.ok(ApiResponse.success("Consultation statistics retrieved successfully", statistics));
    }
}
