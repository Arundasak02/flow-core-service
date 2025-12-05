package com.flow.core.service.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;

/**
 * DTO for static graph ingestion requests.
 *
 * Represents a static graph with nodes, edges, and metadata.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StaticGraphIngestRequest {

    /**
     * Unique identifier for the graph.
     */
    @NotBlank(message = "graphId is required")
    private String graphId;

    /**
     * Optional version identifier.
     */
    private String version;

    /**
     * List of nodes in the graph.
     */
    @NotNull(message = "nodes are required")
    private List<NodeDto> nodes;

    /**
     * List of edges in the graph.
     */
    @NotNull(message = "edges are required")
    private List<EdgeDto> edges;

    /**
     * Additional metadata for the graph.
     */
    private Map<String, Object> metadata;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class NodeDto {

        @NotBlank(message = "nodeId is required")
        private String nodeId;

        private String type;
        private String name;
        private String label;
        private Map<String, Object> attributes;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class EdgeDto {

        @NotBlank(message = "edgeId is required")
        private String edgeId;

        @NotBlank(message = "sourceNodeId is required")
        private String sourceNodeId;

        @NotBlank(message = "targetNodeId is required")
        private String targetNodeId;

        private String type;
        private String label;
        private Map<String, Object> attributes;
    }
}

