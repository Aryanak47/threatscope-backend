package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.ProcessedBreach;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Repository
public interface ProcessedBreachRepository extends JpaRepository<ProcessedBreach, Long> {
    
    // Check if a breach has been processed for a monitoring item (permanent blocking)
    boolean existsByMonitoringItemAndBreachId(MonitoringItem monitoringItem, String breachId);
    
    // Check by content hash as well for additional duplicate detection
    boolean existsByMonitoringItemAndContentHashAndProcessedAtAfter(
            MonitoringItem monitoringItem, 
            String contentHash, 
            LocalDateTime since);
    
    // Cleanup old processed breach records (keep only recent ones)
    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessedBreach p WHERE p.processedAt < :cutoffDate")
    int deleteOldProcessedBreaches(@Param("cutoffDate") LocalDateTime cutoffDate);
    
    // Count processed breaches for monitoring
    long countByMonitoringItemAndProcessedAtAfter(MonitoringItem monitoringItem, LocalDateTime since);
    
    // Delete processed breach when alert is deleted (allows re-detection)
    @Modifying
    @Transactional
    @Query("DELETE FROM ProcessedBreach p WHERE p.breachAlert.id = :alertId")
    int deleteByBreachAlertId(@Param("alertId") Long alertId);
}