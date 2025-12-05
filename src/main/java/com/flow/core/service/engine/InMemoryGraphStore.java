package com.flow.core.service.engine;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.core.FlowCoreEngine;
import com.flow.core.graph.CoreGraph;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.api.dto.StaticGraphIngestRequest.EdgeDto;
import com.flow.core.service.api.dto.StaticGraphIngestRequest.NodeDto;
import com.flow.core.service.config.MetricsConfig;
import com.flow.core.service.config.RetentionConfig;
import jakarta.annotation.PostConstruct;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory implementation of GraphStore.
 * Thread-safe graph storage with flow-engine integration.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryGraphStore implements GraphStore {

    private static final String DEFAULT_VERSION = "1";
    private static final String DEFAULT_NODE_TYPE = "METHOD";
    private static final String DEFAULT_EDGE_TYPE = "CALL";

    private final MetricsConfig metricsConfig;
    private final RetentionConfig retentionConfig;
    private final FlowCoreEngine flowCoreEngine;
    private final ObjectMapper objectMapper;

    private final Map<String, GraphEntry> graphs = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> traceCounters = new ConcurrentHashMap<>();

    // ==================== Lifecycle ====================

    @PostConstruct
    void init() {
        registerMetrics();
        logInitialization();
    }

    private void registerMetrics() {
        metricsConfig.registerStoreGauge(
                "flow.store.graphs.count",
                "Number of graphs in memory",
                this::count
        );
    }

    private void logInitialization() {
        log.info("InMemoryGraphStore initialized, max graphs: {}",
                retentionConfig.getGraph().getMaxCount());
    }

    // ==================== GraphStore Interface ====================

    @Override
    public void ingestStaticGraph(String graphId, StaticGraphIngestRequest request) {
        log.debug("Ingesting static graph: {}", graphId);

        var coreGraph = convertToFlowEngineGraph(request);
        var entry = createGraphEntry(graphId, request, coreGraph);

        storeGraph(graphId, entry);
        logIngestion(graphId, entry);
    }

    @Override
    public Optional<Object> findById(String graphId) {
        return Optional.ofNullable(graphs.get(graphId))
                .map(GraphEntry::graph);
    }

    @Override
    public Collection<Object> findAll() {
        return graphs.values().stream()
                .map(GraphEntry::graph)
                .toList();
    }

    @Override
    public boolean exists(String graphId) {
        return graphs.containsKey(graphId);
    }

    @Override
    public boolean delete(String graphId) {
        var removed = graphs.remove(graphId);
        traceCounters.remove(graphId);

        if (removed != null) {
            log.info("Graph deleted: {}", graphId);
            return true;
        }
        return false;
    }

    @Override
    public int count() {
        return graphs.size();
    }

    @Override
    public void updateMergedGraph(String graphId, Object mergedGraph) {
        var existing = graphs.get(graphId);
        if (existing == null) return;

        var updated = createUpdatedEntry(existing, mergedGraph);
        graphs.put(graphId, updated);
        log.debug("Graph updated after merge: {}", graphId);
    }

    @Override
    public Optional<GraphMetadata> getMetadata(String graphId) {
        return Optional.ofNullable(graphs.get(graphId))
                .map(entry -> buildMetadata(graphId, entry));
    }

    // ==================== Public Helpers ====================

    public void incrementTraceCount(String graphId) {
        traceCounters.computeIfAbsent(graphId, k -> new AtomicInteger(0))
                .incrementAndGet();
    }

    public Collection<GraphEntry> getAllEntries() {
        return graphs.values();
    }

    // ==================== Graph Conversion ====================

    private CoreGraph convertToFlowEngineGraph(StaticGraphIngestRequest request) {
        try {
            var flowJson = buildFlowJsonModel(request);
            var jsonString = serializeToJson(flowJson);
            return processWithFlowEngine(request.getGraphId(), jsonString);
        } catch (JsonProcessingException e) {
            throw handleConversionError(e);
        }
    }

    private FlowJsonModel buildFlowJsonModel(StaticGraphIngestRequest request) {
        return FlowJsonModel.builder()
                .version(resolveVersion(request))
                .graphId(request.getGraphId())
                .nodes(convertNodes(request.getNodes()))
                .edges(convertEdges(request.getEdges()))
                .build();
    }

    private List<FlowJsonModel.NodeModel> convertNodes(List<NodeDto> nodes) {
        return nodes.stream()
                .map(this::toNodeModel)
                .toList();
    }

    private FlowJsonModel.NodeModel toNodeModel(NodeDto node) {
        return FlowJsonModel.NodeModel.builder()
                .id(node.getNodeId())
                .type(resolveNodeType(node))
                .name(node.getName())
                .data(node.getAttributes())
                .build();
    }

    private List<FlowJsonModel.EdgeModel> convertEdges(List<EdgeDto> edges) {
        return edges.stream()
                .map(this::toEdgeModel)
                .toList();
    }

    private FlowJsonModel.EdgeModel toEdgeModel(EdgeDto edge) {
        return FlowJsonModel.EdgeModel.builder()
                .id(edge.getEdgeId())
                .from(edge.getSourceNodeId())
                .to(edge.getTargetNodeId())
                .type(resolveEdgeType(edge))
                .build();
    }

    private String serializeToJson(FlowJsonModel flowJson) throws JsonProcessingException {
        return objectMapper.writeValueAsString(flowJson);
    }

    private CoreGraph processWithFlowEngine(String graphId, String jsonString) {
        log.debug("Converting to CoreGraph: {}", graphId);
        var coreGraph = flowCoreEngine.process(jsonString);
        log.debug("Created CoreGraph with {} nodes and {} edges",
                coreGraph.getNodeCount(), coreGraph.getEdgeCount());
        return coreGraph;
    }

    // ==================== Entry Management ====================

    private GraphEntry createGraphEntry(String graphId, StaticGraphIngestRequest request,
                                        CoreGraph coreGraph) {
        var now = currentTimestamp();
        return new GraphEntry(
                graphId,
                request.getVersion(),
                coreGraph,
                coreGraph.getNodeCount(),
                coreGraph.getEdgeCount(),
                now,
                now,
                false
        );
    }

    private GraphEntry createUpdatedEntry(GraphEntry existing, Object mergedGraph) {
        var counts = extractCounts(existing, mergedGraph);
        return new GraphEntry(
                existing.graphId(),
                existing.version(),
                mergedGraph,
                counts.nodeCount(),
                counts.edgeCount(),
                existing.createdAtEpochMs(),
                currentTimestamp(),
                true
        );
    }

    private void storeGraph(String graphId, GraphEntry entry) {
        graphs.put(graphId, entry);
        traceCounters.putIfAbsent(graphId, new AtomicInteger(0));
    }

    private GraphMetadata buildMetadata(String graphId, GraphEntry entry) {
        return new GraphMetadata(
                entry.graphId(),
                entry.version(),
                entry.nodeCount(),
                entry.edgeCount(),
                entry.createdAtEpochMs(),
                entry.lastUpdatedAtEpochMs(),
                entry.hasRuntimeData(),
                getTraceCount(graphId)
        );
    }

    // ==================== Utility Methods ====================

    private String resolveVersion(StaticGraphIngestRequest request) {
        return request.getVersion() != null ? request.getVersion() : DEFAULT_VERSION;
    }

    private String resolveNodeType(NodeDto node) {
        return node.getType() != null ? node.getType() : DEFAULT_NODE_TYPE;
    }

    private String resolveEdgeType(EdgeDto edge) {
        return edge.getType() != null ? edge.getType() : DEFAULT_EDGE_TYPE;
    }

    private GraphCounts extractCounts(GraphEntry existing, Object mergedGraph) {
        return switch (mergedGraph) {
            case CoreGraph coreGraph -> new GraphCounts(
                    coreGraph.getNodeCount(),
                    coreGraph.getEdgeCount()
            );
            default -> new GraphCounts(existing.nodeCount(), existing.edgeCount());
        };
    }

    private int getTraceCount(String graphId) {
        return traceCounters.getOrDefault(graphId, new AtomicInteger(0)).get();
    }

    private long currentTimestamp() {
        return Instant.now().toEpochMilli();
    }

    private void logIngestion(String graphId, GraphEntry entry) {
        log.info("Static graph stored: {} (nodes={}, edges={})",
                graphId, entry.nodeCount(), entry.edgeCount());
    }

    private IllegalArgumentException handleConversionError(JsonProcessingException e) {
        log.error("Failed to convert request to JSON for flow-engine", e);
        return new IllegalArgumentException("Failed to process graph: " + e.getMessage(), e);
    }

    // ==================== Inner Types ====================

    public record GraphEntry(
            String graphId,
            String version,
            Object graph,
            int nodeCount,
            int edgeCount,
            long createdAtEpochMs,
            long lastUpdatedAtEpochMs,
            boolean hasRuntimeData
    ) {}

    private record GraphCounts(int nodeCount, int edgeCount) {}

    @Data
    @Builder
    private static class FlowJsonModel {
        private String version;
        private String graphId;
        private List<NodeModel> nodes;
        private List<EdgeModel> edges;

        @Data
        @Builder
        static class NodeModel {
            private String id;
            private String type;
            private String name;
            private Map<String, Object> data;
        }

        @Data
        @Builder
        static class EdgeModel {
            private String id;
            private String from;
            private String to;
            private String type;
        }
    }
}

