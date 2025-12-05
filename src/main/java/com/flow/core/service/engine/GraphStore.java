package com.flow.core.service.engine;

import com.flow.core.service.api.dto.StaticGraphIngestRequest;

import java.util.Collection;
import java.util.Optional;

/**
 * Interface for the in-memory graph store.
 *
 * Primary source of truth for current graphs, both static and merged runtime.
 * Uses flow-engine's CoreGraph as the underlying model.
 */
public interface GraphStore {

    /**
     * Ingests a static graph from the request.
     *
     * @param graphId the graph identifier
     * @param request the ingestion request containing nodes and edges
     */
    void ingestStaticGraph(String graphId, StaticGraphIngestRequest request);

    /**
     * Retrieves a graph by ID.
     *
     * @param graphId the graph identifier
     * @return the graph if found
     */
    Optional<Object> findById(String graphId);

    /**
     * Retrieves all graphs.
     *
     * @return collection of all graphs
     */
    Collection<Object> findAll();

    /**
     * Checks if a graph exists.
     *
     * @param graphId the graph identifier
     * @return true if exists
     */
    boolean exists(String graphId);

    /**
     * Deletes a graph by ID.
     *
     * @param graphId the graph identifier
     * @return true if deleted, false if not found
     */
    boolean delete(String graphId);

    /**
     * Gets the count of stored graphs.
     *
     * @return number of graphs
     */
    int count();

    /**
     * Updates a graph after merge operations.
     *
     * @param graphId the graph identifier
     * @param mergedGraph the updated graph
     */
    void updateMergedGraph(String graphId, Object mergedGraph);

    /**
     * Gets metadata for a graph.
     *
     * @param graphId the graph identifier
     * @return graph metadata
     */
    Optional<GraphMetadata> getMetadata(String graphId);

    /**
     * Metadata for tracking graph state.
     */
    record GraphMetadata(
            String graphId,
            String version,
            int nodeCount,
            int edgeCount,
            long createdAtEpochMs,
            long lastUpdatedAtEpochMs,
            boolean hasRuntimeData,
            int traceCount
    ) {}
}

