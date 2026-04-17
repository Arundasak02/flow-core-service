package com.flow.core.service.api.controller;

import com.flow.core.service.enrichment.SseEventPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint for real-time enrichment progress events.
 *
 * Clients subscribe to GET /sse/events and receive:
 *   enrichment_progress  { graphId, nodeId, oneLineSummary }
 *   enrichment_complete  { graphId, totalEnriched }
 *
 * The UI uses this to fill in business labels as they are generated,
 * without polling. ENDPOINT-first ordering means the most visible
 * nodes are labeled first.
 */
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEventPublisher ssePublisher;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe() {
        return ssePublisher.subscribe();
    }
}
