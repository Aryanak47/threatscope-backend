package com.threatscopebackend.util;

import lombok.experimental.UtilityClass;

@UtilityClass
public class SecurityUtils {
    
    /**
     * Masks a password showing only first and last 2 characters
     * For security reasons, full passwords should never be sent to frontend
     * 
     * @param password The original password
     * @return Masked password in format "ab****xy" or "****" for short passwords
     */
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
     * Checks if a password should be visible to the user
     * Only authenticated users with proper permissions should see masked passwords
     * 
     * @param userRole User's role
     * @param isAuthenticated Whether user is authenticated
     * @return true if password can be shown (masked), false if completely hidden
     */
    public static boolean canViewPassword(String userRole, boolean isAuthenticated) {
        if (!isAuthenticated) {
            return false; // Anonymous users cannot see passwords at all
        }
        
        // All authenticated users can see masked passwords
        // In future, we might have different rules for different roles
        return true;
    }
    
    /**
     * Gets a security message for password display
     * 
     * @param hasPassword Whether the record has a password
     * @param canView Whether user can view passwords
     * @return Appropriate message for the UI
     */
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