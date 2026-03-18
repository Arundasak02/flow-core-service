package com.flow.core.service.api.controller;

import com.flow.core.service.api.dto.AgentBatchIngestRequest;
import com.flow.core.service.api.dto.ApiResponse;
import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.config.IngestionConfig;
import com.flow.core.service.engine.GraphStore;
import com.flow.core.service.ingest.IngestionQueue;
import com.flow.core.service.ingest.IngestionWorkItem;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for runtime event ingestion.
 *
 * Handles POST /ingest/runtime for ingesting runtime events from Flow Runtime Plugin.
 */
@Slf4j
@RestController
@RequestMapping("/ingest/runtime")
@Tag(name = "Runtime Event Ingestion", description = "Endpoints for ingesting runtime execution events")
@RequiredArgsConstructor
public class RuntimeIngestController {

    private final IngestionQueue ingestionQueue;
    private final GraphStore graphStore;
    private final IngestionConfig config;

    /**
     * Ingests runtime events for a trace.
     *
     * @param request the event ingestion request
     * @return 202 Accepted if queued, 404 if graph not found, 429 if queue full
     */
    @PostMapping
    @Operation(
            summary = "Ingest runtime events",
            description = "Submits runtime execution events for a trace. Events will be stored in RuntimeTraceBuffer and merged into the graph."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Events accepted for processing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Ingestion queue full")
    })
    public ResponseEntity<ApiResponse<String>> ingestRuntimeEvents(
            @Valid @RequestBody RuntimeEventIngestRequest request) {

        String traceId = request.getTraceId();
        String graphId = request.getGraphId();

        log.debug("Received runtime event ingestion: traceId={}, graphId={}, eventCount={}",
                traceId, graphId, request.getEvents().size());

        // Validate graph exists
        if (!graphStore.exists(graphId)) {
            log.warn("Graph not found for runtime events: {}", graphId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error(
                            "Graph not found: " + graphId,
                            "GRAPH_NOT_FOUND"
                    ));
        }

        // Create work item
        IngestionWorkItem.RuntimeEventWorkItem workItem = new IngestionWorkItem.RuntimeEventWorkItem(
                traceId,
                graphId,
                request,
                request.isTraceComplete()
        );

        // Try to enqueue
        boolean enqueued = ingestionQueue.enqueue(workItem, config.getTimeout().getEnqueueMs());

        if (!enqueued) {
            log.warn("Ingestion queue full, rejecting runtime events: traceId={}", traceId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(
                            "Ingestion queue is full, please retry later",
                            "QUEUE_FULL",
                            "Queue utilization: " + ingestionQueue.getUtilizationPercent() + "%"
                    ));
        }

        log.info("Runtime events accepted: traceId={}, graphId={}, eventCount={}",
                traceId, graphId, request.getEvents().size());
        return ResponseEntity.accepted()
                .body(ApiResponse.success(traceId));
    }

    /**
     * Ingests a batch of runtime events from flow-runtime-agent.
     * Events are grouped by traceId and enqueued as individual trace work items.
     *
     * @param request the agent batch request (may be GZIP-compressed, handled by GzipRequestFilter)
     * @return 202 Accepted, 404 if graph not found
     */
    @PostMapping("/batch")
    @Operation(
            summary = "Ingest agent event batch",
            description = "Accepts a batch of events from flow-runtime-agent. Events are grouped by traceId and processed."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Events accepted for processing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph not found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Ingestion queue full")
    })
    public ResponseEntity<ApiResponse<String>> ingestAgentBatch(
            @Valid @RequestBody AgentBatchIngestRequest request) {

        String graphId = request.getGraphId();
        log.debug("Received agent batch: graphId={}, eventCount={}", graphId, request.getBatch().size());

        // Validate graph exists
        if (!graphStore.exists(graphId)) {
            log.warn("Graph not found for agent batch: {}", graphId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Graph not found: " + graphId, "GRAPH_NOT_FOUND"));
        }

        // Group events by traceId
        Map<String, List<AgentBatchIngestRequest.AgentEventDto>> byTrace = request.getBatch().stream()
                .filter(e -> e.getTraceId() != null)
                .collect(Collectors.groupingBy(AgentBatchIngestRequest.AgentEventDto::getTraceId));

        int totalQueued = 0;
        for (Map.Entry<String, List<AgentBatchIngestRequest.AgentEventDto>> entry : byTrace.entrySet()) {
            String traceId = entry.getKey();
            List<AgentBatchIngestRequest.AgentEventDto> events = entry.getValue();

            RuntimeEventIngestRequest internalRequest = convertAgentBatch(graphId, traceId, events);
            boolean traceComplete = internalRequest.isTraceComplete();

            IngestionWorkItem.RuntimeEventWorkItem workItem = new IngestionWorkItem.RuntimeEventWorkItem(
                    traceId, graphId, internalRequest, traceComplete
            );

            boolean enqueued = ingestionQueue.enqueue(workItem, config.getTimeout().getEnqueueMs());
            if (enqueued) totalQueued++;
        }

        log.info("Agent batch processed: graphId={}, traces={}, queued={}", graphId, byTrace.size(), totalQueued);
        return ResponseEntity.accepted()
                .body(ApiResponse.success("Accepted " + totalQueued + " traces"));
    }

    private RuntimeEventIngestRequest convertAgentBatch(
            String graphId, String traceId, List<AgentBatchIngestRequest.AgentEventDto> agentEvents) {

        List<RuntimeEventIngestRequest.EventDto> events = agentEvents.stream()
                .map(ae -> RuntimeEventIngestRequest.EventDto.builder()
                        .eventId(ae.getSpanId() + "-" + ae.getType())
                        .type(ae.getType())
                        .timestamp(Instant.ofEpochMilli(ae.getTimestamp()))
                        .nodeId(ae.getNodeId())
                        .spanId(ae.getSpanId())
                        .parentSpanId(ae.getParentSpanId())
                        .durationMs(ae.getDurationMs())
                        .errorType(ae.getErrorType())
                        .attributes(ae.getData())
                        .build())
                .toList();

        boolean traceComplete = agentEvents.stream().anyMatch(ae ->
                "METHOD_EXIT".equalsIgnoreCase(ae.getType()) && (ae.getParentSpanId() == null || ae.getParentSpanId().isBlank())
        );

        return RuntimeEventIngestRequest.builder()
                .graphId(graphId)
                .traceId(traceId)
                .events(events)
                .traceComplete(traceComplete)
                .build();
    }
}

