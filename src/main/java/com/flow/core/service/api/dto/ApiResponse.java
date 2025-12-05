package com.flow.core.service.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Standard API response wrapper.
 *
 * Provides consistent response structure across all endpoints.
 *
 * @param <T> the type of the response data
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    /**
     * Whether the request was successful.
     */
    private boolean success;

    /**
     * Response data (null on error).
     */
    private T data;

    /**
     * Error information (null on success).
     */
    private ErrorInfo error;

    /**
     * Timestamp of the response.
     */
    @Builder.Default
    private Instant timestamp = Instant.now();

    /**
     * Creates a successful response with data.
     */
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .build();
    }

    /**
     * Creates an error response.
     */
    public static <T> ApiResponse<T> error(String message, String code) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorInfo.builder()
                        .message(message)
                        .code(code)
                        .build())
                .build();
    }

    /**
     * Creates an error response with details.
     */
    public static <T> ApiResponse<T> error(String message, String code, String details) {
        return ApiResponse.<T>builder()
                .success(false)
                .error(ErrorInfo.builder()
                        .message(message)
                        .code(code)
                        .details(details)
                        .build())
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorInfo {
        private String message;
        private String code;
        private String details;
    }
}

