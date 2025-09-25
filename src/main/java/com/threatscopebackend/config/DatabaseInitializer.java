package com.threatscopebackend.config;

import com.threatscopebackend.service.core.SystemSettingsService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class DatabaseInitializer implements CommandLineRunner {
    
    private final SystemSettingsService systemSettingsService;
    
    @Override
    public void run(String... args) throws Exception {
        log.info("Initializing database with default settings...");
        
        try {
            systemSettingsService.initializeDefaultSettings();
            log.info("Database initialization completed successfully");
        } catch (Exception e) {
            log.error("Error during database initialization", e);
            throw e;
        }
    }
}
