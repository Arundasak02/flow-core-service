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
 * Uses CopyOnWriteArrayList — safe for concurrent add/remove without locks.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultSseEventPublisher implements SseEventPublisher {

    private final ObjectMapper objectMapper;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    @Override
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.add(emitter);
        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> emitters.remove(emitter));
        emitter.onError(e -> emitters.remove(emitter));
        log.debug("SSE client subscribed, total={}", emitters.size());
        return emitter;
    }

    @Override
    public void publishEnrichmentProgress(String graphId, String nodeId, String oneLineSummary) {
        publish("enrichment_progress", Map.of(
            "graphId", graphId,
            "nodeId", nodeId,
            "oneLineSummary", oneLineSummary != null ? oneLineSummary : ""
        ));
    }

    @Override
    public void publishEnrichmentComplete(String graphId, int totalEnriched) {
        publish("enrichment_complete", Map.of(
            "graphId", graphId,
            "totalEnriched", totalEnriched
        ));
    }

    private void publish(String eventType, Object data) {
        if (emitters.isEmpty()) return;
        String json;
        try {
            json = objectMapper.writeValueAsString(data);
        } catch (Exception e) {
            log.warn("Failed to serialize SSE event: {}", e.getMessage());
            return;
        }
        SseEmitter.SseEventBuilder event = SseEmitter.event().name(eventType).data(json);
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(event);
            } catch (IOException e) {
                emitters.remove(emitter);
            }
        }
    }
}
