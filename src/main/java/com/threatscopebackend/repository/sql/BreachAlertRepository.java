package com.threatscope.repository.sql;

import com.threatscope.entity.BreachAlert;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface BreachAlertRepository extends JpaRepository<BreachAlert, Long> {
    
    Page<BreachAlert> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Optional<BreachAlert> findByIdAndUserId(Long id, Long userId);
    
    boolean existsByMonitoringItemIdAndBreachId(Long monitoringItemId, String breachId);
    
    long countByUserIdAndStatus(Long userId, BreachAlert.AlertStatus status);
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
