package com.flow.core.service.ingest;

import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.config.MetricsConfig;
import com.flow.core.service.engine.GraphStore;
import com.flow.core.service.engine.MergeEngineAdapter;
import com.flow.core.service.runtime.RuntimeTraceBuffer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * Handler for runtime event ingestion work items.
 *
 * Writes incoming events into RuntimeTraceBuffer and triggers
 * merge operations when appropriate.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeEventHandler implements IngestionHandler<IngestionWorkItem.RuntimeEventWorkItem> {

    private final RuntimeTraceBuffer traceBuffer;
    private final GraphStore graphStore;
    private final MergeEngineAdapter mergeEngineAdapter;
    private final MetricsConfig metricsConfig;

    @Override
    public void handle(IngestionWorkItem.RuntimeEventWorkItem workItem) {
        String traceId = workItem.traceId();
        String graphId = workItem.graphId();
        Object payload = workItem.payload();
        boolean traceComplete = workItem.traceComplete();

        try {
            log.debug("Processing runtime events: traceId={}, graphId={}", traceId, graphId);

            // Validate graph exists
            if (!graphStore.exists(graphId)) {
                throw new IngestionException(
                        "Graph not found: " + graphId,
                        traceId,
                        "GRAPH_NOT_FOUND"
                );
            }

            // Convert and process events
            RuntimeEventIngestRequest request = convertPayload(payload);
            traceBuffer.addEvents(traceId, graphId, request.getEvents());

            metricsConfig.getRuntimeEventsIngested().increment();

            // Trigger merge if trace is complete
            if (traceComplete) {
                log.debug("Trace complete, marking and triggering merge: {}", traceId);
                traceBuffer.markComplete(traceId);
                triggerMergeAsync(traceId, graphId);
            }

            log.debug("Runtime events processed: traceId={}, eventCount={}",
                    traceId, request.getEvents().size());

        } catch (IngestionException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to process runtime events: traceId={}", traceId, e);
            throw new IngestionException(
                    "Failed to process runtime events: " + e.getMessage(),
                    traceId,
                    "RUNTIME_EVENT_PROCESSING_FAILED",
                    e
            );
        }
    }

    @Override
    public Class<IngestionWorkItem.RuntimeEventWorkItem> getSupportedType() {
        return IngestionWorkItem.RuntimeEventWorkItem.class;
    }

    /**
     * Triggers merge asynchronously to not block ingestion.
     */
    @Async("mergeExecutor")
    public void triggerMergeAsync(String traceId, String graphId) {
        try {
            mergeEngineAdapter.mergeTrace(traceId, graphId);
        } catch (Exception e) {
            log.error("Async merge failed: traceId={}, graphId={}", traceId, graphId, e);
        }
    }

    private RuntimeEventIngestRequest convertPayload(Object payload) {
        if (payload instanceof RuntimeEventIngestRequest request) {
            return request;
        }
        throw new IngestionException("Invalid payload type: expected RuntimeEventIngestRequest");
    }
}

