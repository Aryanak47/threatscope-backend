package com.threatscopebackend.dto.consultation.response;

import com.threatscopebackend.entity.postgresql.Expert;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@Builder
public class ExpertResponse {
    
    private String id;
    private String name;
    private String email;
    private String specialization;
    private String description;
    
    // Availability
    private boolean isAvailable;
    private boolean isOnline;
    private LocalDateTime lastActive;
    
    // Performance Metrics
    private BigDecimal rating;
    private Long totalSessions;
    private Long completedSessions;
    private BigDecimal averageSessionDuration; // in minutes
    private BigDecimal customerSatisfactionScore;
    
    // Expertise
    private List<String> skills;
    private List<String> certifications;
    private String experienceLevel; // JUNIOR, SENIOR, EXPERT, LEAD
    private Integer yearsOfExperience;
    
    // Pricing
    private BigDecimal hourlyRate;
    private BigDecimal sessionRate;
    
    // Current Workload
    private Long activeSessions;
    private Long pendingSessions;
    private Integer maxConcurrentSessions;
    
    // Schedule
    private String timezone;
    private String workingHours;
    private List<String> availableDays;
    
    // Profile
    private String profilePicture;
    private String linkedinProfile;
    private String companyAffiliation;
    
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    // ===== STATIC FACTORY METHODS =====
    
    /**
     * Create ExpertResponse from Expert entity
     */
    public static ExpertResponse fromEntity(Expert expert) {
        if (expert == null) {
            return null;
        }
        
        return ExpertResponse.builder()
                .id(expert.getId().toString())
                .name(expert.getName())
                .email(expert.getEmail())
                .specialization(expert.getSpecialization())
                .description(expert.getBio()) // Use bio field
                .isAvailable(expert.getIsAvailable())
                .isOnline(false) // Expert entity doesn't have isOnline field yet
                .lastActive(expert.getLastActiveAt())
                .totalSessions(expert.getTotalSessions() != null ? expert.getTotalSessions().longValue() : 0L)
                .completedSessions(expert.getCompletedSessions() != null ? expert.getCompletedSessions().longValue() : 0L)
                .averageSessionDuration(BigDecimal.valueOf(0)) // Would need calculation from session data
                .skills(expert.getExpertiseAreas() != null ?
                       List.of(expert.getExpertiseAreas().split(",")) : List.of())
                .certifications(expert.getCertifications() != null ? 
                              List.of(expert.getCertifications().split(",")) : List.of())
                .experienceLevel("SENIOR") // Expert entity doesn't have experienceLevel field yet
                .yearsOfExperience(null) // Expert entity doesn't have this field yet
                .hourlyRate(expert.getHourlyRate())
                .sessionRate(expert.getHourlyRate()) // Use hourly rate as session rate for now
                .activeSessions(0L) // Would need calculation from active sessions
                .pendingSessions(0L) // Would need calculation from pending sessions
                .maxConcurrentSessions(expert.getMaxConcurrentSessions())
                .timezone(expert.getTimezone())
                .workingHours(null) // Expert entity doesn't have this field yet
                .availableDays(expert.getLanguages() != null ? 
                             List.of(expert.getLanguages().split(",")) : List.of()) // Using languages field temporarily
                .profilePicture(null) // Expert entity doesn't have this field yet
                .linkedinProfile(null) // Expert entity doesn't have this field yet
                .companyAffiliation(null) // Expert entity doesn't have this field yet
                .createdAt(expert.getCreatedAt())
                .updatedAt(expert.getUpdatedAt())
                .build();
    }
}
