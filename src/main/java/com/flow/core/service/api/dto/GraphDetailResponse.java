package com.flow.core.service.api.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * DTO for detailed graph responses.
 *
 * Provides the complete graph structure with nodes and edges.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GraphDetailResponse {

    /**
     * Unique graph identifier.
     */
    private String graphId;

    /**
     * Optional version.
     */
    private String version;

    /**
     * When the graph was first created.
     */
    private Instant createdAt;

    /**
     * When the graph was last updated.
     */
    private Instant lastUpdatedAt;

    /**
     * Whether runtime data has been merged.
     */
    private boolean hasRuntimeData;

    /**
     * All nodes in the graph.
     */
    private List<NodeResponse> nodes;

    /**
     * All edges in the graph.
     */
    private List<EdgeResponse> edges;

    /**
     * Additional metadata.
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class NodeResponse {
        private String nodeId;
        private String type;
        private String name;
        private String label;
        private Map<String, Object> attributes;

        // Runtime-merged data
        private Long totalExecutions;
        private Long totalDurationMs;
        private Long avgDurationMs;
        private Long errorCount;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class EdgeResponse {
        private String edgeId;
        private String sourceNodeId;
        private String targetNodeId;
        private String type;
        private String label;
        private Map<String, Object> attributes;

        // Runtime-merged data
        private Long totalTraversals;
        private Long totalDurationMs;
        private Long avgDurationMs;
    }
}

