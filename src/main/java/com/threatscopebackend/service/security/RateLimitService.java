package com.threatscopebackend.service.security;


import com.threatscopebackend.entity.postgresql.Subscription;
import com.threatscopebackend.entity.postgresql.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RateLimitService {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final UserService userService;
    
    public void checkSearchLimit(Long userId) {
        User user = userService.findById(userId);
        Subscription subscription = user.getSubscription();
        
        int dailyLimit = getDailySearchLimit(subscription);
        if (dailyLimit == -1) return; // Unlimited
        
        String key = "search_limit:" + userId + ":" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String current = redisTemplate.opsForValue().get(key);
        
        int currentCount = current != null ? Integer.parseInt(current) : 0;
        
        if (currentCount >= dailyLimit) {
            throw new RuntimeException("Daily search limit exceeded");
        }
        
        redisTemplate.opsForValue().increment(key);
        redisTemplate.expire(key, Duration.ofDays(1));
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
}
