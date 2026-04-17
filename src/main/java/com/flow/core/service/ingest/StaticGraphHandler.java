package com.flow.core.service.ingest;

import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.config.MetricsConfig;
import com.flow.core.service.engine.MergeEngineAdapter;
import com.flow.core.service.engine.GraphStore;
import com.flow.core.service.enrichment.AIEnrichmentService;
import com.flow.core.service.enrichment.EnrichmentStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Handler for static graph ingestion work items.
 *
 * Applies incoming static graphs to the in-memory GraphStore.
 * Uses the flow-engine library to create CoreGraph instances.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StaticGraphHandler implements IngestionHandler<IngestionWorkItem.StaticGraphWorkItem> {

    private final GraphStore graphStore;
    private final MergeEngineAdapter mergeEngineAdapter;
    private final MetricsConfig metricsConfig;
    private final EnrichmentStore enrichmentStore;
    private final ObjectMapper objectMapper;

    @Autowired(required = false)
    private AIEnrichmentService enrichmentService;

    @Override
    public void handle(IngestionWorkItem.StaticGraphWorkItem workItem) {
        String graphId = workItem.graphId();
        Object payload = workItem.payload();

        try {
            log.debug("Processing static graph ingestion: {}", graphId);

            // Convert payload to StaticGraphIngestRequest if needed
            StaticGraphIngestRequest request = convertPayload(payload);

            // Delegate to GraphStore which will use flow-engine
            graphStore.ingestStaticGraph(graphId, request);

            // Once the static graph exists, merge any already-ingested completed traces.
            mergeEngineAdapter.mergePendingTraces(graphId);

            // Trigger async AI enrichment — only enriches new/changed nodes (diff-gated)
            if (enrichmentService != null) {
                String gitCommit = resolveCommitIdentifier(request);
                String gitBranch = extractGitBranch(request);
                enrichmentService.enrichAsync(request, gitCommit, gitBranch);
            }

            // Persist snapshot to SQLite so graph survives FCS restarts
            try {
                String json = objectMapper.writeValueAsString(request);
                enrichmentStore.saveGraphSnapshot(graphId, json);
            } catch (Exception e) {
                log.warn("[snapshot] Failed to persist graph={}: {}", graphId, e.getMessage());
            }

            metricsConfig.getStaticGraphsIngested().increment();
            log.info("Static graph ingested successfully: {}", graphId);

        } catch (Exception e) {
            log.error("Failed to ingest static graph: {}", graphId, e);
            throw new IngestionException(
                    "Failed to ingest static graph: " + e.getMessage(),
                    graphId,
                    "STATIC_GRAPH_INGESTION_FAILED",
                    e
            );
        }
    }

    @Override
    public Class<IngestionWorkItem.StaticGraphWorkItem> getSupportedType() {
        return IngestionWorkItem.StaticGraphWorkItem.class;
    }

    private StaticGraphIngestRequest convertPayload(Object payload) {
        if (payload instanceof StaticGraphIngestRequest request) {
            return request;
        }
        throw new IngestionException("Invalid payload type: expected StaticGraphIngestRequest");
    }

    private String extractGitBranch(StaticGraphIngestRequest request) {
        if (request.getMetadata() == null) return null;
        Object branch = request.getMetadata().get("gitBranch");
        return branch != null ? branch.toString() : null;
    }

    /**
     * Resolves a stable commit-like identifier for diff-gating enrichment.
     * Prefers gitCommit; falls back to graphHash from metadata; then uses buildTimestamp;
     * finally defaults to "unknown" so the NOT NULL constraint is always satisfied.
     */
    private String resolveCommitIdentifier(StaticGraphIngestRequest request) {
        if (request.getGitCommit() != null && !request.getGitCommit().isBlank()) {
            return request.getGitCommit();
        }
        if (request.getMetadata() != null) {
            Object hash = request.getMetadata().get("graphHash");
            if (hash != null && !hash.toString().isBlank()) return hash.toString();
            Object ts = request.getMetadata().get("buildTimestamp");
            if (ts != null && !ts.toString().isBlank()) return ts.toString();
        }
        return "unknown";
    }
}

