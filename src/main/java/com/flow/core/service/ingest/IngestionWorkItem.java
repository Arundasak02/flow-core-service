package com.flow.core.service.ingest;

import java.time.Instant;

/**
 * Sealed interface for ingestion work items.
 *
 * Uses the sealed interface pattern to define a closed hierarchy
 * of work item types that can be processed by the ingestion pipeline.
 */
public sealed interface IngestionWorkItem permits
        IngestionWorkItem.StaticGraphWorkItem,
        IngestionWorkItem.RuntimeEventWorkItem {

    /**
     * Gets the entity ID for this work item.
     */
    String getEntityId();

    /**
     * Gets the timestamp when this item was created.
     */
    Instant getCreatedAt();

    /**
     * Work item for static graph ingestion.
     */
    record StaticGraphWorkItem(
            String graphId,
            Object payload,
            Instant createdAt
    ) implements IngestionWorkItem {

        public StaticGraphWorkItem(String graphId, Object payload) {
            this(graphId, payload, Instant.now());
        }

        @Override
        public String getEntityId() {
            return graphId;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }
    }

    /**
     * Work item for runtime event ingestion.
     */
    record RuntimeEventWorkItem(
            String traceId,
            String graphId,
            Object payload,
            boolean traceComplete,
            Instant createdAt
    ) implements IngestionWorkItem {

        public RuntimeEventWorkItem(String traceId, String graphId, Object payload, boolean traceComplete) {
            this(traceId, graphId, payload, traceComplete, Instant.now());
        }

        @Override
        public String getEntityId() {
            return traceId;
        }

        @Override
        public Instant getCreatedAt() {
            return createdAt;
        }
    }
}

