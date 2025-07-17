package com.threatscopebackend.service.core;

import com.threatscopebackend.entity.postgresql.SystemSettings;
import com.threatscopebackend.repository.sql.SystemSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class SystemSettingsService {
    
    private final SystemSettingsRepository systemSettingsRepository;
    
    // Default rate limits (fallback values)
    private static final int DEFAULT_ANONYMOUS_DAILY_SEARCHES = 5;
    private static final int DEFAULT_FREE_DAILY_SEARCHES = 25;
    private static final int DEFAULT_BASIC_DAILY_SEARCHES = 100;
    private static final int DEFAULT_PROFESSIONAL_HOURLY_SEARCHES = 50;
    private static final int DEFAULT_ENTERPRISE_HOURLY_SEARCHES = 1000;
    
    @Transactional
    public void initializeDefaultSettings() {
        log.info("Initializing default system settings...");
        
        // Anonymous user limits
        createSettingIfNotExists("anonymous.daily_searches", String.valueOf(DEFAULT_ANONYMOUS_DAILY_SEARCHES), 
                "Daily search limit for anonymous users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        
        // Free user limits
        createSettingIfNotExists("free.daily_searches", String.valueOf(DEFAULT_FREE_DAILY_SEARCHES), 
                "Daily search limit for free users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("free.daily_exports", "3", 
                "Daily export limit for free users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("free.monitoring_items", "5", 
                "Maximum monitoring items for free users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        
        // Basic plan limits
        createSettingIfNotExists("basic.daily_searches", String.valueOf(DEFAULT_BASIC_DAILY_SEARCHES), 
                "Daily search limit for basic users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("basic.daily_exports", "10", 
                "Daily export limit for basic users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("basic.monitoring_items", "25", 
                "Maximum monitoring items for basic users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        
        // Professional plan limits
        createSettingIfNotExists("professional.hourly_searches", String.valueOf(DEFAULT_PROFESSIONAL_HOURLY_SEARCHES), 
                "Hourly search limit for professional users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("professional.daily_exports", "50", 
                "Daily export limit for professional users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("professional.monitoring_items", "100", 
                "Maximum monitoring items for professional users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        
        // Enterprise plan limits
        createSettingIfNotExists("enterprise.hourly_searches", String.valueOf(DEFAULT_ENTERPRISE_HOURLY_SEARCHES), 
                "Hourly search limit for enterprise users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("enterprise.daily_exports", "500", 
                "Daily export limit for enterprise users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        createSettingIfNotExists("enterprise.monitoring_items", "1000", 
                "Maximum monitoring items for enterprise users", SystemSettings.DataType.INTEGER, SystemSettings.Category.RATE_LIMITS);
        
        // Feature flags
        createSettingIfNotExists("features.api_access_enabled", "true", 
                "Enable API access for paid users", SystemSettings.DataType.BOOLEAN, SystemSettings.Category.FEATURES);
        createSettingIfNotExists("features.exports_enabled", "true", 
                "Enable export functionality", SystemSettings.DataType.BOOLEAN, SystemSettings.Category.FEATURES);
        createSettingIfNotExists("features.monitoring_enabled", "true", 
                "Enable monitoring functionality", SystemSettings.DataType.BOOLEAN, SystemSettings.Category.FEATURES);
        
        // Security settings
        createSettingIfNotExists("security.max_failed_logins", "5", 
                "Maximum failed login attempts before lockout", SystemSettings.DataType.INTEGER, SystemSettings.Category.SECURITY);
        createSettingIfNotExists("security.lockout_duration_minutes", "15", 
                "Account lockout duration in minutes", SystemSettings.DataType.INTEGER, SystemSettings.Category.SECURITY);
        createSettingIfNotExists("security.session_timeout_hours", "24", 
                "Session timeout in hours", SystemSettings.DataType.INTEGER, SystemSettings.Category.SECURITY);
        
        log.info("Default system settings initialized successfully");
    }
    
    private void createSettingIfNotExists(String key, String value, String description, 
                                        SystemSettings.DataType dataType, SystemSettings.Category category) {
        if (!systemSettingsRepository.existsByKey(key)) {
            SystemSettings setting = new SystemSettings(key, value, description, dataType, category);
            systemSettingsRepository.save(setting);
            log.debug("Created default setting: {} = {}", key, value);
        }
    }
    
    public Optional<SystemSettings> getSetting(String key) {
        return systemSettingsRepository.findByKey(key);
    }
    
    public String getStringValue(String key, String defaultValue) {
        return getSetting(key)
                .map(SystemSettings::getStringValue)
                .orElse(defaultValue);
    }
    
    public Integer getIntegerValue(String key, Integer defaultValue) {
        return getSetting(key)
                .map(SystemSettings::getIntegerValue)
                .orElse(defaultValue);
    }
    
    public Boolean getBooleanValue(String key, Boolean defaultValue) {
        return getSetting(key)
                .map(SystemSettings::getBooleanValue)
                .orElse(defaultValue);
    }
    
    // Rate limit getters with fallbacks
    public int getAnonymousDailySearchLimit() {
        return getIntegerValue("anonymous.daily_searches", DEFAULT_ANONYMOUS_DAILY_SEARCHES);
    }
    
    public int getFreeDailySearchLimit() {
        return getIntegerValue("free.daily_searches", DEFAULT_FREE_DAILY_SEARCHES);
    }
    
    public int getBasicDailySearchLimit() {
        return getIntegerValue("basic.daily_searches", DEFAULT_BASIC_DAILY_SEARCHES);
    }
    
    public int getProfessionalHourlySearchLimit() {
        return getIntegerValue("professional.hourly_searches", DEFAULT_PROFESSIONAL_HOURLY_SEARCHES);
    }
    
    public int getEnterpriseHourlySearchLimit() {
        return getIntegerValue("enterprise.hourly_searches", DEFAULT_ENTERPRISE_HOURLY_SEARCHES);
    }
    
    @Transactional
    public SystemSettings updateSetting(String key, String value) {
        SystemSettings setting = systemSettingsRepository.findByKey(key)
                .orElseThrow(() -> new IllegalArgumentException("Setting not found: " + key));
        
        if (!setting.isEditable()) {
            throw new IllegalArgumentException("Setting is not editable: " + key);
        }
        
        setting.setValue(value);
        SystemSettings updated = systemSettingsRepository.save(setting);
        log.info("Updated setting: {} = {}", key, value);
        return updated;
    }
    
    public List<SystemSettings> getSettingsByCategory(SystemSettings.Category category) {
        return systemSettingsRepository.findByCategoryOrderByKey(category);
    }
    
    public List<SystemSettings> getEditableSettings() {
        return systemSettingsRepository.findByEditableTrue();
    }
    
    public Map<String, String> getAllRateLimitSettings() {
        return systemSettingsRepository.findByCategory(SystemSettings.Category.RATE_LIMITS)
                .stream()
                .collect(Collectors.toMap(SystemSettings::getKey, SystemSettings::getValue));
    }
}
