package com.flow.core.service.engine;

import com.flow.core.graph.CoreGraph;
import com.flow.core.ingest.MergeEngine;
import com.flow.core.runtime.EventType;
import com.flow.core.runtime.RuntimeEvent;
import com.flow.core.service.config.MetricsConfig;
import com.flow.core.service.runtime.RuntimeTraceBuffer;
import com.flow.core.service.runtime.RuntimeTraceBuffer.RuntimeTrace;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

/**
 * Adapter for flow-engine's MergeEngine.
 * Coordinates merging of runtime traces into static graphs.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MergeEngineAdapter {

    private final GraphStore graphStore;
    private final RuntimeTraceBuffer traceBuffer;
    private final MetricsConfig metricsConfig;
    private final MergeEngine mergeEngine;

    // ==================== Public API ====================

    /**
     * Merges a completed trace into its associated graph.
     */
    public void mergeTrace(String traceId, String graphId) {
        var sample = startTimer();
        try {
            executeMerge(traceId, graphId);
        } finally {
            stopTimer(sample);
        }
    }

    /**
     * Triggers batch merge for all pending traces of a graph.
     */
    public int mergePendingTraces(String graphId) {
        log.debug("Merging pending traces for graph: {}", graphId);

        var pendingTraces = traceBuffer.getPendingTracesForGraph(graphId);
        int mergedCount = mergeTracesSequentially(pendingTraces, graphId);

        log.info("Batch merge completed for graph {}: {} traces merged", graphId, mergedCount);
        return mergedCount;
    }

    // ==================== Merge Execution ====================

    private void executeMerge(String traceId, String graphId) {
        log.debug("Starting merge: traceId={}, graphId={}", traceId, graphId);

        var graph = findGraph(graphId);
        if (graph.isEmpty()) {
            logGraphNotFound(graphId);
            return;
        }

        var trace = findTrace(traceId);
        if (trace.isEmpty()) {
            logTraceNotFound(traceId);
            return;
        }

        performMergeAndUpdate(traceId, graphId, graph.get(), trace.get());
    }

    private void performMergeAndUpdate(String traceId, String graphId, Object graph, RuntimeTrace trace) {
        try {
            var mergedGraph = performMerge(graph, trace);
            updateGraphStore(graphId, mergedGraph);
            markTraceAsMerged(traceId);
            recordMergeSuccess(traceId, graphId);
        } catch (Exception e) {
            handleMergeError(traceId, graphId, e);
        }
    }

    private int mergeTracesSequentially(Iterable<RuntimeTrace> traces, String graphId) {
        int merged = 0;
        for (var trace : traces) {
            if (tryMergeTrace(trace, graphId)) {
                merged++;
            }
        }
        return merged;
    }

    private boolean tryMergeTrace(RuntimeTrace trace, String graphId) {
        try {
            mergeTrace(trace.traceId(), graphId);
            return true;
        } catch (Exception e) {
            log.error("Failed to merge trace: {}", trace.traceId(), e);
            return false;
        }
    }

    // ==================== Core Merge Logic ====================

    private Object performMerge(Object graph, Object trace) {
        return switch (graph) {
            case CoreGraph coreGraph when trace instanceof RuntimeTrace runtimeTrace ->
                    mergeWithFlowEngine(coreGraph, runtimeTrace);
            case CoreGraph coreGraph -> {
                log.warn("Trace is not a RuntimeTrace, cannot merge");
                yield graph;
            }
            default -> {
                log.warn("Graph is not a CoreGraph, cannot merge runtime data");
                yield graph;
            }
        };
    }

    private CoreGraph mergeWithFlowEngine(CoreGraph coreGraph, RuntimeTrace serviceTrace) {
        var flowEngineEvents = convertToFlowEngineEvents(serviceTrace);
        log.debug("Merging {} runtime events into graph", flowEngineEvents.size());

        var mergedGraph = mergeEngine.mergeStaticAndRuntime(coreGraph, flowEngineEvents);
        logMergeResult(mergedGraph);

        return mergedGraph;
    }

    // ==================== Event Conversion ====================

    private List<RuntimeEvent> convertToFlowEngineEvents(RuntimeTrace trace) {
        return trace.events().stream()
                .map(this::convertToFlowEngineEvent)
                .toList();
    }

    private RuntimeEvent convertToFlowEngineEvent(RuntimeTraceBuffer.RuntimeEvent event) {
        return new RuntimeEvent(
                resolveEventId(event),
                event.timestampEpochMs(),
                mapEventType(event.type()),
                event.nodeId(),
                event.spanId(),
                event.parentSpanId(),
                event.attributes()
        );
    }

    private String resolveEventId(RuntimeTraceBuffer.RuntimeEvent event) {
        return event.spanId() != null ? event.spanId() : event.eventId();
    }

    private EventType mapEventType(String type) {
        if (type == null) return EventType.CHECKPOINT;

        return switch (type.toUpperCase()) {
            case "START", "METHOD_ENTER" -> EventType.METHOD_ENTER;
            case "END", "METHOD_EXIT" -> EventType.METHOD_EXIT;
            case "ASYNC_SEND", "PRODUCE_TOPIC" -> EventType.PRODUCE_TOPIC;
            case "ASYNC_RECEIVE", "CONSUME_TOPIC" -> EventType.CONSUME_TOPIC;
            case "ERROR" -> EventType.ERROR;
            default -> EventType.CHECKPOINT;
        };
    }

    // ==================== Data Access ====================

    private Optional<Object> findGraph(String graphId) {
        return graphStore.findById(graphId);
    }

    private Optional<RuntimeTrace> findTrace(String traceId) {
        return traceBuffer.getTrace(traceId);
    }

    private void updateGraphStore(String graphId, Object mergedGraph) {
        graphStore.updateMergedGraph(graphId, mergedGraph);
    }

    private void markTraceAsMerged(String traceId) {
        traceBuffer.markMerged(traceId);
    }

    // ==================== Metrics & Logging ====================

    private Timer.Sample startTimer() {
        return Timer.start(metricsConfig.getRegistry());
    }

    private void stopTimer(Timer.Sample sample) {
        sample.stop(metricsConfig.getMergeTimer());
    }

    private void recordMergeSuccess(String traceId, String graphId) {
        metricsConfig.getMergesCompleted().increment();
        log.info("Merge completed: traceId={}, graphId={}", traceId, graphId);
    }

    private void logGraphNotFound(String graphId) {
        log.warn("Cannot merge - graph not found: {}", graphId);
    }

    private void logTraceNotFound(String traceId) {
        log.warn("Cannot merge - trace not found: {}", traceId);
    }

    private void logMergeResult(CoreGraph mergedGraph) {
        log.debug("Merge produced graph with {} nodes and {} edges",
                mergedGraph.getNodeCount(), mergedGraph.getEdgeCount());
    }

    private void handleMergeError(String traceId, String graphId, Exception e) {
        log.error("Merge failed: traceId={}, graphId={}", traceId, graphId, e);
        throw new MergeException("Merge failed: " + e.getMessage(), e);
    }

    // ==================== Exception ====================

    public static class MergeException extends RuntimeException {
        public MergeException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
