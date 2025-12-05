package com.flow.core.service.ingest;

import java.util.Optional;

/**
 * Interface for the ingestion queue.
 *
 * Provides a bounded queue abstraction that decouples HTTP endpoints
 * from heavy processing operations.
 */
public interface IngestionQueue {

    /**
     * Attempts to enqueue a work item with timeout.
     *
     * @param item the work item to enqueue
     * @param timeoutMs timeout in milliseconds
     * @return true if successfully enqueued, false if timeout/full
     */
    boolean enqueue(IngestionWorkItem item, long timeoutMs);

    /**
     * Attempts to dequeue a work item with timeout.
     *
     * @param timeoutMs timeout in milliseconds
     * @return the work item if available, empty otherwise
     */
    Optional<IngestionWorkItem> dequeue(long timeoutMs);

    /**
     * Gets the current queue size.
     *
     * @return number of items in the queue
     */
    int size();

    /**
     * Gets the queue capacity.
     *
     * @return maximum capacity
     */
    int getCapacity();

    /**
     * Gets the queue utilization as a percentage.
     *
     * @return utilization percentage (0-100)
     */
    default int getUtilizationPercent() {
        int capacity = getCapacity();
        return capacity > 0 ? (size() * 100) / capacity : 0;
    }

    /**
     * Checks if the queue is at capacity.
     *
     * @return true if full
     */
    default boolean isFull() {
        return size() >= getCapacity();
    }

    /**
     * Clears all items from the queue.
     */
    void clear();
}

