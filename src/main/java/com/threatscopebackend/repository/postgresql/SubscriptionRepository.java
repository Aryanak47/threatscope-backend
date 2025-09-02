package com.threatscopebackend.repository.postgresql;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.Subscription;
import com.threatscopebackend.entity.postgresql.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    
    Optional<Subscription> findByUser(User user);
    
    Optional<Subscription> findByUserId(Long userId);
    
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
    
    Optional<Subscription> findByStripeCustomerId(String stripeCustomerId);
    
    List<Subscription> findByStatus(CommonEnums.SubscriptionStatus status);
    
    List<Subscription> findByPlanType(CommonEnums.PlanType planType);
    
    @Query("SELECT s FROM Subscription s WHERE s.status = 'TRIALING' AND s.trialEndDate < :now")
    List<Subscription> findExpiredTrials(@Param("now") LocalDateTime now);
    
    @Query("SELECT s FROM Subscription s WHERE s.currentPeriodEnd < :now AND s.status = 'ACTIVE'")
    List<Subscription> findSubscriptionsToRenew(@Param("now") LocalDateTime now);
    
    @Query("SELECT COUNT(s) FROM Subscription s WHERE s.planType = :planType AND s.status IN ('ACTIVE', 'TRIALING')")
    long countActiveByPlanType(@Param("planType") CommonEnums.PlanType planType);
    
    @Query("SELECT s.planType, COUNT(s) FROM Subscription s WHERE s.status IN ('ACTIVE', 'TRIALING') GROUP BY s.planType")
    List<Object[]> getSubscriptionCountsByPlan();
    
    boolean existsByUser(User user);
}
