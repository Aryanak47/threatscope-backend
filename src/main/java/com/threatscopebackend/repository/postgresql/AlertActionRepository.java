package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.AlertAction;
import com.threatscopebackend.entity.enums.AlertActionType;
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
public interface AlertActionRepository extends JpaRepository<AlertAction, Long> {
    
    // Find by user
    Page<AlertAction> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    // Find by alert
    List<AlertAction> findByBreachAlertIdOrderByCreatedAtDesc(Long breachAlertId);
    
    // Find by action type
    Page<AlertAction> findByUserIdAndActionTypeOrderByCreatedAtDesc(Long userId, AlertActionType actionType, Pageable pageable);
    
    // Find pending service requests
    @Query("SELECT aa FROM AlertAction aa WHERE aa.user.id = :userId AND aa.isServiceRequest = true AND aa.isProcessed = false ORDER BY aa.createdAt DESC")
    List<AlertAction> findPendingServiceRequestsByUserId(@Param("userId") Long userId);
    
    // Find all pending service requests for admin
    @Query("SELECT aa FROM AlertAction aa WHERE aa.isServiceRequest = true AND aa.isProcessed = false ORDER BY aa.createdAt ASC")
    Page<AlertAction> findAllPendingServiceRequests(Pageable pageable);
    
    // Count pending actions by user
    long countByUserIdAndIsProcessedFalse(Long userId);
    
    // Count service requests by user
    long countByUserIdAndIsServiceRequestTrue(Long userId);
    
    // Find recent actions
    @Query("SELECT aa FROM AlertAction aa WHERE aa.user.id = :userId AND aa.createdAt >= :since ORDER BY aa.createdAt DESC")
    List<AlertAction> findRecentActionsByUserId(@Param("userId") Long userId, @Param("since") LocalDateTime since);
    
    // Find by alert and action type
    Optional<AlertAction> findByBreachAlertIdAndActionType(Long breachAlertId, AlertActionType actionType);
    
    // Check if user has existing action for alert
    boolean existsByUserIdAndBreachAlertIdAndActionType(Long userId, Long breachAlertId, AlertActionType actionType);
    
    // Statistics queries
    @Query("SELECT COUNT(aa) FROM AlertAction aa WHERE aa.user.id = :userId AND aa.actionType = :actionType")
    long countByUserIdAndActionType(@Param("userId") Long userId, @Param("actionType") AlertActionType actionType);
    
    @Query("SELECT aa.actionType, COUNT(aa) FROM AlertAction aa WHERE aa.user.id = :userId GROUP BY aa.actionType")
    List<Object[]> getActionStatsByUserId(@Param("userId") Long userId);
    
    // Find overdue service requests
    @Query("SELECT aa FROM AlertAction aa WHERE aa.isServiceRequest = true AND aa.isProcessed = false AND aa.createdAt < :deadline")
    List<AlertAction> findOverdueServiceRequests(@Param("deadline") LocalDateTime deadline);
}
