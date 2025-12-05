package com.flow.core.service.api.health;

import com.flow.core.service.config.IngestionConfig;
import com.flow.core.service.ingest.IngestionQueue;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

/**
 * Health indicator for the ingestion queue.
 *
 * Reports queue depth and utilization for monitoring.
 */
@Component
@RequiredArgsConstructor
public class IngestionQueueHealthIndicator implements HealthIndicator {

    private final IngestionQueue queue;
    private final IngestionConfig config;

    @Override
    public Health health() {
        int utilization = queue.getUtilizationPercent();
        int threshold = config.getQueue().getBackpressureThreshold();

        Health.Builder builder = utilization >= threshold
                ? Health.down()
                : Health.up();

        return builder
                .withDetail("queueSize", queue.size())
                .withDetail("queueCapacity", queue.getCapacity())
                .withDetail("utilizationPercent", utilization)
                .withDetail("backpressureThreshold", threshold)
                .withDetail("isFull", queue.isFull())
                .build();
    }
}

