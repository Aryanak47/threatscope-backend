package com.threatscopebackend.dto;

import com.threatscopebackend.document.StealerLog;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SearchResponse {
    
    private List<SearchResult> results;
    
    private long totalResults;
    
    private int currentPage;
    
    private int totalPages;
    
    private int pageSize;
    
    private long executionTimeMs;
    
    private String query;
    
    private SearchRequest.SearchType searchType;
    
    private Map<String, Object> aggregations;
    
    private List<String> suggestions;
    
    private SearchMetadata metadata;
    
    @Data
    @Builder
    public static class SearchMetadata {
        private Map<String, Long> sourceBreakdown;
        private Map<String, Long> countryBreakdown;
        private Map<String, Long> severityBreakdown;
        private Map<String, Long> dateBreakdown;
        private long verifiedCount;
        private long unverifiedCount;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class SearchResult {
        private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
        );
        
        private static final String[] HIGH_VALUE_DOMAINS = {
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
            "facebook.com", "twitter.com", "linkedin.com", "instagram.com",
            "paypal.com", "amazon.com", "apple.com", "microsoft.com",
            "google.com", "github.com", "dropbox.com"
        };
        
        private String id;
        private String email;
        private String domain;
        private String url;
        private String source;
        private LocalDateTime timestamp;  // Timestamp from Elasticsearch
        private LocalDateTime dateCompromised;
        private String severity;
        private boolean hasPassword;
//        private Integer riskScore;
        private Map<String, List<String>> highlights;
        private Map<String, Object> additionalData;
        
        /**
         * Creates a SearchResult from a StealerLog with all necessary enrichment
         */
        public static Optional<SearchResult> fromStealerLog(StealerLog log) {
            if (log == null) {
                return Optional.empty();
            }
            
            return Optional.ofNullable(SearchResult.builder()
                    .id(log.getId())
                    .email(log.getLogin())
                    .url(log.getUrl())
                    .domain(log.getDomain())
                    .source(log.getSource())
                    .timestamp(log.getCreatedAt())  // Using createdAt as the timestamp
                    .hasPassword(log.getPassword() != null && !log.getPassword().isEmpty())
                    .severity(calculateSeverity(log.getLogin(), log.getDomain()))
                    .build());
        }
        
        private static String calculateSeverity(String login, String domain) {
            if (isEmail(login)) {
                return "HIGH";
            } else if (isHighValueDomain(domain)) {
                return "CRITICAL";
            } else {
                return "MEDIUM";
            }
        }
        
        private static boolean isEmail(String login) {
            return login != null && EMAIL_PATTERN.matcher(login).matches();
        }
        
        private static boolean isHighValueDomain(String domain) {
            if (domain == null) {
                return false;
            }
            
            for (String hvDomain : HIGH_VALUE_DOMAINS) {
                if (domain.equalsIgnoreCase(hvDomain)) {
                    return true;
                }
            }
            return false;
        }
    }
}
