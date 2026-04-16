package com.flow.core.service.api.controller;

import com.flow.core.service.api.dto.AgentBatchIngestRequest;
import com.flow.core.service.api.dto.ApiResponse;
import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.config.IngestionConfig;
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

        // Separate synthetic TRACE_COMPLETE signals from real trace events.
        // TRACE_COMPLETE is emitted by EntryPointAdvice.onExit() to trigger immediate merge.
        boolean traceCompleteSignal = agentEvents.stream()
                .anyMatch(ae -> "TRACE_COMPLETE".equalsIgnoreCase(ae.getType()));

        List<AgentBatchIngestRequest.AgentEventDto> realEvents = agentEvents.stream()
                .filter(ae -> !"TRACE_COMPLETE".equalsIgnoreCase(ae.getType()))
                .toList();

        List<RuntimeEventIngestRequest.EventDto> events = realEvents.stream()
                .map(ae -> {
                    // Deduplication uses eventId when present; for CHECKPOINT events we can receive a null spanId
                    // (e.g., when the controller entrypoint isn't instrumented), so we must include more
                    // entropy to avoid collapsing multiple checkpoints into the same eventId.
                    String spanId = ae.getSpanId();
                    String type = ae.getType();
                    String nodeId = ae.getNodeId();
                    long timestamp = ae.getTimestamp();
                    Map<String, Object> attributes = ae.getData();

                    // For checkpoint events, we also include the *attribute key names* (not values) to avoid
                    // dropping multiple checkpoints emitted within the same millisecond.
                    // Example: {"ownerId": 12} vs {"ownerCity": "Springfield"}.
                    boolean isCheckpoint = type != null && "CHECKPOINT".equalsIgnoreCase(type);
                    String checkpointKeyEntropy = "";
                    if (isCheckpoint && attributes != null && !attributes.isEmpty()) {
                        checkpointKeyEntropy = new java.util.TreeSet<>(attributes.keySet()).toString();
                    }

                    String eventId = (spanId != null && !spanId.isBlank())
                            ? spanId + "-" + type + "-" + timestamp + "-" + nodeId + "-" + checkpointKeyEntropy
                            : "NO_SPAN-" + type + "-" + timestamp + "-" + nodeId + "-" + checkpointKeyEntropy;

                    return RuntimeEventIngestRequest.EventDto.builder()
                            .eventId(eventId)
                            .type(type)
                            .timestamp(Instant.ofEpochMilli(timestamp))
                            .nodeId(nodeId)
                            .spanId(spanId)
                            .parentSpanId(ae.getParentSpanId())
                            .durationMs(ae.getDurationMs())
                            .errorType(ae.getErrorType())
                            .attributes(ae.getData())
                            .build();
                })
                .toList();

        // traceComplete is true if an explicit TRACE_COMPLETE signal was received, OR if a
        // root METHOD_EXIT (no parentSpanId) is present — whichever fires first.
        boolean traceComplete = traceCompleteSignal || realEvents.stream().anyMatch(ae ->
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

