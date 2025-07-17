package com.threatscopebackend.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.http.HttpStatus;

import java.time.Instant;
import java.util.Collections;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private Instant timestamp;
    private Integer status;
    private String error;
    private List<String> errors;
    private String path;
    private String requestId;

    public static <T> ApiResponse<T> success(T data) {
        return success(null, data);
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .status(HttpStatus.OK.value())
                .build();
    }

    public static <T> ApiResponse<T> created(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .timestamp(Instant.now())
                .status(HttpStatus.CREATED.value())
                .build();
    }

    public static <T> ApiResponse<T> error(String message) {
        return error(message, null, HttpStatus.BAD_REQUEST);
    }

    public static <T> ApiResponse<T> error(String message, HttpStatus status) {
        return error(message, null, status);
    }

    public static <T> ApiResponse<T> error(String message, List<String> errors, HttpStatus status) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .errors(errors != null ? errors : Collections.emptyList())
                .timestamp(Instant.now())
                .status(status.value())
                .error(status.getReasonPhrase())
                .build();
    }

    public static <T> ApiResponse<T> notFound(String message) {
        return error(message, HttpStatus.NOT_FOUND);
    }

    public static <T> ApiResponse<T> unauthorized(String message) {
        return error(message, HttpStatus.UNAUTHORIZED);
    }

    public static <T> ApiResponse<T> forbidden(String message) {
        return error(message, HttpStatus.FORBIDDEN);
    }

    public static <T> ApiResponse<T> badRequest(String message) {
        return error(message, HttpStatus.BAD_REQUEST);
    }

    public static <T> ApiResponse<T> serverError(String message) {
        return error(message, HttpStatus.INTERNAL_SERVER_ERROR);
    }

    public static <T> ApiResponse<T> validationError(List<String> errors) {
        return error("Validation failed", errors, HttpStatus.BAD_REQUEST);
    }
}
