package com.threatscope.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class SearchException extends RuntimeException {
    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}
