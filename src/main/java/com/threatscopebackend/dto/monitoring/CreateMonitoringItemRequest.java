package com.threatscopebackend.dto.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateMonitoringItemRequest {
    
    @NotNull(message = "Monitor type is required")
    private CommonEnums.MonitorType monitorType;
    
    @NotBlank(message = "Target value is required")
    @Size(max = 500, message = "Target value must not exceed 500 characters")
    private String targetValue;
    
    @Size(max = 100, message = "Monitor name must not exceed 100 characters")
    private String monitorName;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    @NotNull(message = "Frequency is required")
    private CommonEnums.MonitorFrequency frequency;
    
    private Boolean isActive = true;
    
    private Boolean emailAlerts = true;
    
    private Boolean inAppAlerts = true;
    
    private Boolean webhookAlerts = false;
    
    // Validation helper methods
    public boolean isValidTargetValue() {
        if (targetValue == null || targetValue.trim().isEmpty()) {
            return false;
        }
        
        return switch (monitorType) {
            case EMAIL -> targetValue.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
            case DOMAIN -> targetValue.matches("^[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9](?:\\.[a-zA-Z0-9][a-zA-Z0-9-]{0,61}[a-zA-Z0-9])*$");
            case IP_ADDRESS -> targetValue.matches("^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
            case PHONE -> {
                String cleanPhone = targetValue.replaceAll("[^0-9+]", "");
                yield cleanPhone.length() >= 7 && cleanPhone.length() <= 15;
            }
            case USERNAME, KEYWORD -> targetValue.length() >= 2;
            case ORGANIZATION -> targetValue.length() >= 2;
        };
    }
    
    public String getValidationError() {
        if (!isValidTargetValue()) {
            return switch (monitorType) {
                case EMAIL -> "Please enter a valid email address";
                case DOMAIN -> "Please enter a valid domain name";
                case IP_ADDRESS -> "Please enter a valid IP address";
                case PHONE -> "Please enter a valid phone number";
                case USERNAME -> "Username must be at least 2 characters";
                case KEYWORD -> "Keyword must be at least 2 characters";
                case ORGANIZATION -> "Organization name must be at least 2 characters";
            };
        }
        return null;
    }
}
