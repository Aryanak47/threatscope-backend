package com.threatscopebackend.service.core;



import com.threatscopebackend.entity.postgresql.Subscription;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.repository.postgresql.UserRepository;
import com.threatscopebackend.repository.sql.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class SubscriptionService {
    
    private final SubscriptionRepository subscriptionRepository;
    private final UserRepository userRepository;
    
    public void checkMonitoringLimits(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
       Subscription subscription = user.getSubscription();
        int limit = getMonitoringLimit(subscription);
        
        if (limit == -1) return; // Unlimited
        
        // Check current monitoring items count
        long currentCount = getMonitoringItemsCount(userId);
        
        if (currentCount >= limit) {
            throw new RuntimeException("Monitoring items limit exceeded");
        }
    }
    
    public void checkExportLimits(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Subscription subscription = user.getSubscription();
        int monthlyLimit = getExportLimit(subscription);
        
        if (monthlyLimit == -1) return; // Unlimited
        
        // Check current month's exports
        long currentCount = getCurrentMonthExports(userId);
        
        if (currentCount >= monthlyLimit) {
            throw new RuntimeException("Monthly export limit exceeded");
        }
    }
    
    public long getRemainingSearches(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        Subscription subscription = user.getSubscription();
        int dailyLimit = getDailySearchLimit(subscription);
        
        if (dailyLimit == -1) return -1; // Unlimited
        
        long todayCount = getTodaySearchCount(userId);
        return Math.max(0, dailyLimit - todayCount);
    }
    
    private int getMonitoringLimit(Subscription subscription) {
        if (subscription == null) return 10; // Free tier
        
        String planName = subscription.getPlanType().name();
        return switch (planName.toLowerCase()) {
            case "starter" -> 100;
            case "professional" -> 1000;
            case "enterprise" -> -1; // Unlimited
            default -> 10; // Free tier
        };
    }
    
    private int getExportLimit(Subscription subscription) {
        if (subscription == null) return 0; // Free tier
        
        String planName = subscription.getPlanType().name();
        return switch (planName.toLowerCase()) {
            case "starter" -> 10;
            case "professional" -> 100;
            case "enterprise" -> -1; // Unlimited
            default -> 0; // Free tier
        };
    }
    
    private int getDailySearchLimit(Subscription subscription) {
        if (subscription == null) return 5; // Free tier
        
        String planName = subscription.getPlanType().name();
        return switch (planName.toLowerCase()) {
            case "starter" -> 50;
            case "professional" -> 500;
            case "enterprise" -> -1; // Unlimited
            default -> 5; // Free tier
        };
    }
    
    // These would integrate with actual counting services
    private long getMonitoringItemsCount(Long userId) { return 0; }
    private long getCurrentMonthExports(Long userId) { return 0; }
    private long getTodaySearchCount(Long userId) { return 0; }
}
