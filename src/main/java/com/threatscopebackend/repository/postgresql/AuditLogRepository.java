package com.threatscope.repository.postgresql;

import com.threatscope.entity.postgresql.AuditLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    Page<AuditLog> findByUserIdOrderByCreatedAtDesc(Long userId, Pageable pageable);
    
    Page<AuditLog> findByUserIdAndActionInOrderByCreatedAtDesc(
            Long userId, List<AuditLog.AuditAction> actions, Pageable pageable);
    
    @Query("SELECT al FROM AuditLog al WHERE " +
           "al.user.id = :userId AND " +
           "al.createdAt BETWEEN :startDate AND :endDate " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findByUserIdAndDateRange(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate,
            @Param("endDate") LocalDateTime endDate,
            Pageable pageable);
    
    @Query("SELECT al FROM AuditLog al WHERE " +
           "al.user.id = :userId AND " +
           "al.action = :action AND " +
           "al.createdAt >= :since " +
           "ORDER BY al.createdAt DESC")
    List<AuditLog> findRecentActionsByType(
            @Param("userId") Long userId,
            @Param("action") AuditLog.AuditAction action,
            @Param("since") LocalDateTime since);
    
    @Query("SELECT COUNT(al) > 0 FROM AuditLog al WHERE " +
           "al.user.id = :userId AND " +
           "al.action = :action AND " +
           "al.createdAt >= :since")
    boolean hasActionSince(
            @Param("userId") Long userId,
            @Param("action") AuditLog.AuditAction action,
            @Param("since") LocalDateTime since);
    
    @Query("SELECT al FROM AuditLog al WHERE " +
           "al.user.id = :userId AND " +
           "al.resourceType = :resourceType AND " +
           "al.resourceId = :resourceId " +
           "ORDER BY al.createdAt DESC")
    List<AuditLog> findResourceHistory(
            @Param("userId") Long userId,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);
    
    @Query("SELECT al FROM AuditLog al WHERE " +
           "al.user.id = :userId AND " +
           "al.success = false " +
           "ORDER BY al.createdAt DESC")
    Page<AuditLog> findFailedActions(
            @Param("userId") Long userId,
            Pageable pageable);
    
    @Query("SELECT al.action, COUNT(al) as count FROM AuditLog al " +
           "WHERE al.user.id = :userId " +
           "AND al.createdAt >= :startDate " +
           "GROUP BY al.action " +
           "ORDER BY count DESC")
    List<Object[]> countActionsByType(
            @Param("userId") Long userId,
            @Param("startDate") LocalDateTime startDate);
            
    @Query("SELECT COUNT(al) FROM AuditLog al WHERE al.user.id = :userId")
    long countByUserId(@Param("userId") Long userId);
    
    @Query("SELECT COUNT(al) > 0 FROM AuditLog al WHERE " +
           "al.user.id = :userId AND " +
           "al.action = :action AND " +
           "al.resourceType = :resourceType AND " +
           "al.resourceId = :resourceId")
    boolean existsByUserAndActionAndResource(
            @Param("userId") Long userId,
            @Param("action") AuditLog.AuditAction action,
            @Param("resourceType") String resourceType,
            @Param("resourceId") String resourceId);
}
