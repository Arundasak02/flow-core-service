package com.flow.core.service.ingest;

import com.flow.core.service.config.IngestionConfig;
import com.flow.core.service.config.MetricsConfig;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Default implementation of IngestionQueue using a bounded BlockingQueue.
 *
 * This queue decouples HTTP endpoints from heavy processing,
 * providing backpressure when the system is overloaded.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefaultIngestionQueue implements IngestionQueue {

    private final IngestionConfig config;
    private final MetricsConfig metricsConfig;

    private BlockingQueue<IngestionWorkItem> queue;
    private int capacity;

    @PostConstruct
    void init() {
        this.capacity = config.getQueue().getCapacity();
        this.queue = new LinkedBlockingQueue<>(capacity);

        // Register metrics
        metricsConfig.registerQueueGauge(
                "flow.ingest.queue.size",
                "Current ingestion queue size",
                this::size
        );
        metricsConfig.registerQueueGauge(
                "flow.ingest.queue.utilization",
                "Ingestion queue utilization percentage",
                this::getUtilizationPercent
        );

        log.info("IngestionQueue initialized with capacity: {}", capacity);
    }

    @Override
    public boolean enqueue(IngestionWorkItem item, long timeoutMs) {
        try {
            boolean offered = queue.offer(item, timeoutMs, TimeUnit.MILLISECONDS);
            if (!offered) {
                log.warn("Queue full, rejecting work item: {}", item.getEntityId());
            } else {
                log.debug("Enqueued work item: {}", item.getEntityId());
            }
            return offered;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("Interrupted while enqueuing work item: {}", item.getEntityId(), e);
            return false;
        }
    }

    @Override
    public Optional<IngestionWorkItem> dequeue(long timeoutMs) {
        try {
            IngestionWorkItem item = queue.poll(timeoutMs, TimeUnit.MILLISECONDS);
            if (item != null) {
                log.debug("Dequeued work item: {}", item.getEntityId());
            }
            return Optional.ofNullable(item);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.debug("Interrupted while dequeuing work item");
            return Optional.empty();
        }
    }

    @Override
    public int size() {
        return queue.size();
    }

    @Override
    public int getCapacity() {
        return capacity;
    }

    @Override
    public void clear() {
        queue.clear();
        log.info("Queue cleared");
    }
}

