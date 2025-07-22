package com.threatscopebackend.dto.monitoring;

import com.threatscopebackend.entity.enums.AlertActionType;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateAlertActionRequest {
    
    @NotNull(message = "Action type is required")
    private AlertActionType actionType;
    
    @Size(max = 1000, message = "User message cannot exceed 1000 characters")
    private String userMessage;
    
    @Size(max = 100, message = "Contact email cannot exceed 100 characters")
    private String contactEmail;
    
    @Size(max = 20, message = "Contact phone cannot exceed 20 characters")
    private String contactPhone;
    
    @Size(max = 100, message = "Company name cannot exceed 100 characters")
    private String companyName;
    
    private String urgencyLevel; // LOW, MEDIUM, HIGH, CRITICAL
    
    @Size(max = 50, message = "Budget cannot exceed 50 characters")
    private String estimatedBudget;
    
    @Size(max = 100, message = "Timeline cannot exceed 100 characters")
    private String preferredTimeline;
    
    @Size(max = 2000, message = "Additional context cannot exceed 2000 characters")
    private String additionalContext;
}
