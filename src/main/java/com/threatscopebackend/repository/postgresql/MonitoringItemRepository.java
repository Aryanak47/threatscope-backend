package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
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
public interface MonitoringItemRepository extends JpaRepository<MonitoringItem, Long> {
    
    // Find monitoring items by user
    List<MonitoringItem> findByUserAndIsActiveTrue(User user);
    
    Page<MonitoringItem> findByUser(User user, Pageable pageable);
    
    Page<MonitoringItem> findByUserAndIsActive(User user, boolean isActive, Pageable pageable);
    
    // Find by monitoring type
    List<MonitoringItem> findByMonitorTypeAndIsActiveTrue(CommonEnums.MonitorType monitorType);
    
    List<MonitoringItem> findByUserAndMonitorTypeAndIsActiveTrue(User user, CommonEnums.MonitorType monitorType);
    
    // Find by target value (for duplicate checking) - case insensitive
    Optional<MonitoringItem> findByUserAndTargetValueAndMonitorTypeAndIsActiveTrue(
        User user, String targetValue, CommonEnums.MonitorType monitorType);
    
    // Enhanced duplicate check - case insensitive with trimming
    @Query("SELECT m FROM MonitoringItem m WHERE m.user = :user " +
           "AND m.monitorType = :type AND LOWER(TRIM(m.targetValue)) = LOWER(TRIM(:target))")
    Optional<MonitoringItem> findByUserAndTypeAndTargetCaseInsensitive(
        @Param("user") User user,
        @Param("type") CommonEnums.MonitorType type, 
        @Param("target") String target
    );
    
    // Check both active and inactive items for comprehensive duplicate checking
    @Query("SELECT m FROM MonitoringItem m WHERE m.user = :user " +
           "AND m.monitorType = :type AND LOWER(TRIM(m.targetValue)) = LOWER(TRIM(:target)) " +
           "AND m.isActive = :active")
    Optional<MonitoringItem> findByUserAndTypeAndTargetAndActive(
        @Param("user") User user,
        @Param("type") CommonEnums.MonitorType type, 
        @Param("target") String target,
        @Param("active") Boolean active
    );
    
    // Find items that need checking
    @Query("SELECT m FROM MonitoringItem m WHERE m.isActive = true " +
           "AND (m.lastChecked IS NULL OR m.lastChecked < :checkTime)")
    List<MonitoringItem> findItemsNeedingCheck(@Param("checkTime") LocalDateTime checkTime);
    
    // Find items by frequency for scheduled checking
    List<MonitoringItem> findByFrequencyAndIsActiveTrue(CommonEnums.MonitorFrequency frequency);
    
    // Count active monitoring items by user
    long countByUserAndIsActiveTrue(User user);
    
    // Count by monitor type
    @Query("SELECT m.monitorType, COUNT(m) FROM MonitoringItem m " +
           "WHERE m.user = :user AND m.isActive = true GROUP BY m.monitorType")
    List<Object[]> countByUserAndMonitorType(@Param("user") User user);
    
    // Find items with recent alerts
    @Query("SELECT m FROM MonitoringItem m WHERE m.user = :user " +
           "AND m.lastAlertSent > :since ORDER BY m.lastAlertSent DESC")
    List<MonitoringItem> findRecentAlertsForUser(@Param("user") User user, @Param("since") LocalDateTime since);
    
    // Statistics queries
    @Query("SELECT COUNT(m) FROM MonitoringItem m WHERE m.user = :user AND m.isActive = true")
    long countActiveByUser(@Param("user") User user);
    
    @Query("SELECT COUNT(m) FROM MonitoringItem m WHERE m.user = :user AND m.alertCount > 0")
    long countWithAlertsForUser(@Param("user") User user);
    
    @Query("SELECT SUM(m.alertCount) FROM MonitoringItem m WHERE m.user = :user")
    Long getTotalAlertsForUser(@Param("user") User user);
    
    // Search monitoring items
    @Query("SELECT m FROM MonitoringItem m WHERE m.user = :user " +
           "AND (LOWER(m.monitorName) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(m.targetValue) LIKE LOWER(CONCAT('%', :searchTerm, '%')) " +
           "OR LOWER(m.description) LIKE LOWER(CONCAT('%', :searchTerm, '%')))")
    Page<MonitoringItem> searchByUser(@Param("user") User user, @Param("searchTerm") String searchTerm, Pageable pageable);
}
