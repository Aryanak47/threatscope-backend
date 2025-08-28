package com.threatscopebackend.dto.consultation.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BreachAlertSummary {
    private Long id;
    private String title;
    private String description;
    private String severity;
    private String affectedEmail;
    private String affectedDomain;
    private String breachSource;
    private LocalDateTime breachDate;
    private LocalDateTime createdAt;
    
    public static BreachAlertSummary fromEntity(com.threatscopebackend.entity.postgresql.BreachAlert alert) {
        return BreachAlertSummary.builder()
            .id(alert.getId())
            .title(alert.getTitle())
            .description(alert.getDescription())
            .severity(alert.getSeverity().toString())
            .affectedEmail(alert.getAffectedEmail())
            .affectedDomain(alert.getAffectedDomain())
            .breachSource(alert.getBreachSource())
            .breachDate(alert.getBreachDate())
            .createdAt(alert.getCreatedAt())
            .build();
    }
}
