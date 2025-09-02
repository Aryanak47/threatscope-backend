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
public class ConsultationStatistics {
    private Long totalSessions;
    private Long activeSessions;
    private Long completedSessions;
    private Long pendingSessions;
    private BigDecimal totalRevenue;
    private BigDecimal dailyRevenue;
    private Double averageRating;
    private Integer totalExperts;
    private Integer availableExperts;
    private Long totalMessages;
    private Long totalFiles;
    private List<DailySessionCount> dailySessionCounts;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailySessionCount {
        private String date;
        private Long count;
    }
}
