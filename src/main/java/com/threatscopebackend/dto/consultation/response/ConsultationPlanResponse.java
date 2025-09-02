package com.threatscopebackend.dto.consultation.response;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ConsultationPlanResponse {
    private Long id;
    private String name;
    private String displayName;
    private String description;
    private BigDecimal price;
    private String currency;
    private Integer sessionDurationMinutes;
    private String durationDisplay;
    private List<String> features;
    private List<String> deliverables;
    private Boolean isPopular;
    private Boolean includesFollowUp;
    private Integer followUpDays;
    private String formattedPrice;
    
    public static ConsultationPlanResponse fromEntity(com.threatscopebackend.entity.postgresql.ConsultationPlan plan) {
        return ConsultationPlanResponse.builder()
            .id(plan.getId())
            .name(plan.getName())
            .displayName(plan.getDisplayName())
            .description(plan.getDescription())
            .price(plan.getPrice())
            .currency(plan.getCurrency())
            .sessionDurationMinutes(plan.getSessionDurationMinutes())
            .durationDisplay(plan.getDurationDisplay())
            .features(parseJsonArray(plan.getFeatures()))
            .deliverables(parseJsonArray(plan.getDeliverables()))
            .isPopular(plan.getIsPopular())
            .includesFollowUp(plan.getIncludesFollowUp())
            .followUpDays(plan.getFollowUpDays())
            .formattedPrice(plan.getFormattedPrice())
            .build();
    }
    
    private static List<String> parseJsonArray(String json) {
        if (json == null || json.isEmpty()) {
            return List.of();
        }
        try {
            // Remove brackets and quotes, then split by comma
            String cleaned = json.replace("[", "").replace("]", "").trim();
            if (cleaned.isEmpty()) {
                return List.of();
            }
            
            // Split by comma and clean each item
            return java.util.Arrays.stream(cleaned.split(","))
                    .map(item -> item.trim().replace("\"", ""))
                    .filter(item -> !item.isEmpty())
                    .collect(java.util.stream.Collectors.toList());
        } catch (Exception e) {
            System.err.println("Error parsing JSON array: " + json + ", error: " + e.getMessage());
            return List.of();
        }
    }
}
