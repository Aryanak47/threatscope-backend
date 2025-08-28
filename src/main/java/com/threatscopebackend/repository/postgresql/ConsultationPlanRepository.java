package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.ConsultationPlan;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

@Repository
public interface ConsultationPlanRepository extends JpaRepository<ConsultationPlan, Long> {
    
    // Find active plans
    List<ConsultationPlan> findByIsActiveTrueOrderBySortOrderAsc();
    
    // Find active plans with sorting
    List<ConsultationPlan> findByIsActiveTrue(Sort sort);
    
    // Find by name
    Optional<ConsultationPlan> findByNameIgnoreCase(String name);
    
    // Check if plan exists by name
    boolean existsByNameIgnoreCase(String name);
    
    // Find popular plans
    List<ConsultationPlan> findByIsActiveTrueAndIsPopularTrueOrderBySortOrderAsc();
    
    // Find plans by price range
    @Query("SELECT cp FROM ConsultationPlan cp WHERE cp.isActive = true " +
           "AND cp.price BETWEEN :minPrice AND :maxPrice ORDER BY cp.price ASC")
    List<ConsultationPlan> findByPriceRange(@Param("minPrice") BigDecimal minPrice, 
                                           @Param("maxPrice") BigDecimal maxPrice);
    
    // Find plans by duration range
    @Query("SELECT cp FROM ConsultationPlan cp WHERE cp.isActive = true " +
           "AND cp.sessionDurationMinutes BETWEEN :minDuration AND :maxDuration " +
           "ORDER BY cp.sessionDurationMinutes ASC")
    List<ConsultationPlan> findByDurationRange(@Param("minDuration") Integer minDuration, 
                                              @Param("maxDuration") Integer maxDuration);
    
    // Get plan statistics
    @Query("SELECT COUNT(cp), AVG(cp.price), MIN(cp.price), MAX(cp.price) " +
           "FROM ConsultationPlan cp WHERE cp.isActive = true")
    Object[] getPlanStatistics();
    
    // Count active plans
    long countByIsActiveTrue();
}
