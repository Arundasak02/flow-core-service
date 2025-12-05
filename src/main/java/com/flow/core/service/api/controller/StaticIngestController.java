package com.flow.core.service.api.controller;

import com.flow.core.service.api.dto.ApiResponse;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
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

/**
 * Controller for static graph ingestion.
 *
 * Handles POST /ingest/static for ingesting static graphs from Flow Adapter.
 */
@Slf4j
@RestController
@RequestMapping("/ingest/static")
@Tag(name = "Static Graph Ingestion", description = "Endpoints for ingesting static graph definitions")
@RequiredArgsConstructor
public class StaticIngestController {

    private final IngestionQueue ingestionQueue;
    private final IngestionConfig config;

    /**
     * Ingests a static graph.
     *
     * @param request the graph ingestion request
     * @return 202 Accepted if queued, 429 if queue full, 400 if invalid
     */
    @PostMapping
    @Operation(
            summary = "Ingest a static graph",
            description = "Submits a static graph for processing. The graph will be stored in the in-memory GraphStore."
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Graph accepted for processing"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "429", description = "Ingestion queue full")
    })
    public ResponseEntity<ApiResponse<String>> ingestStaticGraph(
            @Valid @RequestBody StaticGraphIngestRequest request) {

        String graphId = request.getGraphId();
        log.debug("Received static graph ingestion request: {}", graphId);

        // Create work item
        IngestionWorkItem.StaticGraphWorkItem workItem =
                new IngestionWorkItem.StaticGraphWorkItem(graphId, request);

        // Try to enqueue
        boolean enqueued = ingestionQueue.enqueue(workItem, config.getTimeout().getEnqueueMs());

        if (!enqueued) {
            log.warn("Ingestion queue full, rejecting static graph: {}", graphId);
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body(ApiResponse.error(
                            "Ingestion queue is full, please retry later",
                            "QUEUE_FULL",
                            "Queue utilization: " + ingestionQueue.getUtilizationPercent() + "%"
                    ));
        }

        log.info("Static graph accepted for ingestion: {}", graphId);
        return ResponseEntity.accepted()
                .body(ApiResponse.success(graphId));
    }
}

