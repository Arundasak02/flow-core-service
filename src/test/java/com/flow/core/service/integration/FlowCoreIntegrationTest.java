package com.flow.core.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.engine.GraphStore;
import com.flow.core.service.ingest.IngestionQueue;
import com.flow.core.service.runtime.RuntimeTraceBuffer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.Neo4jContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Comprehensive integration test for Flow Core Service.
 *
 * Tests the complete flow:
 * 1. Static graph ingestion via API
 * 2. Graph processing through flow-engine
 * 3. Runtime event ingestion
 * 4. Trace merging
 * 5. Graph querying with zoom levels
 * 6. Neo4j export (using Testcontainers)
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FlowCoreIntegrationTest {

    @Container
    static Neo4jContainer<?> neo4jContainer = new Neo4jContainer<>("neo4j:5.15.0")
            .withoutAuthentication();

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GraphStore graphStore;

    @Autowired
    private RuntimeTraceBuffer traceBuffer;

    @Autowired
    private IngestionQueue ingestionQueue;

    private static final String TEST_GRAPH_ID = "integration-test-graph";
    private static final String TEST_TRACE_ID = "integration-test-trace";

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("flow.neo4j.uri", neo4jContainer::getBoltUrl);
        registry.add("flow.neo4j.username", () -> "neo4j");
        registry.add("flow.neo4j.password", () -> "neo4j");
        registry.add("flow.features.neo4j-export-enabled", () -> "true");
        registry.add("flow.features.virtual-threads-enabled", () -> "true");
    }

    @BeforeEach
    void setUp() {
        // Clean up before each test
        graphStore.delete(TEST_GRAPH_ID);
        traceBuffer.deleteTracesForGraph(TEST_GRAPH_ID);
    }

    // ==================== Static Graph Ingestion Tests ====================

    @Test
    @Order(1)
    @DisplayName("Should ingest a static graph successfully")
    void shouldIngestStaticGraph() throws Exception {
        var request = createSampleStaticGraphRequest();

        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(TEST_GRAPH_ID));

        // Wait for async processing
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> graphStore.exists(TEST_GRAPH_ID));

        // Verify graph was stored
        assertThat(graphStore.exists(TEST_GRAPH_ID)).isTrue();

        var metadata = graphStore.getMetadata(TEST_GRAPH_ID);
        assertThat(metadata).isPresent();
        assertThat(metadata.get().nodeCount()).isEqualTo(5);
        assertThat(metadata.get().edgeCount()).isEqualTo(4);
    }

    @Test
    @Order(2)
    @DisplayName("Should retrieve ingested graph by ID")
    void shouldRetrieveGraphById() throws Exception {
        // First ingest the graph
        ingestTestGraph();

        // Then retrieve it
        mockMvc.perform(get("/graphs/" + TEST_GRAPH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.graphId").value(TEST_GRAPH_ID))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.edges").isArray());
    }

    @Test
    @Order(3)
    @DisplayName("Should list all graphs")
    void shouldListAllGraphs() throws Exception {
        // Ingest the graph
        ingestTestGraph();

        mockMvc.perform(get("/graphs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    // ==================== Runtime Event Ingestion Tests ====================

    @Test
    @Order(4)
    @DisplayName("Should ingest runtime events for existing graph")
    void shouldIngestRuntimeEvents() throws Exception {
        // First ingest the static graph
        ingestTestGraph();

        var runtimeRequest = createSampleRuntimeEventRequest(false);

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runtimeRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));

        // Wait for async processing
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> traceBuffer.getTrace(TEST_TRACE_ID).isPresent());

        // Verify trace was stored
        var trace = traceBuffer.getTrace(TEST_TRACE_ID);
        assertThat(trace).isPresent();
        assertThat(trace.get().events()).isNotEmpty();
    }

    @Test
    @Order(5)
    @DisplayName("Should complete trace and trigger merge")
    void shouldCompleteTraceAndTriggerMerge() throws Exception {
        // Ingest static graph
        ingestTestGraph();

        // Ingest runtime events with traceComplete=true
        var runtimeRequest = createSampleRuntimeEventRequest(true);

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runtimeRequest)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));

        // Wait for trace to be processed and merged
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    var trace = traceBuffer.getTrace(TEST_TRACE_ID);
                    return trace.isPresent() && trace.get().isComplete();
                });

        // Verify trace is marked complete
        var trace = traceBuffer.getTrace(TEST_TRACE_ID);
        assertThat(trace).isPresent();
        assertThat(trace.get().isComplete()).isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("Should reject runtime events for non-existent graph")
    void shouldRejectRuntimeEventsForNonExistentGraph() throws Exception {
        var request = RuntimeEventIngestRequest.builder()
                .graphId("non-existent-graph")
                .traceId("trace-1")
                .events(List.of(
                        RuntimeEventIngestRequest.EventDto.builder()
                                .eventId("event-1")
                                .type("START")
                                .timestamp(Instant.now())
                                .nodeId("node-1")
                                .build()
                ))
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("GRAPH_NOT_FOUND"));
    }

    // ==================== Trace Query Tests ====================

    @Test
    @Order(7)
    @DisplayName("Should retrieve trace by ID")
    void shouldRetrieveTraceById() throws Exception {
        // Ingest graph and runtime events
        ingestTestGraph();
        ingestTestRuntimeEvents();

        mockMvc.perform(get("/trace/" + TEST_TRACE_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.traceId").value(TEST_TRACE_ID))
                .andExpect(jsonPath("$.data.graphId").value(TEST_GRAPH_ID));
    }

    // ==================== Zoom Level / Flow Tests ====================

    @Test
    @Order(8)
    @DisplayName("Should get zoomed view of graph")
    void shouldGetZoomedViewOfGraph() throws Exception {
        // Ingest the graph
        ingestTestGraph();

        // Request zoom level 0 (highest level)
        mockMvc.perform(get("/flow/" + TEST_GRAPH_ID)
                        .param("zoom", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        // Request zoom level 1
        mockMvc.perform(get("/flow/" + TEST_GRAPH_ID)
                        .param("zoom", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Neo4j Export Tests ====================

    @Test
    @Order(9)
    @DisplayName("Should generate Cypher statements for graph")
    void shouldGenerateCypherStatements() throws Exception {
        // Ingest the graph
        ingestTestGraph();

        MvcResult result = mockMvc.perform(get("/export/neo4j/" + TEST_GRAPH_ID)
                        .param("mode", "cypher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.graphId").value(TEST_GRAPH_ID))
                .andExpect(jsonPath("$.data.cypherStatements").isArray())
                .andReturn();

        String response = result.getResponse().getContentAsString();
        assertThat(response).contains("FlowGraph");
    }

    @Test
    @Order(10)
    @DisplayName("Should push graph to Neo4j")
    void shouldPushGraphToNeo4j() throws Exception {
        // Ingest the graph
        ingestTestGraph();

        mockMvc.perform(get("/export/neo4j/" + TEST_GRAPH_ID)
                        .param("mode", "push"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.message").value("Export to Neo4j initiated"));
    }

    // ==================== Graph Deletion Tests ====================

    @Test
    @Order(11)
    @DisplayName("Should delete graph and associated traces")
    void shouldDeleteGraphAndTraces() throws Exception {
        // Ingest graph and runtime events
        ingestTestGraph();
        ingestTestRuntimeEvents();

        // Delete the graph
        mockMvc.perform(delete("/graphs/" + TEST_GRAPH_ID))
                .andExpect(status().isNoContent());

        // Verify graph is deleted
        assertThat(graphStore.exists(TEST_GRAPH_ID)).isFalse();

        // Verify traces are also deleted
        assertThat(traceBuffer.getTrace(TEST_TRACE_ID)).isEmpty();
    }

    // ==================== End-to-End Flow Test ====================

    @Test
    @Order(12)
    @DisplayName("Complete end-to-end flow test")
    void completeEndToEndFlowTest() throws Exception {
        String graphId = "e2e-test-" + UUID.randomUUID();
        String traceId = "e2e-trace-" + UUID.randomUUID();

        // Step 1: Ingest static graph
        var staticRequest = createStaticGraphRequest(graphId);
        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(staticRequest)))
                .andExpect(status().isAccepted());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> graphStore.exists(graphId));

        // Step 2: Ingest runtime events (batch 1)
        var runtimeRequest1 = createRuntimeRequest(graphId, traceId, false,
                List.of("START", "CHECKPOINT"));
        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runtimeRequest1)))
                .andExpect(status().isAccepted());

        // Step 3: Ingest more runtime events (batch 2, complete trace)
        var runtimeRequest2 = createRuntimeRequest(graphId, traceId, true,
                List.of("CHECKPOINT", "END"));
        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runtimeRequest2)))
                .andExpect(status().isAccepted());

        // Step 4: Wait for processing
        await().atMost(10, TimeUnit.SECONDS)
                .until(() -> {
                    var trace = traceBuffer.getTrace(traceId);
                    return trace.isPresent() && trace.get().isComplete();
                });

        // Step 5: Query the graph
        mockMvc.perform(get("/graphs/" + graphId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.graphId").value(graphId));

        // Step 6: Get zoomed view
        mockMvc.perform(get("/flow/" + graphId).param("zoom", "1"))
                .andExpect(status().isOk());

        // Step 7: Export to Neo4j (Cypher)
        mockMvc.perform(get("/export/neo4j/" + graphId).param("mode", "cypher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.cypherStatements").isArray());

        // Step 8: Query trace
        mockMvc.perform(get("/trace/" + traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.traceId").value(traceId));

        // Step 9: Cleanup
        mockMvc.perform(delete("/graphs/" + graphId))
                .andExpect(status().isNoContent());

        assertThat(graphStore.exists(graphId)).isFalse();
    }

    // ==================== Health & Metrics Tests ====================

    @Test
    @Order(13)
    @DisplayName("Should expose health endpoint")
    void shouldExposeHealthEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }

    @Test
    @Order(14)
    @DisplayName("Should expose metrics endpoint")
    void shouldExposeMetricsEndpoint() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.names").isArray());
    }

    @Test
    @Order(15)
    @DisplayName("Should expose Prometheus metrics")
    void shouldExposePrometheusMetrics() throws Exception {
        mockMvc.perform(get("/actuator/prometheus"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("flow_")));
    }

    // ==================== Helper Methods ====================

    private void ingestTestGraph() throws Exception {
        var request = createSampleStaticGraphRequest();
        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> graphStore.exists(TEST_GRAPH_ID));
    }

    private void ingestTestRuntimeEvents() throws Exception {
        var request = createSampleRuntimeEventRequest(false);
        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> traceBuffer.getTrace(TEST_TRACE_ID).isPresent());
    }

    private StaticGraphIngestRequest createSampleStaticGraphRequest() {
        return createStaticGraphRequest(TEST_GRAPH_ID);
    }

    private StaticGraphIngestRequest createStaticGraphRequest(String graphId) {
        return StaticGraphIngestRequest.builder()
                .graphId(graphId)
                .version("1.0.0")
                .nodes(List.of(
                        // Endpoint node
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("endpoint-1")
                                .type("ENDPOINT")
                                .name("POST /api/orders")
                                .label("Create Order Endpoint")
                                .attributes(Map.of("method", "POST", "path", "/api/orders"))
                                .build(),
                        // Service nodes
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("service-order")
                                .type("METHOD")
                                .name("OrderService.createOrder")
                                .label("Create Order")
                                .attributes(Map.of("class", "OrderService"))
                                .build(),
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("service-inventory")
                                .type("METHOD")
                                .name("InventoryService.checkStock")
                                .label("Check Stock")
                                .attributes(Map.of("class", "InventoryService"))
                                .build(),
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("service-payment")
                                .type("METHOD")
                                .name("PaymentService.processPayment")
                                .label("Process Payment")
                                .attributes(Map.of("class", "PaymentService"))
                                .build(),
                        // Topic node
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("topic-orders")
                                .type("TOPIC")
                                .name("orders-topic")
                                .label("Orders Kafka Topic")
                                .attributes(Map.of("broker", "kafka"))
                                .build()
                ))
                .edges(List.of(
                        StaticGraphIngestRequest.EdgeDto.builder()
                                .edgeId("edge-1")
                                .sourceNodeId("endpoint-1")
                                .targetNodeId("service-order")
                                .type("CALL")
                                .label("invokes")
                                .build(),
                        StaticGraphIngestRequest.EdgeDto.builder()
                                .edgeId("edge-2")
                                .sourceNodeId("service-order")
                                .targetNodeId("service-inventory")
                                .type("CALL")
                                .label("calls")
                                .build(),
                        StaticGraphIngestRequest.EdgeDto.builder()
                                .edgeId("edge-3")
                                .sourceNodeId("service-order")
                                .targetNodeId("service-payment")
                                .type("CALL")
                                .label("calls")
                                .build(),
                        StaticGraphIngestRequest.EdgeDto.builder()
                                .edgeId("edge-4")
                                .sourceNodeId("service-order")
                                .targetNodeId("topic-orders")
                                .type("PRODUCES")
                                .label("publishes to")
                                .build()
                ))
                .metadata(Map.of(
                        "application", "order-service",
                        "environment", "test",
                        "version", "1.0.0"
                ))
                .build();
    }

    private RuntimeEventIngestRequest createSampleRuntimeEventRequest(boolean traceComplete) {
        return createRuntimeRequest(TEST_GRAPH_ID, TEST_TRACE_ID, traceComplete,
                List.of("START", "CHECKPOINT", "END"));
    }

    private RuntimeEventIngestRequest createRuntimeRequest(String graphId, String traceId,
                                                            boolean traceComplete, List<String> eventTypes) {
        Instant baseTime = Instant.now();
        var events = new java.util.ArrayList<RuntimeEventIngestRequest.EventDto>();

        for (int i = 0; i < eventTypes.size(); i++) {
            events.add(RuntimeEventIngestRequest.EventDto.builder()
                    .eventId(UUID.randomUUID().toString())
                    .type(eventTypes.get(i))
                    .timestamp(baseTime.plusMillis(i * 100L))
                    .nodeId("service-order")
                    .spanId("span-" + i)
                    .parentSpanId(i > 0 ? "span-" + (i - 1) : null)
                    .durationMs((long) (Math.random() * 100))
                    .attributes(Map.of("iteration", i))
                    .build());
        }

        return RuntimeEventIngestRequest.builder()
                .graphId(graphId)
                .traceId(traceId)
                .events(events)
                .traceComplete(traceComplete)
                .metadata(Map.of("source", "integration-test"))
                .build();
    }
}

