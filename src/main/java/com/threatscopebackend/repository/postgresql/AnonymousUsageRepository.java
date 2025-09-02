package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.AnonymousUsage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface AnonymousUsageRepository extends JpaRepository<AnonymousUsage, Long> {
    
    Optional<AnonymousUsage> findByIpAddressAndUsageDate(String ipAddress, LocalDate usageDate);
    
    List<AnonymousUsage> findByIpAddressAndUsageDateBetween(String ipAddress, LocalDate startDate, LocalDate endDate);
    
    @Query("SELECT COALESCE(SUM(a.searchesCount), 0) FROM AnonymousUsage a WHERE a.ipAddress = :ipAddress AND a.usageDate BETWEEN :startDate AND :endDate")
    int getTotalSearchesForIpInPeriod(@Param("ipAddress") String ipAddress, @Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);
    
    // Admin analytics
    @Query("SELECT COALESCE(SUM(a.searchesCount), 0) FROM AnonymousUsage a WHERE a.usageDate = :date")
    int getTotalAnonymousSearchesForDate(@Param("date") LocalDate date);
    
    @Query("SELECT COUNT(DISTINCT a.ipAddress) FROM AnonymousUsage a WHERE a.usageDate = :date AND a.searchesCount > 0")
    long getUniqueAnonymousUsersForDate(@Param("date") LocalDate date);
    
    // Detect potential abuse patterns
    @Query("SELECT a FROM AnonymousUsage a WHERE a.usageDate = :date AND a.searchesCount > :threshold ORDER BY a.searchesCount DESC")
    List<AnonymousUsage> findHighUsageIpsForDate(@Param("date") LocalDate date, @Param("threshold") int threshold);
}
