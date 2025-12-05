package com.flow.core.service.runtime;

import com.flow.core.service.api.dto.RuntimeEventIngestRequest;

/**
 * Interface for event deduplication.
 *
 * Eliminates duplicate events based on composite key or hash.
 */
public interface EventDeduplicator {

    /**
     * Checks if an event is a duplicate.
     *
     * @param traceId the trace ID
     * @param event the event to check
     * @return true if this is a duplicate event
     */
    boolean isDuplicate(String traceId, RuntimeEventIngestRequest.EventDto event);

    /**
     * Clears deduplication state for a trace.
     *
     * @param traceId the trace ID
     */
    void clearTrace(String traceId);

    /**
     * Clears all deduplication state.
     */
    void clearAll();

    /**
     * Gets the number of tracked events.
     *
     * @return count of tracked event keys
     */
    int size();
}

