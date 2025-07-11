package com.threatscopebackend.repository.sql;


import com.threatscopebackend.entity.postgresql.BreachAlert;
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

    long countByUserIdAndStatus(Long userId, BreachAlert.Status status);
    
    long countByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
}
