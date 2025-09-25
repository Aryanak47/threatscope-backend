package com.threatscopebackend.service.core;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.*;
import com.threatscopebackend.repository.postgresql.AnonymousUsageRepository;
import com.threatscopebackend.repository.postgresql.UserUsageRepository;
import com.threatscopebackend.security.UserPrincipal;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.servlet.http.HttpServletRequest;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UsageService {
    
    private final UserUsageRepository userUsageRepository;
    private final AnonymousUsageRepository anonymousUsageRepository;
    private final SystemSettingsService systemSettingsService;
    private final UserService userService;
    
    public enum UsageType {
        SEARCH,
        EXPORT,
        API_CALL,
        MONITORING_ITEM
    }
    
    /**
     * Check if a user can perform an action based on their subscription and current usage
     */
    public boolean canPerformAction(UserPrincipal userPrincipal, UsageType usageType) {
        if (userPrincipal == null) {
            return false; // Should not happen for authenticated users
        }
        
        User user = userService.findById(userPrincipal.getId());
        if (user == null || !user.isActive()) {
            return false;
        }
        
        LocalDate today = LocalDate.now();
        UserUsage usage = getUserUsageForDate(user, today);
        
        // Get user's subscription plan
        Subscription subscription = user.getSubscription();
        CommonEnums.PlanType planType = subscription != null ? subscription.getPlanType() : CommonEnums.PlanType.FREE;
        
        return switch (usageType) {
            case SEARCH -> canPerformSearch(usage, planType);
            case EXPORT -> canPerformExport(usage, planType);
            case API_CALL -> canPerformApiCall(usage, planType);
            case MONITORING_ITEM -> canCreateMonitoringItem(usage, planType);
        };
    }
    
    /**
     * Check if an anonymous user (by IP) can perform a search
     */
    public boolean canAnonymousUserSearch(String ipAddress) {
        LocalDate today = LocalDate.now();
        AnonymousUsage usage = getAnonymousUsageForDate(ipAddress, today);
        int limit = systemSettingsService.getAnonymousDailySearchLimit();
        
        return usage.canSearch(limit);
    }
    
    /**
     * Record usage for authenticated user
     */
    @Transactional
    public void recordUsage(UserPrincipal userPrincipal, UsageType usageType) {
        if (userPrincipal == null) {
            log.warn("Attempted to record usage for null user principal");
            return;
        }
        
        User user = userService.findById(userPrincipal.getId());
        if (user == null) {
            log.warn("User not found for ID: {}", userPrincipal.getId());
            return;
        }
        
        LocalDate today = LocalDate.now();
        UserUsage usage = getUserUsageForDate(user, today);
        
        switch (usageType) {
            case SEARCH -> usage.incrementSearches();
            case EXPORT -> usage.incrementExports();
            case API_CALL -> usage.incrementApiCalls();
            case MONITORING_ITEM -> usage.incrementMonitoringItems();
        }
        
        userUsageRepository.save(usage);
        log.debug("Recorded {} usage for user {}", usageType, user.getId());
    }
    
    /**
     * Record search usage for anonymous user
     */
    @Transactional
    public void recordAnonymousUsage(String ipAddress, HttpServletRequest request) {
        LocalDate today = LocalDate.now();
        AnonymousUsage usage = getAnonymousUsageForDate(ipAddress, today);
        
        // Set additional tracking info on first use
        if (usage.getSearchesCount() == 0) {
            usage.setUserAgent(request.getHeader("User-Agent"));
            // Could add country detection here using IP geolocation service
        }
        
        usage.incrementSearches();
        anonymousUsageRepository.save(usage);
        log.debug("Recorded anonymous search for IP: {}", ipAddress);
    }
    
    /**
     * Get or create usage record for user and date
     */
    private UserUsage getUserUsageForDate(User user, LocalDate date) {
        return userUsageRepository.findByUserAndUsageDate(user, date)
                .orElseGet(() -> {
                    UserUsage newUsage = new UserUsage();
                    newUsage.setUser(user);
                    newUsage.setUsageDate(date);
                    return userUsageRepository.save(newUsage);
                });
    }
    
    /**
     * Get or create anonymous usage record for IP and date
     */
    private AnonymousUsage getAnonymousUsageForDate(String ipAddress, LocalDate date) {
        return anonymousUsageRepository.findByIpAddressAndUsageDate(ipAddress, date)
                .orElseGet(() -> {
                    AnonymousUsage newUsage = new AnonymousUsage();
                    newUsage.setIpAddress(ipAddress);
                    newUsage.setUsageDate(date);
                    return anonymousUsageRepository.save(newUsage);
                });
    }
    
    private boolean canPerformSearch(UserUsage usage, CommonEnums.PlanType planType) {
        return switch (planType) {
            case FREE -> usage.canSearch(systemSettingsService.getFreeDailySearchLimit());
            case BASIC -> usage.canSearch(systemSettingsService.getBasicDailySearchLimit());
            case PROFESSIONAL -> canPerformHourlySearch(usage, systemSettingsService.getProfessionalHourlySearchLimit());
            case ENTERPRISE -> canPerformHourlySearch(usage, systemSettingsService.getEnterpriseHourlySearchLimit());
            case CUSTOM -> true; // Custom plans have no limits by default
        };
    }
    
    private boolean canPerformExport(UserUsage usage, CommonEnums.PlanType planType) {
        int dailyLimit = switch (planType) {
            case FREE -> systemSettingsService.getIntegerValue("free.daily_exports", 3);
            case BASIC -> systemSettingsService.getIntegerValue("basic.daily_exports", 10);
            case PROFESSIONAL -> systemSettingsService.getIntegerValue("professional.daily_exports", 50);
            case ENTERPRISE -> systemSettingsService.getIntegerValue("enterprise.daily_exports", 500);
            case CUSTOM -> Integer.MAX_VALUE;
        };
        return usage.canExport(dailyLimit);
    }
    
    private boolean canPerformApiCall(UserUsage usage, CommonEnums.PlanType planType) {
        // API access is typically unlimited for paid plans, limited for free
        int dailyLimit = switch (planType) {
            case FREE -> 0; // No API access for free users
            case BASIC -> systemSettingsService.getIntegerValue("basic.daily_api_calls", 1000);
            case PROFESSIONAL -> systemSettingsService.getIntegerValue("professional.daily_api_calls", 10000);
            case ENTERPRISE -> systemSettingsService.getIntegerValue("enterprise.daily_api_calls", 100000);
            case CUSTOM -> Integer.MAX_VALUE;
        };
        return usage.canMakeApiCall(dailyLimit);
    }
    
    private boolean canCreateMonitoringItem(UserUsage usage, CommonEnums.PlanType planType) {
        // This checks against total monitoring items, not daily creation
        // Implementation would need to check user's current monitoring item count
        return true; // Simplified for now
    }
    
    /**
     * Check hourly limits for premium plans
     */
    private boolean canPerformHourlySearch(UserUsage usage, int hourlyLimit) {
        // For hourly limits, we need to check searches in the last hour
        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);
        
        // This is a simplified check - in production you might want to track hourly usage separately
        // For now, we'll allow it if they haven't exceeded daily limit equivalent
        int dailyEquivalent = hourlyLimit * 24;
        return usage.canSearch(dailyEquivalent);
    }
    
    /**
     * Get usage statistics for a user
     */
    public UserUsageStats getUserUsageStats(Long userId, LocalDate startDate, LocalDate endDate) {
        List<UserUsage> usageRecords = userUsageRepository.findByUserIdAndUsageDateBetween(userId, startDate, endDate);
        
        int totalSearches = usageRecords.stream().mapToInt(UserUsage::getSearchesCount).sum();
        int totalExports = usageRecords.stream().mapToInt(UserUsage::getExportsCount).sum();
        int totalApiCalls = usageRecords.stream().mapToInt(UserUsage::getApiCallsCount).sum();
        
        return new UserUsageStats(totalSearches, totalExports, totalApiCalls, usageRecords.size());
    }
    
    /**
     * Get current usage for today
     */
    public UserUsageStats getTodayUsage(Long userId) {
        LocalDate today = LocalDate.now();
        Optional<UserUsage> todayUsage = userUsageRepository.findByUserIdAndUsageDate(userId, today);
        
        if (todayUsage.isPresent()) {
            UserUsage usage = todayUsage.get();
            return new UserUsageStats(usage.getSearchesCount(), usage.getExportsCount(), 
                                    usage.getApiCallsCount(), 1);
        }
        
        return new UserUsageStats(0, 0, 0, 0);
    }
    
    /**
     * Get remaining quota for user today
     */
    public UsageQuota getRemainingQuota(UserPrincipal userPrincipal) {
        try {
            User user = userService.findById(userPrincipal.getId());
            log.debug("Found user: {} with email: {}", user.getId(), user.getEmail());
            
            Subscription subscription = user.getSubscription();
            log.debug("User subscription: {}", subscription != null ? subscription.getPlanType() : "null");
            
            CommonEnums.PlanType planType = subscription != null ? subscription.getPlanType() : CommonEnums.PlanType.FREE;
            
            UserUsageStats todayUsage = getTodayUsage(user.getId());
            log.debug("Today's usage: {}", todayUsage);
            
            int searchLimit = getSearchLimitForPlan(planType);
            int exportLimit = getExportLimitForPlan(planType);
            int apiLimit = getApiLimitForPlan(planType);
            
            log.debug("Limits for plan {}: searches={}, exports={}, api={}", 
                    planType, searchLimit, exportLimit, apiLimit);
            
            UsageQuota quota = new UsageQuota(
                Math.max(0, searchLimit - todayUsage.totalSearches()),
                Math.max(0, exportLimit - todayUsage.totalExports()),
                Math.max(0, apiLimit - todayUsage.totalApiCalls()),
                searchLimit,
                exportLimit,
                apiLimit
            );
            
            log.debug("Returning quota: {}", quota);
            return quota;
        } catch (Exception e) {
            log.error("Error getting remaining quota for user {}: {}", userPrincipal.getId(), e.getMessage(), e);
            throw e;
        }
    }
    
    private int getSearchLimitForPlan(CommonEnums.PlanType planType) {
        return switch (planType) {
            case FREE -> systemSettingsService.getFreeDailySearchLimit();
            case BASIC -> systemSettingsService.getBasicDailySearchLimit();
            case PROFESSIONAL -> systemSettingsService.getProfessionalHourlySearchLimit() * 24;
            case ENTERPRISE -> systemSettingsService.getEnterpriseHourlySearchLimit() * 24;
            case CUSTOM -> Integer.MAX_VALUE;
        };
    }
    
    private int getExportLimitForPlan(CommonEnums.PlanType planType) {
        return switch (planType) {
            case FREE -> systemSettingsService.getIntegerValue("free.daily_exports", 3);
            case BASIC -> systemSettingsService.getIntegerValue("basic.daily_exports", 10);
            case PROFESSIONAL -> systemSettingsService.getIntegerValue("professional.daily_exports", 50);
            case ENTERPRISE -> systemSettingsService.getIntegerValue("enterprise.daily_exports", 500);
            case CUSTOM -> Integer.MAX_VALUE;
        };
    }
    
    private int getApiLimitForPlan(CommonEnums.PlanType planType) {
        return switch (planType) {
            case FREE -> 0;
            case BASIC -> systemSettingsService.getIntegerValue("basic.daily_api_calls", 1000);
            case PROFESSIONAL -> systemSettingsService.getIntegerValue("professional.daily_api_calls", 10000);
            case ENTERPRISE -> systemSettingsService.getIntegerValue("enterprise.daily_api_calls", 100000);
            case CUSTOM -> Integer.MAX_VALUE;
        };
    }
    
    /**
     * Get today's anonymous usage count for an IP address
     */
    public int getTodayAnonymousUsageCount(String ipAddress) {
        LocalDate today = LocalDate.now();
        return anonymousUsageRepository.findByIpAddressAndUsageDate(ipAddress, today)
                .map(AnonymousUsage::getSearchesCount)
                .orElse(0);
    }
    
    // Helper records for returning usage data
    public record UserUsageStats(int totalSearches, int totalExports, int totalApiCalls, int activeDays) {}
    
    public record UsageQuota(int remainingSearches, int remainingExports, int remainingApiCalls,
                           int totalSearches, int totalExports, int totalApiCalls) {}
}
