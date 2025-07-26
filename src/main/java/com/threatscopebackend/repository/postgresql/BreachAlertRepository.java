package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface BreachAlertRepository extends JpaRepository<BreachAlert, Long> {
    
    // Find alerts by user
    Page<BreachAlert> findByUser(User user, Pageable pageable);
    
    Page<BreachAlert> findByUserAndStatus(User user, CommonEnums.AlertStatus status, Pageable pageable);
    
    Page<BreachAlert> findByUserAndSeverity(User user, CommonEnums.AlertSeverity severity, Pageable pageable);
    
    Page<BreachAlert> findByUserAndStatusAndSeverity(User user, CommonEnums.AlertStatus status, 
                                                      CommonEnums.AlertSeverity severity, Pageable pageable);
    
    // Find specific alert by ID and user (for security)
    Optional<BreachAlert> findByIdAndUserId(Long id, Long userId);
    
    // Find alerts by monitoring item
    List<BreachAlert> findByMonitoringItemOrderByCreatedAtDesc(MonitoringItem monitoringItem);
    
    Page<BreachAlert> findByMonitoringItem(MonitoringItem monitoringItem, Pageable pageable);
    
    // Find unread alerts
    List<BreachAlert> findByUserAndStatusOrderByCreatedAtDesc(User user, CommonEnums.AlertStatus status);
    
    long countByUserAndStatus(User user, CommonEnums.AlertStatus status);
    
    // Find recent alerts
    @Query("SELECT a FROM BreachAlert a WHERE a.user = :user " +
           "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<BreachAlert> findRecentAlerts(@Param("user") User user, @Param("since") LocalDateTime since);
    
    // ADDED: Find recent alerts for a specific monitoring item (for duplicate detection)
    @Query("SELECT a FROM BreachAlert a WHERE a.monitoringItem = :item " +
           "AND a.createdAt > :since ORDER BY a.createdAt DESC")
    List<BreachAlert> findRecentAlertsForItem(@Param("item") MonitoringItem item, @Param("since") LocalDateTime since);
    
    // Find alerts by severity
    List<BreachAlert> findByUserAndSeverityInOrderByCreatedAtDesc(User user, List<CommonEnums.AlertSeverity> severities);
    
    // Find alerts needing notification
    @Query("SELECT a FROM BreachAlert a WHERE a.notificationSent = false " +
           "AND a.createdAt > :cutoffTime ORDER BY a.severity DESC, a.createdAt ASC")
    List<BreachAlert> findAlertsNeedingNotification(@Param("cutoffTime") LocalDateTime cutoffTime);
    
    // Statistics queries
    @Query("SELECT COUNT(a) FROM BreachAlert a WHERE a.user = :user")
    long countTotalByUser(@Param("user") User user);
    
    @Query("SELECT COUNT(a) FROM BreachAlert a WHERE a.user = :user AND a.status = 'NEW'")
    long countUnreadByUser(@Param("user") User user);
    
    @Query("SELECT a.severity, COUNT(a) FROM BreachAlert a WHERE a.user = :user GROUP BY a.severity")
    List<Object[]> countBySeverityForUser(@Param("user") User user);
    
    @Query("SELECT a.status, COUNT(a) FROM BreachAlert a WHERE a.user = :user GROUP BY a.status")
    List<Object[]> countByStatusForUser(@Param("user") User user);
    
    // Time-based queries
    @Query("SELECT COUNT(a) FROM BreachAlert a WHERE a.user = :user " +
           "AND a.createdAt BETWEEN :startDate AND :endDate")
    long countByUserAndDateRange(@Param("user") User user, 
                                @Param("startDate") LocalDateTime startDate, 
                                @Param("endDate") LocalDateTime endDate);
    
    @Query("SELECT DATE(a.createdAt), COUNT(a) FROM BreachAlert a WHERE a.user = :user " +
           "AND a.createdAt >= :startDate GROUP BY DATE(a.createdAt) ORDER BY DATE(a.createdAt)")
    List<Object[]> getDailyAlertCounts(@Param("user") User user, @Param("startDate") LocalDateTime startDate);
    
    // Bulk operations
    @Modifying
    @Transactional
    @Query("UPDATE BreachAlert a SET a.status = :status WHERE a.user = :user AND a.id IN :alertIds")
    int bulkUpdateStatus(@Param("user") User user, @Param("alertIds") List<Long> alertIds, 
                        @Param("status") CommonEnums.AlertStatus status);
    
    @Modifying
    @Transactional
    @Query("UPDATE BreachAlert a SET a.status = 'VIEWED', a.viewedAt = :viewedTime " +
           "WHERE a.user = :user AND a.status = 'NEW'")
    int markAllAsRead(@Param("user") User user, @Param("viewedTime") LocalDateTime viewedTime);
    
    // Search alerts
    @Query("SELECT a FROM BreachAlert a WHERE a.user = :user " +
           "AND (LOWER(a.title) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(a.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(a.breachSource) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<BreachAlert> searchByUser(@Param("user") User user, @Param("searchTerm") String searchTerm, Pageable pageable);
    
    // Find high priority alerts (CRITICAL and HIGH severity, NEW status)
    @Query("SELECT a FROM BreachAlert a WHERE a.user = :user " +
           "AND a.severity IN ('CRITICAL', 'HIGH') AND a.status = 'NEW' " +
           "ORDER BY a.severity DESC, a.createdAt DESC")
    List<BreachAlert> findHighPriorityAlerts(@Param("user") User user);
    
    // OPTIMIZED: Paginated alerts needing notification
    @Query(value = "SELECT * FROM breach_alerts WHERE notification_sent = false " +
           "AND created_at > :cutoffTime ORDER BY severity DESC, created_at ASC LIMIT :size OFFSET :offset", 
           nativeQuery = true)
    List<BreachAlert> findAlertsNeedingNotificationPaginated(@Param("cutoffTime") LocalDateTime cutoffTime, 
                                                             @Param("offset") int offset, 
                                                             @Param("size") int size);
    
    // OPTIMIZED: Batch cleanup of old archived alerts
    @Modifying
    @Transactional
    @Query(value = "DELETE FROM breach_alerts WHERE status = 'DISMISSED' AND dismissed_at < :cutoffDate LIMIT :batchSize", 
           nativeQuery = true)
    int deleteOldArchivedAlertsBatch(@Param("cutoffDate") LocalDateTime cutoffDate, @Param("batchSize") int batchSize);
    
    // Cleanup old archived alerts (original method)
    @Modifying
    @Transactional
    @Query("DELETE FROM BreachAlert a WHERE a.status = 'DISMISSED' AND a.dismissedAt < :cutoffDate")
    int deleteOldArchivedAlerts(@Param("cutoffDate") LocalDateTime cutoffDate);
}
