package com.flow.core.service.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Default SSE publisher. Clients subscribe via GET /sse/events.
 *
 * <p>Each subscriber is stored with its requested {@code graphId} scope.
 * Events are only delivered to subscribers whose scope matches the event's graphId,
 * or to wildcard subscribers (graphId=null).
 *
 * <p>Uses CopyOnWriteArrayList — safe for concurrent add/remove without locks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSseEventPublisher implements SseEventPublisher {

    private final ObjectMapper objectMapper;
    private final List<ScopedEmitter> emitters = new CopyOnWriteArrayList<>();

    /**
     * Pairs an SSE emitter with the graphId it subscribed for.
     * A null graphId means wildcard — receives all events.
     */
    private record ScopedEmitter(String graphId, SseEmitter emitter) {
        boolean matches(String eventGraphId) {
            return graphId == null || graphId.equals(eventGraphId);
        }
    }

    @Override
    public SseEmitter subscribe(String graphId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        ScopedEmitter scoped = new ScopedEmitter(graphId, emitter);
        emitters.add(scoped);
        emitter.onCompletion(() -> emitters.remove(scoped));
        emitter.onTimeout(() -> emitters.remove(scoped));
        emitter.onError(e -> emitters.remove(scoped));
        log.debug("SSE client subscribed graphId={}, total={}", graphId, emitters.size());
        return emitter;
    }

    @Override
    public void publishEnrichmentProgress(String graphId, String nodeId, String oneLineSummary) {
        publish("enrichment_progress", graphId, Map.of(
            "graphId", graphId,
            "nodeId", nodeId,
            "oneLineSummary", oneLineSummary != null ? oneLineSummary : ""
        ));
    }

    @Override
    public void publishEnrichmentComplete(String graphId, int totalEnriched) {
        publish("enrichment_complete", graphId, Map.of(
            "graphId", graphId,
            "totalEnriched", totalEnriched
        ));
    }

    @Override
    public void publishTraceArrived(String graphId, String traceId) {
        publish("trace_arrived", graphId, Map.of(
            "graphId", graphId,
            "traceId", traceId
        ));
    }

    private void publish(String eventType, String graphId, Object data) {
        if (emitters.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event type={}: {}", eventType, e.getMessage());
            return;
        }
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventType).data(json);
        for (ScopedEmitter scoped : emitters) {
            if (!scoped.matches(graphId)) continue;
            try {
                scoped.emitter().send(event);
            } catch (IOException e) {
                emitters.remove(scoped);
            }
        }
        log.debug("SSE event published type={} graphId={}", eventType, graphId);
    }
}
