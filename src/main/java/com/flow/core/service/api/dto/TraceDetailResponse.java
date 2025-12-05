package com.flow.core.service.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for trace detail responses.
 *
 * Provides complete trace information including events, checkpoints, and errors.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class TraceDetailResponse {

    /**
     * Unique trace identifier.
     */
    private String traceId;

    /**
     * Associated graph ID.
     */
    private String graphId;

    /**
     * When the trace started.
     */
    private Instant startedAt;

    /**
     * When the trace completed (null if ongoing).
     */
    private Instant completedAt;

    /**
     * Total duration in milliseconds.
     */
    private Long durationMs;

    /**
     * Whether the trace is complete.
     */
    private boolean completed;

    /**
     * Whether the trace has errors.
     */
    private boolean hasErrors;

    /**
     * Ordered list of events.
     */
    private List<EventResponse> events;

    /**
     * Checkpoints reached during execution.
     */
    private List<CheckpointResponse> checkpoints;

    /**
     * Errors encountered during execution.
     */
    private List<ErrorResponse> errors;

    /**
     * Async hops (cross-service calls).
     */
    private List<AsyncHopResponse> asyncHops;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EventResponse {
        private String eventId;
        private String type;
        private Instant timestamp;
        private String nodeId;
        private String edgeId;
        private String spanId;
        private Long durationMs;
        private Map<String, Object> attributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CheckpointResponse {
        private String checkpointId;
        private String name;
        private Instant timestamp;
        private String nodeId;
        private Map<String, Object> data;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorResponse {
        private String errorId;
        private String type;
        private String message;
        private String stackTrace;
        private Instant timestamp;
        private String nodeId;
        private String spanId;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class AsyncHopResponse {
        private String hopId;
        private String correlationId;
        private String sourceNodeId;
        private String targetNodeId;
        private String type; // KAFKA, HTTP, QUEUE, etc.
        private Instant sentAt;
        private Instant receivedAt;
        private Long latencyMs;
    }
}

