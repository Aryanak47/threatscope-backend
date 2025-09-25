package com.threatscopebackend.dto.consultation.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@Builder
public class ConsultationAdminDashboard {
    
    // Session Counts
    private Long totalSessions;
    private Long pendingSessions;
    private Long activeSessions;
    private Long completedSessions;
    private Long cancelledSessions;
    
    // Payment Status
    private Long sessionsAwaitingPayment;
    private Long paidSessions;
    private Long failedPayments;
    
    // Revenue
    private BigDecimal totalRevenue;
    private BigDecimal monthlyRevenue;
    private BigDecimal weeklyRevenue;
    private BigDecimal averageSessionValue;
    
    // Expert Statistics
    private Long totalExperts;
    private Long activeExperts;
    private Long availableExperts;

    // User Statistics
    private Long totalUsers;
    private Long activeUsersThisMonth;
    private Long newUsersThisWeek;
    
    // Performance Metrics
    private Double averageSessionDuration; // in minutes
    private Double averageResponseTime; // in hours
    private Double sessionCompletionRate; // percentage
    private Double customerSatisfactionRating; // 1-5 scale
    
    // Recent Activity
    private Map<String, Object> recentSessions;
    private Map<String, Object> popularPlans;
    private Map<String, Object> expertPerformance;
    
    // Time-based Statistics
    private Map<String, Long> sessionsPerDay; // Last 7 days
    private Map<String, BigDecimal> revenuePerMonth; // Last 6 months
    
    // Alerts and Issues
    private Long expiringSessions;
    private Long overduePayments;
    private Long unassignedSessions;
    
    private LocalDateTime lastUpdated;
}
