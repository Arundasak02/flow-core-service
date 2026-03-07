package com.flow.core.service.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import lombok.*;

import java.util.List;
import java.util.Map;

/**
 * DTO for runtime event batches sent by flow-runtime-agent.
 * The agent sends a flat batch of events; the controller groups them by traceId
 * and converts to the internal RuntimeEventIngestRequest format.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentBatchIngestRequest {

    @NotBlank(message = "graphId is required")
    private String graphId;

    private String agentVersion;

    @NotEmpty(message = "batch cannot be empty")
    private List<AgentEventDto> batch;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AgentEventDto {
        private String traceId;
        private String spanId;
        private String parentSpanId;
        private String nodeId;
        private String type;           // METHOD_ENTER, METHOD_EXIT, ERROR, CHECKPOINT
        private long timestamp;        // epoch millis
        private long durationMs;
        private String errorType;
        private Map<String, Object> data;  // checkpoint data
    }
}

