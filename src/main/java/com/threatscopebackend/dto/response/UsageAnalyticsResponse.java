package com.threatscopebackend.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
@Builder
public class UsageAnalyticsResponse {
    
    private int totalSearchesToday;
    private int totalUsersToday;
    private int totalAnonymousSearchesToday;
    private double averageSearchesPerUser;
    
    private List<TopSearchingIP> topSearchingIPs;
    private Map<String, Integer> dailyUsageStats;
    
    @Data
    @Builder
    public static class TopSearchingIP {
        private String ipAddress;
        private int searchCount;
        private String country;
    }
}
