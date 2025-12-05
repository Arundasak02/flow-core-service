package com.flow.core.service.runtime;

import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.config.MetricsConfig;
import com.flow.core.service.config.RetentionConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of RuntimeTraceBuffer.
 *
 * Stores runtime traces with TTL-based eviction.
 * Thread-safe using ConcurrentHashMap.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class InMemoryRuntimeTraceBuffer implements RuntimeTraceBuffer {

    private final EventDeduplicator deduplicator;
    private final MetricsConfig metricsConfig;
    private final RetentionConfig retentionConfig;

    private final Map<String, MutableTrace> traces = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> graphToTraces = new ConcurrentHashMap<>();

    @PostConstruct
    void init() {
        metricsConfig.registerStoreGauge(
                "flow.store.traces.count",
                "Number of traces in memory",
                this::count
        );
        log.info("InMemoryRuntimeTraceBuffer initialized, TTL: {} minutes, max traces: {}",
                retentionConfig.getTrace().getTtlMinutes(),
                retentionConfig.getTrace().getMaxCount());
    }

    @Override
    public void addEvents(String traceId, String graphId, List<RuntimeEventIngestRequest.EventDto> events) {
        MutableTrace trace = traces.computeIfAbsent(traceId, id -> {
            // Track graph-to-trace mapping
            graphToTraces.computeIfAbsent(graphId, k -> ConcurrentHashMap.newKeySet()).add(traceId);
            return new MutableTrace(traceId, graphId);
        });

        for (RuntimeEventIngestRequest.EventDto eventDto : events) {
            // Deduplicate
            if (deduplicator.isDuplicate(traceId, eventDto)) {
                metricsConfig.getDeduplicatedEvents().increment();
                log.debug("Duplicate event skipped: traceId={}, eventId={}", traceId, eventDto.getEventId());
                continue;
            }

            // Convert and add
            RuntimeEvent event = convertEvent(eventDto);
            trace.addEvent(event);

            // Handle special event types
            handleSpecialEvent(trace, eventDto);
        }

        trace.updateLastModified();
        log.debug("Added {} events to trace: {}", events.size(), traceId);
    }

    @Override
    public Optional<RuntimeTrace> getTrace(String traceId) {
        return Optional.ofNullable(traces.get(traceId))
                .map(MutableTrace::toImmutable);
    }

    @Override
    public Collection<RuntimeTrace> getTracesForGraph(String graphId) {
        Set<String> traceIds = graphToTraces.get(graphId);
        if (traceIds == null) return Collections.emptyList();

        return traceIds.stream()
                .map(traces::get)
                .filter(Objects::nonNull)
                .map(MutableTrace::toImmutable)
                .collect(Collectors.toList());
    }

    @Override
    public Collection<RuntimeTrace> getPendingTracesForGraph(String graphId) {
        return getTracesForGraph(graphId).stream()
                .filter(t -> t.isComplete() && !t.isMerged())
                .collect(Collectors.toList());
    }

    @Override
    public void markComplete(String traceId) {
        MutableTrace trace = traces.get(traceId);
        if (trace != null) {
            trace.markComplete();
            log.debug("Trace marked complete: {}", traceId);
        }
    }

    @Override
    public void markMerged(String traceId) {
        MutableTrace trace = traces.get(traceId);
        if (trace != null) {
            trace.markMerged();
            log.debug("Trace marked merged: {}", traceId);
        }
    }

    @Override
    public boolean delete(String traceId) {
        MutableTrace removed = traces.remove(traceId);
        if (removed != null) {
            // Clean up graph mapping
            Set<String> traceIds = graphToTraces.get(removed.graphId);
            if (traceIds != null) {
                traceIds.remove(traceId);
            }
            deduplicator.clearTrace(traceId);
            log.debug("Trace deleted: {}", traceId);
            return true;
        }
        return false;
    }

    @Override
    public int deleteTracesForGraph(String graphId) {
        Set<String> traceIds = graphToTraces.remove(graphId);
        if (traceIds == null) return 0;

        int deleted = 0;
        for (String traceId : traceIds) {
            if (traces.remove(traceId) != null) {
                deduplicator.clearTrace(traceId);
                deleted++;
            }
        }
        log.info("Deleted {} traces for graph: {}", deleted, graphId);
        return deleted;
    }

    @Override
    public int count() {
        return traces.size();
    }

    @Override
    @Scheduled(fixedDelayString = "${flow.retention.trace.eviction-interval-ms:60000}")
    public int evictExpired() {
        long ttlMinutes = retentionConfig.getTrace().getTtlMinutes();
        if (ttlMinutes <= 0) return 0;

        long cutoffMs = Instant.now().toEpochMilli() - (ttlMinutes * 60 * 1000);
        List<String> toEvict = new ArrayList<>();

        for (Map.Entry<String, MutableTrace> entry : traces.entrySet()) {
            MutableTrace trace = entry.getValue();
            // Only evict completed traces that are past TTL
            if (trace.isComplete && trace.completedAtEpochMs != null
                    && trace.completedAtEpochMs < cutoffMs) {
                toEvict.add(entry.getKey());
            }
        }

        for (String traceId : toEvict) {
            delete(traceId);
        }

        if (!toEvict.isEmpty()) {
            log.info("Evicted {} expired traces", toEvict.size());
        }
        return toEvict.size();
    }

    // --- Private helpers ---

    private RuntimeEvent convertEvent(RuntimeEventIngestRequest.EventDto dto) {
        return new RuntimeEvent(
                dto.getEventId() != null ? dto.getEventId() : UUID.randomUUID().toString(),
                dto.getType(),
                dto.getTimestamp() != null ? dto.getTimestamp().toEpochMilli() : Instant.now().toEpochMilli(),
                dto.getNodeId(),
                dto.getEdgeId(),
                dto.getSpanId(),
                dto.getParentSpanId(),
                dto.getDurationMs(),
                dto.getAttributes()
        );
    }

    private void handleSpecialEvent(MutableTrace trace, RuntimeEventIngestRequest.EventDto dto) {
        String type = dto.getType();
        if (type == null) return;

        switch (type.toUpperCase()) {
            case "CHECKPOINT" -> {
                RuntimeCheckpoint checkpoint = new RuntimeCheckpoint(
                        UUID.randomUUID().toString(),
                        (String) dto.getAttributes().getOrDefault("name", "unnamed"),
                        dto.getTimestamp() != null ? dto.getTimestamp().toEpochMilli() : Instant.now().toEpochMilli(),
                        dto.getNodeId(),
                        dto.getAttributes()
                );
                trace.addCheckpoint(checkpoint);
            }
            case "ERROR" -> {
                RuntimeError error = new RuntimeError(
                        UUID.randomUUID().toString(),
                        dto.getErrorType(),
                        dto.getErrorMessage(),
                        (String) dto.getAttributes().getOrDefault("stackTrace", null),
                        dto.getTimestamp() != null ? dto.getTimestamp().toEpochMilli() : Instant.now().toEpochMilli(),
                        dto.getNodeId(),
                        dto.getSpanId()
                );
                trace.addError(error);
            }
            case "ASYNC_SEND", "ASYNC_RECEIVE" -> {
                // Handle async hops - would need correlation logic
                log.debug("Async event: type={}, correlationId={}", type, dto.getCorrelationId());
            }
        }
    }

    /**
     * Mutable trace for internal use.
     */
    private static class MutableTrace {
        final String traceId;
        final String graphId;
        final List<RuntimeEvent> events = Collections.synchronizedList(new ArrayList<>());
        final List<RuntimeCheckpoint> checkpoints = Collections.synchronizedList(new ArrayList<>());
        final List<RuntimeError> errors = Collections.synchronizedList(new ArrayList<>());
        final List<AsyncHop> asyncHops = Collections.synchronizedList(new ArrayList<>());
        final long createdAtEpochMs;
        Long completedAtEpochMs;
        long lastModifiedAtEpochMs;
        boolean isComplete = false;
        boolean isMerged = false;

        MutableTrace(String traceId, String graphId) {
            this.traceId = traceId;
            this.graphId = graphId;
            this.createdAtEpochMs = Instant.now().toEpochMilli();
            this.lastModifiedAtEpochMs = this.createdAtEpochMs;
        }

        void addEvent(RuntimeEvent event) {
            events.add(event);
        }

        void addCheckpoint(RuntimeCheckpoint checkpoint) {
            checkpoints.add(checkpoint);
        }

        void addError(RuntimeError error) {
            errors.add(error);
        }

        void markComplete() {
            this.isComplete = true;
            this.completedAtEpochMs = Instant.now().toEpochMilli();
        }

        void markMerged() {
            this.isMerged = true;
        }

        void updateLastModified() {
            this.lastModifiedAtEpochMs = Instant.now().toEpochMilli();
        }

        RuntimeTrace toImmutable() {
            return new RuntimeTrace(
                    traceId,
                    graphId,
                    new ArrayList<>(events),
                    new ArrayList<>(checkpoints),
                    new ArrayList<>(errors),
                    new ArrayList<>(asyncHops),
                    createdAtEpochMs,
                    completedAtEpochMs,
                    isComplete,
                    isMerged
            );
        }
    }
}

