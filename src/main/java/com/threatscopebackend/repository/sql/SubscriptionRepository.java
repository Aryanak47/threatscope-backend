package com.threatscopebackend.repository.sql;


import com.threatscopebackend.entity.postgresql.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, Long> {
    
    Optional<Subscription> findByUser_Id(Long userId);
    
    List<Subscription> findByStatus(Subscription.Status status);
    
    long countByStatus(Subscription.Status status);
    
    List<Subscription> findByCreatedAtBetween(LocalDateTime start, LocalDateTime end);
    
    List<Subscription> findByCurrentPeriodEndBefore(LocalDateTime date);
    
    Optional<Subscription> findByStripeSubscriptionId(String stripeSubscriptionId);
}
