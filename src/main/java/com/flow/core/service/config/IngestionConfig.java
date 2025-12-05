package com.flow.core.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for the ingestion pipeline.
 *
 * Controls queue sizes, worker pools, backpressure thresholds, and timeouts.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "flow.ingest")
public class IngestionConfig {

    /**
     * Queue configuration.
     */
    private QueueConfig queue = new QueueConfig();

    /**
     * Worker configuration.
     */
    private WorkerConfig worker = new WorkerConfig();

    /**
     * Timeout settings.
     */
    private TimeoutConfig timeout = new TimeoutConfig();

    @Getter
    @Setter
    public static class QueueConfig {

        /**
         * Maximum queue capacity (default: 10,000).
         */
        private int capacity = 10000;

        /**
         * Queue utilization threshold for backpressure alerts (percentage).
         */
        private int backpressureThreshold = 80;
    }

    @Getter
    @Setter
    public static class WorkerConfig {

        /**
         * Worker polling interval in milliseconds.
         */
        private long intervalMs = 50;

        /**
         * Number of worker threads.
         */
        private int threadCount = 2;

        /**
         * Batch size for processing.
         */
        private int batchSize = 100;
    }

    @Getter
    @Setter
    public static class TimeoutConfig {

        /**
         * Enqueue timeout in milliseconds.
         */
        private long enqueueMs = 5000;

        /**
         * Poll timeout in milliseconds.
         */
        private long pollMs = 100;

        /**
         * Merge timeout in milliseconds.
         */
        private long mergeMs = 30000;
    }
}

