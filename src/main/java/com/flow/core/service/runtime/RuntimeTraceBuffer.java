package com.flow.core.service.runtime;

import com.flow.core.service.api.dto.RuntimeEventIngestRequest;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Interface for the runtime trace buffer.
 *
 * Temporary in-memory store for runtime traces before and after merging.
 */
public interface RuntimeTraceBuffer {

    /**
     * Adds events to a trace, creating the trace if necessary.
     *
     * @param traceId the trace identifier
     * @param graphId the associated graph
     * @param events the events to add
     */
    void addEvents(String traceId, String graphId, List<RuntimeEventIngestRequest.EventDto> events);

    /**
     * Retrieves a trace by ID.
     *
     * @param traceId the trace identifier
     * @return the trace if found
     */
    Optional<RuntimeTrace> getTrace(String traceId);

    /**
     * Retrieves all traces for a graph.
     *
     * @param graphId the graph identifier
     * @return collection of traces
     */
    Collection<RuntimeTrace> getTracesForGraph(String graphId);

    /**
     * Retrieves pending (unmerged) traces for a graph.
     *
     * @param graphId the graph identifier
     * @return collection of pending traces
     */
    Collection<RuntimeTrace> getPendingTracesForGraph(String graphId);

    /**
     * Marks a trace as complete.
     *
     * @param traceId the trace identifier
     */
    void markComplete(String traceId);

    /**
     * Marks a trace as merged.
     *
     * @param traceId the trace identifier
     */
    void markMerged(String traceId);

    /**
     * Deletes a trace.
     *
     * @param traceId the trace identifier
     * @return true if deleted
     */
    boolean delete(String traceId);

    /**
     * Deletes all traces for a graph.
     *
     * @param graphId the graph identifier
     * @return number of traces deleted
     */
    int deleteTracesForGraph(String graphId);

    /**
     * Gets the count of stored traces.
     *
     * @return number of traces
     */
    int count();

    /**
     * Evicts expired traces based on TTL.
     *
     * @return number of traces evicted
     */
    int evictExpired();

    /**
     * Runtime trace record.
     */
    record RuntimeTrace(
            String traceId,
            String graphId,
            List<RuntimeEvent> events,
            List<RuntimeCheckpoint> checkpoints,
            List<RuntimeError> errors,
            List<AsyncHop> asyncHops,
            long createdAtEpochMs,
            Long completedAtEpochMs,
            boolean isComplete,
            boolean isMerged
    ) {
        public Long getDurationMs() {
            if (completedAtEpochMs == null) return null;
            return completedAtEpochMs - createdAtEpochMs;
        }
    }

    /**
     * Runtime event record.
     */
    record RuntimeEvent(
            String eventId,
            String type,
            long timestampEpochMs,
            String nodeId,
            String edgeId,
            String spanId,
            String parentSpanId,
            Long durationMs,
            java.util.Map<String, Object> attributes
    ) {}

    /**
     * Checkpoint record.
     */
    record RuntimeCheckpoint(
            String checkpointId,
            String name,
            long timestampEpochMs,
            String nodeId,
            java.util.Map<String, Object> data
    ) {}

    /**
     * Error record.
     */
    record RuntimeError(
            String errorId,
            String type,
            String message,
            String stackTrace,
            long timestampEpochMs,
            String nodeId,
            String spanId
    ) {}

    /**
     * Async hop record.
     */
    record AsyncHop(
            String hopId,
            String correlationId,
            String sourceNodeId,
            String targetNodeId,
            String type,
            Long sentAtEpochMs,
            Long receivedAtEpochMs
    ) {
        public Long getLatencyMs() {
            if (sentAtEpochMs == null || receivedAtEpochMs == null) return null;
            return receivedAtEpochMs - sentAtEpochMs;
        }
    }
}

