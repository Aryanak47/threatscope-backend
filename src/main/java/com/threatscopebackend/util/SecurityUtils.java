package com.threatscopebackend.util;

import com.threatscopebackend.entity.enums.CommonEnums;
import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityUtils {
    
    /**
     * NEW: Subscription-based password masking
     * FREE: Partial masking (ab****ef)
     * BASIC/PROFESSIONAL/ENTERPRISE: Full password visibility
     */
    public static String maskPasswordByPlan(String password, CommonEnums.PlanType planType) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        
        return switch (planType) {
            case FREE -> maskPartially(password, 2, 2);        // ab****ef  
            case BASIC, PROFESSIONAL, ENTERPRISE -> password;  // Full visibility for paid plans
            default -> maskPartially(password, 2, 2);          // Default to masking for safety
        };
    }
    
    /**
     * LEGACY: Original password masking method
     * @deprecated Use maskPasswordByPlan instead for subscription-based masking
     */
    @Deprecated
    public static String maskPassword(String password) {
        if (password == null || password.isEmpty()) {
            return "";
        }
        
        // For very short passwords (1-3 characters), completely mask
        if (password.length() <= 3) {
            return "****";
        }
        
        // For passwords 4-6 characters, show first and last 1 character
        if (password.length() <= 6) {
            return password.charAt(0) + "***" + password.charAt(password.length() - 1);
        }
        
        // For longer passwords, show first and last 2 characters
        String first = password.substring(0, 2);
        String last = password.substring(password.length() - 2);
        int middleLength = Math.min(password.length() - 4, 8); // Cap at 8 asterisks
        String middle = "*".repeat(middleLength);
        
        return first + middle + last;
    }
    
    /**
     * Partially mask a password showing start and end characters
     */
    private static String maskPartially(String password, int showStart, int showEnd) {
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
     * NEW: Check if user can view full passwords based on subscription
     */
    public static boolean canViewFullPasswordsByPlan(CommonEnums.PlanType planType) {
        return planType != CommonEnums.PlanType.FREE;
    }
    
    /**
     * LEGACY: Check password visibility by role
     * @deprecated Use canViewFullPasswordsByPlan for subscription-based access
     */
    @Deprecated
    public static boolean canViewPassword(String userRole, boolean isAuthenticated) {
        if (!isAuthenticated) {
            return false; // Anonymous users cannot see passwords at all
        }
        
        // All authenticated users can see masked passwords
        // In future, we might have different rules for different roles
        return true;
    }
    
    /**
     * NEW: Get password display message based on subscription plan
     */
    public static String getPasswordDisplayMessageByPlan(boolean hasPassword, CommonEnums.PlanType planType) {
        if (!hasPassword) {
            return "No password found";
        }
        
        return switch (planType) {
            case FREE -> "Password partially hidden. Upgrade to view full passwords.";
            case BASIC, PROFESSIONAL, ENTERPRISE -> "Password compromised - change immediately";
            default -> "Password partially hidden.";
        };
    }
    
    /**
     * LEGACY: Gets a security message for password display
     * @deprecated Use getPasswordDisplayMessageByPlan for subscription-based messaging
     */
    @Deprecated
    public static String getPasswordDisplayMessage(boolean hasPassword, boolean canView) {
        if (!hasPassword) {
            return "No password found";
        }
        
        if (!canView) {
            return "Password available - sign in to view";
        }
        
        return "Password compromised - change immediately";
    }
}
