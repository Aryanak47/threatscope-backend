//package com.threatscopebackend.service.external;
//
//import com.threatscopebackend.dto.ExternalBreachData;
//import com.threatscopebackend.exception.ExternalServiceException;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.cache.annotation.Cacheable;
//import org.springframework.http.*;
//import org.springframework.retry.annotation.Backoff;
//import org.springframework.retry.annotation.Retryable;
//import org.springframework.stereotype.Service;
//import org.springframework.web.client.RestTemplate;
//import org.springframework.web.util.UriComponentsBuilder;
//
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * Service for interacting with external breach data providers
// */
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class ExternalBreachService {
//
//    private final RestTemplate restTemplate;
//
//    @Value("${external.breach.api.base-url:https://haveibeenpwned.com/api/v3}")
//    private String apiBaseUrl;
//
//    @Value("${external.breach.api.key:}")
//    private String apiKey;
//
//    @Value("${external.breach.api.retry.max-attempts:3}")
//    private int maxRetryAttempts;
//
//    @Value("${external.breach.api.retry.delay:1000}")
//    private long retryDelay;
//
//    @Value("${external.breach.api.timeout:5000}")
//    private int requestTimeout;
//
//    /**
//     * Search for breach data by email
//     */
//    @Cacheable(value = "breachData", key = "#email")
//    @Retryable(
//        value = {ExternalServiceException.class},
//        maxAttemptsExpression = "${external.breach.api.retry.max-attempts:3}",
//        backoff = @Backoff(delayExpression = "${external.breach.api.retry.delay:1000}")
//    )
//    public List<ExternalBreachData> searchBreachData(String email) {
//        if (email == null || email.trim().isEmpty()) {
//            return Collections.emptyList();
//        }
//
//        try {
//            String url = UriComponentsBuilder.fromHttpUrl(apiBaseUrl + "/breachedaccount/" + encode(email))
//                .queryParam("truncateResponse", "false")
//                .toUriString();
//
//            HttpHeaders headers = new HttpHeaders();
//            headers.set("hibp-api-key", apiKey);
//            headers.setAccept(Collections.singletonList(MediaType.APPLICATION_JSON));
//
//            HttpEntity<String> entity = new HttpEntity<>(headers);
//
//            log.debug("Fetching breach data for email: {}", email);
//            ResponseEntity<Map[]> response = restTemplate.exchange(
//                url, HttpMethod.GET, entity, Map[].class);
//
//            if (response.getStatusCode() == HttpStatus.OK) {
//                response.getBody();
//                return Arrays.stream(response.getBody())
//                        .map(this::mapToExternalBreachData)
//                        .filter(Objects::nonNull)
//                        .collect(Collectors.toList());
//            }
//
//            return Collections.emptyList();
//
//        } catch (Exception e) {
//            log.error("Failed to fetch breach data for {}: {}", email, e.getMessage());
//            throw new ExternalServiceException("Failed to fetch breach data: " + e.getMessage(), e);
//        }
//    }
//
//    private String encode(String value) {
//        return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8);
//    }
//
//    @SuppressWarnings("unchecked")
//    private ExternalBreachData mapToExternalBreachData(Map<String, Object> data) {
//        try {
//            DateTimeFormatter formatter = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
//
//            return ExternalBreachData.builder()
//                .id((String) data.get("Name"))
//                .title((String) data.get("Title"))
//                .domain((String) data.get("Domain"))
//                .breachDate(parseDate((String) data.get("BreachDate"), "yyyy-MM-dd"))
//                .addedDate(parseDate((String) data.get("AddedDate"), "yyyy-MM-dd'T'HH:mm:ssXXX"))
//                .modifiedDate(parseDate((String) data.get("ModifiedDate"), "yyyy-MM-dd'T'HH:mm:ssXXX"))
//                .description((String) data.get("Description"))
//                .dataClasses((List<String>) data.get("DataClasses"))
//                .isVerified((Boolean) data.get("IsVerified"))
//                .isFabricated((Boolean) data.get("IsFabricated"))
//                .isSensitive((Boolean) data.get("IsSensitive"))
//                .isRetired((Boolean) data.get("IsRetired"))
//                .isSpamList((Boolean) data.get("IsSpamList"))
//                .isMalware((Boolean) data.get("IsMalware"))
//                .logoPath((String) data.get("LogoPath"))
//                .additionalInfo(extractAdditionalInfo(data))
//                .build();
//
//        } catch (Exception e) {
//            log.error("Error mapping breach data: {}", e.getMessage(), e);
//            return null;
//        }
//    }
//
//    private LocalDateTime parseDate(String dateStr, String pattern) {
//        if (dateStr == null) return null;
//        DateTimeFormatter formatter = DateTimeFormatter.ofPattern(pattern);
//        return LocalDateTime.parse(dateStr, formatter);
//    }
//
//    @SuppressWarnings("unchecked")
//    private Map<String, Object> extractAdditionalInfo(Map<String, Object> data) {
//        Map<String, Object> additionalInfo = new HashMap<>();
//
//        // Add any additional fields that aren't explicitly mapped
//        Set<String> knownFields = Set.of(
//            "Name", "Title", "Domain", "BreachDate", "AddedDate", "ModifiedDate",
//            "PwnCount", "Description", "DataClasses", "IsVerified", "IsFabricated",
//            "IsSensitive", "IsRetired", "IsSpamList", "IsMalware", "LogoPath"
//        );
//
//        data.entrySet().stream()
//            .filter(entry -> !knownFields.contains(entry.getKey()))
//            .forEach(entry -> additionalInfo.put(entry.getKey(), entry.getValue()));
//
//        return additionalInfo;
//    }
//}
