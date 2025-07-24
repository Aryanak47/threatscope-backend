package com.threatscopebackend.service.monitoring;

import com.threatscopebackend.dto.monitoring.*;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.service.subscription.SubscriptionService;
import com.threatscopebackend.exception.BadRequestException;
import com.threatscopebackend.exception.DuplicateMonitoringException;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.exception.SubscriptionLimitExceededException;
import com.threatscopebackend.repository.postgresql.MonitoringItemRepository;
import com.threatscopebackend.repository.postgresql.BreachAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MonitoringService {
    
    private final MonitoringItemRepository monitoringItemRepository;
    private final BreachAlertRepository breachAlertRepository;
    private final AlertService alertService;
    private final SubscriptionService subscriptionService;
    
    // Validation patterns
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern DOMAIN_PATTERN = Pattern.compile(
        "^[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$");
    private static final Pattern IP_PATTERN = Pattern.compile(
        "^(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$");
    
    /**
     * Create a new monitoring item for a user with subscription validation
     */
    @Transactional
    public MonitoringItemResponse createMonitoringItem(User user, CreateMonitoringItemRequest request) {
        log.info("Creating monitoring item for user: {} with type: {}", user.getId(), request.getMonitorType());
        
        // Debug: Log detailed user and subscription information
        log.info("User subscription: {}", user.getSubscription() != null ? user.getSubscription().getPlanType() : "NULL");
        if (user.getSubscription() != null) {
            log.info("Plan details: maxMonitoringItems={}, planType={}", 
                    user.getSubscription().getPlan() != null ? user.getSubscription().getPlan().getMaxMonitoringItems() : "NULL PLAN",
                    user.getSubscription().getPlanType());
        }
        
        // Check subscription limits
        long currentCount = monitoringItemRepository.countByUserAndIsActiveTrue(user);
        log.info("Current monitoring items count: {} for user: {}", currentCount, user.getId());
        
        boolean canCreate = subscriptionService.canCreateMonitoringItem(user, (int) currentCount);
        log.info("Can create monitoring item: {} (currentCount: {})", canCreate, currentCount);
        
        if (!canCreate) {
            log.warn("Subscription limit exceeded for user: {} (current count: {}, plan: {})", 
                    user.getId(), currentCount, 
                    user.getSubscription() != null ? user.getSubscription().getPlanType() : "NULL");
            throw new SubscriptionLimitExceededException(
                "Monitoring item limit exceeded for your subscription plan. Please upgrade to create more monitoring items.");
        }
        
        // Check if frequency is allowed for user's plan
        if (!subscriptionService.canUseMonitoringFrequency(user, request.getFrequency())) {
            throw new SubscriptionLimitExceededException(
                "Monitoring frequency '" + request.getFrequency() + "' is not available in your subscription plan.");
        }
        
        // Normalize target value for consistent comparison
        String normalizedTarget = normalizeTargetValue(request.getTargetValue(), request.getMonitorType());
        
        // Check for existing monitoring item (active or inactive) with enhanced duplicate prevention
        Optional<MonitoringItem> existingItem = monitoringItemRepository
            .findByUserAndTypeAndTargetCaseInsensitive(user, request.getMonitorType(), normalizedTarget);
            
        if (existingItem.isPresent()) {
            MonitoringItem existing = existingItem.get();
            
            if (existing.getIsActive()) {
                // Active duplicate found - throw specific exception with existing item details
                throw new DuplicateMonitoringException(
                    String.format("You're already monitoring this %s: %s", 
                        request.getMonitorType().getDisplayName().toLowerCase(), 
                        request.getTargetValue()),
                    request.getTargetValue(),
                    request.getMonitorType().name(),
                    existing.getId()
                );
            } else {
                // Inactive item found - reactivate it instead of creating new
                log.info("Reactivating existing inactive monitoring item: {}", existing.getId());
                existing.setIsActive(true);
                existing.setFrequency(request.getFrequency());
                existing.setMonitorName(request.getMonitorName());
                existing.setDescription(request.getDescription());
                existing.setEmailAlerts(request.getEmailAlerts());
                existing.setInAppAlerts(request.getInAppAlerts());
                existing.setUpdatedAt(LocalDateTime.now());
                existing.setLastChecked(null); // Reset to trigger immediate check
                
                MonitoringItem reactivated = monitoringItemRepository.save(existing);
                log.info("Reactivated monitoring item with ID: {}", reactivated.getId());
                return MonitoringItemResponse.fromEntity(reactivated);
            }
        }
        
        // Validate target value based on monitor type
        validateTargetValue(request.getMonitorType(), normalizedTarget);
        
        // Create new monitoring item
        MonitoringItem item = MonitoringItem.builder()
            .user(user)
            .monitorType(request.getMonitorType())
            .targetValue(normalizedTarget)
            .monitorName(request.getMonitorName())
            .description(request.getDescription())
            .frequency(request.getFrequency())
            .isActive(request.getIsActive())
            .emailAlerts(request.getEmailAlerts())
            .inAppAlerts(request.getInAppAlerts())
            .webhookAlerts(false)  // Explicitly set webhook alerts
            .alertCount(0)
            .breachCount(0)
            .matchCount(0)
            .build();
        
        MonitoringItem saved = monitoringItemRepository.save(item);
        log.info("Created monitoring item with ID: {}", saved.getId());
        
        return MonitoringItemResponse.fromEntity(saved);
    }
    
    /**
     * Update an existing monitoring item with duplicate prevention
     */
    @Transactional
    public MonitoringItemResponse updateMonitoringItem(User user, Long itemId, UpdateMonitoringItemRequest request) {
        log.info("Updating monitoring item {} for user: {}", itemId, user.getId());
        
        MonitoringItem item = findMonitoringItemByUserAndId(user, itemId);
        
        // Check for duplicates if target value or monitor type is being changed
        // Note: Assuming UpdateMonitoringItemRequest might have targetValue and monitorType fields
        // If not available in current DTO, this check can be removed or DTO can be enhanced
        
        // Update fields if provided
        if (request.getMonitorName() != null) {
            item.setMonitorName(request.getMonitorName());
        }
        if (request.getDescription() != null) {
            item.setDescription(request.getDescription());
        }
        if (request.getFrequency() != null) {
            item.setFrequency(request.getFrequency());
        }
        if (request.getIsActive() != null) {
            item.setIsActive(request.getIsActive());
        }
        if (request.getEmailAlerts() != null) {
            item.setEmailAlerts(request.getEmailAlerts());
        }
        if (request.getInAppAlerts() != null) {
            item.setInAppAlerts(request.getInAppAlerts());
        }
        
        MonitoringItem updated = monitoringItemRepository.save(item);
        log.info("Updated monitoring item: {}", updated.getId());
        
        return MonitoringItemResponse.fromEntity(updated);
    }
    
    /**
     * Delete a monitoring item
     */
    @Transactional
    public void deleteMonitoringItem(User user, Long itemId) {
        log.info("Deleting monitoring item {} for user: {}", itemId, user.getId());
        
        MonitoringItem item = findMonitoringItemByUserAndId(user, itemId);
        
        // Soft delete by setting isActive to false
        item.setIsActive(false);
        monitoringItemRepository.save(item);
        
        log.info("Deleted monitoring item: {}", itemId);
    }
    
    /**
     * Get a single monitoring item by ID
     */
    public MonitoringItemResponse getMonitoringItem(User user, Long itemId) {
        MonitoringItem item = findMonitoringItemByUserAndId(user, itemId);
        return MonitoringItemResponse.fromEntity(item);
    }
    
    /**
     * Get paginated list of monitoring items for a user
     */
    public Page<MonitoringItemResponse> getMonitoringItems(User user, int page, int size, String sortBy, String sortDir) {
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<MonitoringItem> items = monitoringItemRepository.findByUser(user, pageable);
        return items.map(MonitoringItemResponse::fromEntity);
    }
    
    /**
     * Search monitoring items
     */
    public Page<MonitoringItemResponse> searchMonitoringItems(User user, String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        
        Page<MonitoringItem> items = monitoringItemRepository.searchByUser(user, searchTerm, pageable);
        return items.map(MonitoringItemResponse::fromEntity);
    }
    
    /**
     * Get monitoring dashboard data
     */
    public MonitoringDashboardResponse getDashboard(User user) {
        log.info("Getting monitoring dashboard for user: {}", user.getId());
        
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime yesterday = now.minusDays(1);
        LocalDateTime lastWeek = now.minusDays(7);
        LocalDateTime lastMonth = now.minusDays(30);
        
        // Get basic counts
        long totalItems = monitoringItemRepository.countActiveByUser(user);
        long totalAlerts = breachAlertRepository.countTotalByUser(user);
        long unreadAlerts = breachAlertRepository.countUnreadByUser(user);
        
        // Get alerts by time periods
        long alertsLast24Hours = breachAlertRepository.countByUserAndDateRange(user, yesterday, now);
        long alertsLast7Days = breachAlertRepository.countByUserAndDateRange(user, lastWeek, now);
        long alertsLast30Days = breachAlertRepository.countByUserAndDateRange(user, lastMonth, now);
        
        // Get monitoring items by type
        List<Object[]> itemsByType = monitoringItemRepository.countByUserAndMonitorType(user);
        Map<String, Long> monitoringItemsByType = itemsByType.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> (Long) row[1]
            ));
        
        // Get alerts by severity
        List<Object[]> alertsBySev = breachAlertRepository.countBySeverityForUser(user);
        Map<String, Long> alertsBySeverity = alertsBySev.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> (Long) row[1]
            ));
        
        // Get alerts by status
        List<Object[]> alertsByStatus = breachAlertRepository.countByStatusForUser(user);
        Map<String, Long> alertsByStatusMap = alertsByStatus.stream()
            .collect(Collectors.toMap(
                row -> row[0].toString(),
                row -> (Long) row[1]
            ));
        
        // Calculate critical and high priority alerts
        long criticalAlerts = alertsBySeverity.getOrDefault("CRITICAL", 0L);
        long highAlerts = alertsBySeverity.getOrDefault("HIGH", 0L);
        
        // Determine overall health
        String overallHealth = determineOverallHealth(unreadAlerts, criticalAlerts, highAlerts);
        String healthMessage = generateHealthMessage(overallHealth, unreadAlerts, criticalAlerts);
        
        return MonitoringDashboardResponse.builder()
            .totalMonitoringItems(totalItems)
            .activeMonitoringItems(totalItems) // All counted items are active
            .totalAlerts(totalAlerts)
            .unreadAlerts(unreadAlerts)
            .criticalAlerts(criticalAlerts)
            .highPriorityAlerts(highAlerts)
            .alertsLast24Hours(alertsLast24Hours)
            .alertsLast7Days(alertsLast7Days)
            .alertsLast30Days(alertsLast30Days)
            .monitoringItemsByType(monitoringItemsByType)
            .alertsBySeverity(alertsBySeverity)
            .alertsByStatus(alertsByStatusMap)
            .overallHealth(overallHealth)
            .healthMessage(healthMessage)
            .build();
    }
    
    /**
     * Get items that need checking based on their frequency
     */
    public List<MonitoringItem> getItemsNeedingCheck() {
        LocalDateTime now = LocalDateTime.now();
        
        // Get items that haven't been checked recently based on their frequency
        List<MonitoringItem> itemsToCheck = monitoringItemRepository.findItemsNeedingCheck(now.minusHours(1));
        
        log.info("Found {} monitoring items that need checking", itemsToCheck.size());
        return itemsToCheck;
    }
    
    /**
     * Record that a monitoring item has been checked
     */
    @Transactional
    public void recordCheck(Long itemId) {
        MonitoringItem item = monitoringItemRepository.findById(itemId)
            .orElseThrow(() -> new ResourceNotFoundException("Monitoring item not found"));
        
        item.recordCheck();
        monitoringItemRepository.save(item);
    }
    
    /**
     * Get monitoring statistics for a user
     */
    public Map<String, Object> getMonitoringStatistics(User user) {
        Map<String, Object> stats = new HashMap<>();
        
        stats.put("totalItems", monitoringItemRepository.countActiveByUser(user));
        stats.put("totalAlerts", breachAlertRepository.countTotalByUser(user));
        stats.put("itemsWithAlerts", monitoringItemRepository.countWithAlertsForUser(user));
        stats.put("totalAlertCount", monitoringItemRepository.getTotalAlertsForUser(user));
        
        return stats;
    }
    
    /**
     * Get monitoring items by frequency (paginated) - for optimized scheduler
     */
    public List<MonitoringItem> getItemsByFrequency(CommonEnums.MonitorFrequency frequency, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return monitoringItemRepository.findByFrequencyAndIsActiveTruePaginated(frequency, pageable);
    }
    
    /**
     * Get user's monitoring items by frequency - for optimized scheduler
     */
    public List<MonitoringItem> getUserMonitoringItemsByFrequency(User user, CommonEnums.MonitorFrequency frequency) {
        return monitoringItemRepository.findByUserAndFrequencyAndIsActiveTrue(user, frequency);
    }
    
    /**
     * Get the actual MonitoringItem entity (for internal use)
     */
    public MonitoringItem getMonitoringItemEntity(User user, Long itemId) {
        return monitoringItemRepository.findById(itemId)
            .filter(item -> item.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Monitoring item not found"));
    }
    
    // Helper methods
    
    private MonitoringItem findMonitoringItemByUserAndId(User user, Long itemId) {
        return monitoringItemRepository.findById(itemId)
            .filter(item -> item.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Monitoring item not found"));
    }
    
    private void validateTargetValue(CommonEnums.MonitorType type, String targetValue) {
        switch (type) {
            case EMAIL:
                if (!EMAIL_PATTERN.matcher(targetValue).matches()) {
                    throw new BadRequestException("Invalid email format");
                }
                break;
            case DOMAIN:
                if (!DOMAIN_PATTERN.matcher(targetValue).matches()) {
                    throw new BadRequestException("Invalid domain format");
                }
                break;
            case IP_ADDRESS:
                if (!IP_PATTERN.matcher(targetValue).matches()) {
                    throw new BadRequestException("Invalid IP address format");
                }
                break;
            case USERNAME:
                if (targetValue.length() < 3 || targetValue.length() > 50) {
                    throw new BadRequestException("Username must be between 3 and 50 characters");
                }
                break;
            case KEYWORD:
                if (targetValue.length() < 2 || targetValue.length() > 100) {
                    throw new BadRequestException("Keyword must be between 2 and 100 characters");
                }
                break;
            case PHONE:
                // Basic phone validation - can be enhanced
                String cleanPhone = targetValue.replaceAll("[^0-9+]", "");
                if (cleanPhone.length() < 7 || cleanPhone.length() > 15) {
                    throw new BadRequestException("Invalid phone number format");
                }
                break;
            case ORGANIZATION:
                if (targetValue.length() < 2 || targetValue.length() > 100) {
                    throw new BadRequestException("Organization name must be between 2 and 100 characters");
                }
                break;
        }
    }
    
    private String determineOverallHealth(long unreadAlerts, long criticalAlerts, long highAlerts) {
        if (criticalAlerts > 0) {
            return "CRITICAL";
        } else if (highAlerts > 5 || unreadAlerts > 20) {
            return "WARNING";
        } else {
            return "GOOD";
        }
    }
    
    private String generateHealthMessage(String health, long unreadAlerts, long criticalAlerts) {
        return switch (health) {
            case "CRITICAL" -> String.format("You have %d critical alerts that require immediate attention", criticalAlerts);
            case "WARNING" -> String.format("You have %d unread alerts that need review", unreadAlerts);
            case "GOOD" -> "All monitoring systems are operating normally";
            default -> "System status unknown";
        };
    }
    
    /**
     * Normalize target values to prevent case-sensitive duplicates and inconsistent formatting
     */
    private String normalizeTargetValue(String targetValue, CommonEnums.MonitorType monitorType) {
        if (targetValue == null) return null;
        
        String normalized = targetValue.trim();
        
        return switch (monitorType) {
            case EMAIL, DOMAIN -> normalized.toLowerCase();
            case IP_ADDRESS -> normalized; // Keep original case for IPs
            case USERNAME, KEYWORD, ORGANIZATION -> normalized.toLowerCase();
            case PHONE -> {
                // Normalize phone numbers by removing non-digit chars except +
                String cleaned = normalized.replaceAll("[^0-9+]", "");
                yield cleaned.toLowerCase();
            }
            default -> normalized.toLowerCase();
        };
    }
}
