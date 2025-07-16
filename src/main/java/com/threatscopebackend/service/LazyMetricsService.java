package com.threatscopebackend.service;

import com.threatscopebackend.document.StealerLog;
import com.threatscopebackend.repository.mongo.StealerLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.*;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class LazyMetricsService {
    
    private final MongoTemplate mongoTemplate;
    private final StealerLogRepository stealerLogRepository;
    
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$"
    );
    
    private static final Set<String> HIGH_VALUE_DOMAINS = Set.of(
        "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
        "facebook.com", "twitter.com", "linkedin.com", "instagram.com",
        "paypal.com", "amazon.com", "apple.com", "microsoft.com",
        "google.com", "github.com", "dropbox.com", "netflix.com",
        "binance.com", "coinbase.com", "kraken.com", "stripe.com"
    );
    
    /**
     * Calculate detailed metrics for a specific source using MongoDB aggregation
     * This is called lazily when user requests detailed view
     */
    public SourceDetailedMetrics calculateDetailedMetrics(String source) {
        try {
            log.info("🔍 Calculating detailed metrics for source: {}", source);
            
            // 1. First, let's see what data we have in the collection
            long totalInCollection = mongoTemplate.count(Query.query(Criteria.where("id").exists(true)), StealerLog.class);
            log.info("📊 Total documents in collection: {}", totalInCollection);
            
            // 2. Try different field names to find the correct one
            long totalRecords = 0;
            String actualFieldName = null;
            
            // Try source_db first (what we expect)
            totalRecords = mongoTemplate.count(
                Query.query(Criteria.where("source_db").is(source)), 
                StealerLog.class
            );
            
            if (totalRecords > 0) {
                actualFieldName = "source_db";
                log.info("✅ Found {} records using field 'source_db' = '{}'", totalRecords, source);
            } else {
                log.warn("❌ No records found with 'source_db' = '{}'", source);
                
                // Try 'source' field as fallback
                totalRecords = mongoTemplate.count(
                    Query.query(Criteria.where("source").is(source)), 
                    StealerLog.class
                );
                if (totalRecords > 0) {
                    actualFieldName = "source";
                    log.info("✅ Found {} records using field 'source' = '{}'", totalRecords, source);
                } else {
                    log.warn("❌ No records found with 'source' = '{}'", source);
                    
                    // Let's see what source values actually exist
                    List<String> availableSources = getAvailableSourceValues();
                    log.warn("❓ Available source values in collection: {}", availableSources);
                    
                    return createDefaultMetrics(source);
                }
            }
            
            // 2. Password Statistics
            PasswordStats passwordStats = calculatePasswordStats(actualFieldName, source);
            
            // 3. Domain Distribution
            Map<String, Long> domainDistribution = calculateDomainDistribution(actualFieldName, source);
            
            // 4. Data Quality Assessment (overall, not source-specific)
            DataQualityAssessment qualityAssessment = calculateDataQuality();
            
            // 5. Extract breach date from source name
            LocalDateTime breachDate = extractBreachDateFromSource(source);
            
            // 6. Calculate risk level with enhanced logic
            String riskLevel = calculateRiskLevel(totalRecords, passwordStats, domainDistribution, qualityAssessment);
            
            // 7. Calculate additional insights
            Map<String, Object> insights = calculateInsights(source, totalRecords, passwordStats, domainDistribution);
            
            return SourceDetailedMetrics.builder()
                .source(source)
                .totalRecordsAffected(totalRecords)
                .passwordStats(passwordStats)
                .domainDistribution(domainDistribution)
                .qualityAssessment(qualityAssessment)
                .breachDate(breachDate)
                .riskLevel(riskLevel)
                .insights(insights)
                .calculatedAt(LocalDateTime.now())
                .build();
                
        } catch (Exception e) {
            log.error("Error calculating detailed metrics for source {}: {}", source, e.getMessage(), e);
            return createDefaultMetrics(source);
        }
    }
    
    /**
    * Calculate password statistics using separate queries for better compatibility
    */
    private PasswordStats calculatePasswordStats(String actualFieldName, String source) {
    try {
    log.info("🔍 Calculating password stats for source '{}' using field '{}'", source, actualFieldName);
    
    // Total count
    long totalCount = mongoTemplate.count(
        Query.query(Criteria.where(actualFieldName).is(source)), 
        StealerLog.class
    );
    
    // Count with passwords (not null, not empty, not just whitespace)
    long withPassword = mongoTemplate.count(
    Query.query(Criteria.where(actualFieldName).is(source)
        .and("password").exists(true).ne("").ne(null)
            .not().regex("^\\s*$")), 
        StealerLog.class
    );
    
    // Count strong passwords (basic pattern: at least 8 chars, has upper, lower, digit)
    long strongPasswords = mongoTemplate.count(
    Query.query(Criteria.where(actualFieldName).is(source)
            .and("password").regex("^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).{8,}$")), 
        StealerLog.class
    );
    
    long withoutPassword = totalCount - withPassword;
    double percentage = totalCount > 0 ? (double) withPassword / totalCount * 100 : 0.0;
    double strongPasswordPercentage = withPassword > 0 ? (double) strongPasswords / withPassword * 100 : 0.0;
    
    log.info("📋 Password stats: total={}, withPassword={}, percentage={:.1f}%", 
    totalCount, withPassword, percentage);
    
    return PasswordStats.builder()
    .totalRecords(totalCount)
    .withPassword(withPassword)
    .withoutPassword(withoutPassword)
            .strongPasswords(strongPasswords)
        .percentage(percentage)
        .strongPasswordPercentage(strongPasswordPercentage)
    .build();
    
    } catch (Exception e) {
        log.error("Error calculating password stats for source {}: {}", source, e.getMessage());
            return PasswordStats.builder()
                .totalRecords(0L).withPassword(0L).withoutPassword(0L)
                .strongPasswords(0L).percentage(0.0).strongPasswordPercentage(0.0)
                .build();
        }
    }
    
    /**
     * Calculate domain distribution using MongoDB aggregation with correct field name
     */
    private Map<String, Long> calculateDomainDistribution(String actualFieldName, String source) {
        try {
            log.info("🔍 Calculating domain distribution for source '{}' using field '{}'", source, actualFieldName);
            
            Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(Criteria.where(actualFieldName).is(source) // Use correct field name
                    .and("domain").exists(true).ne("").ne(null)),
                Aggregation.group("domain").count().as("count"),
                Aggregation.sort(Sort.Direction.DESC, "count"),
                Aggregation.limit(10) // Top 10 domains
            );
            
            AggregationResults<Map> results = mongoTemplate.aggregate(aggregation, "stealer_logs", Map.class);
            
            Map<String, Long> distribution = new LinkedHashMap<>();
            for (Map<String, Object> result : results.getMappedResults()) {
                String domain = (String) result.get("_id");
                long count = ((Number) result.get("count")).longValue();
                if (domain != null && !domain.trim().isEmpty()) {
                    distribution.put(domain, count);
                }
            }
            
            log.info("🌐 Found {} unique domains for source '{}'", distribution.size(), source);
            
            return distribution;
            
        } catch (Exception e) {
            log.error("Error calculating domain distribution for source {}: {}", source, e.getMessage());
            return new HashMap<>();
        }
    }
    
    /**
     * Calculate data quality assessment for all records (not source-specific)
     */
    private DataQualityAssessment calculateDataQuality() {
        try {
            log.info("🔍 Calculating overall data quality for entire collection");
            
            // Total records in the entire collection
            long totalRecords = mongoTemplate.count(
                Query.query(Criteria.where("id").exists(true)), 
                StealerLog.class
            );
            
            log.info("📊 Total records for quality assessment: {}", totalRecords);
            
            if (totalRecords == 0) {
                log.warn("⚠️ No records found for data quality assessment");
                return DataQualityAssessment.builder()
                    .score(0.0)
                    .details("No records found")
                    .loginCompleteness(0.0)
                    .passwordCompleteness(0.0)
                    .urlCompleteness(0.0)
                    .domainCompleteness(0.0)
                    .build();
            }
            
            // Count records with each field across all data
            long hasLogin = mongoTemplate.count(
                Query.query(Criteria.where("login").exists(true).ne("").ne(null)), 
                StealerLog.class
            );
            
            long hasPassword = mongoTemplate.count(
                Query.query(Criteria.where("password").exists(true).ne("").ne(null)), 
                StealerLog.class
            );
            
            long hasUrl = mongoTemplate.count(
                Query.query(Criteria.where("url").exists(true).ne("").ne(null)), 
                StealerLog.class
            );
            
            long hasDomain = mongoTemplate.count(
                Query.query(Criteria.where("domain").exists(true).ne("").ne(null)), 
                StealerLog.class
            );
            
            log.info("📋 Data completeness counts: login={}, password={}, url={}, domain={}", 
                hasLogin, hasPassword, hasUrl, hasDomain);
            
            // Calculate completeness percentages
            double loginCompleteness = (double) hasLogin / totalRecords * 100;
            double passwordCompleteness = (double) hasPassword / totalRecords * 100;
            double urlCompleteness = (double) hasUrl / totalRecords * 100;
            double domainCompleteness = (double) hasDomain / totalRecords * 100;
            
            log.info("📊 Completeness percentages: login={:.1f}%, password={:.1f}%, url={:.1f}%, domain={:.1f}%", 
                loginCompleteness, passwordCompleteness, urlCompleteness, domainCompleteness);
            
            // Calculate quality score (0-100) with enhanced weights
            double loginScore = loginCompleteness * 0.25; // 25% weight
            double passwordScore = passwordCompleteness * 0.35; // 35% weight
            double urlScore = urlCompleteness * 0.25; // 25% weight
            double domainScore = domainCompleteness * 0.15; // 15% weight
            
            double totalScore = loginScore + passwordScore + urlScore + domainScore;
            
            log.info("✅ Calculated overall data quality score: {:.1f}/100", totalScore);
            
            String details = String.format(
                "Login: %.1f%%, Password: %.1f%%, URL: %.1f%%, Domain: %.1f%%",
                loginCompleteness, passwordCompleteness, urlCompleteness, domainCompleteness
            );
            
            return DataQualityAssessment.builder()
                .score(totalScore)
                .details(details)
                .loginCompleteness(loginCompleteness)
                .passwordCompleteness(passwordCompleteness)
                .urlCompleteness(urlCompleteness)
                .domainCompleteness(domainCompleteness)
                .build();
                
        } catch (Exception e) {
            log.error("Error calculating overall data quality: {}", e.getMessage());
            return DataQualityAssessment.builder()
                .score(0.0)
                .details("Assessment failed: " + e.getMessage())
                .build();
        }
    }
    
    /**
     * Extract breach date from source name
     * Example: stealer_logs_10_07_2025 -> October 7, 2025
     */
    private LocalDateTime extractBreachDateFromSource(String source) {
        try {
            // Pattern: stealer_logs_MM_DD_YYYY or similar
            Pattern pattern = Pattern.compile(".*(\\d{2})_(\\d{2})_(\\d{4}).*");
            java.util.regex.Matcher matcher = pattern.matcher(source);
            
            if (matcher.find()) {
                int month = Integer.parseInt(matcher.group(1));
                int day = Integer.parseInt(matcher.group(2));
                int year = Integer.parseInt(matcher.group(3));
                
                // Validate date components
                if (month >= 1 && month <= 12 && day >= 1 && day <= 31 && year >= 2000 && year <= 2030) {
                    return LocalDateTime.of(year, month, day, 0, 0, 0);
                }
            }
            
            // Try alternative patterns
            Pattern yearOnlyPattern = Pattern.compile(".*(\\d{4}).*");
            java.util.regex.Matcher yearMatcher = yearOnlyPattern.matcher(source);
            if (yearMatcher.find()) {
                int year = Integer.parseInt(yearMatcher.group(1));
                if (year >= 2000 && year <= 2030) {
                    return LocalDateTime.of(year, 1, 1, 0, 0, 0);
                }
            }
            
        } catch (Exception e) {
            log.warn("Could not extract date from source: {} - {}", source, e.getMessage());
        }
        
        // Fallback to current date
        return LocalDateTime.now();
    }
    
    /**
     * Calculate risk level based on enhanced metrics
     */
    private String calculateRiskLevel(long totalRecords, PasswordStats passwordStats, 
                                    Map<String, Long> domainDistribution, DataQualityAssessment qualityAssessment) {
        try {
            // Risk factors
            boolean largeBreach = totalRecords > 1000000; // 1M+ records
            boolean mediumBreach = totalRecords > 100000; // 100K+ records
            boolean smallBreach = totalRecords > 10000; // 10K+ records
            
            boolean hasHighPasswordRate = passwordStats.getPercentage() > 70;
            boolean hasMediumPasswordRate = passwordStats.getPercentage() > 40;
            
            boolean hasHighValueDomains = domainDistribution.keySet().stream()
                .anyMatch(HIGH_VALUE_DOMAINS::contains);
            
            boolean hasGoodDataQuality = qualityAssessment.getScore() > 75;
            boolean hasMediumDataQuality = qualityAssessment.getScore() > 50;
            
            // Calculate risk score
            int riskScore = 0;
            
            // Size factor
            if (largeBreach) riskScore += 40;
            else if (mediumBreach) riskScore += 25;
            else if (smallBreach) riskScore += 15;
            else riskScore += 5;
            
            // Password factor
            if (hasHighPasswordRate) riskScore += 25;
            else if (hasMediumPasswordRate) riskScore += 15;
            else riskScore += 5;
            
            // Domain value factor
            if (hasHighValueDomains) riskScore += 20;
            else riskScore += 5;
            
            // Data quality factor
            if (hasGoodDataQuality) riskScore += 15;
            else if (hasMediumDataQuality) riskScore += 10;
            else riskScore += 5;
            
            // Determine risk level based on score
            if (riskScore >= 85) return "CRITICAL";
            else if (riskScore >= 65) return "HIGH";
            else if (riskScore >= 45) return "MEDIUM";
            else return "LOW";
            
        } catch (Exception e) {
            log.error("Error calculating risk level for source {}: {}", passwordStats != null ? passwordStats.getTotalRecords() : "unknown", e.getMessage());
            return "UNKNOWN";
        }
    }
    
    /**
     * Calculate additional insights
     */
    private Map<String, Object> calculateInsights(String source, long totalRecords, 
                                                PasswordStats passwordStats, Map<String, Long> domainDistribution) {
        Map<String, Object> insights = new HashMap<>();
        
        try {
            // Top domain insight
            if (!domainDistribution.isEmpty()) {
                String topDomain = domainDistribution.entrySet().iterator().next().getKey();
                Long topDomainCount = domainDistribution.entrySet().iterator().next().getValue();
                insights.put("topDomain", topDomain);
                insights.put("topDomainCount", topDomainCount);
                insights.put("topDomainPercentage", (double) topDomainCount / totalRecords * 100);
            }
            
            // High-value domains count
            long highValueDomainsCount = domainDistribution.keySet().stream()
                .mapToLong(domain -> HIGH_VALUE_DOMAINS.contains(domain) ? domainDistribution.get(domain) : 0)
                .sum();
            insights.put("highValueDomainsCount", highValueDomainsCount);
            insights.put("highValueDomainsPercentage", totalRecords > 0 ? (double) highValueDomainsCount / totalRecords * 100 : 0);
            
            // Password insights
            if (passwordStats.getWithPassword() > 0) {
                insights.put("passwordStrengthGood", passwordStats.getStrongPasswordPercentage() > 50);
                insights.put("strongPasswordCount", passwordStats.getStrongPasswords());
            }
            
            // Breach size category
            if (totalRecords > 1000000) insights.put("breachSizeCategory", "Massive");
            else if (totalRecords > 100000) insights.put("breachSizeCategory", "Large");
            else if (totalRecords > 10000) insights.put("breachSizeCategory", "Medium");
            else insights.put("breachSizeCategory", "Small");
            
        } catch (Exception e) {
            log.error("Error calculating insights for source {}: {}", source, e.getMessage());
            insights.put("error", "Failed to calculate insights");
        }
        
        return insights;
    }
    
    /**
     * Get available source values to help debug field name issues
     */
    private List<String> getAvailableSourceValues() {
        try {
            // Try to get distinct values from source_db field
            List<String> sources = mongoTemplate.findDistinct("source_db", StealerLog.class, String.class);
            if (!sources.isEmpty()) {
                log.info("📊 Found {} distinct 'source_db' values: {}", sources.size(), sources.subList(0, Math.min(5, sources.size())));
                return sources;
            }
            
            // Try source field as fallback
            sources = mongoTemplate.findDistinct("source", StealerLog.class, String.class);
            if (!sources.isEmpty()) {
                log.info("📊 Found {} distinct 'source' values: {}", sources.size(), sources.subList(0, Math.min(5, sources.size())));
                return sources;
            }
            
            return new ArrayList<>();
        } catch (Exception e) {
            log.error("Error getting available source values: {}", e.getMessage());
            return new ArrayList<>();
        }
    }
    
    /**
     * Get debug information about the MongoDB collection
     */
    public Map<String, Object> getDebugInfo() {
        Map<String, Object> debugInfo = new HashMap<>();
        
        try {
            // Total documents
            long totalDocs = mongoTemplate.count(Query.query(Criteria.where("id").exists(true)), StealerLog.class);
            debugInfo.put("totalDocuments", totalDocs);
            
            // Available source_db values
            List<String> sourceDbValues = mongoTemplate.findDistinct("source_db", StealerLog.class, String.class);
            debugInfo.put("sourceDbValues", sourceDbValues.subList(0, Math.min(10, sourceDbValues.size())));
            debugInfo.put("sourceDbCount", sourceDbValues.size());
            
            // Available source values (fallback)
            List<String> sourceValues = mongoTemplate.findDistinct("source", StealerLog.class, String.class);
            debugInfo.put("sourceValues", sourceValues.subList(0, Math.min(10, sourceValues.size())));
            debugInfo.put("sourceCount", sourceValues.size());
            
            // Sample document
            List<StealerLog> sampleDocs = mongoTemplate.find(
                Query.query(Criteria.where("id").exists(true)).limit(1), 
                StealerLog.class
            );
            
            if (!sampleDocs.isEmpty()) {
                StealerLog sample = sampleDocs.get(0);
                Map<String, Object> sampleData = new HashMap<>();
                sampleData.put("id", sample.getId());
                sampleData.put("source", sample.getSource());
                sampleData.put("login", sample.getLogin());
                sampleData.put("domain", sample.getDomain());
                debugInfo.put("sampleDocument", sampleData);
            }
            
        } catch (Exception e) {
            debugInfo.put("error", e.getMessage());
            log.error("Error getting debug info: {}", e.getMessage());
        }
        
        return debugInfo;
    }
    
    private SourceDetailedMetrics createDefaultMetrics(String source) {
        return SourceDetailedMetrics.builder()
            .source(source)
            .totalRecordsAffected(0L)
            .passwordStats(PasswordStats.builder()
                .totalRecords(0L).withPassword(0L).withoutPassword(0L)
                .strongPasswords(0L).percentage(0.0).strongPasswordPercentage(0.0)
                .build())
            .domainDistribution(new HashMap<>())
            .qualityAssessment(DataQualityAssessment.builder()
                .score(0.0).details("Assessment unavailable")
                .loginCompleteness(0.0).passwordCompleteness(0.0)
                .urlCompleteness(0.0).domainCompleteness(0.0)
                .build())
            .breachDate(LocalDateTime.now())
            .riskLevel("UNKNOWN")
            .insights(new HashMap<>())
            .calculatedAt(LocalDateTime.now())
            .build();
    }
    
    // Enhanced Data classes
    
    @lombok.Data
    @lombok.Builder
    public static class SourceDetailedMetrics {
        private String source;
        private long totalRecordsAffected;
        private PasswordStats passwordStats;
        private Map<String, Long> domainDistribution;
        private DataQualityAssessment qualityAssessment;
        private LocalDateTime breachDate;
        private String riskLevel;
        private Map<String, Object> insights;
        private LocalDateTime calculatedAt;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class PasswordStats {
        private long totalRecords;
        private long withPassword;
        private long withoutPassword;
        private long strongPasswords;
        private double percentage;
        private double strongPasswordPercentage;
    }
    
    @lombok.Data
    @lombok.Builder
    public static class DataQualityAssessment {
        private double score; // 0-100
        private String details;
        private double loginCompleteness;
        private double passwordCompleteness;
        private double urlCompleteness;
        private double domainCompleteness;
    }
}