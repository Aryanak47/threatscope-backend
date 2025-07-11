package com.threatscopebackend.exception;

/**
 * Exception thrown when there's an error communicating with an external service
 */
public class ExternalServiceException extends RuntimeException {
    
    public ExternalServiceException(String message) {
        super(message);
    }
    
    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
