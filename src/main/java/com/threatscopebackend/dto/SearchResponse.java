package com.threatscopebackend.dto;

import com.threatscopebackend.document.StealerLog;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
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
        private LocalDateTime timestamp;
        private LocalDateTime dateCompromised;
        private String severity;
        private boolean hasPassword;
        private Boolean isVerified; // NEW: Verification status
        private Map<String, List<String>> highlights;
        private Map<String, Object> additionalData;
        private String password;

        // NEW: Enhanced metrics
        private Long sourceRecordsAffected; // Total records in this breach
        private Double sourceQualityScore;   // Data quality score for source
        private String sourceRiskLevel;      // Risk level for this source
        private List<String> availableDataTypes; // What data types are available
        private String breachDescription;    // Description of the breach
        private BreachTimeline timeline;     // When breach occurred vs discovered
        
        /**
         * Creates a SearchResult from a StealerLog with enhanced metrics
         */
        public static Optional<SearchResult> fromStealerLog(StealerLog log) {
            if (log == null) {
                return Optional.empty();
            }
            
            List<String> dataTypes = calculateAvailableDataTypes(log);
            boolean verified = calculateVerificationStatus(log);
            
            return Optional.of(SearchResult.builder()
                    .id(log.getId())
                    .email(log.getLogin())
                    .url(log.getUrl())
                    .domain(log.getDomain())
                    .password(log.getPassword() != null ? log.getPassword() : "")
                    .source(log.getSource())
                    .timestamp(parseDateFromSource(log.getSource()).orElse(log.getCreatedAt()))
                    .dateCompromised(parseDateFromSource(log.getSource()).orElse(log.getCreatedAt())) // Parse date from source or fallback to created_at
                    .hasPassword(log.getPassword() != null && !log.getPassword().isEmpty())
                    .severity(calculateSeverity(log.getLogin(), log.getDomain()))
                    .isVerified(verified)
                    // Enhanced metrics from BreachMetricsService (with null safety)
                    .sourceRecordsAffected( 0L)
                    .sourceQualityScore( 0.0)
//                    .sourceRiskLevel(metrics != null ? metrics.getRiskLevel() : "UNKNOWN")
                    .availableDataTypes(dataTypes)
                    .breachDescription(generateBreachDescription(log.getSource()))
                    .timeline(createBreachTimeline(log))
                    .build());
        }
        
        private static List<String> calculateAvailableDataTypes(StealerLog log) {
            List<String> types = new ArrayList<>();
            if (log.getLogin() != null) {
                if (isEmail(log.getLogin())) {
                    types.add("Email Address");
                } else {
                    types.add("Username");
                }
            }
            if (log.getPassword() != null && !log.getPassword().isEmpty()) {
                types.add("Password");
            }
            if (log.getUrl() != null) {
                types.add("Target URL");
            }
            if (log.getDomain() != null) {
                types.add("Domain");
            }
            if (log.getMetadata() != null) {
                types.add("Metadata");
            }
            return types;
        }
        
        private static boolean calculateVerificationStatus(StealerLog log) {
            int score = 0;
            int maxScore = 4;
            
            // Valid email format
            if (log.getLogin() != null && isEmail(log.getLogin())) score++;
            
            // Has password
            if (log.getPassword() != null && !log.getPassword().trim().isEmpty() && log.getPassword().length() >= 4) score++;
            
            // Valid URL
            if (log.getUrl() != null && (log.getUrl().startsWith("http://") || log.getUrl().startsWith("https://"))) score++;
            
            // Domain consistency
            if (checkDomainConsistency(log.getLogin(), log.getUrl(), log.getDomain())) score++;
            
            return (double) score / maxScore >= 0.75; // 75% threshold for verification
        }
        
        private static boolean checkDomainConsistency(String login, String url, String domain) {
            if (login == null || url == null) return false;
            
            try {
                if (login.contains("@")) {
                    String emailDomain = login.split("@")[1];
                    return url.contains(emailDomain) || (domain != null && domain.contains(emailDomain));
                }
            } catch (Exception e) {
                // Log error
            }
            return false;
        }
        
        /**
     * Parse date from source field in format 'stealer_logs_MM_dd_yyyy'
     * @param source The source string containing the date
     * @return Optional containing the parsed LocalDateTime, or empty if parsing fails
     */
    private static Optional<LocalDateTime> parseDateFromSource(String source) {
        if (source == null || !source.startsWith("stealer_logs_")) {
            return Optional.empty();
        }
        
        try {
            // Extract the date part after 'stealer_logs_'
            String datePart = source.substring("stealer_logs_".length());
            // Parse the date in format MM_dd_yyyy
            LocalDate date = LocalDate.parse(datePart, DateTimeFormatter.ofPattern("MM_dd_yyyy"));
            // Convert to LocalDateTime at start of day
            return Optional.of(date.atStartOfDay());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
    
    private static String generateBreachDescription(String source) {
            Map<String, String> sourceDescriptions = Map.of(
                "stealer_logs_10_07_2025", "Information stealer malware campaign targeting credentials",
                "stealer_logs_09_15_2025", "Credential harvesting operation via malicious software",
                "stealer_logs_08_20_2025", "Data theft incident from stealer malware distribution"
            );
            
            return sourceDescriptions.getOrDefault(source, "Credential breach from " + source);
        }
        
        private static BreachTimeline createBreachTimeline(StealerLog log) {
            LocalDateTime breachDate = log.getCreatedAt();
            LocalDateTime discoveryDate = log.getCreatedAt(); // Assuming same for now
            
            return BreachTimeline.builder()
                .breachDate(breachDate)
                .discoveryDate(discoveryDate)
                .reportedDate(discoveryDate)
                .daysBetweenBreachAndDiscovery(0L) // Calculate actual difference
                .build();
        }
        
        private static String calculateSeverity(String login, String domain) {
            if (isEmail(login)) {
                String emailDomain = login.split("@")[1];
                if (isHighValueDomain(emailDomain)) {
                    return "CRITICAL";
                }
                return "HIGH";
            } else if (isHighValueDomain(domain)) {
                return "HIGH";
            } else {
                return "MEDIUM";
            }
        }
        
        private static boolean isEmail(String login) {
            return login != null && EMAIL_PATTERN.matcher(login).matches();
        }
        
        private static boolean isHighValueDomain(String domain) {
            if (domain == null) return false;
            
            for (String hvDomain : HIGH_VALUE_DOMAINS) {
                if (domain.equalsIgnoreCase(hvDomain)) {
                    return true;
                }
            }
            return false;
        }
    }
    
    @Data
    @Builder
    public static class BreachTimeline {
        private LocalDateTime breachDate;
        private LocalDateTime discoveryDate;
        private LocalDateTime reportedDate;
        private Long daysBetweenBreachAndDiscovery;
    }
}
