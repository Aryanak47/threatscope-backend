package com.threatscopebackend.dto.monitoring;

import com.threatscopebackend.entity.enums.CommonEnums;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateMonitoringItemRequest {
    
    @Size(max = 100, message = "Monitor name must not exceed 100 characters")
    private String monitorName;
    
    @Size(max = 500, message = "Description must not exceed 500 characters")
    private String description;
    
    private CommonEnums.MonitorFrequency frequency;
    
    private Boolean isActive;
    
    private Boolean emailAlerts;
    
    private Boolean inAppAlerts;
    
    private Boolean webhookAlerts;
}
