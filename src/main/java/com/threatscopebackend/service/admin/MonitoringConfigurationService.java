package com.threatscopebackend.service.admin;

import com.threatscopebackend.entity.postgresql.MonitoringConfiguration;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.MonitoringConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringConfigurationService {
    
    private final MonitoringConfigurationRepository configRepository;
    
    // Default monitoring intervals (in milliseconds)
    private static final Map<String, String> DEFAULT_CONFIGS = Map.ofEntries(
        Map.entry("monitoring.real_time.interval", "300000"),      // 5 minutes
        Map.entry("monitoring.hourly.interval", "3600000"),        // 1 hour
        Map.entry("monitoring.daily.interval", "86400000"),        // 24 hours
        Map.entry("monitoring.weekly.interval", "604800000"),      // 7 days
        Map.entry("monitoring.max_concurrent_checks", "50"),       // Max concurrent monitoring checks
        Map.entry("alerts.max_per_day", "1000"),                   // Max alerts per day
        Map.entry("alerts.cleanup_days", "90"),                    // Days to keep archived alerts
        Map.entry("notifications.retry_attempts", "3"),            // Notification retry attempts
        Map.entry("notifications.retry_delay", "60000"),           // 1 minute retry delay
        Map.entry("system.health_check_interval", "600000"),        // 10 minutes system health check
        
        // ADDITIONAL CONFIGS needed by OptimizedMonitoringScheduler
        Map.entry("monitoring.real_time.max_checks", "100"),       // Max real-time checks per cycle
        Map.entry("monitoring.real_time.batch_size", "20"),        // Real-time batch size
        Map.entry("monitoring.batch_size", "10"),                  // General batch size
        Map.entry("monitoring.max_parallel_threads", "20"),        // Max parallel threads
        Map.entry("notifications.batch_size", "100"),              // Notification batch size
        Map.entry("cleanup.batch_size", "1000")                    // Cleanup batch size
    );
    
    /**
     * Initialize default configurations if they don't exist
     */
    @Transactional
    public void initializeDefaultConfigurations() {
        log.info("Initializing default monitoring configurations");
        
        for (Map.Entry<String, String> entry : DEFAULT_CONFIGS.entrySet()) {
            String key = entry.getKey();
            String value = entry.getValue();
            
            if (!configRepository.existsByConfigKey(key)) {
                MonitoringConfiguration config = createDefaultConfig(key, value);
                configRepository.save(config);
                log.info("Created default configuration: {} = {}", key, value);
            }
        }
    }
    
    private MonitoringConfiguration createDefaultConfig(String key, String value) {
        return MonitoringConfiguration.builder()
            .configKey(key)
            .configValue(value)
            .defaultValue(value)
            .dataType("INTEGER")
            .category(getCategoryFromKey(key))
            .description(getDescriptionFromKey(key))
            .isAdminConfigurable(true)
            .requiresRestart(false)
            .build();
    }
    
    private String getCategoryFromKey(String key) {
        if (key.startsWith("monitoring.")) return "MONITORING";
        if (key.startsWith("alerts.")) return "ALERTS";
        if (key.startsWith("notifications.")) return "NOTIFICATIONS";
        if (key.startsWith("system.")) return "SYSTEM";
        return "OTHER";
    }
    
    private String getDescriptionFromKey(String key) {
        return switch (key) {
            case "monitoring.real_time.interval" -> "Interval for real-time monitoring checks (milliseconds)";
            case "monitoring.hourly.interval" -> "Interval for hourly monitoring checks (milliseconds)";
            case "monitoring.daily.interval" -> "Interval for daily monitoring checks (milliseconds)";
            case "monitoring.weekly.interval" -> "Interval for weekly monitoring checks (milliseconds)";
            case "monitoring.max_concurrent_checks" -> "Maximum number of concurrent monitoring checks";
            case "alerts.max_per_day" -> "Maximum number of alerts that can be generated per day";
            case "alerts.cleanup_days" -> "Number of days to keep archived alerts";
            case "notifications.retry_attempts" -> "Number of retry attempts for failed notifications";
            case "notifications.retry_delay" -> "Delay between notification retry attempts (milliseconds)";
            case "system.health_check_interval" -> "Interval for system health checks (milliseconds)";
            default -> "Configuration parameter";
        };
    }
    
    /**
     * Get configuration value by key
     */
    public String getConfigValue(String key) {
        return configRepository.findByConfigKey(key)
            .map(MonitoringConfiguration::getConfigValue)
            .orElse(DEFAULT_CONFIGS.get(key));
    }
    
    /**
     * Get configuration as integer
     */
    public Integer getConfigValueAsInt(String key) {
        String value = getConfigValue(key);
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer configuration for key {}: {}", key, value);
            return Integer.parseInt(DEFAULT_CONFIGS.getOrDefault(key, "0"));
        }
    }
    
    /**
     * Get configuration as long
     */
    public Long getConfigValueAsLong(String key) {
        String value = getConfigValue(key);
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid long configuration for key {}: {}", key, value);
            return Long.parseLong(DEFAULT_CONFIGS.getOrDefault(key, "0"));
        }
    }
    
    /**
     * Get configuration as boolean
     */
    public Boolean getConfigValueAsBoolean(String key) {
        String value = getConfigValue(key);
        return Boolean.parseBoolean(value);
    }
    
    /**
     * Update configuration value
     */
    @Transactional
    public MonitoringConfiguration updateConfiguration(String key, String value, Long adminUserId) {
        log.info("Updating configuration {} to {} by admin {}", key, value, adminUserId);
        
        MonitoringConfiguration config = configRepository.findByConfigKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("Configuration not found: " + key));
        
        if (!config.getIsAdminConfigurable()) {
            throw new IllegalArgumentException("Configuration " + key + " is not admin configurable");
        }
        
        // Validate value if needed
        validateConfigValue(config, value);
        
        config.setConfigValue(value);
        config.setUpdatedBy(adminUserId);
        
        return configRepository.save(config);
    }
    
    /**
     * Get all configurations by category
     */
    public List<MonitoringConfiguration> getConfigurationsByCategory(String category) {
        return configRepository.findByCategory(category);
    }
    
    /**
     * Get all admin-configurable settings
     */
    public List<MonitoringConfiguration> getAdminConfigurableSettings() {
        return configRepository.findByIsAdminConfigurableTrue();
    }
    
    /**
     * Get monitoring intervals for scheduling
     */
    public Map<String, Long> getMonitoringIntervals() {
        return Map.of(
            "REAL_TIME", getConfigValueAsLong("monitoring.real_time.interval"),
            "HOURLY", getConfigValueAsLong("monitoring.hourly.interval"),
            "DAILY", getConfigValueAsLong("monitoring.daily.interval"),
            "WEEKLY", getConfigValueAsLong("monitoring.weekly.interval")
        );
    }
    
    /**
     * Create new configuration
     */
    @Transactional
    public MonitoringConfiguration createConfiguration(MonitoringConfiguration config, Long adminUserId) {
        log.info("Creating new configuration {} by admin {}", config.getConfigKey(), adminUserId);
        
        if (configRepository.existsByConfigKey(config.getConfigKey())) {
            throw new IllegalArgumentException("Configuration key already exists: " + config.getConfigKey());
        }
        
        config.setUpdatedBy(adminUserId);
        return configRepository.save(config);
    }
    
    /**
     * Delete configuration
     */
    @Transactional
    public void deleteConfiguration(String key, Long adminUserId) {
        log.info("Deleting configuration {} by admin {}", key, adminUserId);
        
        MonitoringConfiguration config = configRepository.findByConfigKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("Configuration not found: " + key));
        
        if (!config.getIsAdminConfigurable()) {
            throw new IllegalArgumentException("Configuration " + key + " cannot be deleted");
        }
        
        configRepository.delete(config);
    }
    
    /**
     * Reset configuration to default value
     */
    @Transactional
    public MonitoringConfiguration resetToDefault(String key, Long adminUserId) {
        log.info("Resetting configuration {} to default by admin {}", key, adminUserId);
        
        MonitoringConfiguration config = configRepository.findByConfigKey(key)
            .orElseThrow(() -> new ResourceNotFoundException("Configuration not found: " + key));
        
        config.setConfigValue(config.getDefaultValue());
        config.setUpdatedBy(adminUserId);
        
        return configRepository.save(config);
    }
    
    /**
     * Validate configuration value
     */
    private void validateConfigValue(MonitoringConfiguration config, String value) {
        if ("INTEGER".equals(config.getDataType())) {
            try {
                int intValue = Integer.parseInt(value);
                
                if (config.getMinValue() != null) {
                    int minValue = Integer.parseInt(config.getMinValue());
                    if (intValue < minValue) {
                        throw new IllegalArgumentException("Value must be at least " + minValue);
                    }
                }
                
                if (config.getMaxValue() != null) {
                    int maxValue = Integer.parseInt(config.getMaxValue());
                    if (intValue > maxValue) {
                        throw new IllegalArgumentException("Value must be at most " + maxValue);
                    }
                }
            } catch (NumberFormatException e) {
                throw new IllegalArgumentException("Invalid integer value: " + value);
            }
        }
        
        if ("BOOLEAN".equals(config.getDataType())) {
            if (!"true".equalsIgnoreCase(value) && !"false".equalsIgnoreCase(value)) {
                throw new IllegalArgumentException("Boolean value must be 'true' or 'false'");
            }
        }
    }
    
    /**
     * Get configurations grouped by category
     */
    public Map<String, List<MonitoringConfiguration>> getConfigurationsGroupedByCategory() {
        return getAdminConfigurableSettings().stream()
            .collect(Collectors.groupingBy(MonitoringConfiguration::getCategory));
    }
    
    /**
     * Bulk update configurations
     */
    @Transactional
    public void bulkUpdateConfigurations(Map<String, String> updates, Long adminUserId) {
        log.info("Bulk updating {} configurations by admin {}", updates.size(), adminUserId);
        
        for (Map.Entry<String, String> entry : updates.entrySet()) {
            try {
                updateConfiguration(entry.getKey(), entry.getValue(), adminUserId);
            } catch (Exception e) {
                log.error("Failed to update configuration {}: {}", entry.getKey(), e.getMessage());
                // Continue with other updates
            }
        }
    }
}
