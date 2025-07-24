package com.threatscopebackend.scheduler;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.User;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import java.util.stream.Collectors;
import com.threatscopebackend.websocket.RealTimeNotificationService;
import jakarta.annotation.PreDestroy;


@Component
@RequiredArgsConstructor
@Slf4j
public class OptimizedMonitoringScheduler {
    
    private final MonitoringService monitoringService;
    private final AlertService alertService;
    private final BreachDetectionService breachDetectionService;
    private final MonitoringConfigurationService configService;
    private final SubscriptionService subscriptionService;
    private final TaskScheduler taskScheduler;
    private final RealTimeNotificationService realTimeNotificationService;
    
    // Thread pool for parallel processing
    private final ThreadPoolTaskExecutor monitoringExecutor = createMonitoringExecutor();
    
    // Dynamic scheduling references
    private final AtomicReference<ScheduledFuture<?>> realTimeTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> hourlyTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> dailyTask = new AtomicReference<>();
    private final AtomicReference<ScheduledFuture<?>> weeklyTask = new AtomicReference<>();
    
    // Performance tracking
    private final Map<String, Long> lastProcessingTimes = new ConcurrentHashMap<>();
    private final Map<String, Integer> processingCounts = new ConcurrentHashMap<>();
    
    /**
     * Create optimized thread pool for monitoring tasks
     */
    private ThreadPoolTaskExecutor createMonitoringExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(10);
        executor.setMaxPoolSize(50);
        executor.setQueueCapacity(1000);
        executor.setThreadNamePrefix("MonitoringTask-");
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.CallerRunsPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        executor.initialize();
        return executor;
    }
    
    /**
     * Initialize default configurations and start optimized scheduling
     */
    @EventListener(ApplicationReadyEvent.class)
    public void initializeScheduling() {
        log.info("🚀 Initializing optimized monitoring scheduler with performance enhancements");
        
        // Initialize default configurations
        configService.initializeDefaultConfigurations();
        
        // Start optimized scheduling
        scheduleOptimizedMonitoringTasks();
        
        log.info("✅ Optimized scheduler initialized successfully");
    }
    
    /**
     * Schedule monitoring tasks with performance optimizations
     */
    public void scheduleOptimizedMonitoringTasks() {
        // Cancel existing tasks
        cancelExistingTasks();
        
        // Get intervals from configuration
        Long realTimeInterval = configService.getConfigValueAsLong("monitoring.real_time.interval");
        Long hourlyInterval = configService.getConfigValueAsLong("monitoring.hourly.interval");
        Long dailyInterval = configService.getConfigValueAsLong("monitoring.daily.interval");
        Long weeklyInterval = configService.getConfigValueAsLong("monitoring.weekly.interval");
        
        log.info("📅 Scheduling optimized monitoring tasks - Real-time: {}ms, Hourly: {}ms, Daily: {}ms, Weekly: {}ms",
                realTimeInterval, hourlyInterval, dailyInterval, weeklyInterval);
        
        // Schedule REAL-TIME monitoring with WebSocket notifications
        realTimeTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkRealTimeMonitoringOptimized, 
            Duration.ofMillis(realTimeInterval)
        ));
        
        // Schedule OPTIMIZED hourly monitoring
        hourlyTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkHourlyMonitoringOptimized, 
            Duration.ofMillis(hourlyInterval)
        ));
        
        // Schedule OPTIMIZED daily monitoring
        dailyTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkDailyMonitoringOptimized, 
            Duration.ofMillis(dailyInterval)
        ));
        
        // Schedule OPTIMIZED weekly monitoring
        weeklyTask.set(taskScheduler.scheduleWithFixedDelay(
            this::checkWeeklyMonitoringOptimized, 
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
     * REAL-TIME monitoring with instant WebSocket notifications
     * Processes REAL_TIME frequency items with immediate alert delivery
     */
    public void checkRealTimeMonitoringOptimized() {
        long startTime = System.currentTimeMillis();
        log.debug("⚡ Starting REAL-TIME monitoring check with WebSocket notifications");
        
        try {
            // 1. Get configuration for real-time processing
            Integer maxRealTimeChecks = configService.getConfigValueAsInt("monitoring.real_time.max_checks");
            Integer realTimeBatchSize = configService.getConfigValueAsInt("monitoring.real_time.batch_size");
            
            // Default values if not configured
            maxRealTimeChecks = maxRealTimeChecks != null ? maxRealTimeChecks : 100;
            realTimeBatchSize = realTimeBatchSize != null ? realTimeBatchSize : 20;
            
            // 2. Get real-time monitoring items (only for users with real-time feature)
            List<MonitoringItem> realTimeItems = getRealTimeMonitoringItems(maxRealTimeChecks);
            
            if (realTimeItems.isEmpty()) {
                log.debug("⚡ No real-time monitoring items to process");
                return;
            }
            
            log.debug("⚡ Processing {} real-time monitoring items", realTimeItems.size());
            
            // 3. Process in smaller batches for real-time responsiveness
            int totalProcessed = 0;
            for (int i = 0; i < realTimeItems.size(); i += realTimeBatchSize) {
                int endIndex = Math.min(i + realTimeBatchSize, realTimeItems.size());
                List<MonitoringItem> batch = realTimeItems.subList(i, endIndex);
                
                // Process batch in parallel with real-time notification
                List<CompletableFuture<Integer>> batchTasks = batch.stream()
                    .map(item -> CompletableFuture.supplyAsync(
                        () -> processRealTimeMonitoringItem(item),
                        monitoringExecutor
                    ))
                    .toList();
                
                // Wait for batch with shorter timeout for real-time processing
                try {
                    List<Integer> batchResults = CompletableFuture.allOf(
                        batchTasks.toArray(new CompletableFuture[0])
                    ).thenApply(v -> 
                        batchTasks.stream()
                            .map(CompletableFuture::join)
                            .toList()
                    ).get(30, TimeUnit.SECONDS); // 30 second timeout for real-time
                    
                    int batchTotal = batchResults.stream().mapToInt(Integer::intValue).sum();
                    totalProcessed += batchTotal;
                    
                } catch (TimeoutException e) {
                    log.warn("⚠️ Real-time batch timed out after 30 seconds");
                    batchTasks.forEach(task -> task.cancel(true));
                }
            }
            
            // 4. Record performance metrics
            long duration = System.currentTimeMillis() - startTime;
            lastProcessingTimes.put("real-time", duration);
            processingCounts.put("real-time", totalProcessed);
            
            log.debug("⚡ REAL-TIME monitoring completed: {} items processed in {}ms", 
                     totalProcessed, duration);
            
        } catch (Exception e) {
            log.error("❌ Error during real-time monitoring check: {}", e.getMessage(), e);
        }
    }
    
    /**
     * OPTIMIZED hourly monitoring with batching and parallel processing
     */
    public void checkHourlyMonitoringOptimized() {
        performOptimizedMonitoringCheck(CommonEnums.MonitorFrequency.HOURLY, "hourly");
    }
    
    /**
     * OPTIMIZED daily monitoring with batching and parallel processing
     */
    public void checkDailyMonitoringOptimized() {
        performOptimizedMonitoringCheck(CommonEnums.MonitorFrequency.DAILY, "daily");
    }
    
    /**
     * OPTIMIZED weekly monitoring with batching and parallel processing
     */
    public void checkWeeklyMonitoringOptimized() {
        performOptimizedMonitoringCheck(CommonEnums.MonitorFrequency.WEEKLY, "weekly");
    }
    
    /**
     * Core optimized monitoring logic with performance enhancements
     */
    private void performOptimizedMonitoringCheck(CommonEnums.MonitorFrequency frequency, String frequencyName) {
        long startTime = System.currentTimeMillis();
        log.info("🔄 Starting OPTIMIZED {} monitoring check", frequencyName);
        
        try {
            // 1. Get configuration parameters for optimization
            Integer batchSize = configService.getConfigValueAsInt("monitoring.batch_size");
            Integer maxParallelThreads = configService.getConfigValueAsInt("monitoring.max_parallel_threads");
            
            // 2. Use pagination to process users in batches (prevents memory overflow)
            int page = 0;
            int totalProcessed = 0;
            
            while (true) {
                // Get batch of users with monitoring items for this frequency
                List<User> userBatch = getUsersWithMonitoringForFrequency(frequency, page, batchSize);
                
                if (userBatch.isEmpty()) {
                    break; // No more users to process
                }
                
                log.debug("📦 Processing batch {} with {} users for {} monitoring", page + 1, userBatch.size(), frequencyName);
                
                // 3. Process users in parallel using thread pool
                List<CompletableFuture<Integer>> batchTasks = userBatch.stream()
                    .map(user -> CompletableFuture.supplyAsync(
                        () -> processUserMonitoringItems(user, frequency, frequencyName),
                        monitoringExecutor
                    ))
                    .toList();
                
                // 4. Wait for batch completion and collect results
                try {
                    List<Integer> batchResults = CompletableFuture.allOf(
                        batchTasks.toArray(new CompletableFuture[0])
                    ).thenApply(v -> 
                        batchTasks.stream()
                            .map(CompletableFuture::join)
                            .toList()
                    ).get(5, TimeUnit.MINUTES); // 5-minute timeout per batch
                    
                    int batchTotal = batchResults.stream().mapToInt(Integer::intValue).sum();
                    totalProcessed += batchTotal;
                    
                    log.debug("✅ Batch {} completed: {} items processed", page + 1, batchTotal);
                    
                } catch (TimeoutException e) {
                    log.warn("⚠️ Batch {} timed out after 5 minutes", page + 1);
                    // Cancel remaining tasks in this batch
                    batchTasks.forEach(task -> task.cancel(true));
                }
                
                page++;
                
                // Safety break to prevent infinite loops
                if (page > 1000) {
                    log.warn("⚠️ Reached maximum page limit (1000) for {} monitoring", frequencyName);
                    break;
                }
            }
            
            // 5. Record performance metrics
            long duration = System.currentTimeMillis() - startTime;
            lastProcessingTimes.put(frequencyName, duration);
            processingCounts.put(frequencyName, totalProcessed);
            
            log.info("✅ OPTIMIZED {} monitoring completed: {} items processed in {}ms ({} batches)", 
                    frequencyName, totalProcessed, duration, page);
            
        } catch (Exception e) {
            log.error("❌ Error during optimized {} monitoring check: {}", frequencyName, e.getMessage(), e);
        }
    }
    
    /**
     * Get real-time monitoring items for users with real-time feature
     */
    private List<MonitoringItem> getRealTimeMonitoringItems(int maxChecks) {
        try {
            // Get items that need checking for REAL_TIME frequency
            List<MonitoringItem> allItems = monitoringService.getItemsNeedingCheck();
            
            return allItems.stream()
                .filter(item -> item.getFrequency() == CommonEnums.MonitorFrequency.REAL_TIME)
                .filter(item -> subscriptionService.canUseMonitoringFrequency(item.getUser(), CommonEnums.MonitorFrequency.REAL_TIME))
                .limit(maxChecks)
                .toList();
                
        } catch (Exception e) {
            log.error("Error getting real-time monitoring items: {}", e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Process a single real-time monitoring item with instant WebSocket notification
     */
    private Integer processRealTimeMonitoringItem(MonitoringItem item) {
        try {
            log.debug("⚡ Processing real-time item: {} for user: {}", item.getId(), item.getUser().getId());
            
            // 1. Check monitoring item for new breaches
            breachDetectionService.checkMonitoringItem(item);
            
            // 2. Send real-time monitoring status update
            Map<String, Object> statusUpdate = Map.of(
                "itemId", item.getId(),
                "targetValue", item.getTargetValue(),
                "monitorType", item.getMonitorType().name(),
                "lastChecked", java.time.LocalDateTime.now().toString(),
                "status", "CHECKED"
            );
            
            // Send non-blocking real-time update
            realTimeNotificationService.sendMonitoringUpdate(item, "CHECKED", statusUpdate);
            
            return 1;
            
        } catch (Exception e) {
            log.error("Error processing real-time monitoring item {}: {}", item.getId(), e.getMessage());
            
            // Send error status update
            try {
                Map<String, Object> errorUpdate = Map.of(
                    "itemId", item.getId(),
                    "status", "ERROR",
                    "error", e.getMessage(),
                    "timestamp", java.time.LocalDateTime.now().toString()
                );
                realTimeNotificationService.sendMonitoringUpdate(item, "ERROR", errorUpdate);
            } catch (Exception notificationError) {
                log.error("Failed to send error notification: {}", notificationError.getMessage());
            }
            
            return 0;
        }
    }
    
    /**
     * Get users with monitoring items for a specific frequency (paginated)
     */
    private List<User> getUsersWithMonitoringForFrequency(CommonEnums.MonitorFrequency frequency, int page, int size) {
        try {
            // This would need to be implemented in the repository
            // For now, we'll get all monitoring items and extract unique users
            List<MonitoringItem> items = monitoringService.getItemsByFrequency(frequency, page, size);
            
            return items.stream()
                .map(MonitoringItem::getUser)
                .distinct()
                .toList();
                
        } catch (Exception e) {
            log.error("Error getting users for {} frequency: {}", frequency, e.getMessage());
            return List.of();
        }
    }
    
    /**
     * Process all monitoring items for a single user with performance optimizations
     */
    private Integer processUserMonitoringItems(User user, CommonEnums.MonitorFrequency frequency, String frequencyName) {
        try {
            // 1. Check user subscription permissions first
            if (!subscriptionService.canUseMonitoringFrequency(user, frequency)) {
                log.debug("👤 User {} doesn't have permission for {} monitoring", user.getId(), frequencyName);
                return 0;
            }
            
            // 2. Get user's monitoring items for this frequency
            List<MonitoringItem> userItems = monitoringService.getUserMonitoringItemsByFrequency(user, frequency);
            
            if (userItems.isEmpty()) {
                return 0;
            }
            
            log.debug("👤 Processing {} {} monitoring items for user {}", userItems.size(), frequencyName, user.getId());
            
            // 3. Group items by similarity for bulk processing
            Map<String, List<MonitoringItem>> groupedItems = groupItemsBySimilarity(userItems);
            
            // 4. Process each group
            int processedCount = 0;
            for (Map.Entry<String, List<MonitoringItem>> group : groupedItems.entrySet()) {
                try {
                    // Use bulk processing for similar items
                    processBulkMonitoringItems(group.getValue());
                    processedCount += group.getValue().size();
                    
                } catch (Exception e) {
                    log.error("Error processing group {} for user {}: {}", group.getKey(), user.getId(), e.getMessage());
                }
            }
            
            log.debug("✅ User {} completed: {} items processed", user.getId(), processedCount);
            return processedCount;
            
        } catch (Exception e) {
            log.error("Error processing monitoring items for user {}: {}", user.getId(), e.getMessage());
            return 0;
        }
    }
    
    /**
     * Group monitoring items by similarity for bulk processing optimization
     */
    private Map<String, List<MonitoringItem>> groupItemsBySimilarity(List<MonitoringItem> items) {
        // Group by monitor type for bulk Elasticsearch queries
        return items.stream()
            .collect(Collectors.groupingBy(
                item -> item.getMonitorType().name(),
                Collectors.toList()
            ));
    }
    
    /**
     * Process multiple monitoring items in bulk for better performance
     */
    private void processBulkMonitoringItems(List<MonitoringItem> items) {
        try {
            // Use bulk processing from BreachDetectionService
            breachDetectionService.processBulkMonitoringItems(items);
            
        } catch (Exception e) {
            // Fallback to individual processing if bulk fails
            log.warn("Bulk processing failed, falling back to individual processing: {}", e.getMessage());
            
            for (MonitoringItem item : items) {
                try {
                    breachDetectionService.checkMonitoringItem(item);
                } catch (Exception individualError) {
                    log.error("Error checking individual monitoring item {}: {}", item.getId(), individualError.getMessage());
                }
            }
        }
    }
    
    /**
     * Process pending notifications every 10 minutes (optimized)
     */
    @Scheduled(fixedRate = 600000) // 10 minutes
    public void processPendingNotificationsOptimized() {
        log.debug("📧 Processing pending alert notifications (optimized)");
        
        try {
            Integer maxAlertsPerDay = configService.getConfigValueAsInt("alerts.max_per_day");
            Integer batchSize = configService.getConfigValueAsInt("notifications.batch_size");
            
            // Process notifications in batches for better performance
            alertService.processPendingNotificationsBatched(maxAlertsPerDay, batchSize);
            
        } catch (Exception e) {
            log.error("Error processing pending notifications: {}", e.getMessage(), e);
        }
    }
    

    

    
    /**
     * System health check (optimized)
     */


    

    
    /**
     * Graceful shutdown of thread pool
     */
    @PreDestroy
    public void shutdown() {
        log.info("🛑 Shutting down optimized monitoring scheduler");
        
        cancelExistingTasks();
        
        monitoringExecutor.shutdown();
        try {
            if (!monitoringExecutor.getThreadPoolExecutor().awaitTermination(60, TimeUnit.SECONDS)) {
                monitoringExecutor.getThreadPoolExecutor().shutdownNow();
            }
        } catch (InterruptedException e) {
            monitoringExecutor.getThreadPoolExecutor().shutdownNow();
            Thread.currentThread().interrupt();
        }
        
        log.info("✅ Optimized monitoring scheduler shutdown completed");
    }
}
