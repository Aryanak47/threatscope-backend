package com.threatscopebackend.service.consultation;

import com.threatscopebackend.dto.consultation.request.*;
import com.threatscopebackend.dto.consultation.response.*;
import com.threatscopebackend.entity.postgresql.*;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.*;
import com.threatscopebackend.security.UserPrincipal;
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
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConsultationService {
    
    private final ConsultationSessionRepository consultationSessionRepository;
    private final ConsultationPlanRepository consultationPlanRepository;
    private final BreachAlertRepository breachAlertRepository;
    private final UserRepository userRepository;
    private final ExpertAssignmentService expertAssignmentService;
    private final PaymentService paymentService;
    private final ChatService chatService;
    
    /**
     * Create a new consultation session
     */
    @Transactional
    public ConsultationSessionResponse createConsultationSession(
            UserPrincipal userPrincipal, 
            CreateConsultationSessionRequest request) {
        
        log.info("Creating consultation session for user: {}, alert: {}, plan: {}", 
                userPrincipal.getEmail(), request.getAlertId(), request.getPlanId());
        
        // Validate user
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        // Validate alert belongs to user (only if alertId is provided)
        BreachAlert alert = null;
        if (request.getAlertId() != null) {
            alert = breachAlertRepository.findById(request.getAlertId())
                    .orElseThrow(() -> new ResourceNotFoundException("Alert not found"));
            
            if (!alert.getUser().getId().equals(user.getId())) {
                throw new IllegalArgumentException("Alert does not belong to user");
            }
            
            // Check for existing active sessions for this alert
            List<ConsultationSession> existingSessions = consultationSessionRepository
                    .findByUserAndAlert(user, request.getAlertId());
            
            boolean hasActiveSession = existingSessions.stream()
                    .anyMatch(session -> session.getStatus() == ConsultationSession.SessionStatus.PENDING ||
                                       session.getStatus() == ConsultationSession.SessionStatus.ASSIGNED ||
                                       session.getStatus() == ConsultationSession.SessionStatus.ACTIVE);
            
            if (hasActiveSession) {
                throw new IllegalArgumentException("You already have an active consultation for this alert");
            }
        }
        
        // Validate plan
        ConsultationPlan plan = consultationPlanRepository.findById(request.getPlanId())
                .orElseThrow(() -> new ResourceNotFoundException("Consultation plan not found"));
        
        if (!plan.isAvailableForPurchase()) {
            throw new IllegalArgumentException("Consultation plan is not available");
        }
        
        // Create consultation session
        ConsultationSession session = ConsultationSession.builder()
                .user(user)
                .plan(plan)
                .triggeringAlert(alert)
                .status(ConsultationSession.SessionStatus.PENDING)
                .sessionPrice(plan.getPrice())
                .paymentStatus(ConsultationSession.PaymentStatus.PENDING)
                .sessionNotes(request.getSessionNotes())
                .build();
        
        session = consultationSessionRepository.save(session);
        
        // Process payment
        try {
            String paymentIntentId = paymentService.createPaymentIntent(
                    session.getId(), 
                    plan.getPrice(), 
                    plan.getCurrency(),
                    user);
            
            session.setPaymentIntentId(paymentIntentId);
            session = consultationSessionRepository.save(session);
            
            log.info("Created consultation session {} with payment intent {}", 
                    session.getId(), paymentIntentId);
            
        } catch (Exception e) {
            log.error("Failed to create payment intent for session {}: {}", session.getId(), e.getMessage());
            session.setStatus(ConsultationSession.SessionStatus.CANCELLED);
            consultationSessionRepository.save(session);
            throw new RuntimeException("Failed to process payment: " + e.getMessage());
        }
        
        return ConsultationSessionResponse.fromEntity(session);
    }
    
    /**
     * Process successful payment and assign expert
     */
    @Transactional
    public void processSuccessfulPayment(Long sessionId, String paymentIntentId) {
        log.info("Processing successful payment for session: {}", sessionId);
        
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        
        log.info("Found session: {} - User: {} - Current status: {} - Payment status: {}", 
                sessionId, session.getUser().getId(), session.getStatus(), session.getPaymentStatus());
        
        // For mock payments, don't validate payment intent ID
        if (!paymentIntentId.startsWith("mock_payment_") && !paymentIntentId.equals(session.getPaymentIntentId())) {
            throw new IllegalArgumentException("Payment intent ID mismatch");
        }
        
        // Update payment status
        session.setPaymentStatus(ConsultationSession.PaymentStatus.PAID);
        
        // Try to assign an expert
        assignExpertToSession(session);
        
        consultationSessionRepository.save(session);
        
        // Send notification to user about payment success and expert assignment
        // TODO: Implement notification service call
        
        log.info("Successfully processed payment and assigned expert for session: {}", sessionId);
    }
    
    /**
     * Assign expert to session
     */
    @Transactional
    public void assignExpertToSession(ConsultationSession session) {
        log.debug("Assigning expert to session: {}", session.getId());
        
        if (session.getExpert() != null) {
            log.warn("Session {} already has an expert assigned", session.getId());
            return;
        }
        
        // Determine breach type and severity from alert
        String breachType = "general";
        String alertSeverity = "MEDIUM";
        
        if (session.getTriggeringAlert() != null) {
            BreachAlert alert = session.getTriggeringAlert();
            breachType = alert.getBreachSource() != null ? alert.getBreachSource() : "general";
            alertSeverity = alert.getSeverity().toString();
        }
        
        // Find best available expert
        Optional<Expert> expertOpt = expertAssignmentService.findBestAvailableExpert(breachType, alertSeverity);
        
        if (expertOpt.isPresent()) {
            Expert expert = expertOpt.get();
            session.assignExpert(expert);
            
            // Create initial system message
            ChatMessage welcomeMessage = ChatMessage.createSystemMessage(session,
                    "A security expert has been assigned to your consultation. " +
                    "They will be with you shortly to discuss your security concern.");
            
            chatService.saveMessage(welcomeMessage);
            
            log.info("Assigned expert {} to session {}", expert.getName(), session.getId());
            
        } else {
            log.warn("No available expert found for session {}", session.getId());
            session.setStatus(ConsultationSession.SessionStatus.PENDING);
            
            // Create system message about waiting for expert
            ChatMessage waitingMessage = ChatMessage.createSystemMessage(session,
                    "Your consultation request has been received. We are finding the best expert for your case. " +
                    "You will be notified once an expert is assigned.");
            
            chatService.saveMessage(waitingMessage);
        }
    }
    
    /**
     * Start a consultation session
     */
    @Transactional
    public ConsultationSessionResponse startSession(Long sessionId, UserPrincipal userPrincipal) {
        log.info("Starting consultation session: {} for user: {}", sessionId, userPrincipal.getEmail());
        
        ConsultationSession session = findSessionByIdAndUser(sessionId, userPrincipal.getId());
        
        if (!session.canStart()) {
            throw new IllegalArgumentException("Session cannot be started at this time");
        }
        
        session.startSession();
        session = consultationSessionRepository.save(session);
        
        // Create session start message
        ChatMessage startMessage = ChatMessage.createSessionStartMessage(session);
        chatService.saveMessage(startMessage);
        
        log.info("Started consultation session: {}", sessionId);
        
        return ConsultationSessionResponse.fromEntity(session);
    }
    
    /**
     * Complete a consultation session
     */
    @Transactional
    public ConsultationSessionResponse completeSession(
            Long sessionId, 
            Long expertId, 
            CompleteSessionRequest request) {
        
        log.info("Completing consultation session: {} by expert: {}", sessionId, expertId);
        
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        
        if (session.getExpert() == null || !session.getExpert().getId().equals(expertId)) {
            throw new IllegalArgumentException("Expert is not assigned to this session");
        }
        
        if (!session.isActive()) {
            throw new IllegalArgumentException("Session is not active");
        }
        
        // Complete the session
        String deliverables = request.getDeliverables() != null ? 
                String.join(", ", request.getDeliverables()) : "";
        
        session.completeSession(request.getSummary(), deliverables);
        
        if (request.getExpertFeedback() != null) {
            session.setExpertFeedback(request.getExpertFeedback());
        }
        
        session = consultationSessionRepository.save(session);
        
        // Update expert's completed sessions count and revenue
        Expert expert = session.getExpert();
        expert.recordCompletedSession(session.getSessionPrice());
        // expertRepository.save(expert); // Will be saved via cascade or separate service call
        
        // Create session end message
        ChatMessage endMessage = ChatMessage.createSessionEndMessage(session);
        chatService.saveMessage(endMessage);
        
        log.info("Completed consultation session: {}", sessionId);
        
        return ConsultationSessionResponse.fromEntity(session);
    }
    

    
    /**
     * Get user's consultation sessions
     */
    @Transactional(readOnly = true)
    public Page<ConsultationSessionResponse> getUserSessions(
            UserPrincipal userPrincipal, 
            int page, 
            int size) {
        
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<ConsultationSession> sessions = consultationSessionRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        
        return sessions.map(ConsultationSessionResponse::fromEntity);
    }
    
    /**
     * Get specific session for user or admin
     */
    @Transactional(readOnly = true)
    public ConsultationSessionResponse getUserSession(Long sessionId, UserPrincipal userPrincipal) {
        ConsultationSession session = findSessionByIdAndUserOrAdmin(sessionId, userPrincipal);
        return ConsultationSessionResponse.fromEntity(session);
    }
    
    /**
     * Get session by ID (admin access)
     */
    @Transactional(readOnly = true)
    public ConsultationSessionResponse getSessionById(Long sessionId) {
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        return ConsultationSessionResponse.fromEntity(session);
    }
    
    /**
     * Start session by ID (admin access)
     */
    @Transactional
    public ConsultationSessionResponse startSessionById(Long sessionId) {
        log.info("Admin starting consultation session: {}", sessionId);
        
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        
        if (!session.canStart()) {
            throw new IllegalArgumentException("Session cannot be started at this time");
        }
        
        session.startSession();
        session = consultationSessionRepository.save(session);
        
        // Create session start message
        ChatMessage startMessage = ChatMessage.createSessionStartMessage(session);
        chatService.saveMessage(startMessage);
        
        log.info("Admin started consultation session: {}", sessionId);
        
        return ConsultationSessionResponse.fromEntity(session);
    }
    
    /**
     * Get active sessions for user
     */
    @Transactional(readOnly = true)
    public List<ConsultationSessionResponse> getActiveSessionsForUser(UserPrincipal userPrincipal) {
        User user = userRepository.findById(userPrincipal.getId())
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        
        List<ConsultationSession> sessions = consultationSessionRepository.findActiveSessionsForUser(user);
        
        return sessions.stream()
                .map(ConsultationSessionResponse::fromEntity)
                .toList();
    }
    
    /**
     * Cancel a consultation session
     */
    @Transactional
    public ConsultationSessionResponse cancelSession(Long sessionId, UserPrincipal userPrincipal) {
        log.info("Cancelling consultation session: {} for user: {}", sessionId, userPrincipal.getEmail());
        
        ConsultationSession session = findSessionByIdAndUser(sessionId, userPrincipal.getId());
        
        if (session.getStatus() == ConsultationSession.SessionStatus.COMPLETED ||
            session.getStatus() == ConsultationSession.SessionStatus.CANCELLED) {
            throw new IllegalArgumentException("Session cannot be cancelled");
        }
        
        session.setStatus(ConsultationSession.SessionStatus.CANCELLED);
        session = consultationSessionRepository.save(session);
        
        // Process refund if payment was made
        if (session.getPaymentStatus() == ConsultationSession.PaymentStatus.PAID) {
            try {
                paymentService.processRefund(session.getPaymentIntentId(), session.getSessionPrice());
                session.setPaymentStatus(ConsultationSession.PaymentStatus.REFUNDED);
                session = consultationSessionRepository.save(session);
            } catch (Exception e) {
                log.error("Failed to process refund for session {}: {}", sessionId, e.getMessage());
            }
        }
        
        log.info("Cancelled consultation session: {}", sessionId);
        
        return ConsultationSessionResponse.fromEntity(session);
    }
    
    /**
     * Get consultation statistics
     */
    @Transactional(readOnly = true)
    public ConsultationStatistics getConsultationStatistics() {
        Object[] sessionStats = consultationSessionRepository.getSessionStatistics();
        Object[] revenueStats = consultationSessionRepository.getRevenueStatistics(LocalDateTime.now().minusDays(1));
        Object[] expertStats = expertAssignmentService.getExpertStatistics();
        
        return ConsultationStatistics.builder()
                .totalSessions((Long) sessionStats[0])
                .completedSessions((Long) sessionStats[1])
                .activeSessions((Long) sessionStats[2])
                .totalRevenue((BigDecimal) revenueStats[0])
                .dailyRevenue((BigDecimal) revenueStats[0]) // Same as total for daily
                .totalExperts(((Number) expertStats[0]).intValue())
                .availableExperts(((Number) expertStats[0]).intValue()) // Simplified
                .build();
    }
    
    // Helper methods
    
    private ConsultationSession findSessionByIdAndUserOrAdmin(Long sessionId, UserPrincipal userPrincipal) {
        log.info("🔍 DEBUG: Looking for session {} for user: {} (ID: {})", sessionId, userPrincipal.getEmail(), userPrincipal.getId());
        
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        
        log.info("🔍 DEBUG: Found session {} - Session user ID: {} - Status: {} - Expired: {}", 
                sessionId, session.getUser().getId(), session.getStatus(), session.isExpired());
        
        // Check if user owns the session
        boolean isOwner = session.getUser().getId().equals(userPrincipal.getId());
        
        // Check if user is admin
        boolean isAdmin = userPrincipal.getAuthorities().stream()
                .anyMatch(authority -> 
                    authority.getAuthority().equals("ROLE_ADMIN") || 
                    authority.getAuthority().equals("ROLE_SUPER_ADMIN"));
        
        // Check if user is assigned expert (SAFE: compare IDs)
        boolean isAssignedExpert = false;
        if (session.getExpert() != null) {
            Long expertId = session.getExpert().getId();
            isAssignedExpert = expertId != null && expertId.equals(userPrincipal.getId());
        }
        
        boolean hasAccess = isOwner || isAdmin || isAssignedExpert;
        
        if (!hasAccess) {
            log.error("❌ DEBUG: Access denied! Owner: {}, Admin: {}, Expert: {}", isOwner, isAdmin, isAssignedExpert);
            throw new IllegalArgumentException("User does not have access to this session");
        }
        
        // ENHANCED: Check session expiration with admin override
        if (session.isExpired()) {
            if (isAdmin) {
                log.info("⚠️ DEBUG: Admin accessing expired session {} - Access granted", sessionId);
                // Admins can access expired sessions
            } else if (isOwner && !session.canUserAccess()) {
                log.error("❌ DEBUG: Session {} has expired and user cannot access it", sessionId);
                throw new IllegalArgumentException("Your consultation session has expired. Thank you for using ThreatScope! Book another consultation");
            } else if (isAssignedExpert) {
                log.info("⚠️ DEBUG: Expert accessing expired session {} - Limited access granted", sessionId);
                // Experts can view expired sessions but with limited functionality
            }
        }
        
        log.info("✅ DEBUG: Access granted! Owner: {}, Admin: {}, Expert: {} - Expired: {} (Admin override: {})", 
                isOwner, isAdmin, isAssignedExpert, session.isExpired(), isAdmin && session.isExpired());
        return session;
    }
    
    private ConsultationSession findSessionByIdAndUser(Long sessionId, Long userId) {
        log.info("🔍 DEBUG: Looking for session {} for user ID: {}", sessionId, userId);
        
        ConsultationSession session = consultationSessionRepository.findById(sessionId)
                .orElseThrow(() -> new ResourceNotFoundException("Consultation session not found"));
        
        log.info("🔍 DEBUG: Found session {} - Session user ID: {}, Requested user ID: {} - Status: {} - Expired: {}", 
                sessionId, session.getUser().getId(), userId, session.getStatus(), session.isExpired());
        
        if (!session.getUser().getId().equals(userId)) {
            log.error("❌ DEBUG: Session ownership mismatch! Session user: {}, Requested user: {}", 
                    session.getUser().getId(), userId);
            throw new IllegalArgumentException("Session does not belong to user");
        }
        
        // ENHANCED: Check if user can access the session (respects expiration)
        if (!session.canUserAccess()) {
            log.error("❌ DEBUG: User cannot access session {} - Expired: {}, Status: {}", 
                    sessionId, session.isExpired(), session.getStatus());
            throw new IllegalArgumentException("Your consultation session has expired. Thank you for using ThreatScope! Book another consultation");
        }
        
        log.info("✅ DEBUG: Session ownership and access verified successfully");
        return session;
    }
    
    /**
     * Cleanup expired sessions (scheduled task)
     */
    @Transactional
    public void cleanupExpiredSessions() {
        List<ConsultationSession> expiredSessions = consultationSessionRepository
                .findExpiredSessions(LocalDateTime.now());
        
        for (ConsultationSession session : expiredSessions) {
            session.setStatus(ConsultationSession.SessionStatus.EXPIRED);
            
            // Process refund if payment was made
            if (session.getPaymentStatus() == ConsultationSession.PaymentStatus.PAID) {
                try {
                    paymentService.processRefund(session.getPaymentIntentId(), session.getSessionPrice());
                    session.setPaymentStatus(ConsultationSession.PaymentStatus.REFUNDED);
                } catch (Exception e) {
                    log.error("Failed to process refund for expired session {}: {}", 
                            session.getId(), e.getMessage());
                }
            }
        }
        
        if (!expiredSessions.isEmpty()) {
            consultationSessionRepository.saveAll(expiredSessions);
            log.info("Cleaned up {} expired consultation sessions", expiredSessions.size());
        }
    }
}
