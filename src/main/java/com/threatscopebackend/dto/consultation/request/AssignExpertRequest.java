package com.threatscopebackend.dto.consultation.request;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class AssignExpertRequest {
    
    @NotNull(message = "Expert ID is required")
    private Long expertId;
    
    private String notes;
    
    private String specialization;
    
    private String urgencyLevel; // LOW, MEDIUM, HIGH, CRITICAL
}
