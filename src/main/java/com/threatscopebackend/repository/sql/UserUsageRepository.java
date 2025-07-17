package com.threatscopebackend.repository.sql;

import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.entity.postgresql.UserUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface UserUsageRepository extends JpaRepository<UserUsage, Long> {
    
    Optional<UserUsage> findByUserAndUsageDate(User user, LocalDate usageDate);
    
    Optional<UserUsage> findByUserIdAndUsageDate(Long userId, LocalDate usageDate);
    
    List<UserUsage> findByUserAndUsageDateBetween(User user, LocalDate startDate, LocalDate endDate);
    
    List<UserUsage> findByUserIdAndUsageDateBetween(Long userId, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(u.searchesCount), 0) FROM UserUsage u WHERE u.user.id = :userId AND u.usageDate BETWEEN :startDate AND :endDate")
    int getTotalSearchesForPeriod(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(u.exportsCount), 0) FROM UserUsage u WHERE u.user.id = :userId AND u.usageDate BETWEEN :startDate AND :endDate")
    int getTotalExportsForPeriod(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(u.apiCallsCount), 0) FROM UserUsage u WHERE u.user.id = :userId AND u.usageDate BETWEEN :startDate AND :endDate")
    int getTotalApiCallsForPeriod(@Param("userId") Long userId, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    @Query("SELECT u FROM UserUsage u WHERE u.user.id = :userId ORDER BY u.usageDate DESC")
    List<UserUsage> findRecentUsageByUserId(@Param("userId") Long userId);
    
    // Admin analytics queries
    @Query("SELECT COALESCE(SUM(u.searchesCount), 0) FROM UserUsage u WHERE u.usageDate = :date")
    int getTotalSearchesForDate(@Param("date") LocalDate date);
    
    @Query("SELECT COUNT(DISTINCT u.user.id) FROM UserUsage u WHERE u.usageDate = :date AND u.searchesCount > 0")
    long getActiveUsersForDate(@Param("date") LocalDate date);
}
