package com.threatscopebackend.dto.monitoring;

import lombok.Data;
import lombok.Builder;

import java.util.Map;

@Data
@Builder
public class MonitoringDashboardResponse {
    
    // Summary statistics
    private Long totalMonitoringItems;
    private Long activeMonitoringItems;
    private Long totalAlerts;
    private Long unreadAlerts;
    private Long criticalAlerts;
    private Long highPriorityAlerts;
    
    // Recent activity
    private Long alertsLast24Hours;
    private Long alertsLast7Days;
    private Long alertsLast30Days;
    
    // Monitoring breakdown by type
    private Map<String, Long> monitoringItemsByType;
    
    // Alert breakdown by severity
    private Map<String, Long> alertsBySeverity;
    
    // Alert breakdown by status
    private Map<String, Long> alertsByStatus;
    
    // Performance metrics
    private Double averageResponseTime; // in minutes
    private Long itemsCheckedLast24Hours;
    private Long successfulChecks;
    private Long failedChecks;
    
    // Trends (compared to previous period)
    private Integer alertTrend; // percentage change
    private Integer monitoringItemTrend; // percentage change
    
    // Health status
    private String overallHealth; // "GOOD", "WARNING", "CRITICAL"
    private String healthMessage;
}
