package com.flow.core.service.config;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.Getter;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Metrics configuration for Flow Core Service.
 *
 * Provides custom metrics for ingestion, merge, and export operations.
 */
@Configuration
@Getter
public class MetricsConfig {

    private final MeterRegistry registry;

    // Counters
    private final Counter staticGraphsIngested;
    private final Counter runtimeEventsIngested;
    private final Counter mergesCompleted;
    private final Counter exportsCompleted;
    private final Counter deduplicatedEvents;

    // Timers
    private final Timer mergeTimer;
    private final Timer exportTimer;
    private final Timer ingestionTimer;

    public MetricsConfig(MeterRegistry registry) {
        this.registry = registry;

        // Initialize counters
        this.staticGraphsIngested = Counter.builder("flow.ingest.static.count")
                .description("Number of static graphs ingested")
                .register(registry);

        this.runtimeEventsIngested = Counter.builder("flow.ingest.runtime.count")
                .description("Number of runtime event batches ingested")
                .register(registry);

        this.mergesCompleted = Counter.builder("flow.merge.count")
                .description("Number of merge operations completed")
                .register(registry);

        this.exportsCompleted = Counter.builder("flow.export.count")
                .description("Number of export operations completed")
                .register(registry);

        this.deduplicatedEvents = Counter.builder("flow.dedup.count")
                .description("Number of events deduplicated")
                .register(registry);

        // Initialize timers
        this.mergeTimer = Timer.builder("flow.merge.duration")
                .description("Time taken for merge operations")
                .register(registry);

        this.exportTimer = Timer.builder("flow.export.duration")
                .description("Time taken for export operations")
                .register(registry);

        this.ingestionTimer = Timer.builder("flow.ingest.duration")
                .description("Time taken for ingestion processing")
                .register(registry);
    }

    /**
     * Registers a gauge for queue depth monitoring.
     *
     * @param name the metric name
     * @param description the metric description
     * @param sizeSupplier supplier for the current size
     */
    public void registerQueueGauge(String name, String description, Supplier<Number> sizeSupplier) {
        Gauge.builder(name, sizeSupplier)
                .description(description)
                .register(registry);
    }

    /**
     * Registers a gauge for store size monitoring.
     *
     * @param name the metric name
     * @param description the metric description
     * @param sizeSupplier supplier for the current size
     */
    public void registerStoreGauge(String name, String description, Supplier<Number> sizeSupplier) {
        Gauge.builder(name, sizeSupplier)
                .description(description)
                .register(registry);
    }
}

