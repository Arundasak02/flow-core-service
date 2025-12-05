package com.flow.core.service.api.controller;

import com.flow.core.service.api.dto.ApiResponse;
import com.flow.core.service.engine.GraphStore;
import com.flow.core.service.persistence.Neo4jWriter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Controller for graph export operations.
 *
 * Handles GET /export/neo4j/{graphId} for Neo4j exports.
 */
@Slf4j
@RestController
@RequestMapping("/export")
@Tag(name = "Graph Export", description = "Endpoints for exporting graphs to external systems")
@RequiredArgsConstructor
public class ExportController {

    private final GraphStore graphStore;
    private final Neo4jWriter neo4jWriter;

    /**
     * Exports a graph to Neo4j.
     *
     * @param graphId the graph to export
     * @param mode "cypher" to return Cypher statements, "push" to push directly to Neo4j
     */
    @GetMapping("/neo4j/{graphId}")
    @Operation(
            summary = "Export graph to Neo4j",
            description = "Generates Cypher statements or pushes the graph directly to Neo4j"
    )
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Cypher statements returned"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "202", description = "Push to Neo4j initiated"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph not found")
    })
    public ResponseEntity<ApiResponse<Object>> exportToNeo4j(
            @Parameter(description = "Graph ID") @PathVariable String graphId,
            @Parameter(description = "Export mode: 'cypher' or 'push'")
            @RequestParam(defaultValue = "cypher") String mode) {

        log.debug("Export request: graphId={}, mode={}", graphId, mode);

        // Validate graph exists
        if (!graphStore.exists(graphId)) {
            log.warn("Graph not found for export: {}", graphId);
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Graph not found", "NOT_FOUND"));
        }

        if ("push".equalsIgnoreCase(mode)) {
            return handlePushMode(graphId);
        } else {
            return handleCypherMode(graphId);
        }
    }

    private ResponseEntity<ApiResponse<Object>> handleCypherMode(String graphId) {
        log.debug("Generating Cypher for graph: {}", graphId);

        List<String> cypherStatements = neo4jWriter.generateCypher(graphId);

        if (cypherStatements.isEmpty()) {
            return ResponseEntity.ok(ApiResponse.success(
                    new CypherExportResponse(graphId, List.of(), "No statements generated")
            ));
        }

        log.info("Generated {} Cypher statements for graph: {}", cypherStatements.size(), graphId);
        return ResponseEntity.ok(ApiResponse.success(
                new CypherExportResponse(graphId, cypherStatements, null)
        ));
    }

    private ResponseEntity<ApiResponse<Object>> handlePushMode(String graphId) {
        log.debug("Initiating push to Neo4j for graph: {}", graphId);

        if (!neo4jWriter.isConnected()) {
            log.warn("Neo4j not connected, cannot push graph: {}", graphId);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(ApiResponse.error("Neo4j not connected", "NEO4J_UNAVAILABLE"));
        }

        // Trigger async push
        neo4jWriter.pushToNeo4j(graphId)
                .thenAccept(result -> {
                    if (result.success()) {
                        log.info("Neo4j push completed: {} (nodes={}, edges={})",
                                graphId, result.nodesExported(), result.edgesExported());
                    } else {
                        log.error("Neo4j push failed: {} - {}", graphId, result.errorMessage());
                    }
                });

        log.info("Neo4j push initiated for graph: {}", graphId);
        return ResponseEntity.accepted()
                .body(ApiResponse.success(
                        new PushExportResponse(graphId, "Export to Neo4j initiated")
                ));
    }

    /**
     * Response for Cypher export mode.
     */
    record CypherExportResponse(
            String graphId,
            List<String> cypherStatements,
            String message
    ) {}

    /**
     * Response for push export mode.
     */
    record PushExportResponse(
            String graphId,
            String message
    ) {}
}

