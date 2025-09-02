package com.threatscopebackend.service.consultation;

import com.threatscopebackend.dto.consultation.response.*;
import com.threatscopebackend.entity.postgresql.ConsultationSession;
import com.threatscopebackend.entity.postgresql.Expert;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.ConsultationSessionRepository;
import com.threatscopebackend.repository.postgresql.ExpertRepository;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.service.notification.CriticalNotificationService;
import com.threatscopebackend.controller.websocket.SimpleChatController;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class AdminConsultationService {
    
    private final ConsultationSessionRepository sessionRepository;
    private final ExpertRepository expertRepository;
    private final UserRepository userRepository;
    private final CriticalNotificationService criticalNotificationService;
    private final SimpleChatController simpleChatController;
    
    // ===== SESSION MANAGEMENT =====
    
    public Page<ConsultationSessionResponse> getAllSessions(int page, int size, String sortBy, String sortDir,
                                                           String status, String paymentStatus) {
        return getAllSessionsWithFilters(page, size, sortBy, sortDir, status, paymentStatus, null);
    }
    
    public Page<ConsultationSessionResponse> getAllSessionsWithFilters(int page, int size, String sortBy, String sortDir,
                                                                       String status, String paymentStatus, String search) {
        Sort sort = sortDir.equalsIgnoreCase("desc") ? 
            Sort.by(sortBy).descending() : Sort.by(sortBy).ascending();
        Pageable pageable = PageRequest.of(page, size, sort);
        
        Page<ConsultationSession> sessionPage;
        
        if (search != null && !search.trim().isEmpty()) {
            // Use advanced search with filters
            String statusFilter = (status != null && !status.isEmpty()) ? status.toUpperCase() : null;
            String paymentStatusFilter = (paymentStatus != null && !paymentStatus.isEmpty()) ? paymentStatus.toUpperCase() : null;
            
            sessionPage = sessionRepository.searchSessionsWithFilters(
                search.trim(), statusFilter, paymentStatusFilter, pageable);
        } else if (status != null && paymentStatus != null) {
            sessionPage = sessionRepository.findByStatusAndPaymentStatus(
                ConsultationSession.SessionStatus.valueOf(status.toUpperCase()),
                ConsultationSession.PaymentStatus.valueOf(paymentStatus.toUpperCase()),
                pageable);
        } else if (status != null) {
            sessionPage = sessionRepository.findByStatus(
                ConsultationSession.SessionStatus.valueOf(status.toUpperCase()), pageable);
        } else if (paymentStatus != null) {
            sessionPage = sessionRepository.findByPaymentStatus(
                ConsultationSession.PaymentStatus.valueOf(paymentStatus.toUpperCase()), pageable);
        } else {
            sessionPage = sessionRepository.findAll(pageable);
        }
        
        log.info("📊 Admin sessions query: page={}, size={}, total={}, search='{}', status='{}', paymentStatus='{}'", 
                page + 1, sessionPage.getNumberOfElements(), sessionPage.getTotalElements(), 
                search, status, paymentStatus);
        
        return sessionPage.map(this::convertToResponse);
    }
    
    public ConsultationSessionResponse getSessionById(Long sessionId) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        return convertToResponse(session);
    }
    
    // ===== PAYMENT MANAGEMENT =====
    
    @Transactional
    public ConsultationSessionResponse processPaymentApproval(Long sessionId, String adminEmail, String notes) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        log.info("🔍 Processing payment approval for session: {} - Current status: {}, Payment status: {}", 
                sessionId, session.getStatus(), session.getPaymentStatus());
        
        // Validate session can be processed
        if (session.getStatus() != ConsultationSession.SessionStatus.PENDING) {
            throw new IllegalStateException("Session must be in PENDING status to process payment approval");
        }
        
        // Mark session as ready for payment - move to ASSIGNED status
        session.setStatus(ConsultationSession.SessionStatus.ASSIGNED);
        
        // Add admin notes to session notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\\n\\n[ADMIN APPROVAL - %s by %s]: %s", 
                LocalDateTime.now(), adminEmail, notes);
        session.setSessionNotes(currentNotes + adminNote);
        
        // Auto-assign a default expert if none assigned yet
        if (session.getExpert() == null) {
            Expert defaultExpert = findAvailableExpert("general");
            if (defaultExpert != null) {
                session.setExpert(defaultExpert);
                log.info("✅ Auto-assigned expert {} to session {}", defaultExpert.getName(), sessionId);
            }
        }
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know payment was approved
        try {
            criticalNotificationService.notifyPaymentApproved(session);
        } catch (Exception e) {
            log.warn("Failed to send payment approval notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Payment approval processed for session: {} - New status: {}", 
                sessionId, session.getStatus());
        
        return convertToResponse(session);
    }
    
    @Transactional
    public ConsultationSessionResponse markSessionAsPaid(Long sessionId, String paymentIntentId, 
                                                        String adminEmail, String notes) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        // Mark payment as completed
        session.setPaymentStatus(ConsultationSession.PaymentStatus.PAID);
        session.setPaymentIntentId(paymentIntentId);
        
        // Move to ASSIGNED status if still pending
        if (session.getStatus() == ConsultationSession.SessionStatus.PENDING) {
            session.setStatus(ConsultationSession.SessionStatus.ASSIGNED);
        }
        
        // Add admin notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\\n\\n[ADMIN PAYMENT - %s by %s]: %s", 
                LocalDateTime.now(), adminEmail, notes);
        session.setSessionNotes(currentNotes + adminNote);
        
        session = sessionRepository.save(session);
        
        // Note: Payment success notifications can be optional as user sees immediate UI feedback
        // Only critical failures need notifications
        
        log.info("✅ Session {} marked as paid by admin - Payment ID: {}", sessionId, paymentIntentId);
        
        return convertToResponse(session);
    }
    
    @Transactional
    public ConsultationSessionResponse markPaymentAsFailed(Long sessionId, String failureReason, 
                                                          String adminEmail, String notes) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        // Mark payment as failed
        session.setPaymentStatus(ConsultationSession.PaymentStatus.FAILED);
        
        // Add admin notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\n\n[ADMIN PAYMENT FAILURE - %s by %s]: %s. Reason: %s", 
                LocalDateTime.now(), adminEmail, notes != null ? notes : "Payment marked as failed", failureReason);
        session.setSessionNotes(currentNotes + adminNote);
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know payment failed and fix it
        try {
            criticalNotificationService.notifyPaymentFailed(session, failureReason);
        } catch (Exception e) {
            log.warn("Failed to send payment failure notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("❌ Session {} payment marked as failed by admin - Reason: {}", sessionId, failureReason);
        
        return convertToResponse(session);
    }
    
    // ===== EXPERT ASSIGNMENT =====
    
    @Transactional
    public ConsultationSessionResponse assignExpertToSession(Long sessionId, Long expertId, 
                                                            String adminEmail, String notes) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        Expert expert = expertRepository.findById(expertId)
            .orElseThrow(() -> new ResourceNotFoundException("Expert", "id", expertId));
        
        // Assign expert
        session.setExpert(expert);
        
        // Update session status if it's still pending
        if (session.getStatus() == ConsultationSession.SessionStatus.PENDING) {
            session.setStatus(ConsultationSession.SessionStatus.ASSIGNED);
        }
        
        // Add admin notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\\n\\n[EXPERT ASSIGNMENT - %s by %s]: Assigned expert %s. %s", 
                LocalDateTime.now(), adminEmail, expert.getName(), notes != null ? notes : "");
        session.setSessionNotes(currentNotes + adminNote);
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know who their expert is
        try {
            criticalNotificationService.notifyExpertAssigned(session);
        } catch (Exception e) {
            log.warn("Failed to send expert assignment notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Expert {} assigned to session {} by admin {}", 
                expert.getName(), sessionId, adminEmail);
        
        return convertToResponse(session);
    }
    
    public List<ExpertResponse> getAvailableExperts(String specialization) {
        List<Expert> experts;
        
        if (specialization != null && !specialization.isEmpty()) {
            experts = expertRepository.findBySpecializationAndIsAvailableTrue(specialization);
        } else {
            experts = expertRepository.findByIsAvailableTrue();
        }
        
        // If no real experts exist, create generic ones for the system to work
        if (experts.isEmpty()) {
            experts = createGenericExperts();
            // Save them to database for consistency
            experts = expertRepository.saveAll(experts);
            log.info("✅ Created {} generic experts for system functionality", experts.size());
        }
        
        return experts.stream()
                .map(this::convertExpertToResponse)
                .collect(Collectors.toList());
    }
    
    private Expert findAvailableExpert(String specialization) {
        List<Expert> experts = expertRepository.findBySpecializationAndIsAvailableTrue(specialization);
        
        if (experts.isEmpty()) {
            // Fallback to any available expert
            experts = expertRepository.findByIsAvailableTrue();
        }
        
        // If still no experts, create generic ones
        if (experts.isEmpty()) {
            experts = createGenericExperts();
            experts = expertRepository.saveAll(experts);
            log.info("✅ Created {} generic experts for auto-assignment", experts.size());
        }
        
        // Return expert with lowest current workload
        return experts.stream()
                .min(Comparator.comparingLong(expert -> 
                    sessionRepository.countByExpertAndStatusIn(expert, 
                        Arrays.asList(ConsultationSession.SessionStatus.ASSIGNED, 
                                     ConsultationSession.SessionStatus.ACTIVE))))
                .orElse(null);
    }
    
    // ===== SESSION STATUS MANAGEMENT =====
    
    @Transactional
    public ConsultationSessionResponse cancelSession(Long sessionId, String adminEmail, 
                                                    String reason, boolean refund) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        // Update session status
        session.setStatus(ConsultationSession.SessionStatus.CANCELLED);
        
        // Handle refund if requested
        if (refund && session.getPaymentStatus() == ConsultationSession.PaymentStatus.PAID) {
            session.setPaymentStatus(ConsultationSession.PaymentStatus.REFUNDED);
        }
        
        // Add admin notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\\n\\n[ADMIN CANCELLATION - %s by %s]: %s (Refund: %s)", 
                LocalDateTime.now(), adminEmail, reason, refund ? "Yes" : "No");
        session.setSessionNotes(currentNotes + adminNote);
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know session was cancelled
        try {
            criticalNotificationService.notifySessionCancelled(session, reason, refund);
        } catch (Exception e) {
            log.warn("Failed to send session cancellation notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Session {} cancelled by admin - Reason: {}, Refund: {}", sessionId, reason, refund);
        
        return convertToResponse(session);
    }
    
    @Transactional
    public ConsultationSessionResponse completeSession(Long sessionId, String adminEmail,
                                                      String expertSummary, String deliverables) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        // Mark session as completed
        session.setStatus(ConsultationSession.SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        session.setExpertSummary(expertSummary);
        session.setDeliverablesProvided(deliverables);
        
        // Add admin notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\\n\\n[ADMIN COMPLETION - %s by %s]: Session completed by admin", 
                LocalDateTime.now(), adminEmail);
        session.setSessionNotes(currentNotes + adminNote);
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know session is completed and can rate
        try {
            criticalNotificationService.notifySessionCompleted(session);
            
            // ENHANCED: Send WebSocket notification for completion
            simpleChatController.sendNotificationToSession(
                sessionId,
                "SESSION_COMPLETED",
                "Consultation Completed",
                "Your consultation session has been completed!",
                Map.of(
                    "completedAt", session.getCompletedAt().toString(),
                    "canRate", session.canBeRated(),
                    "expertSummary", session.getExpertSummary() != null ? session.getExpertSummary() : ""
                )
            );
            
            // Send session status update
            simpleChatController.sendSessionStatusUpdate(sessionId, "ACTIVE", "COMPLETED");
            
        } catch (Exception e) {
            log.warn("Failed to send session completion notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Session {} completed by admin {}", sessionId, adminEmail);
        
        return convertToResponse(session);
    }
    
    // ===== DASHBOARD AND STATISTICS =====
    
    public ConsultationAdminDashboard getAdminDashboard() {
        // Session counts
        Long totalSessions = sessionRepository.count();
        Long pendingSessions = sessionRepository.countByStatus(ConsultationSession.SessionStatus.PENDING);
        Long activeSessions = sessionRepository.countByStatus(ConsultationSession.SessionStatus.ACTIVE);
        Long completedSessions = sessionRepository.countByStatus(ConsultationSession.SessionStatus.COMPLETED);
        Long cancelledSessions = sessionRepository.countByStatus(ConsultationSession.SessionStatus.CANCELLED);
        
        // Payment status
        Long sessionsAwaitingPayment = sessionRepository.countByPaymentStatus(ConsultationSession.PaymentStatus.PENDING);
        Long paidSessions = sessionRepository.countByPaymentStatus(ConsultationSession.PaymentStatus.PAID);
        Long failedPayments = sessionRepository.countByPaymentStatus(ConsultationSession.PaymentStatus.FAILED);
        
        // Revenue calculations
        BigDecimal totalRevenue = calculateTotalRevenue();
        BigDecimal monthlyRevenue = calculateMonthlyRevenue();
        BigDecimal weeklyRevenue = calculateWeeklyRevenue();
        BigDecimal averageSessionValue = totalRevenue.divide(BigDecimal.valueOf(Math.max(1, totalSessions)), 2, java.math.RoundingMode.HALF_UP);
        
        // Expert statistics
        Long totalExperts = expertRepository.count();
        Long activeExperts = expertRepository.countByIsAvailableTrue();
        Long availableExperts = getAvailableExpertsCount();
        
        // Performance metrics
        Double averageSessionDuration = calculateAverageSessionDuration();
        Double sessionCompletionRate = calculateSessionCompletionRate();
        
        return ConsultationAdminDashboard.builder()
                .totalSessions(totalSessions)
                .pendingSessions(pendingSessions)
                .activeSessions(activeSessions)
                .completedSessions(completedSessions)
                .cancelledSessions(cancelledSessions)
                .sessionsAwaitingPayment(sessionsAwaitingPayment)
                .paidSessions(paidSessions)
                .failedPayments(failedPayments)
                .totalRevenue(totalRevenue)
                .monthlyRevenue(monthlyRevenue)
                .weeklyRevenue(weeklyRevenue)
                .averageSessionValue(averageSessionValue)
                .totalExperts(totalExperts)
                .activeExperts(activeExperts)
                .availableExperts(availableExperts)
                .averageSessionDuration(averageSessionDuration)
                .sessionCompletionRate(sessionCompletionRate)
                .lastUpdated(LocalDateTime.now())
                .build();
    }
    
    public List<ConsultationSessionResponse> getPendingSessions() {
        List<ConsultationSession> pendingSessions = sessionRepository.findByStatusOrderByCreatedAtAsc(
                ConsultationSession.SessionStatus.PENDING);
        
        return pendingSessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    public Map<String, Object> getRevenueStatistics(String startDate, String endDate) {
        // This would need actual implementation based on date filtering
        BigDecimal totalRevenue = calculateTotalRevenue();
        Long totalSessions = sessionRepository.countByPaymentStatus(ConsultationSession.PaymentStatus.PAID);
        
        Map<String, Object> revenue = new HashMap<>();
        revenue.put("totalRevenue", totalRevenue);
        revenue.put("totalPaidSessions", totalSessions);
        revenue.put("averageSessionValue", totalRevenue.divide(BigDecimal.valueOf(Math.max(1, totalSessions)), 2, java.math.RoundingMode.HALF_UP));
        revenue.put("currency", "USD");
        
        return revenue;
    }
    
    // ===== BULK OPERATIONS =====
    
    @Transactional
    public Map<String, Object> bulkProcessPayments(List<Long> sessionIds, String adminEmail, String notes) {
        Map<String, Object> result = new HashMap<>();
        List<Long> successful = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        
        for (Long sessionId : sessionIds) {
            try {
                processPaymentApproval(sessionId, adminEmail, notes);
                successful.add(sessionId);
            } catch (Exception e) {
                Map<String, Object> failureInfo = new HashMap<>();
                failureInfo.put("sessionId", sessionId);
                failureInfo.put("error", e.getMessage());
                failed.add(failureInfo);
            }
        }
        
        result.put("successful", successful);
        result.put("failed", failed);
        result.put("totalProcessed", sessionIds.size());
        result.put("successCount", successful.size());
        result.put("failureCount", failed.size());
        
        return result;
    }
    
    // ===== NEW: SESSION EXTENSION METHODS =====
    
    @Transactional
    public ConsultationSessionResponse extendSession(Long sessionId, int additionalHours, 
                                                   String adminEmail, String reason) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        log.info("⏰ Extending session {} by {} hours - Admin: {} - Current status: {}", 
                sessionId, additionalHours, adminEmail, session.getStatus());
        
        // FIXED: Allow extension of completed sessions for follow-up consultations
        if (session.getStatus() == ConsultationSession.SessionStatus.COMPLETED) {
            // Reactivate completed session for additional consultation time
            session.setStatus(ConsultationSession.SessionStatus.ACTIVE);
            log.info("🔄 Reactivating completed session {} for extension", sessionId);
        }
        
        // Extend the session
        session.extendSession(additionalHours, adminEmail, reason);
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know session was extended
        try {
            criticalNotificationService.notifySessionExtended(session, additionalHours, reason);
            
            // ENHANCED: Send WebSocket notification for extension
            simpleChatController.sendNotificationToSession(
                sessionId,
                "SESSION_EXTENDED",
                "Session Extended",
                String.format("Your session has been extended by %d hours", additionalHours),
                Map.of(
                    "additionalHours", additionalHours,
                    "reason", reason,
                    "newExpiryTime", session.getEffectiveExpirationTime() != null ? session.getEffectiveExpirationTime().toString() : "",
                    "extendedByAdmin", adminEmail
                )
            );
            
        } catch (Exception e) {
            log.warn("Failed to send session extension notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Session {} extended by {} hours until {} - Status: {}", 
                sessionId, additionalHours, session.getAdminExtendedUntil(), session.getStatus());
        
        return convertToResponse(session);
    }
    
    @Transactional
    public ConsultationSessionResponse setAdminManaged(Long sessionId, boolean managed, 
                                                      String adminEmail, String reason) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        log.info("🔧 Setting session {} as admin-managed: {} - Admin: {}", sessionId, managed, adminEmail);
        
        // Set admin management
        session.setAdminManaged(managed, adminEmail, reason);
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know about management change
        try {
            criticalNotificationService.notifySessionManagementChanged(session, managed, reason);
        } catch (Exception e) {
            log.warn("Failed to send management change notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Session {} {} admin management", sessionId, managed ? "placed under" : "removed from");
        
        return convertToResponse(session);
    }
    
    @Transactional
    public ConsultationSessionResponse reactivateExpiredSession(Long sessionId, String adminEmail, String reason) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        log.info("🔄 Reactivating expired session {} - Admin: {}", sessionId, adminEmail);
        
        // Check if session is actually expired
        if (!session.isExpired()) {
            throw new IllegalArgumentException("Session is not expired and doesn't need reactivation");
        }
        
        // Reactivate by extending and setting as admin-managed
        session.extendSession(24, adminEmail, reason); // Extend by 24 hours
        session.setAdminManaged(true, adminEmail, "Reactivated from expired state: " + reason);
        
        // If session was EXPIRED, set it back to appropriate status
        if (session.getStatus() == ConsultationSession.SessionStatus.EXPIRED) {
            if (session.getExpert() != null && session.getPaymentStatus() == ConsultationSession.PaymentStatus.PAID) {
                session.setStatus(ConsultationSession.SessionStatus.ASSIGNED);
            } else {
                session.setStatus(ConsultationSession.SessionStatus.PENDING);
            }
        }
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User needs to know session was reactivated
        try {
            criticalNotificationService.notifySessionReactivated(session, reason);
        } catch (Exception e) {
            log.warn("Failed to send reactivation notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Session {} reactivated successfully - New status: {}", sessionId, session.getStatus());
        
        return convertToResponse(session);
    }
    
    public List<ConsultationSessionResponse> getSessionsNeedingAttention() {
        List<ConsultationSession> sessions = sessionRepository.findAll().stream()
            .filter(ConsultationSession::needsAdminAttention)
            .sorted(Comparator.comparing(ConsultationSession::getCreatedAt))
            .collect(Collectors.toList());
        
        return sessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    public List<ConsultationSessionResponse> getRecentSessions(int minutesBack) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(minutesBack);
        List<ConsultationSession> recentSessions = sessionRepository.findCreatedAfter(since);
        
        log.info("📊 Found {} sessions created in the last {} minutes", recentSessions.size(), minutesBack);
        
        return recentSessions.stream()
                .map(this::convertToResponse)
                .collect(Collectors.toList());
    }
    
    @Transactional
    public Map<String, Object> bulkExtendSessions(List<Long> sessionIds, int additionalHours, 
                                                 String adminEmail, String reason) {
        Map<String, Object> result = new HashMap<>();
        List<Long> successful = new ArrayList<>();
        List<Map<String, Object>> failed = new ArrayList<>();
        
        for (Long sessionId : sessionIds) {
            try {
                extendSession(sessionId, additionalHours, adminEmail, reason);
                successful.add(sessionId);
            } catch (Exception e) {
                Map<String, Object> failureInfo = new HashMap<>();
                failureInfo.put("sessionId", sessionId);
                failureInfo.put("error", e.getMessage());
                failed.add(failureInfo);
            }
        }
        
        result.put("successful", successful);
        result.put("failed", failed);
        result.put("totalProcessed", sessionIds.size());
        result.put("successCount", successful.size());
        result.put("failureCount", failed.size());
        
        return result;
    }
    
    // ===== NEW: TIMER CONTROL METHODS =====
    
    @Transactional
    public ConsultationSessionResponse startSessionTimer(Long sessionId, String adminEmail, String notes) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        log.info("⏰ Starting timer for session {} - Current status: {}, Timer started: {}", 
                sessionId, session.getStatus(), session.getTimerStartedAt());
        
        // Store original status for WebSocket notifications
        ConsultationSession.SessionStatus originalStatus = session.getStatus();
        
        // Validate session can have timer started
        if (session.getStatus() != ConsultationSession.SessionStatus.ASSIGNED && 
            session.getStatus() != ConsultationSession.SessionStatus.ACTIVE) {
            throw new IllegalStateException("Timer can only be started for ASSIGNED or ACTIVE sessions");
        }
        
        // Start the timer
        session.startTimer();
        
        // If session is still ASSIGNED, move it to ACTIVE
        if (session.getStatus() == ConsultationSession.SessionStatus.ASSIGNED) {
            session.setStatus(ConsultationSession.SessionStatus.ACTIVE);
            session.setStartedAt(LocalDateTime.now());
        }
        
        // Add admin notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\n\n[TIMER START - %s by %s]: %s", 
                LocalDateTime.now(), adminEmail, notes);
        session.setSessionNotes(currentNotes + adminNote);
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User and expert need to know timer started
        try {
            criticalNotificationService.notifyTimerStarted(session, adminEmail);
            log.info("Timer start notification processed for session {}", sessionId);
            
            // ENHANCED: Send WebSocket notification using simple chat controller
            simpleChatController.sendNotificationToSession(
                sessionId, 
                "TIMER_STARTED", 
                "Consultation Timer Started", 
                "Your consultation timer has started!",
                Map.of(
                    "timerStartedAt", session.getTimerStartedAt().toString(),
                    "sessionDuration", session.getPlan() != null ? session.getPlan().getSessionDurationMinutes() : 30,
                    "adminEmail", adminEmail
                )
            );
            
            // Send session status update
            if (session.getStatus() != originalStatus) {
                simpleChatController.sendSessionStatusUpdate(sessionId, originalStatus.toString(), session.getStatus().toString());
            }
            
        } catch (Exception e) {
            log.warn("Failed to send timer start notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Timer started for session {} by admin {} - New status: {}", 
                sessionId, adminEmail, session.getStatus());
        
        return convertToResponse(session);
    }
    
    @Transactional
    public ConsultationSessionResponse stopSessionTimer(Long sessionId, String adminEmail, String notes) {
        ConsultationSession session = sessionRepository.findById(sessionId)
            .orElseThrow(() -> new ResourceNotFoundException("ConsultationSession", "id", sessionId));
        
        log.info("⏹️ Stopping timer for session {} - Current status: {}, Timer started: {}", 
                sessionId, session.getStatus(), session.getTimerStartedAt());
        
        // Validate session has timer running
        if (session.getTimerStartedAt() == null) {
            throw new IllegalStateException("No timer is currently running for this session");
        }
        
        // Calculate duration
        long durationMinutes = java.time.Duration.between(
                session.getTimerStartedAt(), LocalDateTime.now()).toMinutes();
        
        // Stop the timer by completing the session
        session.setStatus(ConsultationSession.SessionStatus.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        
        // Set summary
        String autoSummary = String.format("Session completed by admin after %d minutes. Timer stopped manually.", 
                durationMinutes);
        session.setExpertSummary(autoSummary);
        
        // Add admin notes
        String currentNotes = session.getSessionNotes() != null ? session.getSessionNotes() : "";
        String adminNote = String.format("\n\n[TIMER STOP - %s by %s]: %s (Duration: %d minutes)", 
                LocalDateTime.now(), adminEmail, notes, durationMinutes);
        session.setSessionNotes(currentNotes + adminNote);
        
        session = sessionRepository.save(session);
        
        // CRITICAL NOTIFICATION: User and expert need to know timer stopped
        try {
            criticalNotificationService.notifyTimerStopped(session, adminEmail, durationMinutes);
            log.info("⏹️ Timer stop notification processed for session {} - Duration: {} minutes", sessionId, durationMinutes);
        } catch (Exception e) {
            log.warn("Failed to send timer stop notification for session {}: {}", sessionId, e.getMessage());
        }
        
        log.info("✅ Timer stopped for session {} by admin {} - Duration: {} minutes", 
                sessionId, adminEmail, durationMinutes);
        
        return convertToResponse(session);
    }
    
    // ===== HELPER METHODS =====
    
    private ConsultationSessionResponse convertToResponse(ConsultationSession session) {
        return ConsultationSessionResponse.builder()
                .id(session.getId()) // Keep as Long, not toString()
                .status(session.getStatus().name())
                .paymentStatus(session.getPaymentStatus().name())
                .sessionPrice(session.getSessionPrice())
                .sessionNotes(session.getSessionNotes())
                .expertSummary(session.getExpertSummary())
                .userRating(session.getUserRating())
                .userFeedback(session.getUserFeedback())
                .durationMinutes(session.getDurationMinutes())
                .scheduledAt(session.getScheduledAt())
                .startedAt(session.getStartedAt())
                .timerStartedAt(session.getTimerStartedAt()) // Include timer start time
                .completedAt(session.getCompletedAt())
                .expiresAt(session.getExpiresAt())
                .createdAt(session.getCreatedAt())
                // Admin extension fields
                .adminExtendedUntil(session.getAdminExtendedUntil())
                .extendedByAdminEmail(session.getExtendedByAdminEmail())
                .extensionReason(session.getExtensionReason())
                .isAdminManaged(session.getIsAdminManaged())
                .effectiveExpirationTime(session.getEffectiveExpirationTime())
                // Related objects - INCLUDING USER INFO
                .user(session.getUser() != null ? convertUserToSummary(session.getUser()) : null)
                .plan(convertPlanToResponse(session.getPlan()))
                .expert(session.getExpert() != null ? convertExpertToResponse(session.getExpert()) : null)
                .canStart(session.canStart())
                .canRate(session.canBeRated())
                .isExpired(session.isExpired())
                .build();
    }
    
    private UserSummary convertUserToSummary(User user) {
        return UserSummary.fromEntity(user);
    }
    
    private ConsultationPlanResponse convertPlanToResponse(com.threatscopebackend.entity.postgresql.ConsultationPlan plan) {
        return ConsultationPlanResponse.builder()
                .id(plan.getId()) // Keep as Long
                .name(plan.getName())
                .displayName(plan.getDisplayName())
                .description(plan.getDescription())
                .price(plan.getPrice())
                .currency(plan.getCurrency())
                .sessionDurationMinutes(plan.getSessionDurationMinutes()) // Use correct field name
                .durationDisplay(plan.getDurationDisplay())
                .features(plan.getFeatures() != null ? Arrays.asList(plan.getFeatures().split(",")) : new ArrayList<>())
                .deliverables(plan.getDeliverables() != null ? Arrays.asList(plan.getDeliverables().split(",")) : new ArrayList<>())
                .isPopular(plan.getIsPopular())
                .includesFollowUp(plan.getIncludesFollowUp())
                .followUpDays(plan.getFollowUpDays())
                .formattedPrice(plan.getFormattedPrice())
                .build();
    }
    
    private ExpertResponse convertExpertToResponse(Expert expert) {
        return ExpertResponse.builder()
                .id(expert.getId().toString()) // ExpertResponse.id is String
                .name(expert.getName())
                .email(expert.getEmail())
                .specialization(expert.getSpecialization())
                .description(expert.getBio()) // Use bio field instead of getDescription()
                .isAvailable(expert.getIsAvailable())
                .totalSessions(expert.getTotalSessions() != null ? expert.getTotalSessions().longValue() : 0L)
                .experienceLevel("SENIOR") // Expert entity doesn't have experienceLevel field
                .createdAt(expert.getCreatedAt())
                .updatedAt(expert.getUpdatedAt())
                .build();
    }
    
    private BigDecimal calculateTotalRevenue() {
        List<ConsultationSession> paidSessions = sessionRepository.findByPaymentStatus(
                ConsultationSession.PaymentStatus.PAID);
        
        return paidSessions.stream()
                .map(ConsultationSession::getSessionPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateMonthlyRevenue() {
        LocalDateTime startOfMonth = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0).withSecond(0);
        List<ConsultationSession> monthlySessions = sessionRepository.findByPaymentStatusAndCreatedAtAfter(
                ConsultationSession.PaymentStatus.PAID, startOfMonth);
        
        return monthlySessions.stream()
                .map(ConsultationSession::getSessionPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private BigDecimal calculateWeeklyRevenue() {
        LocalDateTime startOfWeek = LocalDateTime.now().minusDays(7);
        List<ConsultationSession> weeklySessions = sessionRepository.findByPaymentStatusAndCreatedAtAfter(
                ConsultationSession.PaymentStatus.PAID, startOfWeek);
        
        return weeklySessions.stream()
                .map(ConsultationSession::getSessionPrice)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
    
    private Long getAvailableExpertsCount() {
        // Count experts who are available and not at their maximum capacity
        return expertRepository.countByIsAvailableTrue();
    }
    
    private Double calculateAverageSessionDuration() {
        List<ConsultationSession> completedSessions = sessionRepository.findByStatus(
                ConsultationSession.SessionStatus.COMPLETED);
        
        if (completedSessions.isEmpty()) {
            return 0.0;
        }
        
        long totalMinutes = completedSessions.stream()
                .mapToLong(ConsultationSession::getDurationMinutes)
                .sum();
        
        return (double) totalMinutes / completedSessions.size();
    }
    
    private Double calculateSessionCompletionRate() {
        Long totalSessions = sessionRepository.count();
        Long completedSessions = sessionRepository.countByStatus(ConsultationSession.SessionStatus.COMPLETED);
        
        if (totalSessions == 0) {
            return 0.0;
        }
        
        return (completedSessions.doubleValue() / totalSessions.doubleValue()) * 100.0;
    }
    
    /**
     * Create generic experts for system functionality
     * These are not real people but generic placeholders
     */
    private List<Expert> createGenericExperts() {
        List<Expert> genericExperts = new ArrayList<>();
        
        // Create generic experts with professional but generic names
        genericExperts.add(Expert.builder()
                .name("Security Expert")
                .email("expert1@threatscope.internal")
                .specialization("Data Breach Response")
                .bio("Cybersecurity expert specializing in data breach response and incident management.")
                .hourlyRate(new BigDecimal("150.00"))
                .isAvailable(true)
                .isActive(true)
                .maxConcurrentSessions(0)
                .totalSessions(0)
                .completedSessions(0)
                .timezone("UTC")
                .build());
        
        log.info("🔧 Generated {} generic experts for system functionality", genericExperts.size());
        return genericExperts;
    }
    
}
