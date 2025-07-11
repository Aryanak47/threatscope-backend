package com.threatscopebackend.exception;

import com.threatscope.dto.response.ApiResponse;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import org.springframework.lang.NonNull;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    @Override
    @NonNull
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            @NonNull MethodArgumentNotValidException ex,
            @NonNull HttpHeaders headers,
            @NonNull HttpStatusCode status,
            @NonNull WebRequest request) {
        
        Map<String, String> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fieldError -> {
                            fieldError.getDefaultMessage();
                            return fieldError.getDefaultMessage();
                        }
                ));
        
        return ResponseEntity.badRequest()
                .body(ApiResponse.validationError(
                        new ArrayList<>(errors.values())
                ));
    }


    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceNotFoundException(
            ResourceNotFoundException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.notFound(ex.getMessage()),
                HttpStatus.NOT_FOUND
        );
    }

    @ExceptionHandler(BadRequestException.class)
    public ResponseEntity<ApiResponse<Void>> handleBadRequestException(
            BadRequestException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.badRequest(ex.getMessage()),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleConstraintViolationException(
            ConstraintViolationException ex, WebRequest request) {
        Map<String, String> errors = new HashMap<>();
        ex.getConstraintViolations().forEach(violation -> {
            String fieldName = violation.getPropertyPath().toString();
            String message = violation.getMessage();
            errors.put(fieldName, message);
        });
        
        return new ResponseEntity<>(
                ApiResponse.error("Validation failed",
                        new ArrayList<>(errors.values()),
                        HttpStatus.BAD_REQUEST),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthenticationException(
            AuthenticationException ex, WebRequest request) {
        String message = "Authentication failed";
        if (ex instanceof BadCredentialsException) {
            message = "Invalid username or password";
        } else if (ex instanceof DisabledException) {
            message = "User account is disabled";
        } else if (ex instanceof LockedException) {
            message = "User account is locked";
        }
        
        return new ResponseEntity<>(
                ApiResponse.unauthorized(message),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDeniedException(
            AccessDeniedException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.forbidden("Access Denied: " + ex.getMessage()),
                HttpStatus.FORBIDDEN
        );
    }

    @ExceptionHandler(UsernameNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleUsernameNotFoundException(
            UsernameNotFoundException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.unauthorized("User not found with the provided credentials"),
                HttpStatus.UNAUTHORIZED
        );
    }

    @ExceptionHandler(SearchException.class)
    public ResponseEntity<ApiResponse<Void>> handleSearchException(
            SearchException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(EmailSendingException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailSendingException(
            EmailSendingException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.error("Failed to send email: " + ex.getMessage(), 
                        HttpStatus.INTERNAL_SERVER_ERROR),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(EmailAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleEmailAlreadyExistsException(
            EmailAlreadyExistsException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.error(ex.getMessage(), HttpStatus.CONFLICT),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ApiResponse<Void>> handleInvalidTokenException(
            InvalidTokenException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.error(ex.getMessage(), HttpStatus.BAD_REQUEST),
                HttpStatus.BAD_REQUEST
        );
    }

    @ExceptionHandler(ResourceAlreadyExistsException.class)
    public ResponseEntity<ApiResponse<Void>> handleResourceAlreadyExistsException(
            ResourceAlreadyExistsException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.error(ex.getMessage(), HttpStatus.CONFLICT),
                HttpStatus.CONFLICT
        );
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleAllExceptions(
            Exception ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.serverError("An unexpected error occurred: " + ex.getMessage()),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ApiResponse<Void>> handleRuntimeException(
            RuntimeException ex, WebRequest request) {
        return new ResponseEntity<>(
                ApiResponse.error("An error occurred: " + ex.getMessage(), 
                        HttpStatus.INTERNAL_SERVER_ERROR),
                HttpStatus.INTERNAL_SERVER_ERROR
        );
    }
}
