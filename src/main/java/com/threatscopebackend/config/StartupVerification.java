package com.threatscopebackend.config;

import com.threatscopebackend.service.admin.MonitoringConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Startup verification to ensure all monitoring components are working
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class StartupVerification {
    
    private final MonitoringConfigurationService configService;
    
    @EventListener(ApplicationReadyEvent.class)
    public void verifyStartup() {
        log.info("🚀 ===== ThreatScope Startup Verification =====");
        
        try {
            // Verify configuration service
            Long realTimeInterval = configService.getConfigValueAsLong("monitoring.real_time.interval");
            Integer maxChecks = configService.getConfigValueAsInt("monitoring.real_time.max_checks");
            Integer batchSize = configService.getConfigValueAsInt("monitoring.real_time.batch_size");
            
            log.info("✅ Monitoring Configuration Service: WORKING");
            log.info("   - Real-time interval: {} ms ({} minutes)", realTimeInterval, realTimeInterval / 60000);
            log.info("   - Max checks per cycle: {}", maxChecks);
            log.info("   - Batch size: {}", batchSize);
            
            // Verify all required configs exist
            String[] requiredConfigs = {
                "monitoring.real_time.interval",
                "monitoring.real_time.max_checks", 
                "monitoring.real_time.batch_size",
                "monitoring.batch_size",
                "monitoring.max_parallel_threads",
                "alerts.max_per_day",
                "notifications.batch_size",
                "cleanup.batch_size"
            };
            
            boolean allConfigsExist = true;
            for (String config : requiredConfigs) {
                try {
                    String value = configService.getConfigValue(config);
                    if (value == null) {
                        log.error("❌ Missing config: {}", config);
                        allConfigsExist = false;
                    } else {
                        log.debug("✅ Config {}: {}", config, value);
                    }
                } catch (Exception e) {
                    log.error("❌ Error reading config {}: {}", config, e.getMessage());
                    allConfigsExist = false;
                }
            }
            
            if (allConfigsExist) {
                log.info("✅ All Required Configurations: LOADED");
            } else {
                log.error("❌ Some configurations are missing - check DEFAULT_CONFIGS");
            }
            
        } catch (Exception e) {
            log.error("❌ Startup verification failed: {}", e.getMessage(), e);
        }
        
        log.info("📊 Expected to see:");
        log.info("   - '🚀 Initializing optimized monitoring scheduler'");
        log.info("   - '✅ Optimized scheduler initialized successfully'");
        log.info("   - '⚡ Starting REAL-TIME monitoring check' (within 5 minutes)");
        log.info("🚀 ===== Startup Verification Complete =====");
    }
}
