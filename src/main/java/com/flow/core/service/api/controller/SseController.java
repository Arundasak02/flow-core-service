package com.flow.core.service.api.controller;

import com.flow.core.service.enrichment.SseEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE endpoint for real-time push events.
 *
 * Clients subscribe to GET /sse/events?graphId={id} and receive:
 *   enrichment_progress  { graphId, nodeId, oneLineSummary }
 *   enrichment_complete  { graphId, totalEnriched }
 *   trace_arrived        { graphId, traceId }
 *
 * The graphId parameter scopes events to a single graph.
 * Omitting it (null) subscribes to all graphs — intended for admin/debug only.
 */
@RestController
@RequestMapping("/sse")
@RequiredArgsConstructor
public class SseController {

    private final SseEventPublisher ssePublisher;

    @GetMapping(value = "/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(
        summary = "Subscribe to real-time graph events",
        description = "Opens an SSE stream. Provide graphId to scope events to one graph. " +
                      "Omit graphId to receive all events (wildcard — admin use only).")
    public SseEmitter subscribe(
            @Parameter(description = "Graph ID to scope events. Null = wildcard.")
            @RequestParam(required = false) String graphId) {
        return ssePublisher.subscribe(graphId);
    }
}
