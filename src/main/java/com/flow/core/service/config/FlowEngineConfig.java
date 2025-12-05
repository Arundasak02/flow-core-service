package com.flow.core.service.config;

import com.flow.core.FlowCoreEngine;
import com.flow.core.export.ExporterFactory;
import com.flow.core.export.GraphExporter;
import com.flow.core.flow.FlowExtractor;
import com.flow.core.graph.GraphValidator;
import com.flow.core.ingest.MergeEngine;
import com.flow.core.ingest.StaticGraphLoader;
import com.flow.core.zoom.ZoomEngine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configuration for flow-engine library beans.
 *
 * Creates and configures all flow-engine components as Spring beans
 * for injection into service adapters.
 */
@Slf4j
@Configuration
public class FlowEngineConfig {

    /**
     * Main flow-engine orchestrator.
     * Handles the complete processing pipeline: load → zoom → validate → extract.
     */
    @Bean
    public FlowCoreEngine flowCoreEngine() {
        log.info("Initializing FlowCoreEngine");
        return new FlowCoreEngine();
    }

    /**
     * Static graph loader.
     * Parses flow.json format into CoreGraph.
     */
    @Bean
    public StaticGraphLoader staticGraphLoader() {
        log.info("Initializing StaticGraphLoader");
        return new StaticGraphLoader();
    }

    /**
     * Zoom level assignment engine.
     * Assigns zoom levels (1-5) to nodes based on type.
     */
    @Bean
    public ZoomEngine zoomEngine() {
        log.info("Initializing ZoomEngine");
        return new ZoomEngine();
    }

    /**
     * Graph structure validator.
     * Validates node/edge integrity and zoom level assignments.
     */
    @Bean
    public GraphValidator graphValidator() {
        log.info("Initializing GraphValidator (strict mode)");
        return new GraphValidator(true);
    }

    /**
     * Flow extractor.
     * Uses BFS traversal to extract flows from endpoint/topic nodes.
     */
    @Bean
    public FlowExtractor flowExtractor() {
        log.info("Initializing FlowExtractor");
        return new FlowExtractor();
    }

    /**
     * Merge engine.
     * Merges runtime events into static graphs.
     */
    @Bean
    public MergeEngine mergeEngine() {
        log.info("Initializing MergeEngine");
        return new MergeEngine();
    }

    /**
     * Neo4j graph exporter.
     * Converts CoreGraph to Cypher statements.
     */
    @Bean
    public GraphExporter neo4jExporter() {
        log.info("Initializing Neo4jExporter");
        return ExporterFactory.getExporter(ExporterFactory.Format.NEO4J);
    }
}

