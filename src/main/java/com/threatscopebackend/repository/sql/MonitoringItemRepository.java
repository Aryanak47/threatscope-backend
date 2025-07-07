package com.threatscope.repository.sql;

import com.threatscope.entity.MonitoringItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface MonitoringItemRepository extends JpaRepository<MonitoringItem, Long> {
    
    Page<MonitoringItem> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Optional<MonitoringItem> findByIdAndUserId(Long id, Long userId);
    
    List<MonitoringItem> findByIsActiveTrue();
    
    long countByUserId(Long userId);
    
    List<MonitoringItem> findByUserIdAndIsActiveTrue(Long userId);
}
