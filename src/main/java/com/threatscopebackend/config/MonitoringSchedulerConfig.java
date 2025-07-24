package com.threatscopebackend.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Configuration for optimized monitoring scheduler performance
 */
@Configuration
public class MonitoringSchedulerConfig {
    
    /**
     * Task scheduler for dynamic scheduling with optimized thread pool
     */
    @Bean(name = "monitoringTaskScheduler")
    public TaskScheduler monitoringTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(5); // Core pool for scheduled tasks
        scheduler.setThreadNamePrefix("MonitoringScheduler-");
        scheduler.setWaitForTasksToCompleteOnShutdown(true);
        scheduler.setAwaitTerminationSeconds(60);
        scheduler.initialize();
        return scheduler;
    }
    
    /**
     * Dedicated executor for monitoring item processing with optimized settings
     */
    @Bean(name = "monitoringExecutor")
    public ThreadPoolTaskExecutor monitoringExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Optimized thread pool configuration
        executor.setCorePoolSize(10);  // Start with 10 threads
        executor.setMaxPoolSize(50);   // Scale up to 50 threads under load
        executor.setQueueCapacity(1000); // Large queue for pending tasks
        executor.setKeepAliveSeconds(300); // Keep threads alive for 5 minutes
        
        // Thread naming and rejection policy
        executor.setThreadNamePrefix("MonitoringWorker-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        // Graceful shutdown configuration
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(60);
        
        // Allow core threads to timeout when idle
        executor.setAllowCoreThreadTimeOut(true);
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Executor for bulk notification processing
     */
    @Bean(name = "notificationExecutor")
    public ThreadPoolTaskExecutor notificationExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // Smaller pool optimized for I/O operations (email sending)
        executor.setCorePoolSize(5);
        executor.setMaxPoolSize(20);
        executor.setQueueCapacity(500);
        executor.setKeepAliveSeconds(180);
        
        executor.setThreadNamePrefix("NotificationWorker-");
        executor.setRejectedExecutionHandler(new java.util.concurrent.ThreadPoolExecutor.CallerRunsPolicy());
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        
        executor.initialize();
        return executor;
    }
    
    /**
     * Configuration values for monitoring optimization
     */
    @Bean
    public MonitoringOptimizationConfig monitoringOptimizationConfig() {
        return MonitoringOptimizationConfig.builder()
            .batchSize(20)              // Process 20 users per batch
            .maxParallelThreads(10)     // Maximum parallel threads per batch
            .bulkSearchBatchSize(50)    // Bulk search 50 items at once
            .notificationBatchSize(25)  // Process 25 notifications per batch
            .cleanupBatchSize(100)      // Clean up 100 records per batch
            .healthCheckIntervalMs(300000) // Health check every 5 minutes
            .performanceReportIntervalMs(3600000) // Performance report every hour
            .maxProcessingTimeoutMs(300000) // 5 minute timeout per batch
            .enableBulkProcessing(true)
            .enablePerformanceTracking(true)
            .enableAutoScaling(true)
            .build();
    }
    
    /**
     * Configuration class for monitoring optimization settings
     */
    public static class MonitoringOptimizationConfig {
        private int batchSize;
        private int maxParallelThreads;
        private int bulkSearchBatchSize;
        private int notificationBatchSize;
        private int cleanupBatchSize;
        private long healthCheckIntervalMs;
        private long performanceReportIntervalMs;
        private long maxProcessingTimeoutMs;
        private boolean enableBulkProcessing;
        private boolean enablePerformanceTracking;
        private boolean enableAutoScaling;
        
        public static Builder builder() {
            return new Builder();
        }
        
        public static class Builder {
            private final MonitoringOptimizationConfig config = new MonitoringOptimizationConfig();
            
            public Builder batchSize(int batchSize) {
                config.batchSize = batchSize;
                return this;
            }
            
            public Builder maxParallelThreads(int maxParallelThreads) {
                config.maxParallelThreads = maxParallelThreads;
                return this;
            }
            
            public Builder bulkSearchBatchSize(int bulkSearchBatchSize) {
                config.bulkSearchBatchSize = bulkSearchBatchSize;
                return this;
            }
            
            public Builder notificationBatchSize(int notificationBatchSize) {
                config.notificationBatchSize = notificationBatchSize;
                return this;
            }
            
            public Builder cleanupBatchSize(int cleanupBatchSize) {
                config.cleanupBatchSize = cleanupBatchSize;
                return this;
            }
            
            public Builder healthCheckIntervalMs(long healthCheckIntervalMs) {
                config.healthCheckIntervalMs = healthCheckIntervalMs;
                return this;
            }
            
            public Builder performanceReportIntervalMs(long performanceReportIntervalMs) {
                config.performanceReportIntervalMs = performanceReportIntervalMs;
                return this;
            }
            
            public Builder maxProcessingTimeoutMs(long maxProcessingTimeoutMs) {
                config.maxProcessingTimeoutMs = maxProcessingTimeoutMs;
                return this;
            }
            
            public Builder enableBulkProcessing(boolean enableBulkProcessing) {
                config.enableBulkProcessing = enableBulkProcessing;
                return this;
            }
            
            public Builder enablePerformanceTracking(boolean enablePerformanceTracking) {
                config.enablePerformanceTracking = enablePerformanceTracking;
                return this;
            }
            
            public Builder enableAutoScaling(boolean enableAutoScaling) {
                config.enableAutoScaling = enableAutoScaling;
                return this;
            }
            
            public MonitoringOptimizationConfig build() {
                return config;
            }
        }
        
        // Getters
        public int getBatchSize() { return batchSize; }
        public int getMaxParallelThreads() { return maxParallelThreads; }
        public int getBulkSearchBatchSize() { return bulkSearchBatchSize; }
        public int getNotificationBatchSize() { return notificationBatchSize; }
        public int getCleanupBatchSize() { return cleanupBatchSize; }
        public long getHealthCheckIntervalMs() { return healthCheckIntervalMs; }
        public long getPerformanceReportIntervalMs() { return performanceReportIntervalMs; }
        public long getMaxProcessingTimeoutMs() { return maxProcessingTimeoutMs; }
        public boolean isEnableBulkProcessing() { return enableBulkProcessing; }
        public boolean isEnablePerformanceTracking() { return enablePerformanceTracking; }
        public boolean isEnableAutoScaling() { return enableAutoScaling; }
    }
}
