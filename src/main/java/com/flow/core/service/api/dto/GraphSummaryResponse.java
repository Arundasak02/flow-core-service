package com.flow.core.service.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

/**
 * DTO for graph summary responses.
 *
 * Provides a lightweight view of a graph for listing endpoints.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphSummaryResponse {

    /**
     * Unique graph identifier.
     */
    private String graphId;

    /**
     * Optional version.
     */
    private String version;

    /**
     * Number of nodes in the graph.
     */
    private int nodeCount;

    /**
     * Number of edges in the graph.
     */
    private int edgeCount;

    /**
     * When the graph was first created.
     */
    private Instant createdAt;

    /**
     * When the graph was last updated (including runtime merges).
     */
    private Instant lastUpdatedAt;

    /**
     * Whether runtime data has been merged into this graph.
     */
    private boolean hasRuntimeData;

    /**
     * Number of traces associated with this graph.
     */
    private int traceCount;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;
}

