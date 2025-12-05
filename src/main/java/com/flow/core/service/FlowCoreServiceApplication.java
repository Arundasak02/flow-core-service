package com.flow.core.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Flow Core Service Application - Entry point for the Spring Boot application.
 *
 * This application manages the ingestion, merging, and querying of flow graphs
 * and runtime traces. It serves as the central service that:
 * - Receives static graphs from Flow Adapter (build time)
 * - Receives runtime events from Flow Runtime Plugin (execution time)
 * - Serves UI queries for graphs, traces, and exports
 *
 * @see <a href="FlowCoreService-ARCHITECTURE.md">Architecture Documentation</a>
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan("com.flow.core.service.config")
public class FlowCoreServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlowCoreServiceApplication.class, args);
    }
}

