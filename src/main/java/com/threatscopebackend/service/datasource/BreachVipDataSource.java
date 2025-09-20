package com.threatscopebackend.service.datasource;

import com.threatscopebackend.dto.SearchRequest;
import com.threatscopebackend.dto.SearchResponse;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.security.UserPrincipal;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import io.github.resilience4j.timelimiter.annotation.TimeLimiter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Resilient BreachVIP data source with circuit breaker, retry, and timeout patterns
 * Implements fault tolerance patterns to prevent cascading failures
 */
@Service
@Slf4j  
public class BreachVipDataSource implements DataSourceService {
    
    private final RestTemplate restTemplate;
    private final RestTemplate healthCheckRestTemplate;
    
    public BreachVipDataSource(
            @Qualifier("dataSourceRestTemplate") RestTemplate restTemplate,
            @Qualifier("healthCheckRestTemplate") RestTemplate healthCheckRestTemplate) {
        this.restTemplate = restTemplate;
        this.healthCheckRestTemplate = healthCheckRestTemplate;
    }
    
    // Dedicated thread pool for external API calls to prevent thread pool exhaustion
    private final Executor externalApiExecutor = Executors.newFixedThreadPool(4);
    
    @Value("${datasources.breach-vip.enabled:false}")
    private boolean enabled;
    
    @Value("${datasources.breach-vip.base-url:https://breach.vip}")
    private String baseUrl;
    
    @Value("${datasources.breach-vip.priority:1}")
    private int priority;
    
    @Value("${datasources.breach-vip.timeout:5000}")
    private int timeoutMs;
    
    @Value("${datasources.breach-vip.max-results:100}")
    private int maxResultsLimit;
    
    @Value("${datasources.breach-vip.rate-limit:1000}")
    private int rateLimitPerHour;
    
    // Health check state
    private volatile LocalDateTime lastHealthCheck = LocalDateTime.now();
    private volatile boolean lastHealthStatus = true;
    private volatile String lastHealthError = null;
    
    // Rate limiting state
    private volatile LocalDateTime rateLimitWindowStart = LocalDateTime.now();
    private volatile int requestsInCurrentWindow = 0;
    
    @Override
    public CompletableFuture<List<SearchResponse.SearchResult>> search(SearchRequest request, UserPrincipal user) {
        if (!enabled) {
            log.debug("ResilientBreachVIP data source is disabled");
            return CompletableFuture.completedFuture(List.of());
        }
        
        if (!isRateLimitAllowed()) {
            log.warn("Rate limit exceeded for BreachVIP API");
            return CompletableFuture.completedFuture(List.of());
        }
        
        // Use dedicated thread pool and apply all resilience patterns
        return CompletableFuture.supplyAsync(() -> 
            searchWithResilience(request, user), externalApiExecutor);
    }
    
    @CircuitBreaker(name = "breach-vip", fallbackMethod = "fallbackSearch")
    @Retry(name = "breach-vip")
    @TimeLimiter(name = "breach-vip")
    @Bulkhead(name = "breach-vip", type = Bulkhead.Type.THREADPOOL)
    public List<SearchResponse.SearchResult> searchWithResilience(SearchRequest request, UserPrincipal user) {
        try {
            log.debug("Searching ResilientBreachVIP data source for query: {}", request.getQuery());
            incrementRequestCount();
            
            // Build API request body
            Map<String, Object> requestBody = buildApiRequest(request);
            if (requestBody == null) {
                log.warn("Unsupported search type for BreachVIP: {}", request.getSearchType());
                return List.of();
            }
            
            String apiUrl = baseUrl + "/api/search";
            log.debug("BreachVIP API URL: {}, Request: {}", apiUrl, requestBody);
            
            // Make API call with proper timeout handling
            ResponseEntity<Map> response = restTemplate.postForEntity(apiUrl, requestBody, Map.class);
            
            if (response.getStatusCode() == HttpStatus.OK && response.getBody() != null) {
                List<SearchResponse.SearchResult> results = parseApiResponse(response.getBody(), user);
                
                // Update health status on successful call
                updateHealthStatus(true, null);
                
                log.debug("ResilientBreachVIP data source returned {} results", results.size());
                return results;
            }
            
            log.warn("BreachVIP API returned non-OK status: {}", response.getStatusCode());
            updateHealthStatus(false, "API returned status: " + response.getStatusCode());
            return List.of();
            
        } catch (Exception e) {
            log.error("Error in ResilientBreachVIP search: {}", e.getMessage(), e);
            updateHealthStatus(false, e.getMessage());
            throw e; // Re-throw to trigger circuit breaker
        }
    }
    
    /**
     * Fallback method for circuit breaker - returns empty results
     */
    public List<SearchResponse.SearchResult> fallbackSearch(SearchRequest request, UserPrincipal user, Exception ex) {
        log.warn("BreachVIP search fallback triggered for query: {} - {}", 
                request.getQuery(), ex.getMessage());
        
        // Update health status
        updateHealthStatus(false, "Circuit breaker fallback: " + ex.getMessage());
        
        return List.of();
    }
    
    private Map<String, Object> buildApiRequest(SearchRequest request) {
        Map<String, Object> requestBody = new HashMap<>();
        requestBody.put("term", request.getQuery());
        requestBody.put("wildcard", false);
        requestBody.put("case_sensitive", false);
        
        // Map search types to BreachVIP fields
        List<String> fields = new ArrayList<>();
        switch (request.getSearchType()) {
            case EMAIL:
                fields.add("email");
                break;
            case USERNAME:
                fields.add("username");
                break;
            case DOMAIN:
                fields.add("domain");
                break;
            case PASSWORD:
                fields.add("password");
                break;
            case IP:
                fields.add("ip");
                break;
            case PHONE:
                fields.add("phone");
                break;
            default:
                log.warn("Unsupported search type for BreachVIP: {}", request.getSearchType());
                return null;
        }
        
        requestBody.put("fields", fields);
        return requestBody;
    }
    
    @SuppressWarnings("unchecked")
    private List<SearchResponse.SearchResult> parseApiResponse(Map<String, Object> apiResponse, UserPrincipal user) {
        List<SearchResponse.SearchResult> results = new ArrayList<>();
        
        try {
            List<Map<String, Object>> breaches = (List<Map<String, Object>>) apiResponse.get("results");
            if (breaches == null || breaches.isEmpty()) {
                log.debug("No results found in BreachVIP API response");
                return results;
            }
            
            // Limit results to prevent memory issues
            int limit = Math.min(breaches.size(), maxResultsLimit);
            
            for (int i = 0; i < limit; i++) {
                SearchResponse.SearchResult result = convertBreachToSearchResult(breaches.get(i), user);
                if (result != null) {
                    results.add(result);
                }
            }
            
        } catch (Exception e) {
            log.error("Error parsing BreachVIP API response: {}", e.getMessage(), e);
        }
        
        return results;
    }
    
    private SearchResponse.SearchResult convertBreachToSearchResult(Map<String, Object> breach, UserPrincipal user) {
        try {
            String email = (String) breach.get("email");
            String password = (String) breach.get("password");
            String source = (String) breach.get("source");
            String domain = (String) breach.get("domain");
            String name = (String) breach.get("name");
            String salt = (String) breach.get("salt");
            String breachId = (String) breach.get("id");
            
            @SuppressWarnings("unchecked")
            List<String> categories = (List<String>) breach.get("categories");
            
            // Validate required fields - email is mandatory for BreachVIP
            if (email == null || email.trim().isEmpty()) {
                log.debug("Skipping breach result with no email");
                return null;
            }
            
            // Create additional data map with all BreachVIP specific data
            Map<String, Object> additionalData = new HashMap<>();
            additionalData.put("dataSource", getSourceName());
            additionalData.put("sourceDisplayName", getDisplayName());
            additionalData.put("originalSource", source);
            additionalData.put("breachId", breachId);
            additionalData.put("salt", salt);
            additionalData.put("categories", categories);
            additionalData.put("fullName", name != null ? name.trim() : null);
            
            // Determine user's plan type for password masking (following SearchService pattern)
            CommonEnums.PlanType planType = CommonEnums.PlanType.FREE; // Default for anonymous/free users
            
            if (user != null && user.getId() != null && !"anonymous".equals(user.getUsername())) {
                try {
                    // Access subscription through the User entity from UserPrincipal
                    if (user.getUser() != null && user.getUser().getSubscription() != null) {
                        planType = user.getUser().getSubscription().getPlanType();
                    }
                } catch (Exception e) {
                    log.debug("Failed to get user subscription for password masking, using FREE plan: {}", e.getMessage());
                }
            }
            
            return SearchResponse.SearchResult.builder()
                    .id("bvip_" + breachId) // Use actual BreachVIP ID
                    .email(email)
                    .url(null) // BreachVIP doesn't provide URL
                    .domain(domain != null ? domain : extractDomainFromEmail(email))
                    .source(source) // Use the actual breach source name (e.g., "animoto.com")
                    .timestamp(null) // BreachVIP doesn't provide timestamps
                    .dateCompromised(null) // BreachVIP doesn't provide exact dates
                    .hasPassword(password != null && !password.trim().isEmpty())
                    .severity(calculateSeverity(email, domain, categories))
                    .isVerified(false) // BreachVIP results marked as unverified initially
                    .dataQuality(calculateDataQuality(email, password, domain, name, categories))
                    .additionalData(additionalData)
                    .build();
                    
        } catch (Exception e) {
            log.error("Error converting BreachVIP result: {}", e.getMessage(), e);
            return null;
        }
    }
    
    private String extractDomainFromEmail(String email) {
        if (email != null && email.contains("@")) {
            return email.split("@")[1];
        }
        return null;
    }
    
    private String calculateSeverity(String email, String domain, List<String> categories) {
        // Start with base severity
        String severity = "MEDIUM";
        
        // Check if domain is high-value
        if (email != null && email.contains("@")) {
            String emailDomain = email.split("@")[1];
            if (isHighValueDomain(emailDomain)) {
                severity = "HIGH";
            }
        }
        
        // Increase severity for certain breach categories
        if (categories != null && !categories.isEmpty()) {
            for (String category : categories) {
                switch (category.toLowerCase()) {
                    case "financial":
                    case "banking":
                    case "payment":
                        return "CRITICAL";
                    case "healthcare":
                    case "government":
                    case "education":
                        severity = "HIGH";
                        break;
                }
            }
        }
        
        return severity;
    }
    
    private boolean isHighValueDomain(String domain) {
        String[] highValueDomains = {
            "gmail.com", "yahoo.com", "outlook.com", "hotmail.com",
            "paypal.com", "amazon.com", "apple.com", "microsoft.com"
        };
        
        if (domain == null) return false;
        
        for (String hvDomain : highValueDomains) {
            if (domain.equalsIgnoreCase(hvDomain)) {
                return true;
            }
        }
        return false;
    }
    
    private int calculateDataQuality(String email, String password, String domain, String name, List<String> categories) {
        int score = 0;
        int maxScore = 5; // Updated for BreachVIP data structure
        
        if (email != null && !email.trim().isEmpty()) score++;
        if (password != null && !password.trim().isEmpty()) score++;
        if (domain != null && !domain.trim().isEmpty()) score++;
        if (name != null && !name.trim().isEmpty()) score++;
        if (categories != null && !categories.isEmpty()) score++;
        
        return Math.round((float) score / maxScore * 100);
    }
    
    private synchronized boolean isRateLimitAllowed() {
        LocalDateTime now = LocalDateTime.now();
        
        // Reset window if hour has passed
        if (Duration.between(rateLimitWindowStart, now).toHours() >= 1) {
            rateLimitWindowStart = now;
            requestsInCurrentWindow = 0;
        }
        
        return requestsInCurrentWindow < rateLimitPerHour;
    }
    
    private synchronized void incrementRequestCount() {
        requestsInCurrentWindow++;
    }
    
    private synchronized void updateHealthStatus(boolean healthy, String error) {
        lastHealthCheck = LocalDateTime.now();
        lastHealthStatus = healthy;
        lastHealthError = error;
    }
    
    @Override
    public String getSourceName() {
        return "breach-vip";
    }
    
    @Override
    public boolean isEnabled() {
        return enabled;
    }
    
    @Override
    public int getPriority() {
        return priority;
    }
    
    @Override
    public String getDisplayName() {
        return "BreachVIP";
    }
    
    @Override
    public boolean supportsSearchType(SearchRequest.SearchType searchType) {
        return searchType != SearchRequest.SearchType.ADVANCED;
    }
    
    @Override
    public int getMaxResultsLimit() {
        return maxResultsLimit;
    }
    
    @Override
    public boolean isHealthy() {
        // Consider healthy if last check was within 5 minutes and successful
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);
        
        if (lastHealthCheck.isBefore(fiveMinutesAgo)) {
            // Perform a simple health check
            return performHealthCheck();
        }
        
        return lastHealthStatus;
    }
    
    /**
     * Perform actual health check by making a lightweight API call
     */
    private boolean performHealthCheck() {
        try {
            // Use a very simple request that should always work if the API is up
            Map<String, Object> healthCheckRequest = new HashMap<>();
            healthCheckRequest.put("term", "test");
            healthCheckRequest.put("fields", List.of("email"));
            healthCheckRequest.put("wildcard", false);
            
            String apiUrl = baseUrl + "/api/search";
            
            // Use dedicated health check RestTemplate with shorter timeouts
            ResponseEntity<Map> response = healthCheckRestTemplate.postForEntity(apiUrl, healthCheckRequest, Map.class);
            
            boolean healthy = response.getStatusCode().is2xxSuccessful();
            updateHealthStatus(healthy, healthy ? null : "Health check returned: " + response.getStatusCode());
            
            return healthy;
            
        } catch (Exception e) {
            log.debug("BreachVIP health check failed: {}", e.getMessage());
            updateHealthStatus(false, "Health check exception: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Get health status details for monitoring
     */
    public Map<String, Object> getHealthDetails() {
        Map<String, Object> health = new HashMap<>();
        health.put("healthy", isHealthy());
        health.put("lastCheck", lastHealthCheck);
        health.put("lastError", lastHealthError);
        health.put("requestsInWindow", requestsInCurrentWindow);
        health.put("rateLimitPerHour", rateLimitPerHour);
        health.put("windowStart", rateLimitWindowStart);
        return health;
    }
}