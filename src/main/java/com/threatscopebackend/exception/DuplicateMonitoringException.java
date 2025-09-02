package com.threatscopebackend.exception;

import lombok.Getter;

/**
 * Exception thrown when attempting to create a duplicate monitoring item
 */
@Getter
public class DuplicateMonitoringException extends RuntimeException {
    private final String targetValue;
    private final String monitorType;
    private final Long existingItemId;
    
    public DuplicateMonitoringException(String message, String targetValue, String monitorType, Long existingItemId) {
        super(message);
        this.targetValue = targetValue;
        this.monitorType = monitorType;
        this.existingItemId = existingItemId;
    }
    
    public DuplicateMonitoringException(String message, String targetValue, String monitorType) {
        this(message, targetValue, monitorType, null);
    }
}
