package com.flow.core.service.engine;

import com.flow.core.flow.FlowExtractor;
import com.flow.core.flow.FlowModel;
import com.flow.core.flow.FlowStep;
import com.flow.core.graph.CoreEdge;
import com.flow.core.graph.CoreGraph;
import com.flow.core.graph.CoreNode;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Adapter for flow-engine's FlowExtractor.
 * Provides zoom-level slicing and extraction capabilities for graph visualization.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowExtractorAdapter {

    private final GraphStore graphStore;
    private final FlowExtractor flowExtractor;

    // ==================== Public API ====================

    /**
     * Extracts a zoomed view of a graph.
     */
    public Optional<Object> extractZoomedView(String graphId, int zoomLevel) {
        log.debug("Extracting zoomed view: graphId={}, zoom={}", graphId, zoomLevel);
        return graphStore.findById(graphId)
                .map(graph -> extractAtZoomLevel(graph, zoomLevel));
    }

    /**
     * Extracts a subgraph starting from a specific node.
     */
    public Optional<Object> extractSubgraph(String graphId, String startNodeId, int depth) {
        log.debug("Extracting subgraph: graphId={}, startNode={}, depth={}", graphId, startNodeId, depth);
        return graphStore.findById(graphId)
                .map(graph -> extractFromNode(graph, startNodeId));
    }

    /**
     * Extracts the flow path between two nodes.
     */
    public Optional<Object> extractPath(String graphId, String fromNodeId, String toNodeId) {
        log.debug("Extracting path: graphId={}, from={}, to={}", graphId, fromNodeId, toNodeId);
        return graphStore.findById(graphId)
                .map(graph -> extractPathBetween(graph, fromNodeId, toNodeId));
    }

    /**
     * Extracts all flows from a graph (from all ENDPOINT/TOPIC nodes).
     */
    public Optional<List<FlowModel>> extractAllFlows(String graphId) {
        log.debug("Extracting all flows for graph: {}", graphId);
        return graphStore.findById(graphId)
                .filter(CoreGraph.class::isInstance)
                .map(graph -> extractFlowsFromCoreGraph(graphId, (CoreGraph) graph));
    }

    // ==================== Zoom Level Extraction ====================

    private Object extractAtZoomLevel(Object graph, int zoomLevel) {
        return switch (graph) {
            case CoreGraph coreGraph -> buildZoomedView(coreGraph, zoomLevel);
            default -> returnAsIs(graph, "Graph is not a CoreGraph");
        };
    }

    private ZoomedGraphView buildZoomedView(CoreGraph coreGraph, int zoomLevel) {
        var filteredNodes = coreGraph.getNodesByZoomLevel(zoomLevel);
        var nodeIds = collectNodeIds(filteredNodes);
        var filteredEdges = filterEdgesByNodes(coreGraph, nodeIds);

        log.debug("Extracted {} nodes at zoom level {}", filteredNodes.size(), zoomLevel);

        return ZoomedGraphView.builder()
                .zoomLevel(zoomLevel)
                .nodes(toNodeViews(filteredNodes))
                .edges(toEdgeViews(filteredEdges))
                .build();
    }

    private Set<String> collectNodeIds(List<CoreNode> nodes) {
        return nodes.stream()
                .map(CoreNode::getId)
                .collect(Collectors.toSet());
    }

    private List<CoreEdge> filterEdgesByNodes(CoreGraph graph, Set<String> nodeIds) {
        return graph.getAllEdges().stream()
                .filter(edge -> isEdgeConnectedToNodes(edge, nodeIds))
                .toList();
    }

    private boolean isEdgeConnectedToNodes(CoreEdge edge, Set<String> nodeIds) {
        return nodeIds.contains(edge.getSourceId()) && nodeIds.contains(edge.getTargetId());
    }

    // ==================== Subgraph Extraction ====================

    private Object extractFromNode(Object graph, String startNodeId) {
        return switch (graph) {
            case CoreGraph coreGraph -> extractFlowFromStartNode(coreGraph, startNodeId);
            default -> returnAsIs(graph, "Graph is not a CoreGraph");
        };
    }

    private Object extractFlowFromStartNode(CoreGraph coreGraph, String startNodeId) {
        var nodeOpt = findNode(coreGraph, startNodeId);
        if (nodeOpt.isEmpty()) {
            return handleNodeNotFound(startNodeId, coreGraph);
        }
        return extractAndLogFlow(coreGraph, nodeOpt.get(), startNodeId);
    }

    private FlowModel extractAndLogFlow(CoreGraph coreGraph, CoreNode node, String nodeId) {
        var flow = flowExtractor.extractFlow(coreGraph, node);
        log.debug("Extracted flow from node {} with {} steps", nodeId, flow.getSteps().size());
        return flow;
    }

    // ==================== Path Extraction ====================

    private Object extractPathBetween(Object graph, String fromNodeId, String toNodeId) {
        return switch (graph) {
            case CoreGraph coreGraph -> extractPathFromGraph(coreGraph, fromNodeId, toNodeId);
            default -> returnAsIs(graph, "Graph is not a CoreGraph");
        };
    }

    private Object extractPathFromGraph(CoreGraph coreGraph, String fromNodeId, String toNodeId) {
        var nodeOpt = findNode(coreGraph, fromNodeId);
        if (nodeOpt.isEmpty()) {
            return handleNodeNotFound(fromNodeId, coreGraph);
        }
        return buildPathToTarget(coreGraph, nodeOpt.get(), fromNodeId, toNodeId);
    }

    private List<FlowStep> buildPathToTarget(CoreGraph coreGraph, CoreNode fromNode,
                                              String fromNodeId, String toNodeId) {
        var flow = flowExtractor.extractFlow(coreGraph, fromNode);
        var pathSteps = collectStepsUntilTarget(flow, toNodeId);
        appendTargetIfFound(flow, toNodeId, pathSteps);

        log.debug("Extracted path from {} to {} with {} steps", fromNodeId, toNodeId, pathSteps.size());
        return pathSteps;
    }

    private List<FlowStep> collectStepsUntilTarget(FlowModel flow, String targetNodeId) {
        return flow.getSteps().stream()
                .takeWhile(step -> !step.getNodeId().equals(targetNodeId))
                .collect(Collectors.toList());
    }

    private void appendTargetIfFound(FlowModel flow, String targetNodeId, List<FlowStep> pathSteps) {
        flow.getSteps().stream()
                .filter(step -> step.getNodeId().equals(targetNodeId))
                .findFirst()
                .ifPresent(pathSteps::add);
    }

    // ==================== All Flows Extraction ====================

    private List<FlowModel> extractFlowsFromCoreGraph(String graphId, CoreGraph coreGraph) {
        var flows = flowExtractor.extractFlows(coreGraph);
        log.info("Extracted {} flows from graph: {}", flows.size(), graphId);
        return flows;
    }

    // ==================== View Converters ====================

    private List<NodeView> toNodeViews(List<CoreNode> nodes) {
        return nodes.stream()
                .map(this::toNodeView)
                .toList();
    }

    private List<EdgeView> toEdgeViews(List<CoreEdge> edges) {
        return edges.stream()
                .map(this::toEdgeView)
                .toList();
    }

    private NodeView toNodeView(CoreNode node) {
        return NodeView.builder()
                .id(node.getId())
                .name(node.getName())
                .type(node.getType().name())
                .serviceId(node.getServiceId())
                .visibility(node.getVisibility().name())
                .zoomLevel(node.getZoomLevel())
                .metadata(node.getAllMetadata())
                .build();
    }

    private EdgeView toEdgeView(CoreEdge edge) {
        return EdgeView.builder()
                .id(edge.getId())
                .sourceId(edge.getSourceId())
                .targetId(edge.getTargetId())
                .type(edge.getType().name())
                .executionCount(edge.getExecutionCount())
                .build();
    }

    // ==================== Helper Methods ====================

    private Optional<CoreNode> findNode(CoreGraph coreGraph, String nodeId) {
        return Optional.ofNullable(coreGraph.getNode(nodeId));
    }

    private Object handleNodeNotFound(String nodeId, Object fallback) {
        log.warn("Node not found: {}", nodeId);
        return fallback;
    }

    private Object returnAsIs(Object graph, String reason) {
        log.debug("{}, returning as-is", reason);
        return graph;
    }

    // ==================== View Models ====================

    @Data
    @Builder
    public static class ZoomedGraphView {
        private int zoomLevel;
        private List<NodeView> nodes;
        private List<EdgeView> edges;
    }

    @Data
    @Builder
    public static class NodeView {
        private String id;
        private String name;
        private String type;
        private String serviceId;
        private String visibility;
        private int zoomLevel;
        private Map<String, Object> metadata;
    }

    @Data
    @Builder
    public static class EdgeView {
        private String id;
        private String sourceId;
        private String targetId;
        private String type;
        private long executionCount;
    }
}

