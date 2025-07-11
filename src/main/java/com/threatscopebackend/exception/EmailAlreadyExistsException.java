package com.threatscopebackend.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.CONFLICT)
public class EmailAlreadyExistsException extends RuntimeException {
    
    public EmailAlreadyExistsException(String email) {
        super(String.format("Email '%s' is already in use", email));
    }
    
    public EmailAlreadyExistsException(String message, Throwable cause) {
        super(message, cause);
    }
}
