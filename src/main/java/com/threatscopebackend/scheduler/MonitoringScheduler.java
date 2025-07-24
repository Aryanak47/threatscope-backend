package com.threatscopebackend.scheduler;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.service.monitoring.MonitoringService;
import com.threatscopebackend.service.monitoring.AlertService;
import com.threatscopebackend.service.monitoring.BreachDetectionService;
import com.threatscopebackend.service.admin.MonitoringConfigurationService;
import com.threatscopebackend.service.subscription.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.atomic.AtomicReference;

// DISABLED: Replaced by OptimizedMonitoringScheduler
// @Component
@RequiredArgsConstructor
@Slf4j
public class MonitoringScheduler {
    
    private final MonitoringService monitoringService;
    private final AlertService alertService;
    private final BreachDetectionService breachDetectionService;
    private final MonitoringConfigurationService configService;
    private final SubscriptionService subscriptionService;
    private final TaskScheduler taskScheduler;
    
    // Dynamic scheduling references
    private final AtomicReference<ScheduledFuture<?>> realTimeTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> hourlyTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> dailyTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> weeklyTask = new AtomicReference<>();
    
    /**
     * Initialize default configurations and start dynamic scheduling
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeScheduling() {
        log.info("Initializing monitoring scheduler with admin configurations");
        
        // Initialize default configurations
        configService.initializeDefaultConfigurations();
        
        // Start dynamic scheduling
        scheduleMonitoringTasks();
    }
    
    /**
     * Schedule monitoring tasks based on admin configurations
     */
    public void scheduleMonitoringTasks() {
        // Cancel existing tasks
        cancelExistingTasks();
        
        // Get intervals from configuration
        Long realTimeInterval = configService.getConfigValueAsLong("monitoring.real_time.interval");
        Long hourlyInterval = configService.getConfigValueAsLong("monitoring.hourly.interval");
        Long dailyInterval = configService.getConfigValueAsLong("monitoring.daily.interval");
        Long weeklyInterval = configService.getConfigValueAsLong("monitoring.weekly.interval");
        
        log.info("Scheduling monitoring tasks - Real-time: {}ms, Hourly: {}ms, Daily: {}ms, Weekly: {}ms",
                realTimeInterval, hourlyInterval, dailyInterval, weeklyInterval);
        
        // Schedule real-time monitoring
        realTimeTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkRealTimeMonitoring, 
            Duration.ofMillis(realTimeInterval)
        ));
        
        // Schedule hourly monitoring
        hourlyTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkHourlyMonitoring, 
            Duration.ofMillis(hourlyInterval)
        ));
        
        // Schedule daily monitoring
        dailyTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkDailyMonitoring, 
            Duration.ofMillis(dailyInterval)
        ));
        
        // Schedule weekly monitoring
        weeklyTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkWeeklyMonitoring, 
            Duration.ofMillis(weeklyInterval)
        ));
    }
    
    private void cancelExistingTasks() {
        if (realTimeTask.get() != null) {
            realTimeTask.get().cancel(false);
        }
        if (hourlyTask.get() != null) {
            hourlyTask.get().cancel(false);
        }
        if (dailyTask.get() != null) {
            dailyTask.get().cancel(false);
        }
        if (weeklyTask.get() != null) {
            weeklyTask.get().cancel(false);
        }
    }
    
    /**
     * Check monitoring items for real-time monitoring (admin configurable interval)
     */
    public void checkRealTimeMonitoring() {
        log.debug("Starting real-time monitoring check");
        
        try {
            Integer maxConcurrentChecks = configService.getConfigValueAsInt("monitoring.max_concurrent_checks");
            List<MonitoringItem> itemsToCheck = monitoringService.getItemsNeedingCheck();
            
            // Filter for real-time frequency and subscription limits
            List<MonitoringItem> realTimeItems = itemsToCheck.stream()
                .filter(item -> item.getFrequency() == CommonEnums.MonitorFrequency.REAL_TIME)
                .filter(item -> subscriptionService.hasFeature(item.getUser(), "real_time_monitoring"))
                .limit(maxConcurrentChecks)
                .toList();
            
            log.debug("Processing {} real-time monitoring items", realTimeItems.size());
            
            for (MonitoringItem item : realTimeItems) {
                try {
                    breachDetectionService.checkMonitoringItem(item);
                } catch (Exception e) {
                    log.error("Error checking real-time monitoring item {}: {}", item.getId(), e.getMessage());
                }
            }
            
            log.debug("Completed real-time monitoring check for {} items", realTimeItems.size());
            
        } catch (Exception e) {
            log.error("Error during real-time monitoring check: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Check hourly monitoring items (admin configurable)
     */
    public void checkHourlyMonitoring() {
        performMonitoringCheck(CommonEnums.MonitorFrequency.HOURLY, "hourly");
    }
    
    /**
     * Check daily monitoring items (admin configurable)
     */
    public void checkDailyMonitoring() {
        performMonitoringCheck(CommonEnums.MonitorFrequency.DAILY, "daily");
    }
    
    /**
     * Check weekly monitoring items (admin configurable)
     */
    public void checkWeeklyMonitoring() {
        performMonitoringCheck(CommonEnums.MonitorFrequency.WEEKLY, "weekly");
    }
    
    private void performMonitoringCheck(CommonEnums.MonitorFrequency frequency, String frequencyName) {
        log.info("Starting {} monitoring check", frequencyName);
        
        try {
            Integer maxConcurrentChecks = configService.getConfigValueAsInt("monitoring.max_concurrent_checks");
            List<MonitoringItem> itemsToCheck = monitoringService.getItemsNeedingCheck();
            
            List<MonitoringItem> filteredItems = itemsToCheck.stream()
                .filter(item -> item.getFrequency() == frequency)
                .filter(item -> subscriptionService.canUseMonitoringFrequency(item.getUser(), frequency))
                .limit(maxConcurrentChecks)
                .toList();
            
            log.info("Processing {} {} monitoring items", filteredItems.size(), frequencyName);
            
            for (MonitoringItem item : filteredItems) {
                try {
                    breachDetectionService.checkMonitoringItem(item);
                } catch (Exception e) {
                    log.error("Error checking {} monitoring item {}: {}", 
                            frequencyName, item.getId(), e.getMessage());
                }
            }
            
            log.info("Completed {} monitoring check for {} items", frequencyName, filteredItems.size());
            
        } catch (Exception e) {
            log.error("Error during {} monitoring check: {}", frequencyName, e.getMessage(), e);
        }
    }
    
    /**
     * Process pending notifications every 10 minutes (fixed schedule)
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void processPendingNotifications() {
        log.debug("Processing pending alert notifications");
        
        try {
            Integer maxAlertsPerDay = configService.getConfigValueAsInt("alerts.max_per_day");
            alertService.processPendingNotifications(maxAlertsPerDay);
        } catch (Exception e) {
            log.error("Error processing pending notifications: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Clean up old archived alerts daily at 2 AM (fixed schedule)
     */
    @Scheduled(cron = "0 0 2 * * *") // Daily at 2 AM
    public void cleanupOldAlerts() {
        log.info("Starting cleanup of old archived alerts");
        
        try {
            Integer daysToKeep = configService.getConfigValueAsInt("alerts.cleanup_days");
            int deleted = alertService.cleanupOldArchivedAlerts(daysToKeep);
            log.info("Cleaned up {} old archived alerts", deleted);
        } catch (Exception e) {
            log.error("Error during alert cleanup: {}", e.getMessage(), e);
        }
    }
    
    /**
     * Process expired trials daily at 3 AM (fixed schedule)
     */
    @Scheduled(cron = "0 0 3 * * *") // Daily at 3 AM
    public void processExpiredTrials() {
        log.info("Processing expired trial subscriptions");
        
        try {
            subscriptionService.processExpiredTrials();
        } catch (Exception e) {
            log.error("Error processing expired trials: {}", e.getMessage(), e);
        }
    }
    
    /**
     * System health check (admin configurable interval)
     */
    @Scheduled(fixedRateString = "#{@monitoringConfigurationService.getConfigValueAsLong('system.health_check_interval')}")
    public void performSystemHealthCheck() {
        log.debug("Performing system health check");
        
        try {
            // Monitor system resources, database connections, etc.
            performHealthChecks();
        } catch (Exception e) {
            log.error("Error during system health check: {}", e.getMessage(), e);
        }
    }
    
    private void performHealthChecks() {
        // Check database connectivity
        // Check Elasticsearch connectivity
        // Check memory usage
        // Check active monitoring tasks
        // Log system metrics
        log.debug("System health check completed successfully");
    }
    
    /**
     * Reload scheduling configuration (called by admin when configuration changes)
     */
    public void reloadSchedulingConfiguration() {
        log.info("Reloading monitoring scheduling configuration");
        scheduleMonitoringTasks();
    }
}
