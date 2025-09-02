package com.threatscopebackend.service.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class PasswordMaskingService {
    
    private final ObjectMapper objectMapper;
    
    /**
     * Mask sensitive data in JSON based on user's subscription plan
     * FREE: Partial masking (ab****ef)
     * BASIC/PROFESSIONAL/ENTERPRISE: Full password visibility
     */
    public String maskSensitiveDataBasedOnPlan(String jsonData, CommonEnums.PlanType planType) {
        if (jsonData == null || jsonData.trim().isEmpty()) {
            return jsonData;
        }
        
        try {
            Map<String, Object> data = objectMapper.readValue(jsonData, Map.class);
            
            // Mask password field based on subscription
            if (data.containsKey("password")) {
                String password = (String) data.get("password");
                data.put("password", maskPassword(password, planType));
            }
            
            // Also check for other password-like fields
            maskOtherSensitiveFields(data, planType);
            
            return objectMapper.writeValueAsString(data);
            
        } catch (Exception e) {
            log.error("Error masking sensitive data: {}", e.getMessage());
            return jsonData; // Return original if parsing fails
        }
    }
    
    /**
     * Mask password based on subscription plan
     * FREE: ab****ef (partial masking)
     * BASIC/PROFESSIONAL/ENTERPRISE: full password (abcdef)
     */
    private String maskPassword(String password, CommonEnums.PlanType planType) {
        if (password == null || password.trim().isEmpty()) {
            return password;
        }
        
        return switch (planType) {
            case FREE -> maskPartially(password, 2, 2);        // ab****ef
            case BASIC, PROFESSIONAL, ENTERPRISE -> password;  // Full visibility for paid plans
            default -> maskPartially(password, 2, 2);          // Default to masking for safety
        };
    }
    
    /**
     * Partially mask a password showing start and end characters
     */
    private String maskPartially(String password, int showStart, int showEnd) {
        if (password.length() <= showStart + showEnd) {
            // For very short passwords, mask completely
            return "*".repeat(Math.max(4, password.length()));
        }
        
        String start = password.substring(0, showStart);
        String end = password.substring(password.length() - showEnd);
        int maskedLength = Math.max(4, password.length() - showStart - showEnd);
        
        return start + "*".repeat(maskedLength) + end;
    }
    
    /**
     * Mask other sensitive fields that might contain passwords
     */
    private void maskOtherSensitiveFields(Map<String, Object> data, CommonEnums.PlanType planType) {
        // List of fields that might contain sensitive information
        String[] sensitiveFields = {"pwd", "pass", "passwd", "secret", "key"};
        
        for (String field : sensitiveFields) {
            if (data.containsKey(field) && data.get(field) instanceof String) {
                String value = (String) data.get(field);
                data.put(field, maskPassword(value, planType));
            }
        }
    }
    
    /**
     * Check if user has permission to view full passwords
     */
    public boolean canViewFullPasswords(CommonEnums.PlanType planType) {
        return planType != CommonEnums.PlanType.FREE;
    }
    
    /**
     * Get masking description for frontend display
     */
    public String getMaskingDescription(CommonEnums.PlanType planType) {
        return switch (planType) {
            case FREE -> "Passwords partially hidden. Upgrade to view full passwords.";
            case BASIC, PROFESSIONAL, ENTERPRISE -> "Full password visibility included in your plan.";
            default -> "Passwords partially hidden.";
        };
    }
}