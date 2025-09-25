package com.threatscopebackend.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.time.LocalDate;

@RestController
@RequestMapping("/anonymous")
@Slf4j
public class SimpleAnonymousUsageController {

    // In-memory storage for anonymous usage (for testing)
    private static final Map<String, Map<String, Object>> anonymousUsage = new ConcurrentHashMap<>();
    private static final int DAILY_LIMIT = 5;

    /**
     * Simple test endpoint to verify controller is loaded
     */
    @GetMapping("/test")
    public ResponseEntity<Map<String, Object>> testEndpoint() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Anonymous controller is working!");
        response.put("timestamp", LocalDate.now().toString());
        log.info("Test endpoint called successfully");
        return ResponseEntity.ok(response);
    }

    /**
     * Get anonymous user's current usage status
     */
    @GetMapping("/usage")
    public ResponseEntity<Map<String, Object>> getAnonymousUsage(HttpServletRequest request) {
        try {
            String ipAddress = getClientIpAddress(request);
            String today = LocalDate.now().toString();
            
            Map<String, Object> usage = getOrCreateUsage(ipAddress, today);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Anonymous usage retrieved");
            
            Map<String, Object> data = new HashMap<>();
            data.put("canSearch", (Integer) usage.get("count") < DAILY_LIMIT);
            data.put("remaining", Math.max(0, DAILY_LIMIT - (Integer) usage.get("count")));
            data.put("dailyLimit", DAILY_LIMIT);
            data.put("todayUsage", usage.get("count"));
            
            response.put("data", data);
            
            log.info("Anonymous usage retrieved for IP: {}, usage: {}", ipAddress, usage.get("count"));
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error retrieving anonymous usage", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to retrieve usage information");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Increment anonymous user usage (called after a search)
     */
    @PostMapping("/usage/increment")
    public ResponseEntity<Map<String, Object>> incrementAnonymousUsage(HttpServletRequest request) {
        try {
            String ipAddress = getClientIpAddress(request);
            String today = LocalDate.now().toString();
            
            Map<String, Object> usage = getOrCreateUsage(ipAddress, today);
            int currentCount = (Integer) usage.get("count");
            
            if (currentCount < DAILY_LIMIT) {
                usage.put("count", currentCount + 1);
                log.info("Incremented usage for IP: {}, new count: {}", ipAddress, currentCount + 1);
            }
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Usage recorded");
            
            Map<String, Object> data = new HashMap<>();
            data.put("canSearch", (Integer) usage.get("count") < DAILY_LIMIT);
            data.put("remaining", Math.max(0, DAILY_LIMIT - (Integer) usage.get("count")));
            data.put("dailyLimit", DAILY_LIMIT);
            data.put("todayUsage", usage.get("count"));
            
            response.put("data", data);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error recording anonymous usage", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to record usage");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Check if anonymous user can search (without incrementing)
     */
    @GetMapping("/usage/check")
    public ResponseEntity<Map<String, Object>> checkAnonymousLimit(HttpServletRequest request) {
        try {
            String ipAddress = getClientIpAddress(request);
            String today = LocalDate.now().toString();
            
            Map<String, Object> usage = getOrCreateUsage(ipAddress, today);
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("message", "Usage limit checked");
            
            Map<String, Object> data = new HashMap<>();
            data.put("canSearch", (Integer) usage.get("count") < DAILY_LIMIT);
            data.put("remaining", Math.max(0, DAILY_LIMIT - (Integer) usage.get("count")));
            
            response.put("data", data);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error checking anonymous usage limit", e);
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("success", false);
            errorResponse.put("message", "Failed to check usage limit");
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    /**
     * Get client IP address from request
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        String remoteAddr = request.getRemoteAddr();
        return remoteAddr != null ? remoteAddr : "unknown";
    }

    /**
     * Get or create usage record for IP and date
     */
    private Map<String, Object> getOrCreateUsage(String ipAddress, String date) {
        String key = ipAddress + ":" + date;
        return anonymousUsage.computeIfAbsent(key, k -> {
            Map<String, Object> usage = new HashMap<>();
            usage.put("count", 0);
            usage.put("date", date);
            usage.put("ip", ipAddress);
            return usage;
        });
    }

    /**
     * Debug endpoint to view all usage (for testing)
     */
    @GetMapping("/usage/debug")
    public ResponseEntity<Map<String, Object>> debugUsage() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("message", "Debug usage data");
        response.put("data", anonymousUsage);
        return ResponseEntity.ok(response);
    }
}
