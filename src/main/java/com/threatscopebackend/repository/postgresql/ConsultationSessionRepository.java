package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.ConsultationSession;
import com.threatscopebackend.entity.postgresql.Expert;
import com.threatscopebackend.entity.postgresql.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationSessionRepository extends JpaRepository<ConsultationSession, Long> {
    
    // Find sessions by user
    Page<ConsultationSession> findByUserOrderByCreatedAtDesc(User user, Pageable pageable);
    
    // Find sessions by expert
    Page<ConsultationSession> findByExpertOrderByCreatedAtDesc(Expert expert, Pageable pageable);
    
    // Find sessions by status
    List<ConsultationSession> findByStatus(ConsultationSession.SessionStatus status);
    
    // Find sessions by user and status
    List<ConsultationSession> findByUserAndStatus(User user, ConsultationSession.SessionStatus status);
    
    // Find sessions by expert and status
    List<ConsultationSession> findByExpertAndStatus(Expert expert, ConsultationSession.SessionStatus status);
    
    // Find active sessions for user
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.user = :user " +
           "AND cs.status IN ('ASSIGNED', 'ACTIVE') ORDER BY cs.createdAt DESC")
    List<ConsultationSession> findActiveSessionsForUser(@Param("user") User user);
    
    // Find active sessions for expert
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.expert = :expert " +
           "AND cs.status IN ('ASSIGNED', 'ACTIVE') ORDER BY cs.createdAt DESC")
    List<ConsultationSession> findActiveSessionsForExpert(@Param("expert") Expert expert);
    
    // Count active sessions for expert
    @Query("SELECT COUNT(cs) FROM ConsultationSession cs WHERE cs.expert = :expert " +
           "AND cs.status IN ('ASSIGNED', 'ACTIVE')")
    long countActiveSessionsForExpert(@Param("expert") Expert expert);
    
    // Find pending sessions (no expert assigned)
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.status = 'PENDING' " +
           "AND cs.paymentStatus = 'PAID' ORDER BY cs.createdAt ASC")
    List<ConsultationSession> findPendingAssignmentSessions();
    
    // Find expired sessions
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.status IN ('PENDING', 'ASSIGNED') " +
           "AND cs.expiresAt < :now")
    List<ConsultationSession> findExpiredSessions(@Param("now") LocalDateTime now);
    
    // Find sessions by user and alert
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.user = :user " +
           "AND cs.triggeringAlert.id = :alertId")
    List<ConsultationSession> findByUserAndAlert(@Param("user") User user, @Param("alertId") Long alertId);
    
    // Find recent sessions for user
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.user = :user " +
           "AND cs.createdAt >= :since ORDER BY cs.createdAt DESC")
    List<ConsultationSession> findRecentSessionsForUser(@Param("user") User user, 
                                                        @Param("since") LocalDateTime since);
    
    // Get session statistics
    @Query("SELECT COUNT(cs), " +
           "COUNT(CASE WHEN cs.status = 'COMPLETED' THEN 1 END), " +
           "COUNT(CASE WHEN cs.status = 'ACTIVE' THEN 1 END), " +
           "AVG(cs.userRating) " +
           "FROM ConsultationSession cs")
    Object[] getSessionStatistics();
    
    // Get revenue statistics
    @Query("SELECT SUM(cs.sessionPrice), COUNT(cs) FROM ConsultationSession cs " +
           "WHERE cs.paymentStatus = 'PAID' AND cs.createdAt >= :since")
    Object[] getRevenueStatistics(@Param("since") LocalDateTime since);
    
    // Find sessions needing follow-up
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.status = 'COMPLETED' " +
           "AND cs.plan.includesFollowUp = true " +
           "AND cs.completedAt >= :followUpCutoff")
    List<ConsultationSession> findSessionsNeedingFollowUp(@Param("followUpCutoff") LocalDateTime followUpCutoff);
    
    // Advanced search with filters (consolidated version)
    @Query("SELECT cs FROM ConsultationSession cs WHERE " +
           "(COALESCE(:search, '') = '' OR (" +
           "LOWER(cs.sessionNotes) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(cs.expertSummary) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(cs.user.email) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(cs.user.firstName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "LOWER(cs.user.lastName) LIKE LOWER(CONCAT('%', :search, '%')) OR " +
           "(cs.expert IS NOT NULL AND LOWER(cs.expert.name) LIKE LOWER(CONCAT('%', :search, '%'))))) AND " +
           "(COALESCE(:status, '') = '' OR cs.status = :status) AND " +
           "(COALESCE(:paymentStatus, '') = '' OR cs.paymentStatus = :paymentStatus)")
    Page<ConsultationSession> searchSessionsWithFilters(@Param("search") String search,
                                                        @Param("status") String status,
                                                        @Param("paymentStatus") String paymentStatus,
                                                        Pageable pageable);
    
    // Find sessions by payment status
    List<ConsultationSession> findByPaymentStatus(ConsultationSession.PaymentStatus paymentStatus);
    
    // Find sessions for admin dashboard
    @Query("SELECT cs FROM ConsultationSession cs WHERE " +
           "(:status IS NULL OR cs.status = :status) AND " +
           "(:expertId IS NULL OR cs.expert.id = :expertId) " +
           "ORDER BY cs.createdAt DESC")
    Page<ConsultationSession> findForAdmin(@Param("status") ConsultationSession.SessionStatus status,
                                          @Param("expertId") Long expertId,
                                          Pageable pageable);
    
    // Count sessions by status for admin
    @Query("SELECT cs.status, COUNT(cs) FROM ConsultationSession cs GROUP BY cs.status")
    List<Object[]> countSessionsByStatus();
    
    // Get daily session counts for analytics
    @Query("SELECT DATE(cs.createdAt), COUNT(cs) FROM ConsultationSession cs " +
           "WHERE cs.createdAt >= :since GROUP BY DATE(cs.createdAt) ORDER BY DATE(cs.createdAt)")
    List<Object[]> getDailySessionCounts(@Param("since") LocalDateTime since);
    
    // ===== ADMIN CONSULTATION METHODS =====
    
    // Find by status and payment status
    Page<ConsultationSession> findByStatusAndPaymentStatus(
            ConsultationSession.SessionStatus status, 
            ConsultationSession.PaymentStatus paymentStatus, 
            Pageable pageable);
    
    // Find by status with pagination
    Page<ConsultationSession> findByStatus(ConsultationSession.SessionStatus status, Pageable pageable);
    
    // Find by payment status with pagination
    Page<ConsultationSession> findByPaymentStatus(ConsultationSession.PaymentStatus paymentStatus, Pageable pageable);
    
    // Count by status
    Long countByStatus(ConsultationSession.SessionStatus status);
    
    // Count by payment status
    Long countByPaymentStatus(ConsultationSession.PaymentStatus paymentStatus);
    
    // Find by status ordered by created date
    List<ConsultationSession> findByStatusOrderByCreatedAtAsc(ConsultationSession.SessionStatus status);
    
    // Find by payment status and created after date
    List<ConsultationSession> findByPaymentStatusAndCreatedAtAfter(
            ConsultationSession.PaymentStatus paymentStatus, 
            LocalDateTime createdAt);
    
    // Count by expert and status list
    Long countByExpertAndStatusIn(Expert expert, List<ConsultationSession.SessionStatus> statuses);
    
    // Find recent sessions (created within specified minutes)
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.createdAt >= :since ORDER BY cs.createdAt DESC")
    List<ConsultationSession> findCreatedAfter(@Param("since") LocalDateTime since);
    
    // Enhanced pagination for user sessions with better ordering
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.user = :user " +
           "ORDER BY " +
           "CASE " +
           "  WHEN cs.status IN ('PENDING', 'ASSIGNED', 'ACTIVE') THEN 1 " +
           "  WHEN cs.status = 'COMPLETED' THEN 2 " +
           "  ELSE 3 " +
           "END, " +
           "cs.createdAt DESC")
    Page<ConsultationSession> findByUserOrderByStatusPriorityAndCreatedAtDesc(User user, Pageable pageable);

    // Find sessions by user and not expired
    @Query("SELECT cs FROM ConsultationSession cs WHERE cs.user = :user " +
           "AND ((cs.adminExtendedUntil IS NULL AND cs.expiresAt >= :now) OR " +
           "     (cs.adminExtendedUntil IS NOT NULL AND cs.adminExtendedUntil >= :now)) " +
           "ORDER BY cs.createdAt DESC")
    Page<ConsultationSession> findByUserAndNotExpiredOrderByCreatedAtDesc(
            User user, 
            @Param("now") LocalDateTime now, 
            Pageable pageable);
}
