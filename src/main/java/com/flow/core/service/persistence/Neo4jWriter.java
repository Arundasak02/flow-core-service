package com.flow.core.service.persistence;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Interface for Neo4j export operations.
 *
 * Provides both Cypher generation and direct push capabilities.
 */
public interface Neo4jWriter {

    /**
     * Generates Cypher statements for a graph.
     *
     * @param graphId the graph to export
     * @return list of Cypher statements
     */
    List<String> generateCypher(String graphId);

    /**
     * Pushes a graph to Neo4j asynchronously.
     *
     * @param graphId the graph to export
     * @return future that completes when export is done
     */
    CompletableFuture<ExportResult> pushToNeo4j(String graphId);

    /**
     * Checks if Neo4j connection is available.
     *
     * @return true if connected
     */
    boolean isConnected();

    /**
     * Result of an export operation.
     */
    record ExportResult(
            String graphId,
            boolean success,
            int nodesExported,
            int edgesExported,
            long durationMs,
            String errorMessage
    ) {
        public static ExportResult success(String graphId, int nodes, int edges, long durationMs) {
            return new ExportResult(graphId, true, nodes, edges, durationMs, null);
        }

        public static ExportResult failure(String graphId, String error) {
            return new ExportResult(graphId, false, 0, 0, 0, error);
        }
    }
}

