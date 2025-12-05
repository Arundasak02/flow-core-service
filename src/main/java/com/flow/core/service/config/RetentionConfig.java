package com.flow.core.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for data retention and eviction.
 *
 * Controls TTLs for graphs and traces, eviction schedules, and capacity limits.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "flow.retention")
public class RetentionConfig {

    /**
     * Graph retention settings.
     */
    private GraphRetention graph = new GraphRetention();

    /**
     * Trace retention settings.
     */
    private TraceRetention trace = new TraceRetention();

    @Getter
    @Setter
    public static class GraphRetention {

        /**
         * TTL for graphs in minutes (0 = no automatic eviction).
         */
        private long ttlMinutes = 0;

        /**
         * Maximum number of graphs to keep in memory.
         */
        private int maxCount = 10000;

        /**
         * Eviction check interval in milliseconds.
         */
        private long evictionIntervalMs = 300000; // 5 minutes
    }

    @Getter
    @Setter
    public static class TraceRetention {

        /**
         * TTL for traces in minutes after completion (default: 10).
         */
        private long ttlMinutes = 10;

        /**
         * Maximum number of traces to keep in memory.
         */
        private int maxCount = 100000;

        /**
         * Eviction check interval in milliseconds.
         */
        private long evictionIntervalMs = 60000; // 1 minute
    }
}

