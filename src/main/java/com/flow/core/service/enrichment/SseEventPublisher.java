package com.flow.core.service.enrichment;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * Manages active SSE connections and real-time push events.
 * Thread-safe — multiple clients can subscribe simultaneously.
 *
 * <p>Graph scoping: clients subscribe with a {@code graphId} to receive only events
 * for that graph. A {@code null} graphId is a wildcard — receives all events
 * (intended for admin/debug use only).
 */
public interface SseEventPublisher {

    /**
     * Subscribe to events scoped to a specific graph.
     * Pass {@code null} to receive events for all graphs (wildcard).
     */
    SseEmitter subscribe(String graphId);

    /** Fired for each node as AI enrichment completes it. */
    void publishEnrichmentProgress(String graphId, String nodeId, String oneLineSummary);

    /** Fired once when all nodes in a graph have been enriched. */
    void publishEnrichmentComplete(String graphId, int totalEnriched);

    /**
     * Fired when a runtime trace is fully assembled and ready for the UI to fetch.
     * The UI should call GET /trace/{traceId} upon receiving this event.
     */
    void publishTraceArrived(String graphId, String traceId);
}
