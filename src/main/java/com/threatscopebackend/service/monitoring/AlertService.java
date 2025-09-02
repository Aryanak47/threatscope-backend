package com.threatscopebackend.service.monitoring;

import com.threatscopebackend.dto.monitoring.BreachAlertResponse;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.User;
import com.threatscopebackend.entity.postgresql.ProcessedBreach;
import com.threatscopebackend.repository.postgresql.ProcessedBreachRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import com.threatscopebackend.websocket.RealTimeNotificationService;
import com.threatscopebackend.exception.ResourceNotFoundException;
import com.threatscopebackend.repository.postgresql.BreachAlertRepository;
import com.threatscopebackend.repository.postgresql.MonitoringItemRepository;
import com.threatscopebackend.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class AlertService {
    
    private final BreachAlertRepository breachAlertRepository;
    private final MonitoringItemRepository monitoringItemRepository;
    private final NotificationService notificationService;
    private final ProcessedBreachRepository processedBreachRepository;
    private final ObjectMapper objectMapper;
    private final RealTimeNotificationService realTimeNotificationService;
    
    /**
     * Check if there's a recent alert with similar content to prevent duplicates
     * ADDED: Simple duplicate detection mechanism
     */
    public boolean hasRecentAlertWithSimilarContent(MonitoringItem item, String contentHash) {
        try {
            LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24); // Check last 24 hours
            
            // Simple check: look for alerts with same description pattern in last 24 hours
            List<BreachAlert> recentAlerts = breachAlertRepository.findRecentAlertsForItem(item, cutoffTime);
            
            if (recentAlerts.size() > 5) { // If more than 5 alerts in 24 hours, likely duplicates
                log.warn("High alert volume detected for item {}: {} alerts in 24 hours - applying duplicate filtering", 
                        item.getId(), recentAlerts.size());
                return true; // Block additional alerts temporarily
            }
            
            // Check for exact content matches (simple approach)
            String hashToCheck = contentHash;
            for (BreachAlert alert : recentAlerts) {
                if (alert.getBreachData() != null) {
                    // Generate hash from existing alert and compare
                    String existingHash = String.valueOf(alert.getBreachData().hashCode());
                    if (hashToCheck.equals(existingHash)) {
                        log.debug("Found duplicate alert content for item {}", item.getId());
                        return true;
                    }
                }
            }
            
            return false;
            
        } catch (Exception e) {
            log.error("Error checking for duplicate alerts: {}", e.getMessage());
            return false; // If check fails, allow alert creation
        }
    }
    
    /**
     * Create a new breach alert
     */
    @Transactional
    public BreachAlert createAlert(MonitoringItem monitoringItem, String title, String description, 
                                  CommonEnums.AlertSeverity severity, String breachSource, 
                                  LocalDateTime breachDate, String breachData) {
        
        log.info("Creating alert for monitoring item: {} with severity: {}", 
                monitoringItem.getId(), severity);
        
        BreachAlert alert = new BreachAlert();
        alert.setUser(monitoringItem.getUser());
        alert.setMonitoringItem(monitoringItem);
        alert.setTitle(title);
        alert.setDescription(description);
        alert.setSeverity(severity);
        alert.setBreachSource(breachSource);
        alert.setBreachDate(breachDate);
        alert.setBreachData(breachData);
        alert.setStatus(CommonEnums.AlertStatus.NEW);
        
        BreachAlert saved = breachAlertRepository.save(alert);
        
        // Update monitoring item breach and alert counts
        monitoringItem.recordBreach(); // This increments both breachCount and alertCount
        monitoringItemRepository.save(monitoringItem);
        
        // Send notifications if enabled
        if (monitoringItem.getEmailAlerts() || monitoringItem.getInAppAlerts()) {
            sendAlertNotification(saved, monitoringItem);
        }
        
        log.info("Created alert with ID: {}", saved.getId());
        return saved;
    }
    
    /**
     * Get paginated alerts for a user
     */
    public Page<BreachAlertResponse> getAlerts(User user, int page, int size, String sortBy, String sortDir,
                                              CommonEnums.AlertStatus status, CommonEnums.AlertSeverity severity) {
        
        Sort.Direction direction = sortDir.equalsIgnoreCase("desc") ? Sort.Direction.DESC : Sort.Direction.ASC;
        Pageable pageable = PageRequest.of(page, size, Sort.by(direction, sortBy));
        
        Page<BreachAlert> alerts;
        
        if (status != null && severity != null) {
            alerts = breachAlertRepository.findByUserAndStatusAndSeverity(user, status, severity, pageable);
        } else if (status != null) {
            alerts = breachAlertRepository.findByUserAndStatus(user, status, pageable);
        } else if (severity != null) {
            alerts = breachAlertRepository.findByUserAndSeverity(user, severity, pageable);
        } else {
            alerts = breachAlertRepository.findByUser(user, pageable);
        }
        
        return alerts.map(BreachAlertResponse::fromEntity);
    }
    
    /**
     * Get a single alert by ID
     */
    public BreachAlertResponse getAlert(User user, Long alertId) {
        BreachAlert alert = findAlertByUserAndId(user, alertId);
        return BreachAlertResponse.fromEntity(alert);
    }
    
    /**
     * Mark alert as read
     */
    @Transactional
    public BreachAlertResponse markAsRead(User user, Long alertId) {
        BreachAlert alert = findAlertByUserAndId(user, alertId);
        
        if (alert.getStatus() == CommonEnums.AlertStatus.NEW) {
            alert.setStatus(CommonEnums.AlertStatus.VIEWED);
            alert.setViewedAt(LocalDateTime.now());
            alert = breachAlertRepository.save(alert);
            log.info("Marked alert {} as read", alertId);
        }
        
        return BreachAlertResponse.fromEntity(alert);
    }
    
    /**
     * Mark alert as archived
     */
    @Transactional
    public BreachAlertResponse markAsArchived(User user, Long alertId) {
        BreachAlert alert = findAlertByUserAndId(user, alertId);
        
        alert.setStatus(CommonEnums.AlertStatus.DISMISSED);
        alert.setDismissedAt(LocalDateTime.now());
        alert = breachAlertRepository.save(alert);
        
        log.info("Marked alert {} as archived", alertId);
        return BreachAlertResponse.fromEntity(alert);
    }
    
    /**
     * Mark alert as false positive
     */
    @Transactional
    public BreachAlertResponse markAsFalsePositive(User user, Long alertId) {
        BreachAlert alert = findAlertByUserAndId(user, alertId);
        
        alert.setIsFalsePositive(true);
        alert.setStatus(CommonEnums.AlertStatus.DISMISSED);
        alert = breachAlertRepository.save(alert);
        
        log.info("Marked alert {} as false positive", alertId);
        return BreachAlertResponse.fromEntity(alert);
    }
    
    /**
     * Mark alert as remediated
     */
    @Transactional
    public BreachAlertResponse markAsRemediated(User user, Long alertId, String remediationNotes) {
        BreachAlert alert = findAlertByUserAndId(user, alertId);
        
        alert.setIsRemediated(true);
        alert.setRemediationNotes(remediationNotes);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setStatus(CommonEnums.AlertStatus.RESOLVED);
        alert = breachAlertRepository.save(alert);
        
        log.info("Marked alert {} as remediated", alertId);
        return BreachAlertResponse.fromEntity(alert);
    }
    
    /**
     * Escalate an alert
     */
    @Transactional
    public BreachAlertResponse escalateAlert(User user, Long alertId, String escalationNotes) {
        BreachAlert alert = findAlertByUserAndId(user, alertId);
        
        alert.setIsEscalated(true);
        alert.setEscalationNotes(escalationNotes);
        alert.setSeverity(CommonEnums.AlertSeverity.CRITICAL); // Escalated alerts become critical
        alert.setEscalatedAt(LocalDateTime.now());
        alert = breachAlertRepository.save(alert);
        
        log.info("Escalated alert {} with notes: {}", alertId, escalationNotes);
        return BreachAlertResponse.fromEntity(alert);
    }
    
    /**
     * Bulk mark alerts as read
     */
    @Transactional
    public int bulkMarkAsRead(User user, List<Long> alertIds) {
        int updated = breachAlertRepository.bulkUpdateStatus(user, alertIds, CommonEnums.AlertStatus.VIEWED);
        log.info("Bulk marked {} alerts as read for user {}", updated, user.getId());
        return updated;
    }
    
    /**
     * Mark all alerts as read
     */
    @Transactional
    public int markAllAsRead(User user) {
        int updated = breachAlertRepository.markAllAsRead(user, LocalDateTime.now());
        log.info("Marked all {} alerts as read for user {}", updated, user.getId());
        return updated;
    }
    
    /**
     * Get unread alert count
     */
    public long getUnreadCount(User user) {
        return breachAlertRepository.countByUserAndStatus(user, CommonEnums.AlertStatus.NEW);
    }
    

    /**
     * Get recent alerts
     */
    public List<BreachAlertResponse> getRecentAlerts(User user, int days) {
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<BreachAlert> alerts = breachAlertRepository.findRecentAlerts(user, since);
        return alerts.stream()
                .map(BreachAlertResponse::fromEntity)
                .toList();
    }
    
    /**
     * Get high priority alerts
     */
    public List<BreachAlertResponse> getHighPriorityAlerts(User user) {
        List<BreachAlert> alerts = breachAlertRepository.findHighPriorityAlerts(user);
        return alerts.stream()
                .map(BreachAlertResponse::fromEntity)
                .toList();
    }
    
    /**
     * Search alerts
     */
    public Page<BreachAlertResponse> searchAlerts(User user, String searchTerm, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<BreachAlert> alerts = breachAlertRepository.searchByUser(user, searchTerm, pageable);
        return alerts.map(BreachAlertResponse::fromEntity);
    }
    
    /**
     * Get alert statistics
     */
    public Map<String, Long> getAlertStatistics(User user) {
        List<Object[]> severityStats = breachAlertRepository.countBySeverityForUser(user);
        List<Object[]> statusStats = breachAlertRepository.countByStatusForUser(user);
        
        Map<String, Long> stats = new java.util.HashMap<>();
        
        // Add severity stats
        severityStats.forEach(row -> 
            stats.put("severity_" + row[0].toString().toLowerCase(), (Long) row[1]));
        
        // Add status stats
        statusStats.forEach(row -> 
            stats.put("status_" + row[0].toString().toLowerCase(), (Long) row[1]));
        
        // Add total count
        stats.put("total", breachAlertRepository.countTotalByUser(user));
        
        return stats;
    }
    
    /**
     * Process pending notifications with admin-configurable limits
     */
    @Transactional
    public void processPendingNotifications(Integer maxAlertsPerDay) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24); // Don't notify for alerts older than 24h
        List<BreachAlert> alertsNeedingNotification = breachAlertRepository.findAlertsNeedingNotification(cutoffTime);
        
        // Limit the number of alerts processed to prevent spam
        int alertsToProcess = Math.min(alertsNeedingNotification.size(), maxAlertsPerDay != null ? maxAlertsPerDay : 1000);
        
        log.info("Processing {} pending alert notifications (limited to {})", alertsToProcess, maxAlertsPerDay);
        
        for (int i = 0; i < alertsToProcess; i++) {
            BreachAlert alert = alertsNeedingNotification.get(i);
            try {
                sendAlertNotification(alert, alert.getMonitoringItem());
                
                alert.setNotificationSent(true);
                alert.setNotificationSentAt(LocalDateTime.now());
                breachAlertRepository.save(alert);
                
            } catch (Exception e) {
                log.error("Failed to send notification for alert {}: {}", alert.getId(), e.getMessage());
            }
        }
    }
    
    /**
     * Process pending notifications in batches with admin-configurable limits
     * OPTIMIZED for better performance
     */
    @Transactional
    public void processPendingNotificationsBatched(Integer maxAlertsPerDay, Integer batchSize) {
        LocalDateTime cutoffTime = LocalDateTime.now().minusHours(24);
        
        int totalProcessed = 0;
        int currentBatch = 0;
        int effectiveBatchSize = batchSize != null ? batchSize : 100;
        int maxAlerts = maxAlertsPerDay != null ? maxAlertsPerDay : 1000;
        
        log.info("Starting batched notification processing - batch size: {}, max alerts: {}", effectiveBatchSize, maxAlerts);
        
        while (totalProcessed < maxAlerts) {
            // Get next batch of alerts needing notification
            int offset = currentBatch * effectiveBatchSize;
            List<BreachAlert> alertBatch = breachAlertRepository.findAlertsNeedingNotificationPaginated(
                cutoffTime, offset, effectiveBatchSize);
            
            if (alertBatch.isEmpty()) {
                break;
            }
            
            log.debug("Processing notification batch {} with {} alerts", currentBatch + 1, alertBatch.size());
            
            for (BreachAlert alert : alertBatch) {
                if (totalProcessed >= maxAlerts) {
                    break;
                }
                
                try {
                    sendAlertNotification(alert, alert.getMonitoringItem());
                    
                    alert.setNotificationSent(true);
                    alert.setNotificationSentAt(LocalDateTime.now());
                    breachAlertRepository.save(alert);
                    
                    totalProcessed++;
                    
                } catch (Exception e) {
                    log.error("Failed to send notification for alert {}: {}", alert.getId(), e.getMessage());
                }
            }
            
            currentBatch++;
        }
        
        log.info("Completed batched notification processing: {} alerts processed", totalProcessed);
    }
    
    /**
     * Clean up old archived alerts in batches
     * OPTIMIZED for better performance and to avoid database locks
     */
    @Transactional
    public int cleanupOldArchivedAlertsBatched(int daysToKeep, Integer batchSize) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        int effectiveBatchSize = batchSize != null ? batchSize : 1000;
        int totalDeleted = 0;
        
        log.info("Starting batched cleanup of alerts older than {} days with batch size {}", daysToKeep, effectiveBatchSize);
        
        while (true) {
            int deletedInBatch = breachAlertRepository.deleteOldArchivedAlertsBatch(cutoffDate, effectiveBatchSize);
            
            if (deletedInBatch == 0) {
                break;
            }
            
            totalDeleted += deletedInBatch;
            log.debug("Deleted {} alerts in this batch, total: {}", deletedInBatch, totalDeleted);
            
            // Small delay to prevent database overload
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        
        log.info("Completed batched cleanup: {} old archived alerts cleaned up", totalDeleted);
        return totalDeleted;
    }
    
    /**
     * Delete an alert and its processed breach record (allows re-detection)
     */
    @Transactional
    public void deleteAlert(User user, Long alertId) {
        BreachAlert alert = findAlertByUserAndId(user, alertId);
        
        // Remove the processed breach record first (allows re-detection of this breach)
        try {
            int deletedRecords = processedBreachRepository.deleteByBreachAlertId(alertId);
            if (deletedRecords > 0) {
                log.info("Deleted {} processed breach records for alert {}", deletedRecords, alertId);
            }
        } catch (Exception e) {
            log.warn("Failed to delete processed breach records for alert {}: {}", alertId, e.getMessage());
        }
        
        // Delete the alert
        breachAlertRepository.delete(alert);
        log.info("Deleted alert {} for user {}", alertId, user.getId());
    }
    
    /**
     * Create alert from monitoring result with atomic transaction
     * Moved from BreachDetectionService to fix @Transactional self-invocation issue
     */
    @Transactional
    public boolean createAlertFromResult(MonitoringItem item, Map<String, Object> result, ProcessedBreachRepository processedBreachRepo) {
        try {
            String breachId = (String) result.get("id");

            String title = buildAlertTitle(item, result);
            String description = buildAlertDescription(item, result);
            CommonEnums.AlertSeverity severity = determineSeverity(item, result);
            String breachSource = (String) result.getOrDefault("source", "Unknown");
            LocalDateTime breachDate = parseBreachDate(result);
            String breachData = convertResultToJson(result);

            BreachAlert alert = createAlert(item, title, description, severity, breachSource, breachDate, breachData);

            // Record this breach as processed to prevent duplicates
            recordProcessedBreach(item, result, alert, processedBreachRepo);

            // Send immediate real-time notification for all alerts
            try {
                realTimeNotificationService.sendRealTimeAlert(alert);
                log.info("⚡ Real-time alert sent via WebSocket: {} (severity: {})", alert.getId(), severity);
            } catch (Exception e) {
                log.error("❌ Failed to send real-time notification for alert {}: {}", alert.getId(), e.getMessage());
                // Don't fail the entire alert creation if real-time notification fails
            }

            log.debug("✅ Successfully created alert {} for breach {} in item {}", alert.getId(), breachId, item.getId());
            return true; // Alert created successfully

        } catch (Exception e) {
            log.error("Error creating alert for monitoring item {}: {}", item.getId(), e.getMessage());
            return false;
        }
    }
    
    // Helper methods moved from BreachDetectionService for @Transactional support
    
    private String buildAlertTitle(MonitoringItem item, Map<String, Object> result) {
        String source = cleanString((String) result.get("source"));
        String domain = extractDomainFromResult(result);

        if (isNotEmpty(source) && !"Unknown".equals(source)) {
            return String.format("🚨 New breach detected for %s in %s", item.getTargetValue(), source);
        } else if (isNotEmpty(domain)) {
            return String.format("🚨 New breach detected for %s on %s", item.getTargetValue(), domain);
        } else {
            return String.format("🚨 New breach detected for %s", item.getTargetValue());
        }
    }

    private String buildAlertDescription(MonitoringItem item, Map<String, Object> result) {
        StringBuilder description = new StringBuilder();

        description.append("🔍 **Security Alert: New Data Breach Detected**\n\n");
        description.append("🎯 **Monitored Asset:** ").append(item.getTargetValue()).append("\n");
        description.append("💱 **Monitor Type:** ").append(item.getMonitorType().toString().toLowerCase().replace("_", " ")).append("\n\n");
        description.append("📄 **Breach Information:**\n");

        String source = cleanString((String) result.get("source"));
        if (isNotEmpty(source) && !"Unknown".equals(source)) {
            description.append("• **Source:** ").append(source).append("\n");
        }

        Object breachDateObj = result.get("breach_date");
        if (breachDateObj != null) {
            String formattedDate = formatBreachDateFromObject(breachDateObj);
            if (isNotEmpty(formattedDate) && !"Unknown".equals(formattedDate)) {
                description.append("• **Date:** ").append(formattedDate).append("\n");
            }
        }

        String domain = extractDomainFromResult(result);
        if (isNotEmpty(domain)) {
            description.append("• **Associated Domain:** ").append(domain).append("\n");
        }

        String password = cleanString((String) result.get("password"));
        if (isNotEmpty(password)) {
            description.append("• **Password Exposed:** ").append(maskPassword(password)).append("\n");
        }

        String url = cleanString((String) result.get("url"));
        if (isNotEmpty(url) && !url.equals(domain)) {
            description.append("• **URL:** ").append(url).append("\n");
        }

        description.append("\n🔒 **Recommended Actions:**\n");
        description.append("• Change passwords immediately for this account\n");
        description.append("• Enable two-factor authentication (2FA)\n");
        description.append("• Monitor account activity for suspicious behavior\n");
        description.append("• Consider using unique passwords for each service\n\n");
        description.append("⚠️ Please review this alert and take appropriate security measures.");

        return description.toString();
    }

    private CommonEnums.AlertSeverity determineSeverity(MonitoringItem item, Map<String, Object> result) {
        String password = cleanString((String) result.get("password"));
        String source = cleanString((String) result.get("source"));
        boolean hasPassword = isNotEmpty(password);
        boolean hasKnownSource = isNotEmpty(source) && !"Unknown".equals(source);

        if (item.getMonitorType() == CommonEnums.MonitorType.EMAIL && hasPassword) {
            return CommonEnums.AlertSeverity.CRITICAL;
        }
        if (item.getMonitorType() == CommonEnums.MonitorType.DOMAIN && hasPassword) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        if (item.getMonitorType() == CommonEnums.MonitorType.IP_ADDRESS) {
            return CommonEnums.AlertSeverity.HIGH;
        }
        if (item.getMonitorType() == CommonEnums.MonitorType.USERNAME && hasPassword) {
            return CommonEnums.AlertSeverity.HIGH;
        }

        LocalDateTime breachDate = parseBreachDate(result);
        if (breachDate != null && breachDate.isAfter(LocalDateTime.now().minusDays(30))) {
            return hasPassword ? CommonEnums.AlertSeverity.HIGH : CommonEnums.AlertSeverity.MEDIUM;
        }

        if (hasKnownSource) {
            return hasPassword ? CommonEnums.AlertSeverity.MEDIUM : CommonEnums.AlertSeverity.LOW;
        }

        return hasPassword ? CommonEnums.AlertSeverity.MEDIUM : CommonEnums.AlertSeverity.LOW;
    }

    private LocalDateTime parseBreachDate(Map<String, Object> result) {
        try {
            Object dateObj = result.get("breach_date");
            return parseBreachDateFromObject(dateObj);
        } catch (Exception e) {
            log.debug("Could not parse breach date: {}", e.getMessage());
            return null;
        }
    }

    private LocalDateTime parseBreachDateFromObject(Object dateObj) {
        if (dateObj == null) return null;
        
        try {
            if (dateObj instanceof LocalDateTime) {
                return (LocalDateTime) dateObj;
            }
            if (dateObj instanceof String) {
                String dateStr = (String) dateObj;
                if ("null".equals(dateStr) || dateStr.trim().isEmpty()) {
                    return null;
                }
                try {
                    return LocalDateTime.parse(dateStr);
                } catch (DateTimeParseException e) {
                    return LocalDateTime.parse(dateStr, DateTimeFormatter.ISO_LOCAL_DATE_TIME);
                }
            }
            return null;
        } catch (Exception e) {
            log.debug("Error parsing breach date: {}", e.getMessage());
            return null;
        }
    }

    private String convertResultToJson(Map<String, Object> result) {
        try {
            return objectMapper.writeValueAsString(result);
        } catch (JsonProcessingException e) {
            log.error("JSON processing error: {}", e.getMessage());
            return result.toString();
        }
    }

    private void recordProcessedBreach(MonitoringItem item, Map<String, Object> result, BreachAlert alert, ProcessedBreachRepository processedBreachRepo) {
        try {
            String breachId = (String) result.get("id");
            String databaseSource = (String) result.get("database_source");
            String contentHash = generateSimpleContentHash(item, result);
            
            if (breachId != null && !breachId.trim().isEmpty()) {
                ProcessedBreach processedBreach = ProcessedBreach.builder()
                        .monitoringItem(item)
                        .breachAlert(alert)
                        .breachId(breachId)
                        .databaseSource(databaseSource)
                        .contentHash(contentHash)
                        .build();
                
                processedBreachRepo.save(processedBreach);
                log.debug("Recorded processed breach: {} linked to alert: {} for item: {}", 
                         breachId, alert.getId(), item.getId());
            }
        } catch (Exception e) {
            log.warn("Failed to record processed breach for item {}: {}", item.getId(), e.getMessage());
        }
    }

    private String generateSimpleContentHash(MonitoringItem item, Map<String, Object> result) {
        try {
            StringBuilder hashInput = new StringBuilder();
            hashInput.append(item.getId()).append("|");

            Object login = result.get("login");
            hashInput.append(login != null ? login.toString() : "").append("|");

            Object password = result.get("password");
            hashInput.append(password != null ? password.toString() : "").append("|");

            Object source = result.get("source");
            hashInput.append(source != null ? source.toString() : "").append("|");

            Object url = result.get("url");
            hashInput.append(url != null ? url.toString() : "");

            return String.valueOf(hashInput.toString().hashCode());
        } catch (Exception e) {
            log.debug("Error generating content hash for item {}: {}", item.getId(), e.getMessage());
            return String.valueOf(System.currentTimeMillis());
        }
    }

    private String formatBreachDateFromObject(Object dateObj) {
        if (dateObj == null) return "Unknown";
        
        try {
            LocalDateTime dateTime = parseBreachDateFromObject(dateObj);
            if (dateTime != null) {
                return dateTime.format(DateTimeFormatter.ofPattern("MMM dd, yyyy 'at' HH:mm"));
            }
            return "Unknown";
        } catch (Exception e) {
            log.debug("Error formatting breach date: {}", e.getMessage());
            return dateObj.toString();
        }
    }

    private boolean isNotEmpty(String value) {
        return value != null && !value.trim().isEmpty() && !"null".equalsIgnoreCase(value.trim());
    }

    private String cleanString(String value) {
        if (value == null || "null".equalsIgnoreCase(value.trim())) {
            return null;
        }
        return value.trim();
    }

    private String extractDomainFromResult(Map<String, Object> result) {
        String url = cleanString((String) result.get("url"));
        if (isNotEmpty(url)) {
            try {
                if (!url.startsWith("http")) {
                    url = "https://" + url;
                }
                java.net.URI uri = new java.net.URI(url);
                String domain = uri.getHost();
                return domain != null ? domain.toLowerCase() : null;
            } catch (Exception e) {
                if (url.contains(".")) {
                    String[] parts = url.split("/")[0].split("@");
                    String domainPart = parts[parts.length - 1];
                    if (domainPart.contains(".")) {
                        return domainPart.toLowerCase();
                    }
                }
            }
        }

        Object additionalData = result.get("additional_data");
        if (additionalData instanceof Map) {
            Map<?, ?> dataMap = (Map<?, ?>) additionalData;
            String domain = cleanString((String) dataMap.get("domain"));
            if (isNotEmpty(domain)) {
                return domain.toLowerCase();
            }
        }
        return null;
    }

    private String maskPassword(String password) {
        if (!isNotEmpty(password)) {
            return "[Not disclosed]";
        }
        if (password.length() <= 4) {
            return "****";
        }
        int visibleChars = Math.min(2, password.length() / 3);
        String start = password.substring(0, visibleChars);
        String end = password.substring(password.length() - visibleChars);
        int maskedLength = Math.max(4, password.length() - (2 * visibleChars));
        String masked = "*".repeat(maskedLength);
        return start + masked + end;
    }
    
    /**
     * Clean up old archived alerts (original method for backward compatibility)
     */
    @Transactional
    public int cleanupOldArchivedAlerts(int daysToKeep) {
        LocalDateTime cutoffDate = LocalDateTime.now().minusDays(daysToKeep);
        int deleted = breachAlertRepository.deleteOldArchivedAlerts(cutoffDate);
        log.info("Cleaned up {} old archived alerts", deleted);
        return deleted;
    }
    
    // Helper methods
    
    private BreachAlert findAlertByUserAndId(User user, Long alertId) {
        return breachAlertRepository.findById(alertId)
            .filter(alert -> alert.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new ResourceNotFoundException("Alert not found"));
    }
    
    private void sendAlertNotification(BreachAlert alert, MonitoringItem monitoringItem) {
        try {
            // Send email notification if enabled
            if (monitoringItem.getEmailAlerts()) {
                notificationService.sendAlertEmail(alert);
            }
            
            // Send in-app notification if enabled
            if (monitoringItem.getInAppAlerts()) {
                notificationService.sendInAppNotification(alert);
            }
            
        } catch (Exception e) {
            log.error("Failed to send notification for alert {}: {}", alert.getId(), e.getMessage());
        }
    }
}
