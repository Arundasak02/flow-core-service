package com.flow.core.service.runtime;

import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.config.FlowConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Default implementation of EventDeduplicator.
 *
 * Uses a composite key of {traceId, spanId, eventType, timestamp}
 * to identify duplicate events.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultEventDeduplicator implements EventDeduplicator {

    private final FlowConfig flowConfig;

    // Map of traceId -> Set of event keys
    private final Map<String, Set<String>> seenEvents = new ConcurrentHashMap<>();

    @Override
    public boolean isDuplicate(String traceId, RuntimeEventIngestRequest.EventDto event) {
        // Skip deduplication if disabled
        if (!flowConfig.getFeatures().isDeduplicationEnabled()) {
            return false;
        }

        String key = generateKey(event);
        Set<String> traceEvents = seenEvents.computeIfAbsent(traceId, k -> ConcurrentHashMap.newKeySet());

        // If we've seen this key before, it's a duplicate
        boolean added = traceEvents.add(key);
        return !added;
    }

    @Override
    public void clearTrace(String traceId) {
        seenEvents.remove(traceId);
        log.debug("Cleared deduplication state for trace: {}", traceId);
    }

    @Override
    public void clearAll() {
        seenEvents.clear();
        log.info("Cleared all deduplication state");
    }

    @Override
    public int size() {
        return seenEvents.values().stream()
                .mapToInt(Set::size)
                .sum();
    }

    /**
     * Generates a composite key for an event.
     *
     * Uses eventId if present, otherwise creates a composite from
     * spanId + eventType + timestamp.
     */
    private String generateKey(RuntimeEventIngestRequest.EventDto event) {
        // Use eventId if provided
        if (event.getEventId() != null && !event.getEventId().isBlank()) {
            return event.getEventId();
        }

        // Otherwise use composite key
        return String.format("%s:%s:%s",
                Objects.toString(event.getSpanId(), ""),
                Objects.toString(event.getType(), ""),
                event.getTimestamp() != null ? event.getTimestamp().toEpochMilli() : ""
        );
    }
}

