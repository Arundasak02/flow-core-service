package com.flow.core.service.enrichment;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * Manages active SSE connections for enrichment progress events.
 * Thread-safe — multiple clients can subscribe simultaneously.
 */
public interface SseEventPublisher {
    SseEmitter subscribe();
    void publishEnrichmentProgress(String graphId, String nodeId, String oneLineSummary);
    void publishEnrichmentComplete(String graphId, int totalEnriched);
}
