package com.flow.core.service.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for runtime event ingestion requests.
 *
 * Represents a batch of runtime events for a specific trace.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RuntimeEventIngestRequest {

    /**
     * ID of the static graph this trace relates to.
     */
    @NotBlank(message = "graphId is required")
    private String graphId;

    /**
     * Unique identifier for the runtime trace.
     */
    @NotBlank(message = "traceId is required")
    private String traceId;

    /**
     * List of runtime events.
     */
    @NotEmpty(message = "events cannot be empty")
    private List<EventDto> events;

    /**
     * Whether this batch marks the trace as complete.
     */
    @Builder.Default
    private boolean traceComplete = false;

    /**
     * Additional metadata for the trace.
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EventDto {

        /**
         * Unique event identifier (for deduplication).
         */
        private String eventId;

        /**
         * Event type: START, END, CHECKPOINT, ERROR, ASYNC_SEND, ASYNC_RECEIVE.
         */
        @NotBlank(message = "type is required")
        private String type;

        /**
         * Event timestamp.
         */
        private Instant timestamp;

        /**
         * Associated node ID (from static graph).
         */
        private String nodeId;

        /**
         * Associated edge ID (from static graph).
         */
        private String edgeId;

        /**
         * Span ID for distributed tracing correlation.
         */
        private String spanId;

        /**
         * Parent span ID for hierarchy.
         */
        private String parentSpanId;

        /**
         * Duration in milliseconds (for END events).
         */
        private Long durationMs;

        /**
         * Error message (for ERROR events).
         */
        private String errorMessage;

        /**
         * Error type/class (for ERROR events).
         */
        private String errorType;

        /**
         * Correlation ID for async hops.
         */
        private String correlationId;

        /**
         * Additional event attributes.
         */
        private Map<String, Object> attributes;
    }
}

