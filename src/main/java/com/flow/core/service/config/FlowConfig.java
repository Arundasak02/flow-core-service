package com.flow.core.service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Overall application configuration for Flow Core Service.
 *
 * Contains toggles, feature flags, and tenant model hooks.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "flow")
public class FlowConfig {

    /**
     * Enable or disable the entire flow service.
     */
    private boolean enabled = true;

    /**
     * Feature flags for optional capabilities.
     */
    private Features features = new Features();

    /**
     * Tenant configuration for multi-tenancy support.
     */
    private TenantConfig tenant = new TenantConfig();

    @Getter
    @Setter
    public static class Features {

        /**
         * Enable Neo4j export functionality.
         */
        private boolean neo4jExportEnabled = false;

        /**
         * Enable async merge pipeline.
         */
        private boolean asyncMergeEnabled = true;

        /**
         * Enable trace deduplication.
         */
        private boolean deduplicationEnabled = true;

        /**
         * Enable metrics collection.
         */
        private boolean metricsEnabled = true;

        /**
         * Enable Java 21 virtual threads for I/O-bound operations.
         * When enabled, ingestion workers, merge operations, and exports
         * use lightweight virtual threads instead of platform threads.
         */
        private boolean virtualThreadsEnabled = true;
    }

    @Getter
    @Setter
    public static class TenantConfig {

        /**
         * Enable multi-tenant mode.
         */
        private boolean enabled = false;

        /**
         * Header name for tenant identification.
         */
        private String headerName = "X-Tenant-Id";

        /**
         * Default tenant ID when not specified.
         */
        private String defaultTenantId = "default";
    }
}

