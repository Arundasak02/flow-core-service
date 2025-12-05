package com.flow.core.service.api.controller;

import com.flow.core.graph.CoreEdge;
import com.flow.core.graph.CoreGraph;
import com.flow.core.graph.CoreNode;
import com.flow.core.service.api.dto.ApiResponse;
import com.flow.core.service.api.dto.GraphDetailResponse;
import com.flow.core.service.api.dto.GraphDetailResponse.EdgeResponse;
import com.flow.core.service.api.dto.GraphDetailResponse.NodeResponse;
import com.flow.core.service.api.dto.GraphSummaryResponse;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.engine.FlowExtractorAdapter;
import com.flow.core.service.engine.GraphStore;
import com.flow.core.service.engine.GraphStore.GraphMetadata;
import com.flow.core.service.engine.InMemoryGraphStore;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Controller for graph query and management.
 * Handles graph listing, retrieval, zoomed views, and deletion.
 */
@Slf4j
@RestController
@Tag(name = "Graph Management", description = "Endpoints for querying and managing graphs")
@RequiredArgsConstructor
public class GraphController {

    private static final String UNKNOWN_GRAPH_ID = "unknown";

    private final GraphStore graphStore;
    private final FlowExtractorAdapter flowExtractor;
    private final RuntimeTraceBuffer traceBuffer;

    // ==================== Endpoints ====================

    @GetMapping("/graphs")
    @Operation(summary = "List all graphs", description = "Returns summaries of all graphs in the store")
    public ResponseEntity<ApiResponse<List<GraphSummaryResponse>>> getAllGraphs() {
        log.debug("Listing all graphs");

        var summaries = graphStore.findAll().stream()
                .map(this::toSummary)
                .toList();

        log.info("Returning {} graph summaries", summaries.size());
        return ResponseEntity.ok(ApiResponse.success(summaries));
    }

    @GetMapping("/graphs/{graphId}")
    @Operation(summary = "Get graph by ID", description = "Returns the complete graph with nodes and edges")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Graph found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph not found")
    })
    public ResponseEntity<ApiResponse<GraphDetailResponse>> getGraphById(
            @Parameter(description = "Graph ID") @PathVariable String graphId) {
        log.debug("Getting graph: {}", graphId);
        return graphStore.findById(graphId)
                .map(graph -> successResponse(graphId, graph))
                .orElseGet(() -> notFoundResponse(graphId));
    }

    @GetMapping("/flow/{graphId}")
    @Operation(summary = "Get zoomed graph view",
               description = "Returns a zoom-level sliced view of the graph for visualization")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Zoomed view found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph not found")
    })
    public ResponseEntity<ApiResponse<Object>> getZoomedFlow(
            @Parameter(description = "Graph ID") @PathVariable String graphId,
            @Parameter(description = "Zoom level (0 = highest level summary)")
            @RequestParam(defaultValue = "0") int zoom) {
        log.debug("Getting zoomed view: graphId={}, zoom={}", graphId, zoom);
        return flowExtractor.extractZoomedView(graphId, zoom)
                .map(this::successZoomResponse)
                .orElseGet(() -> notFoundZoomResponse(graphId));
    }

    @DeleteMapping("/graphs/{graphId}")
    @Operation(summary = "Delete graph", description = "Deletes a graph and all associated traces")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "204", description = "Graph deleted")
    })
    public ResponseEntity<Void> deleteGraph(
            @Parameter(description = "Graph ID") @PathVariable String graphId) {
        log.debug("Deleting graph: {}", graphId);

        int tracesDeleted = traceBuffer.deleteTracesForGraph(graphId);
        graphStore.delete(graphId);

        log.info("Deleted graph {} and {} traces", graphId, tracesDeleted);
        return ResponseEntity.noContent().build();
    }

    // ==================== Response Builders ====================

    private ResponseEntity<ApiResponse<GraphDetailResponse>> successResponse(String graphId, Object graph) {
        log.debug("Found graph: {}", graphId);
        return ResponseEntity.ok(ApiResponse.success(toDetail(graphId, graph)));
    }

    private ResponseEntity<ApiResponse<GraphDetailResponse>> notFoundResponse(String graphId) {
        log.warn("Graph not found: {}", graphId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Graph not found", "NOT_FOUND"));
    }

    private ResponseEntity<ApiResponse<Object>> successZoomResponse(Object view) {
        return ResponseEntity.ok(ApiResponse.success(view));
    }

    private ResponseEntity<ApiResponse<Object>> notFoundZoomResponse(String graphId) {
        log.warn("Graph not found for zoom: {}", graphId);
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error("Graph not found", "NOT_FOUND"));
    }

    // ==================== Summary Conversion ====================

    private GraphSummaryResponse toSummary(Object graph) {
        return switch (graph) {
            case CoreGraph coreGraph -> buildCoreGraphSummary(coreGraph);
            case StaticGraphIngestRequest request -> buildLegacySummary(request);
            default -> GraphSummaryResponse.builder().build();
        };
    }

    private GraphSummaryResponse buildCoreGraphSummary(CoreGraph coreGraph) {
        String graphId = extractGraphId(coreGraph);
        var metadata = graphStore.getMetadata(graphId);

        return GraphSummaryResponse.builder()
                .graphId(graphId)
                .version(coreGraph.getVersion())
                .nodeCount(coreGraph.getNodeCount())
                .edgeCount(coreGraph.getEdgeCount())
                .createdAt(extractCreatedAt(metadata))
                .lastUpdatedAt(extractLastUpdatedAt(metadata))
                .hasRuntimeData(extractHasRuntimeData(metadata))
                .traceCount(extractTraceCount(metadata))
                .build();
    }

    private GraphSummaryResponse buildLegacySummary(StaticGraphIngestRequest request) {
        var metadata = graphStore.getMetadata(request.getGraphId());

        return GraphSummaryResponse.builder()
                .graphId(request.getGraphId())
                .version(request.getVersion())
                .nodeCount(countNodes(request))
                .edgeCount(countEdges(request))
                .createdAt(extractCreatedAt(metadata))
                .lastUpdatedAt(extractLastUpdatedAt(metadata))
                .hasRuntimeData(extractHasRuntimeData(metadata))
                .traceCount(extractTraceCount(metadata))
                .metadata(request.getMetadata())
                .build();
    }

    // ==================== Detail Conversion ====================

    private GraphDetailResponse toDetail(String graphId, Object graph) {
        return switch (graph) {
            case CoreGraph coreGraph -> buildCoreGraphDetail(graphId, coreGraph);
            case StaticGraphIngestRequest request -> buildLegacyDetail(graphId, request);
            default -> GraphDetailResponse.builder().graphId(graphId).build();
        };
    }

    private GraphDetailResponse buildCoreGraphDetail(String graphId, CoreGraph coreGraph) {
        var metadata = graphStore.getMetadata(graphId);

        return GraphDetailResponse.builder()
                .graphId(graphId)
                .version(coreGraph.getVersion())
                .createdAt(extractCreatedAt(metadata))
                .lastUpdatedAt(extractLastUpdatedAt(metadata))
                .hasRuntimeData(extractHasRuntimeData(metadata))
                .nodes(convertCoreNodes(coreGraph))
                .edges(convertCoreEdges(coreGraph))
                .build();
    }

    private GraphDetailResponse buildLegacyDetail(String graphId, StaticGraphIngestRequest request) {
        var metadata = graphStore.getMetadata(graphId);

        return GraphDetailResponse.builder()
                .graphId(graphId)
                .version(request.getVersion())
                .createdAt(extractCreatedAt(metadata))
                .lastUpdatedAt(extractLastUpdatedAt(metadata))
                .hasRuntimeData(extractHasRuntimeData(metadata))
                .nodes(convertLegacyNodes(request))
                .edges(convertLegacyEdges(request))
                .metadata(request.getMetadata())
                .build();
    }

    // ==================== Node/Edge Converters ====================

    private List<NodeResponse> convertCoreNodes(CoreGraph coreGraph) {
        return coreGraph.getAllNodes().stream()
                .map(this::toNodeResponse)
                .toList();
    }

    private List<EdgeResponse> convertCoreEdges(CoreGraph coreGraph) {
        return coreGraph.getAllEdges().stream()
                .map(this::toEdgeResponse)
                .toList();
    }

    private NodeResponse toNodeResponse(CoreNode node) {
        return NodeResponse.builder()
                .nodeId(node.getId())
                .type(node.getType().name())
                .name(node.getName())
                .label(node.getName())
                .attributes(buildNodeAttributes(node))
                .build();
    }

    private EdgeResponse toEdgeResponse(CoreEdge edge) {
        return EdgeResponse.builder()
                .edgeId(edge.getId())
                .sourceNodeId(edge.getSourceId())
                .targetNodeId(edge.getTargetId())
                .type(edge.getType().name())
                .label(edge.getType().name())
                .attributes(Map.of("executionCount", edge.getExecutionCount()))
                .build();
    }

    private Map<String, Object> buildNodeAttributes(CoreNode node) {
        var attributes = new HashMap<>(node.getAllMetadata());
        attributes.put("serviceId", node.getServiceId());
        attributes.put("visibility", node.getVisibility().name());
        attributes.put("zoomLevel", node.getZoomLevel());
        return attributes;
    }

    // ==================== Legacy Converters ====================

    private List<NodeResponse> convertLegacyNodes(StaticGraphIngestRequest request) {
        if (request.getNodes() == null) return List.of();

        return request.getNodes().stream()
                .map(n -> NodeResponse.builder()
                        .nodeId(n.getNodeId())
                        .type(n.getType())
                        .name(n.getName())
                        .label(n.getLabel())
                        .attributes(n.getAttributes())
                        .build())
                .toList();
    }

    private List<EdgeResponse> convertLegacyEdges(StaticGraphIngestRequest request) {
        if (request.getEdges() == null) return List.of();

        return request.getEdges().stream()
                .map(e -> EdgeResponse.builder()
                        .edgeId(e.getEdgeId())
                        .sourceNodeId(e.getSourceNodeId())
                        .targetNodeId(e.getTargetNodeId())
                        .type(e.getType())
                        .label(e.getLabel())
                        .attributes(e.getAttributes())
                        .build())
                .toList();
    }

    // ==================== Metadata Extractors ====================

    private Instant extractCreatedAt(Optional<GraphMetadata> metadata) {
        return metadata.map(m -> Instant.ofEpochMilli(m.createdAtEpochMs())).orElse(null);
    }

    private Instant extractLastUpdatedAt(Optional<GraphMetadata> metadata) {
        return metadata.map(m -> Instant.ofEpochMilli(m.lastUpdatedAtEpochMs())).orElse(null);
    }

    private boolean extractHasRuntimeData(Optional<GraphMetadata> metadata) {
        return metadata.map(GraphMetadata::hasRuntimeData).orElse(false);
    }

    private int extractTraceCount(Optional<GraphMetadata> metadata) {
        return metadata.map(GraphMetadata::traceCount).orElse(0);
    }

    // ==================== Utility Methods ====================

    private int countNodes(StaticGraphIngestRequest request) {
        return request.getNodes() != null ? request.getNodes().size() : 0;
    }

    private int countEdges(StaticGraphIngestRequest request) {
        return request.getEdges() != null ? request.getEdges().size() : 0;
    }

    private String extractGraphId(CoreGraph coreGraph) {
        return ((InMemoryGraphStore) graphStore).getAllEntries().stream()
                .filter(entry -> entry.graph() == coreGraph)
                .findFirst()
                .map(InMemoryGraphStore.GraphEntry::graphId)
                .orElse(UNKNOWN_GRAPH_ID);
    }
}
