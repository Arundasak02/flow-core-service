package com.flow.core.service.persistence;

import com.flow.core.export.GraphExporter;
import com.flow.core.graph.CoreEdge;
import com.flow.core.graph.CoreGraph;
import com.flow.core.graph.CoreNode;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.engine.GraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/**
 * Builds Cypher statements from in-memory graphs.
 * Converts CoreGraph (from flow-engine) to Neo4j Cypher statements.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CypherBuilder {

    private static final String STATEMENT_DELIMITER = ";";
    private static final String UNKNOWN_ID = "unknown";

    private final GraphStore graphStore;
    private final GraphExporter neo4jExporter;

    // ==================== Public API ====================

    /**
     * Builds Cypher statements for a graph.
     */
    public List<String> buildCypher(String graphId) {
        return graphStore.findById(graphId)
                .map(graph -> buildStatementsForGraph(graphId, graph))
                .orElseGet(() -> handleGraphNotFound(graphId));
    }

    /**
     * Builds Cypher using flow-engine directly for a CoreGraph.
     */
    public String buildCypherFromCoreGraph(CoreGraph coreGraph) {
        return neo4jExporter.export(coreGraph);
    }

    /**
     * Builds Cypher CREATE statements for nodes from a CoreGraph.
     */
    public List<String> buildNodeStatementsFromCoreGraph(String graphId, CoreGraph coreGraph) {
        return coreGraph.getAllNodes().stream()
                .map(node -> buildNodeCreateStatement(graphId, node))
                .toList();
    }

    /**
     * Builds Cypher CREATE statements for edges from a CoreGraph.
     */
    public List<String> buildEdgeStatementsFromCoreGraph(String graphId, CoreGraph coreGraph) {
        return coreGraph.getAllEdges().stream()
                .map(edge -> buildEdgeCreateStatement(graphId, edge))
                .toList();
    }

    // ==================== Statement Building ====================

    private List<String> buildStatementsForGraph(String graphId, Object graph) {
        var statements = new ArrayList<String>();
        statements.add(buildGraphMetadataStatement(graphId));

        return switch (graph) {
            case CoreGraph coreGraph -> buildCoreGraphStatements(graphId, coreGraph, statements);
            case StaticGraphIngestRequest request -> buildLegacyStatements(graphId, request, statements);
            default -> statements;
        };
    }

    private List<String> buildCoreGraphStatements(String graphId, CoreGraph coreGraph,
                                                   List<String> statements) {
        var cypherOutput = neo4jExporter.export(coreGraph);
        if (isValidOutput(cypherOutput)) {
            statements.addAll(splitIntoStatements(cypherOutput));
        }
        log.debug("Generated {} Cypher statements for CoreGraph: {}", statements.size(), graphId);
        return statements;
    }

    private List<String> buildLegacyStatements(String graphId, StaticGraphIngestRequest request,
                                                List<String> statements) {
        statements.addAll(buildLegacyNodeStatements(graphId, request));
        statements.addAll(buildLegacyEdgeStatements(graphId, request));
        log.debug("Generated {} Cypher statements for graph: {}", statements.size(), graphId);
        return statements;
    }

    private List<String> handleGraphNotFound(String graphId) {
        log.warn("Graph not found for Cypher generation: {}", graphId);
        return List.of();
    }

    // ==================== Graph Metadata ====================

    private String buildGraphMetadataStatement(String graphId) {
        return graphStore.getMetadata(graphId)
                .map(meta -> buildMetadataWithStats(graphId, meta))
                .orElseGet(() -> buildBasicMetadata(graphId));
    }

    private String buildBasicMetadata(String graphId) {
        return "MERGE (g:FlowGraph {graphId: '%s'}) SET g.updatedAt = timestamp()"
                .formatted(escape(graphId));
    }

    private String buildMetadataWithStats(String graphId, GraphStore.GraphMetadata meta) {
        return """
            MERGE (g:FlowGraph {graphId: '%s'}) \
            SET g.nodeCount = %d, g.edgeCount = %d, g.version = '%s', g.updatedAt = timestamp()"""
                .formatted(
                        escape(graphId),
                        meta.nodeCount(),
                        meta.edgeCount(),
                        escape(nullToEmpty(meta.version()))
                );
    }

    // ==================== CoreGraph Node/Edge Builders ====================

    private String buildNodeCreateStatement(String graphId, CoreNode node) {
        var props = new StringBuilder();
        appendNodeProperties(props, graphId, node);
        appendNodeMetadata(props, node);
        return "CREATE (n%s:FlowNode {%s})".formatted(sanitizeId(node.getId()), props);
    }

    private void appendNodeProperties(StringBuilder props, String graphId, CoreNode node) {
        props.append("id: '%s', ".formatted(escape(node.getId())));
        props.append("graphId: '%s', ".formatted(escape(graphId)));
        props.append("name: '%s', ".formatted(escape(node.getName())));
        props.append("type: '%s', ".formatted(node.getType().name()));
        props.append("serviceId: '%s', ".formatted(escape(node.getServiceId())));
        props.append("visibility: '%s', ".formatted(node.getVisibility().name()));
        props.append("zoomLevel: %d".formatted(node.getZoomLevel()));
    }

    private void appendNodeMetadata(StringBuilder props, CoreNode node) {
        for (var entry : node.getAllMetadata().entrySet()) {
            props.append(", ").append(escape(entry.getKey())).append(": ");
            props.append(formatPropertyValue(entry.getValue()));
        }
    }

    private String buildEdgeCreateStatement(String graphId, CoreEdge edge) {
        return """
            MATCH (s:FlowNode {id: '%s', graphId: '%s'}), \
            (t:FlowNode {id: '%s', graphId: '%s'}) \
            CREATE (s)-[e:%s {id: '%s', executionCount: %d}]->(t)"""
                .formatted(
                        escape(edge.getSourceId()), escape(graphId),
                        escape(edge.getTargetId()), escape(graphId),
                        edge.getType().name(),
                        escape(edge.getId()),
                        edge.getExecutionCount()
                );
    }

    // ==================== Legacy Format Support ====================

    private List<String> buildLegacyNodeStatements(String graphId, StaticGraphIngestRequest request) {
        if (request.getNodes() == null) return List.of();

        return request.getNodes().stream()
                .map(node -> buildLegacyNodeStatement(graphId, node))
                .toList();
    }

    private String buildLegacyNodeStatement(String graphId, StaticGraphIngestRequest.NodeDto node) {
        return """
            MERGE (n:FlowNode {nodeId: '%s', graphId: '%s'}) \
            SET n.type = '%s', n.name = '%s', n.label = '%s'"""
                .formatted(
                        escape(node.getNodeId()),
                        escape(graphId),
                        escape(nullToEmpty(node.getType())),
                        escape(nullToEmpty(node.getName())),
                        escape(nullToEmpty(node.getLabel()))
                );
    }

    private List<String> buildLegacyEdgeStatements(String graphId, StaticGraphIngestRequest request) {
        if (request.getEdges() == null) return List.of();

        return request.getEdges().stream()
                .map(edge -> buildLegacyEdgeStatement(graphId, edge))
                .toList();
    }

    private String buildLegacyEdgeStatement(String graphId, StaticGraphIngestRequest.EdgeDto edge) {
        return """
            MATCH (s:FlowNode {nodeId: '%s', graphId: '%s'}), \
            (t:FlowNode {nodeId: '%s', graphId: '%s'}) \
            MERGE (s)-[e:FLOW_EDGE {edgeId: '%s'}]->(t) \
            SET e.type = '%s', e.label = '%s'"""
                .formatted(
                        escape(edge.getSourceNodeId()), escape(graphId),
                        escape(edge.getTargetNodeId()), escape(graphId),
                        escape(edge.getEdgeId()),
                        escape(nullToEmpty(edge.getType())),
                        escape(nullToEmpty(edge.getLabel()))
                );
    }

    // ==================== Utility Methods ====================

    private boolean isValidOutput(String output) {
        return output != null && !output.isBlank();
    }

    private List<String> splitIntoStatements(String cypherOutput) {
        return Arrays.stream(cypherOutput.split(STATEMENT_DELIMITER))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .map(s -> s + STATEMENT_DELIMITER)
                .toList();
    }

    private String formatPropertyValue(Object value) {
        return value instanceof String
                ? "'%s'".formatted(escape(value.toString()))
                : String.valueOf(value);
    }

    private String sanitizeId(String id) {
        return id == null ? UNKNOWN_ID : id.replaceAll("[^a-zA-Z0-9_]", "_");
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("'", "\\'");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
