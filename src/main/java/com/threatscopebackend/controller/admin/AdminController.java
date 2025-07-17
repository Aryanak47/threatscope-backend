package com.threatscopebackend.controller.admin;

import com.threatscopebackend.dto.request.UpdateSettingRequest;

import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.dto.response.UsageAnalyticsResponse;
import com.threatscopebackend.entity.postgresql.SystemSettings;
import com.threatscopebackend.service.core.SystemSettingsService;
import com.threatscopebackend.service.core.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class AdminController {

    private final SystemSettingsService systemSettingsService;
    private final UsageService usageService;

    /**
     * Get all system settings by category
     */
    @GetMapping("/settings")
    public ResponseEntity<ApiResponse<Map<String, List<SystemSettings>>>> getAllSettings() {
        try {
            Map<String, List<SystemSettings>> settingsByCategory = Map.of(
                "RATE_LIMITS", systemSettingsService.getSettingsByCategory(SystemSettings.Category.RATE_LIMITS),
                "FEATURES", systemSettingsService.getSettingsByCategory(SystemSettings.Category.FEATURES),
                "SECURITY", systemSettingsService.getSettingsByCategory(SystemSettings.Category.SECURITY),
                "SYSTEM", systemSettingsService.getSettingsByCategory(SystemSettings.Category.SYSTEM)
            );

            return ResponseEntity.ok(ApiResponse.<Map<String, List<SystemSettings>>>success("Settings retrieved successfully", settingsByCategory));
        } catch (Exception e) {
            log.error("Error retrieving settings", e);
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, List<SystemSettings>>>error("Failed to retrieve settings"));
        }
    }

    /**
     * Get rate limit settings specifically
     */
    @GetMapping("/settings/rate-limits")
    public ResponseEntity<ApiResponse<Map<String, String>>> getRateLimitSettings() {
        try {
            Map<String, String> rateLimits = systemSettingsService.getAllRateLimitSettings();
            return ResponseEntity.ok(ApiResponse.<Map<String, String>>success("Rate limit settings retrieved", rateLimits));
        } catch (Exception e) {
            log.error("Error retrieving rate limit settings", e);
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, String>>error("Failed to retrieve rate limit settings"));
        }
    }

    /**
     * Update a system setting
     */
    @PutMapping("/settings/{key}")
    public ResponseEntity<ApiResponse<SystemSettings>> updateSetting(
            @PathVariable String key,
            @Valid @RequestBody UpdateSettingRequest request) {
        try {
            SystemSettings updated = systemSettingsService.updateSetting(key, request.getValue());
            log.info("Admin updated setting: {} = {}", key, request.getValue());
            return ResponseEntity.ok(ApiResponse.<SystemSettings>success("Setting updated successfully", updated));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(ApiResponse.<SystemSettings>error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error updating setting: {}", key, e);
            return ResponseEntity.badRequest().body(ApiResponse.<SystemSettings>error("Failed to update setting"));
        }
    }

    /**
     * Get usage analytics for the admin dashboard
     */
    @GetMapping("/analytics/usage")
    public ResponseEntity<ApiResponse<UsageAnalyticsResponse>> getUsageAnalytics(
            @RequestParam(defaultValue = "30") int days) {
        try {
            LocalDate endDate = LocalDate.now();
            LocalDate startDate = endDate.minusDays(days);

            // This would need implementation in UsageService to aggregate data
            // For now, returning a placeholder response
            UsageAnalyticsResponse analytics = UsageAnalyticsResponse.builder()
                    .totalSearchesToday(0)
                    .totalUsersToday(0)
                    .totalAnonymousSearchesToday(0)
                    .averageSearchesPerUser(0.0)
                    .topSearchingIPs(List.of())
                    .dailyUsageStats(Map.of())
                    .build();

            return ResponseEntity.ok(ApiResponse.<UsageAnalyticsResponse>success("Usage analytics retrieved", analytics));
        } catch (Exception e) {
            log.error("Error retrieving usage analytics", e);
            return ResponseEntity.badRequest().body(ApiResponse.<UsageAnalyticsResponse>error("Failed to retrieve analytics"));
        }
    }

    /**
     * Get current system status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getSystemStatus() {
        try {
            Map<String, Object> status = Map.of(
                "database", "connected",
                "elasticsearch", "connected",
                "version", "1.0.0",
                "uptime", System.currentTimeMillis(),
                "activeUsers", 0, // Would need implementation
                "totalSearches", 0 // Would need implementation
            );

            return ResponseEntity.ok(ApiResponse.<Map<String, Object>>success("System status retrieved", status));
        } catch (Exception e) {
            log.error("Error retrieving system status", e);
            return ResponseEntity.badRequest().body(ApiResponse.<Map<String, Object>>error("Failed to retrieve system status"));
        }
    }
}
