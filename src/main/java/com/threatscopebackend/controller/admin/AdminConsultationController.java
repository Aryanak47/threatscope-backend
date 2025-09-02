package com.threatscopebackend.controller.admin;

import com.threatscopebackend.dto.consultation.request.AssignExpertRequest;
import com.threatscopebackend.dto.consultation.response.*;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.consultation.AdminConsultationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/admin/consultation")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Admin Consultation", description = "Admin management of consultation sessions")
@PreAuthorize("hasRole('ADMIN')")
public class AdminConsultationController {

    private final AdminConsultationService adminConsultationService;

    // ===== CONSULTATION SESSION MANAGEMENT =====

    @Operation(summary = "Get all consultation sessions for admin with enhanced filtering")
    @GetMapping("/sessions")
    public ResponseEntity<ApiResponse<Page<ConsultationSessionResponse>>> getAllSessions(
            @Parameter(description = "Page number (0-based)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "Page size") @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field") @RequestParam(defaultValue = "createdAt") String sortBy,
            @Parameter(description = "Sort direction") @RequestParam(defaultValue = "desc") String sortDir,
            @Parameter(description = "Filter by status") @RequestParam(required = false) String status,
            @Parameter(description = "Filter by payment status") @RequestParam(required = false) String paymentStatus,
            @Parameter(description = "Search by user email, expert name, or notes") @RequestParam(required = false) String search) {

        log.info("🔍 Admin fetching consultation sessions - page: {}, size: {}, status: {}, paymentStatus: {}, search: '{}'",
                page, size, status, paymentStatus, search);

        try {
            Page<ConsultationSessionResponse> sessions = adminConsultationService.getAllSessionsWithFilters(
                    page, size, sortBy, sortDir, status, paymentStatus, search);

            log.info("✅ Retrieved {} consultation sessions for admin (page {} of {})",
                    sessions.getNumberOfElements(), sessions.getNumber() + 1, sessions.getTotalPages());

            return ResponseEntity.ok(ApiResponse.success("Consultation sessions retrieved successfully", sessions));
        } catch (Exception e) {
            log.error("❌ Error fetching consultation sessions: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Page<ConsultationSessionResponse>>error("Failed to fetch sessions: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get specific consultation session details")
    @GetMapping("/sessions/{sessionId}")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> getSession(
            @PathVariable Long sessionId) {

        log.info("🔍 Admin fetching consultation session: {}", sessionId);

        try {
            ConsultationSessionResponse session = adminConsultationService.getSessionById(sessionId);

            return ResponseEntity.ok(ApiResponse.success("Session retrieved successfully", session));
        } catch (Exception e) {
            log.error("❌ Error fetching session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to fetch session: " + e.getMessage()));
        }
    }

    // ===== PAYMENT MANAGEMENT =====

    @Operation(summary = "Process payment acknowledgment - Mark session as ready for payment")
    @PostMapping("/sessions/{sessionId}/process-payment")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> processPayment(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody(required = false) Map<String, String> notes) {

        log.info("💳 Admin {} processing payment for session: {}", adminUser.getEmail(), sessionId);

        try {
            String adminNotes = notes != null ? notes.get("notes") : "Payment approved by admin";

            ConsultationSessionResponse session = adminConsultationService.processPaymentApproval(
                    sessionId, adminUser.getEmail(), adminNotes);

            log.info("✅ Payment processed for session: {} - Status: {}, Payment Status: {}",
                    sessionId, session.getStatus(), session.getPaymentStatus());

            return ResponseEntity.ok(ApiResponse.success("Payment processed successfully - Session ready for user payment", session));
        } catch (Exception e) {
            log.error("❌ Error processing payment for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to process payment: " + e.getMessage()));
        }
    }

    @Operation(summary = "Mark payment as completed manually")
    @PostMapping("/sessions/{sessionId}/mark-paid")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> markAsPaid(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody(required = false) Map<String, String> paymentDetails) {

        log.info("💰 Admin {} marking session {} as paid", adminUser.getEmail(), sessionId);

        try {
            String paymentIntentId = paymentDetails != null ? paymentDetails.get("paymentIntentId") : "admin_manual_" + sessionId;
            String notes = paymentDetails != null ? paymentDetails.get("notes") : "Payment marked as completed by admin";

            ConsultationSessionResponse session = adminConsultationService.markSessionAsPaid(
                    sessionId, paymentIntentId, adminUser.getEmail(), notes);

            log.info("✅ Session {} marked as paid by admin", sessionId);

            return ResponseEntity.ok(ApiResponse.success("Session marked as paid successfully", session));
        } catch (Exception e) {
            log.error("❌ Error marking session {} as paid: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to mark as paid: " + e.getMessage()));
        }
    }

    // ===== EXPERT ASSIGNMENT =====

    @Operation(summary = "Assign expert to consultation session")
    @PostMapping("/sessions/{sessionId}/assign-expert")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> assignExpert(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @Valid @RequestBody AssignExpertRequest request) {

        log.info("👨‍💼 Admin {} assigning expert {} to session {}",
                adminUser.getEmail(), request.getExpertId(), sessionId);

        try {
            ConsultationSessionResponse session = adminConsultationService.assignExpertToSession(
                    sessionId, request.getExpertId(), adminUser.getEmail(), request.getNotes());

            log.info("✅ Expert {} assigned to session {}", request.getExpertId(), sessionId);

            return ResponseEntity.ok(ApiResponse.success("Expert assigned successfully", session));
        } catch (Exception e) {
            log.error("❌ Error assigning expert to session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to assign expert: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get available experts for assignment")
    @GetMapping("/experts/available")
    public ResponseEntity<ApiResponse<List<ExpertResponse>>> getAvailableExperts(
            @RequestParam(required = false) String specialization) {

        log.info("🔍 Admin fetching available experts - specialization: {}", specialization);

        try {
            List<ExpertResponse> experts = adminConsultationService.getAvailableExperts(specialization);

            return ResponseEntity.ok(ApiResponse.success("Available experts retrieved successfully", experts));
        } catch (Exception e) {
            log.error("❌ Error fetching available experts: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<ExpertResponse>>error("Failed to fetch experts: " + e.getMessage()));
        }
    }

    // ===== SESSION STATUS MANAGEMENT =====

    @Operation(summary = "Cancel consultation session")
    @PostMapping("/sessions/{sessionId}/cancel")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> cancelSession(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody(required = false) Map<String, String> cancelDetails) {

        log.info("❌ Admin {} cancelling session {}", adminUser.getEmail(), sessionId);

        try {
            String reason = cancelDetails != null ? cancelDetails.get("reason") : "Cancelled by admin";
            boolean refund = cancelDetails != null ? Boolean.parseBoolean(cancelDetails.get("refund")) : false;

            ConsultationSessionResponse session = adminConsultationService.cancelSession(
                    sessionId, adminUser.getEmail(), reason, refund);

            log.info("✅ Session {} cancelled by admin - Refund: {}", sessionId, refund);

            return ResponseEntity.ok(ApiResponse.success("Session cancelled successfully", session));
        } catch (Exception e) {
            log.error("❌ Error cancelling session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to cancel session: " + e.getMessage()));
        }
    }

    @Operation(summary = "Complete consultation session")
    @PostMapping("/sessions/{sessionId}/complete")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> completeSession(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> completionDetails) {

        log.info("✅ Admin {} completing session {}", adminUser.getEmail(), sessionId);

        try {
            String expertSummary = completionDetails.get("expertSummary");
            String deliverables = completionDetails.get("deliverables");

            ConsultationSessionResponse session = adminConsultationService.completeSession(
                    sessionId, adminUser.getEmail(), expertSummary, deliverables);

            log.info("✅ Session {} completed by admin", sessionId);

            return ResponseEntity.ok(ApiResponse.success("Session completed successfully", session));
        } catch (Exception e) {
            log.error("❌ Error completing session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to complete session: " + e.getMessage()));
        }
    }

    // ===== STATISTICS AND REPORTS =====

    @Operation(summary = "Get consultation dashboard statistics")
    @GetMapping("/dashboard")
    public ResponseEntity<ApiResponse<ConsultationAdminDashboard>> getDashboard() {
        log.info("📊 Admin fetching consultation dashboard");

        try {
            ConsultationAdminDashboard dashboard = adminConsultationService.getAdminDashboard();

            return ResponseEntity.ok(ApiResponse.success("Admin dashboard retrieved successfully", dashboard));
        } catch (Exception e) {
            log.error("❌ Error fetching admin dashboard: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationAdminDashboard>error("Failed to fetch dashboard: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get pending sessions requiring admin attention")
    @GetMapping("/sessions/pending")
    public ResponseEntity<ApiResponse<List<ConsultationSessionResponse>>> getPendingSessions() {
        log.info("⏳ Admin fetching pending consultation sessions");

        try {
            List<ConsultationSessionResponse> pendingSessions = adminConsultationService.getPendingSessions();

            return ResponseEntity.ok(ApiResponse.success("Pending sessions retrieved successfully", pendingSessions));
        } catch (Exception e) {
            log.error("❌ Error fetching pending sessions: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<ConsultationSessionResponse>>error("Failed to fetch pending sessions: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get consultation revenue statistics")
    @GetMapping("/revenue")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRevenueStats(
            @RequestParam(required = false) String startDate,
            @RequestParam(required = false) String endDate) {

        log.info("💰 Admin fetching revenue statistics");

        try {
            Map<String, Object> revenue = adminConsultationService.getRevenueStatistics(startDate, endDate);

            return ResponseEntity.ok(ApiResponse.success("Revenue statistics retrieved successfully", revenue));
        } catch (Exception e) {
            log.error("❌ Error fetching revenue statistics: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>error("Failed to fetch revenue statistics: " + e.getMessage()));
        }
    }

    // ===== SESSION EXTENSION AND MANAGEMENT =====

    @Operation(summary = "Extend consultation session duration")
    @PostMapping("/sessions/{sessionId}/extend")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> extendSession(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> extendRequest) {

        Integer additionalHours = (Integer) extendRequest.get("additionalHours");
        String reason = (String) extendRequest.get("reason");

        log.info("⏰ Admin {} extending session {} by {} hours",
                adminUser.getEmail(), sessionId, additionalHours);

        try {
            ConsultationSessionResponse session = adminConsultationService.extendSession(
                    sessionId, additionalHours, adminUser.getEmail(), reason);

            log.info("✅ Session {} extended by {} hours successfully", sessionId, additionalHours);

            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Session extended by %d hours successfully", additionalHours), session));
        } catch (Exception e) {
            log.error("❌ Error extending session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to extend session: " + e.getMessage()));
        }
    }

    @Operation(summary = "Set session under admin management")
    @PostMapping("/sessions/{sessionId}/manage")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> setSessionManaged(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody Map<String, Object> manageRequest) {

        Boolean managed = (Boolean) manageRequest.get("managed");
        String reason = (String) manageRequest.get("reason");

        log.info("🔧 Admin {} setting session {} as managed: {}",
                adminUser.getEmail(), sessionId, managed);

        try {
            ConsultationSessionResponse session = adminConsultationService.setAdminManaged(
                    sessionId, managed, adminUser.getEmail(), reason);

            log.info("✅ Session {} {} admin management", sessionId, managed ? "placed under" : "removed from");

            return ResponseEntity.ok(ApiResponse.success(
                    String.format("Session %s admin management successfully",
                            managed ? "placed under" : "removed from"), session));
        } catch (Exception e) {
            log.error("❌ Error setting session {} management: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to update session management: " + e.getMessage()));
        }
    }

    @Operation(summary = "Reactivate expired consultation session")
    @PostMapping("/sessions/{sessionId}/reactivate")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> reactivateSession(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody Map<String, String> reactivateRequest) {

        String reason = reactivateRequest.get("reason");

        log.info("🔄 Admin {} reactivating expired session {}", adminUser.getEmail(), sessionId);

        try {
            ConsultationSessionResponse session = adminConsultationService.reactivateExpiredSession(
                    sessionId, adminUser.getEmail(), reason);

            log.info("✅ Session {} reactivated successfully", sessionId);

            return ResponseEntity.ok(ApiResponse.success("Session reactivated successfully", session));
        } catch (Exception e) {
            log.error("❌ Error reactivating session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to reactivate session: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get sessions needing admin attention")
    @GetMapping("/sessions/attention")
    public ResponseEntity<ApiResponse<List<ConsultationSessionResponse>>> getSessionsNeedingAttention() {
        log.info("⚠️ Admin fetching sessions needing attention");

        try {
            List<ConsultationSessionResponse> sessions = adminConsultationService.getSessionsNeedingAttention();

            return ResponseEntity.ok(ApiResponse.success("Sessions needing attention retrieved successfully", sessions));
        } catch (Exception e) {
            log.error("❌ Error fetching sessions needing attention: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<ConsultationSessionResponse>>error("Failed to fetch sessions: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get recently created sessions (for catching new sessions)")
    @GetMapping("/sessions/recent")
    public ResponseEntity<ApiResponse<List<ConsultationSessionResponse>>> getRecentSessions(
            @Parameter(description = "Minutes to look back") @RequestParam(defaultValue = "60") int minutesBack) {

        log.info("🔍 Admin fetching recent sessions from last {} minutes", minutesBack);

        try {
            List<ConsultationSessionResponse> recentSessions = adminConsultationService.getRecentSessions(minutesBack);

            log.info("✅ Retrieved {} recent consultation sessions", recentSessions.size());

            return ResponseEntity.ok(ApiResponse.success("Recent sessions retrieved successfully", recentSessions));
        } catch (Exception e) {
            log.error("❌ Error fetching recent sessions: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<List<ConsultationSessionResponse>>error("Failed to fetch recent sessions: " + e.getMessage()));
        }
    }

    // ===== BULK OPERATIONS =====

    @Operation(summary = "Process multiple payment approvals")
    @PostMapping("/sessions/bulk/process-payments")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkProcessPayments(
            @CurrentUser UserPrincipal adminUser,
            @RequestBody Map<String, Object> bulkRequest) {

        @SuppressWarnings("unchecked")
        List<Long> sessionIds = (List<Long>) bulkRequest.get("sessionIds");
        String notes = (String) bulkRequest.get("notes");

        log.info("📋 Admin {} processing bulk payment approval for {} sessions",
                adminUser.getEmail(), sessionIds.size());

        try {
            Map<String, Object> result = adminConsultationService.bulkProcessPayments(
                    sessionIds, adminUser.getEmail(), notes);

            log.info("✅ Bulk payment processing completed - Successful: {}, Failed: {}",
                    result.get("successful"), result.get("failed"));

            return ResponseEntity.ok(ApiResponse.success("Bulk payment processing completed", result));
        } catch (Exception e) {
            log.error("❌ Error in bulk payment processing: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>error("Failed to process bulk payments: " + e.getMessage()));
        }
    }

    @Operation(summary = "Bulk extend multiple sessions")
    @PostMapping("/sessions/bulk/extend")
    public ResponseEntity<ApiResponse<Map<String, Object>>> bulkExtendSessions(
            @CurrentUser UserPrincipal adminUser,
            @RequestBody Map<String, Object> bulkRequest) {

        @SuppressWarnings("unchecked")
        List<Long> sessionIds = (List<Long>) bulkRequest.get("sessionIds");
        Integer additionalHours = (Integer) bulkRequest.get("additionalHours");
        String reason = (String) bulkRequest.get("reason");

        log.info("📋 Admin {} bulk extending {} sessions by {} hours",
                adminUser.getEmail(), sessionIds.size(), additionalHours);

        try {
            Map<String, Object> result = adminConsultationService.bulkExtendSessions(
                    sessionIds, additionalHours, adminUser.getEmail(), reason);

            log.info("✅ Bulk session extension completed - Successful: {}, Failed: {}",
                    result.get("successful"), result.get("failed"));

            return ResponseEntity.ok(ApiResponse.success("Bulk session extension completed", result));
        } catch (Exception e) {
            log.error("❌ Error in bulk session extension: {}", e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>error("Failed to extend sessions: " + e.getMessage()));
        }
    }

    // ===== NEW: TIMER CONTROL =====

    @Operation(summary = "Start session timer manually")
    @PostMapping("/sessions/{sessionId}/start-timer")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> startSessionTimer(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody(required = false) Map<String, String> timerRequest) {

        String notes = timerRequest != null ? timerRequest.get("notes") : "Timer started by admin";

        log.info("⏰ Admin {} starting timer for session {}", adminUser.getEmail(), sessionId);

        ConsultationSessionResponse session = adminConsultationService.startSessionTimer(
                sessionId, adminUser.getEmail(), notes);

        log.info("✅ Timer started for session {} by admin", sessionId);

        return ResponseEntity.ok(ApiResponse.success("Session timer started successfully", session));


    }

    @Operation(summary = "Stop session timer manually")
    @PostMapping("/sessions/{sessionId}/stop-timer")
    public ResponseEntity<ApiResponse<ConsultationSessionResponse>> stopSessionTimer(
            @CurrentUser UserPrincipal adminUser,
            @PathVariable Long sessionId,
            @RequestBody(required = false) Map<String, String> timerRequest) {

        String notes = timerRequest != null ? timerRequest.get("notes") : "Timer stopped by admin";

        log.info("⏹️ Admin {} stopping timer for session {}", adminUser.getEmail(), sessionId);

        try {
            ConsultationSessionResponse session = adminConsultationService.stopSessionTimer(
                    sessionId, adminUser.getEmail(), notes);

            log.info("✅ Timer stopped for session {} by admin", sessionId);

            return ResponseEntity.ok(ApiResponse.success("Session timer stopped successfully", session));
        } catch (Exception e) {
            log.error("❌ Error stopping timer for session {}: {}", sessionId, e.getMessage(), e);
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<ConsultationSessionResponse>error("Failed to stop timer: " + e.getMessage()));
        }
    }

    @Operation(summary = "Get session status for real-time updates")
    @GetMapping("/sessions/{sessionId}/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSessionStatus(
            @PathVariable Long sessionId) {

        try {
            ConsultationSessionResponse session = adminConsultationService.getSessionById(sessionId);

            Map<String, Object> status = new HashMap<>();
            status.put("sessionId", sessionId);
            status.put("status", session.getStatus());
            status.put("timerStartedAt", session.getTimerStartedAt());
            status.put("completedAt", session.getCompletedAt());
            status.put("isExpired", session.getIsExpired());
            status.put("adminExtendedUntil", session.getAdminExtendedUntil());
            status.put("timestamp", System.currentTimeMillis());

            return ResponseEntity.ok(ApiResponse.success("Session status retrieved", status));
        } catch (Exception e) {
            log.error("❌ Error getting session status {}: {}", sessionId, e.getMessage());
            return ResponseEntity.badRequest()
                    .body(ApiResponse.<Map<String, Object>>error("Failed to get session status: " + e.getMessage()));
        }
    }
}
