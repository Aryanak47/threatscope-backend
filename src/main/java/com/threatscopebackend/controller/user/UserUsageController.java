package com.threatscopebackend.controller.user;

//import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.dto.response.ApiResponse;
import com.threatscopebackend.security.CurrentUser;
import com.threatscopebackend.security.UserPrincipal;
import com.threatscopebackend.service.core.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/user")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('USER')")
public class UserUsageController {

    private final UsageService usageService;

    /**
     * Get current user's usage statistics
     */
    @GetMapping("/usage/stats")
    public ResponseEntity<ApiResponse<UsageService.UserUsageStats>> getUserUsageStats(
            @CurrentUser UserPrincipal userPrincipal,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            if (startDate == null) {
                startDate = LocalDate.now().minusDays(30);
            }
            if (endDate == null) {
                endDate = LocalDate.now();
            }

            UsageService.UserUsageStats stats = usageService.getUserUsageStats(
                userPrincipal.getId(), startDate, endDate
            );

            return ResponseEntity.ok(ApiResponse.<UsageService.UserUsageStats>success("Usage statistics retrieved", stats));
        } catch (Exception e) {
            log.error("Error retrieving usage stats for user {}", userPrincipal.getId(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UsageService.UserUsageStats>error("Failed to retrieve usage statistics"));
        }
    }

    /**
     * Get current user's remaining quota for today
     */
    @GetMapping("/usage/quota")
    public ResponseEntity<ApiResponse<UsageService.UsageQuota>> getRemainingQuota(
            @CurrentUser UserPrincipal userPrincipal) {

        try {
            UsageService.UsageQuota quota = usageService.getRemainingQuota(userPrincipal);
            return ResponseEntity.ok(ApiResponse.<UsageService.UsageQuota>success("Usage quota retrieved", quota));
        } catch (Exception e) {
            log.error("Error retrieving quota for user {}", userPrincipal.getId(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UsageService.UsageQuota>error("Failed to retrieve usage quota"));
        }
    }

    /**
     * Get today's usage
     */
    @GetMapping("/usage/today")
    public ResponseEntity<ApiResponse<UsageService.UserUsageStats>> getTodayUsage(
            @CurrentUser UserPrincipal userPrincipal) {

        try {
            UsageService.UserUsageStats todayStats = usageService.getTodayUsage(userPrincipal.getId());
            return ResponseEntity.ok(ApiResponse.<UsageService.UserUsageStats>success("Today's usage retrieved", todayStats));
        } catch (Exception e) {
            log.error("Error retrieving today's usage for user {}", userPrincipal.getId(), e);
            return ResponseEntity.badRequest().body(ApiResponse.<UsageService.UserUsageStats>error("Failed to retrieve today's usage"));
        }
    }
}
