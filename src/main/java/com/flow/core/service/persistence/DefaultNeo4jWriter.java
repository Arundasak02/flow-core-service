package com.flow.core.service.persistence;

import com.flow.core.service.config.FlowConfig;
import com.flow.core.service.config.MetricsConfig;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.neo4j.driver.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Default implementation of Neo4jWriter.
 * Provides graph export functionality to Neo4j database.
 */
@Slf4j
@Component
public class DefaultNeo4jWriter implements Neo4jWriter {

    private static final String NODE_MARKER = ":FlowNode";
    private static final String EDGE_MARKER = ":FLOW_EDGE";

    private final CypherBuilder cypherBuilder;
    private final FlowConfig flowConfig;
    private final MetricsConfig metricsConfig;

    @Value("${flow.neo4j.uri:bolt://localhost:7687}")
    private String neo4jUri;

    @Value("${flow.neo4j.username:neo4j}")
    private String neo4jUsername;

    @Value("${flow.neo4j.password:password}")
    private String neo4jPassword;

    private Driver driver;
    private boolean connected = false;

    public DefaultNeo4jWriter(CypherBuilder cypherBuilder,
                              FlowConfig flowConfig,
                              MetricsConfig metricsConfig) {
        this.cypherBuilder = cypherBuilder;
        this.flowConfig = flowConfig;
        this.metricsConfig = metricsConfig;
    }

    @PostConstruct
    void init() {
        if (isNeo4jEnabled()) {
            initializeDriver();
        } else {
            log.info("Neo4j export is disabled");
        }
    }

    @PreDestroy
    void cleanup() {
        closeDriver();
    }

    @Override
    public List<String> generateCypher(String graphId) {
        return cypherBuilder.buildCypher(graphId);
    }

    @Override
    @Async("exportExecutor")
    public CompletableFuture<ExportResult> pushToNeo4j(String graphId) {
        var sample = startTimer();

        try {
            return executePush(graphId);
        } finally {
            stopTimer(sample);
        }
    }

    @Override
    public boolean isConnected() {
        return connected;
    }

    // ==================== Private Methods ====================

    private boolean isNeo4jEnabled() {
        return flowConfig.getFeatures().isNeo4jExportEnabled();
    }

    private void initializeDriver() {
        try {
            driver = GraphDatabase.driver(neo4jUri, AuthTokens.basic(neo4jUsername, neo4jPassword));
            driver.verifyConnectivity();
            connected = true;
            log.info("Connected to Neo4j at {}", neo4jUri);
        } catch (Exception e) {
            log.warn("Failed to connect to Neo4j at {}: {}", neo4jUri, e.getMessage());
            connected = false;
        }
    }

    private void closeDriver() {
        if (driver != null) {
            driver.close();
            log.info("Neo4j driver closed");
        }
    }

    private Timer.Sample startTimer() {
        return Timer.start(metricsConfig.getRegistry());
    }

    private void stopTimer(Timer.Sample sample) {
        sample.stop(metricsConfig.getExportTimer());
    }

    private CompletableFuture<ExportResult> executePush(String graphId) {
        if (!isConnectionValid()) {
            return handleNotConnected(graphId);
        }

        var cypherStatements = generateCypher(graphId);
        if (cypherStatements.isEmpty()) {
            return handleNoStatements(graphId);
        }

        return executeStatements(graphId, cypherStatements);
    }

    private boolean isConnectionValid() {
        return connected && driver != null;
    }

    private CompletableFuture<ExportResult> handleNotConnected(String graphId) {
        log.warn("Neo4j not connected, cannot push graph: {}", graphId);
        return CompletableFuture.completedFuture(
                ExportResult.failure(graphId, "Neo4j not connected")
        );
    }

    private CompletableFuture<ExportResult> handleNoStatements(String graphId) {
        return CompletableFuture.completedFuture(
                ExportResult.failure(graphId, "No Cypher statements generated")
        );
    }

    private CompletableFuture<ExportResult> executeStatements(String graphId,
                                                               List<String> cypherStatements) {
        try {
            var result = runCypherStatements(graphId, cypherStatements);
            metricsConfig.getExportsCompleted().increment();
            return CompletableFuture.completedFuture(result);
        } catch (Exception e) {
            return handleExportError(graphId, e);
        }
    }

    private ExportResult runCypherStatements(String graphId, List<String> cypherStatements) {
        long startTime = System.currentTimeMillis();
        var counts = new ExportCounts();

        try (Session session = driver.session()) {
            cypherStatements.forEach(cypher -> executeSingleStatement(session, cypher, counts));
        }

        long duration = System.currentTimeMillis() - startTime;
        logExportSuccess(graphId, counts, duration);

        return ExportResult.success(graphId, counts.nodes, counts.edges, duration);
    }

    private void executeSingleStatement(Session session, String cypher, ExportCounts counts) {
        session.run(cypher);
        counts.incrementFor(cypher);
    }

    private void logExportSuccess(String graphId, ExportCounts counts, long duration) {
        log.info("Exported graph {} to Neo4j: {} nodes, {} edges in {}ms",
                graphId, counts.nodes, counts.edges, duration);
    }

    private CompletableFuture<ExportResult> handleExportError(String graphId, Exception e) {
        log.error("Failed to export graph {} to Neo4j", graphId, e);
        return CompletableFuture.completedFuture(
                ExportResult.failure(graphId, e.getMessage())
        );
    }

    // ==================== Inner Classes ====================

    /**
     * Mutable counter for tracking exported nodes and edges.
     */
    private static class ExportCounts {
        int nodes = 0;
        int edges = 0;

        void incrementFor(String cypher) {
            if (cypher.contains(NODE_MARKER)) nodes++;
            if (cypher.contains(EDGE_MARKER)) edges++;
        }
    }
}
