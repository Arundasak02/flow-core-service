package com.flow.core.service.api.controller;

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
}

