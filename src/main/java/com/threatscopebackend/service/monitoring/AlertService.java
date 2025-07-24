package com.threatscopebackend.service.monitoring;

import com.threatscopebackend.dto.monitoring.BreachAlertResponse;
import com.threatscopebackend.entity.enums.CommonEnums;
import com.threatscopebackend.entity.postgresql.BreachAlert;
import com.threatscopebackend.entity.postgresql.MonitoringItem;
import com.threatscopebackend.entity.postgresql.User;
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
