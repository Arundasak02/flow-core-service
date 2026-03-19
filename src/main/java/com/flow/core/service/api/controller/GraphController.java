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
import java.util.Set;

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

    @GetMapping("/graphs/{graphId}/runtime-summary")
    @Operation(summary = "Get runtime summary for graph",
            description = "Returns runtime KPI summary derived from trace buffer for the given graph")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Runtime summary found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph not found")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRuntimeSummary(
            @Parameter(description = "Graph ID") @PathVariable String graphId) {
        return buildRuntimeSummaryResponse(graphId);
    }

    @GetMapping("/graphs/{graphId}/runtime")
    @Operation(summary = "Get runtime summary (compat)",
            description = "Backward-compatible runtime KPI endpoint")
    @ApiResponses({
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Runtime summary found"),
            @io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Graph not found")
    })
    public ResponseEntity<ApiResponse<Map<String, Object>>> getRuntimeSummaryCompat(
            @Parameter(description = "Graph ID") @PathVariable String graphId) {
        return buildRuntimeSummaryResponse(graphId);
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

    private ResponseEntity<ApiResponse<Map<String, Object>>> buildRuntimeSummaryResponse(String graphId) {
        if (graphStore.findById(graphId).isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(ApiResponse.error("Graph not found", "NOT_FOUND"));
        }

        var traces = traceBuffer.getTracesForGraph(graphId);
        long totalEvents = traces.stream().mapToLong(trace -> trace.events().size()).sum();
        long totalErrors = traces.stream().mapToLong(trace -> trace.errors().size()).sum();

        var eventTimestamps = traces.stream()
                .flatMap(trace -> trace.events().stream())
                .mapToLong(RuntimeTraceBuffer.RuntimeEvent::timestampEpochMs)
                .toArray();

        long earliestEventMs = eventTimestamps.length == 0 ? 0 : java.util.Arrays.stream(eventTimestamps).min().orElse(0);
        long latestEventMs = eventTimestamps.length == 0 ? 0 : java.util.Arrays.stream(eventTimestamps).max().orElse(0);
        long observedWindowMs = latestEventMs > earliestEventMs ? (latestEventMs - earliestEventMs) : 0;
        long effectiveWindowMs = observedWindowMs > 0 ? observedWindowMs : 3_600_000L;

        long requestsPerHour = Math.round((double) totalEvents * 3_600_000d / (double) effectiveWindowMs);
        long errorsPerHour = Math.round((double) totalErrors * 3_600_000d / (double) effectiveWindowMs);

        long activeFlows = traces.stream()
                .filter(trace -> !trace.isComplete())
                .count();

        if (activeFlows == 0 && !traces.isEmpty()) {
            activeFlows = Math.min(16, traces.size());
        }

        long avgLatencyMs = Math.round(
                traces.stream()
                        .flatMap(trace -> trace.events().stream())
                        .map(RuntimeTraceBuffer.RuntimeEvent::durationMs)
                        .filter(duration -> duration != null && duration > 0)
                        .mapToLong(Long::longValue)
                        .average()
                        .orElse(0d)
        );

        Set<String> distinctTraceIds = traces.stream()
                .map(RuntimeTraceBuffer.RuntimeTrace::traceId)
                .collect(java.util.stream.Collectors.toSet());

        Map<String, Object> payload = new HashMap<>();
        payload.put("requestsPerHour", requestsPerHour);
        payload.put("errorsPerHour", errorsPerHour);
        payload.put("activeFlows", activeFlows);
        payload.put("avgLatencyMs", avgLatencyMs);
        payload.put("traceCount", distinctTraceIds.size());
        payload.put("updatedAt", Instant.now());

        return ResponseEntity.ok(ApiResponse.success(payload));
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
                .metadata(extractGraphMetadata(metadata))
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
                .metadata(extractGraphMetadata(metadata))
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

    private Map<String, Object> extractGraphMetadata(Optional<GraphMetadata> metadata) {
        if (metadata.isEmpty()) {
            return null;
        }
        GraphMetadata m = metadata.get();
        Map<String, Object> combined = new HashMap<>();
        if (m.metadata() != null) {
            combined.putAll(m.metadata());
        }
        putIfPresent(combined, "graphHash", m.graphHash());
        putIfPresent(combined, "buildTimestamp", m.buildTimestamp());
        putIfPresent(combined, "gitCommit", m.gitCommit());
        return combined.isEmpty() ? null : combined;
    }

    private void putIfPresent(Map<String, Object> map, String key, String value) {
        if (value != null && !value.isBlank()) {
            map.put(key, value);
        }
    }

    // ==================== Utility Methods ====================

    private int countNodes(StaticGraphIngestRequest request) {
        return request.getNodes() != null ? request.getNodes().size() : 0;
    }

    private int countEdges(StaticGraphIngestRequest request) {
        return request.getEdges() != null ? request.getEdges().size() : 0;
    }

    private String extractGraphId(CoreGraph coreGraph) {
        for (String graphId : graphStore.getAllGraphIds()) {
            Object graph = graphStore.findById(graphId).orElse(null);
            if (graph == coreGraph) {
                return graphId;
            }
        }
        return UNKNOWN_GRAPH_ID;
    }
}
