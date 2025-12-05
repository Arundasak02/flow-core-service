package com.flow.core.service.ingest;

import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.config.MetricsConfig;
import com.flow.core.service.engine.GraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
    private final MetricsConfig metricsConfig;

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
}

