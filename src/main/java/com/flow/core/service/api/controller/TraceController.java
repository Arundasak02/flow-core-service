package com.flow.core.service.api.controller;

import com.flow.core.service.api.dto.ApiResponse;
import com.flow.core.service.api.dto.TraceDetailResponse;
import com.flow.core.service.runtime.RuntimeTraceBuffer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * Controller for trace queries.
 *
 * Handles GET /trace/{traceId} for fetching trace details.
 */
@Slf4j
@RestController
@RequestMapping("/trace")
@Tag(name = "Trace Queries", description = "Endpoints for querying runtime traces")
@RequiredArgsConstructor
public class TraceController {

    private final RuntimeTraceBuffer traceBuffer;

    /**
     * Gets trace details by ID.
     */
    @GetMapping("/{traceId}")
    @Operation(
            summary = "Get trace details",
            description = "Returns complete details of a runtime trace including events, checkpoints, and errors"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Trace found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Trace not found or evicted")
    })
    public ResponseEntity<ApiResponse<TraceDetailResponse>> getTraceDetails(
            @Parameter(description = "Trace ID") @PathVariable String traceId) {

        log.debug("Getting trace details: {}", traceId);

        return traceBuffer.getTrace(traceId)
                .map(trace -> {
                    log.debug("Found trace: {}", traceId);
                    return ResponseEntity.ok(ApiResponse.success(toDetailResponse(trace)));
                })
                .orElseGet(() -> {
                    log.warn("Trace not found: {}", traceId);
                    return ResponseEntity.status(HttpStatus.NOT_FOUND)
                            .body(ApiResponse.error("Trace not found or evicted", "NOT_FOUND"));
                });
    }

    // --- Private helpers ---

    private TraceDetailResponse toDetailResponse(RuntimeTraceBuffer.RuntimeTrace trace) {
        return TraceDetailResponse.builder()
                .traceId(trace.traceId())
                .graphId(trace.graphId())
                .startedAt(Instant.ofEpochMilli(trace.createdAtEpochMs()))
                .completedAt(trace.completedAtEpochMs() != null
                        ? Instant.ofEpochMilli(trace.completedAtEpochMs()) : null)
                .durationMs(trace.getDurationMs())
                .completed(trace.isComplete())
                .hasErrors(!trace.errors().isEmpty())
                .events(trace.events().stream()
                        .map(e -> TraceDetailResponse.EventResponse.builder()
                                .eventId(e.eventId())
                                .type(e.type())
                                .timestamp(Instant.ofEpochMilli(e.timestampEpochMs()))
                                .nodeId(e.nodeId())
                                .edgeId(e.edgeId())
                                .spanId(e.spanId())
                                .durationMs(e.durationMs())
                                .attributes(e.attributes())
                                .build())
                        .collect(Collectors.toList()))
                .checkpoints(trace.checkpoints().stream()
                        .map(c -> TraceDetailResponse.CheckpointResponse.builder()
                                .checkpointId(c.checkpointId())
                                .name(c.name())
                                .timestamp(Instant.ofEpochMilli(c.timestampEpochMs()))
                                .nodeId(c.nodeId())
                                .data(c.data())
                                .build())
                        .collect(Collectors.toList()))
                .errors(trace.errors().stream()
                        .map(err -> TraceDetailResponse.ErrorResponse.builder()
                                .errorId(err.errorId())
                                .type(err.type())
                                .message(err.message())
                                .stackTrace(err.stackTrace())
                                .timestamp(Instant.ofEpochMilli(err.timestampEpochMs()))
                                .nodeId(err.nodeId())
                                .spanId(err.spanId())
                                .build())
                        .collect(Collectors.toList()))
                .asyncHops(trace.asyncHops().stream()
                        .map(h -> TraceDetailResponse.AsyncHopResponse.builder()
                                .hopId(h.hopId())
                                .correlationId(h.correlationId())
                                .sourceNodeId(h.sourceNodeId())
                                .targetNodeId(h.targetNodeId())
                                .type(h.type())
                                .sentAt(h.sentAtEpochMs() != null
                                        ? Instant.ofEpochMilli(h.sentAtEpochMs()) : null)
                                .receivedAt(h.receivedAtEpochMs() != null
                                        ? Instant.ofEpochMilli(h.receivedAtEpochMs()) : null)
                                .latencyMs(h.getLatencyMs())
                                .build())
                        .collect(Collectors.toList()))
                .build();
    }
}

