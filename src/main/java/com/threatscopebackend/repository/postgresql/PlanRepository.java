package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.postgresql.Plan;
import com.threatscopebackend.entity.enums.CommonEnums;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PlanRepository extends JpaRepository<Plan, Long> {
    
    Optional<Plan> findByPlanType(CommonEnums.PlanType planType);
    
    Optional<Plan> findByDisplayNameIgnoreCase(String displayName);
    
    List<Plan> findByIsActiveTrue();
    
    List<Plan> findByIsPublicTrueAndIsActiveTrueOrderBySortOrder();
    
    @Query("SELECT p FROM Plan p WHERE p.isActive = true AND p.price = 0")
    Optional<Plan> findFreePlan();
    
    @Query("SELECT p FROM Plan p WHERE p.isActive = true AND p.price > 0 ORDER BY p.price ASC")
    List<Plan> findPaidPlansOrderByPrice();
    
    boolean existsByPlanType(CommonEnums.PlanType planType);
    
    boolean existsByDisplayNameIgnoreCase(String displayName);
    
    // Convenience method to find by plan type name string
    @Query("SELECT p FROM Plan p WHERE p.planType = :planType")
    Optional<Plan> findByPlanTypeName(@Param("planType") CommonEnums.PlanType planType);
}
