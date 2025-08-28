package com.threatscopebackend.dto.consultation.request;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import jakarta.validation.constraints.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateConsultationSessionRequest {
    
    // Optional - null for general cybersecurity consultations
    private Long alertId;
    
    @NotNull(message = "Plan ID is required")
    private Long planId;
    
    @NotBlank(message = "Session notes are required")
    @Size(max = 1000, message = "Session notes must not exceed 1000 characters")
    private String sessionNotes;
    
    private String preferredTime; // Optional scheduling preference
    
    // Optional consultation type/category for general consultations
    private String consultationType; // "alert" or "general"
    private String consultationCategory; // For general: "phishing-protection", "password-security", etc.
}
