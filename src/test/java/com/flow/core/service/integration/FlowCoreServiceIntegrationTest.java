package com.flow.core.service.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.engine.GraphStore;
import com.flow.core.service.runtime.RuntimeTraceBuffer;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

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
 * Integration test for Flow Core Service WITHOUT external dependencies.
 *
 * This test verifies core functionality without Neo4j Testcontainers,
 * making it faster for development feedback loops.
 *
 * Tests:
 * - Static graph ingestion and storage
 * - Runtime event ingestion
 * - Graph querying
 * - Zoom level extraction
 * - Cypher generation (without push)
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@ActiveProfiles("test")
class FlowCoreServiceIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private GraphStore graphStore;

    @Autowired
    private RuntimeTraceBuffer traceBuffer;

    private static final String GRAPH_ID = "test-order-flow";

    @BeforeEach
    void cleanup() {
        graphStore.delete(GRAPH_ID);
        traceBuffer.deleteTracesForGraph(GRAPH_ID);
    }

    // ==================== Static Graph Tests ====================

    @Test
    @Order(1)
    @DisplayName("1. Ingest static graph - E-commerce order flow")
    void step1_ingestStaticGraph() throws Exception {
        var request = buildEcommerceOrderFlowGraph();

        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value(GRAPH_ID));

        // Wait for async ingestion
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> graphStore.exists(GRAPH_ID));

        // Verify stored correctly
        var metadata = graphStore.getMetadata(GRAPH_ID);
        assertThat(metadata).isPresent();
        assertThat(metadata.get().nodeCount()).isEqualTo(6);
        assertThat(metadata.get().edgeCount()).isEqualTo(5);
        assertThat(metadata.get().hasRuntimeData()).isFalse();
    }

    @Test
    @Order(2)
    @DisplayName("2. Query graph by ID")
    void step2_queryGraphById() throws Exception {
        // Ingest first
        ingestGraph();

        mockMvc.perform(get("/graphs/" + GRAPH_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.graphId").value(GRAPH_ID))
                .andExpect(jsonPath("$.data.version").value("1.0.0"))
                .andExpect(jsonPath("$.data.nodes").isArray())
                .andExpect(jsonPath("$.data.nodes.length()").value(6))
                .andExpect(jsonPath("$.data.edges").isArray())
                .andExpect(jsonPath("$.data.edges.length()").value(5));
    }

    @Test
    @Order(3)
    @DisplayName("3. Get zoomed view - level 0 (highest)")
    void step3_getZoomedViewLevel0() throws Exception {
        ingestGraph();

        mockMvc.perform(get("/flow/" + GRAPH_ID)
                        .param("zoom", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").exists());
    }

    @Test
    @Order(4)
    @DisplayName("4. Get zoomed view - level 2")
    void step4_getZoomedViewLevel2() throws Exception {
        ingestGraph();

        mockMvc.perform(get("/flow/" + GRAPH_ID)
                        .param("zoom", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ==================== Runtime Event Tests ====================

    @Test
    @Order(5)
    @DisplayName("5. Ingest runtime events - first batch")
    void step5_ingestRuntimeEventsFirstBatch() throws Exception {
        ingestGraph();
        String traceId = "trace-" + UUID.randomUUID();

        var request = RuntimeEventIngestRequest.builder()
                .graphId(GRAPH_ID)
                .traceId(traceId)
                .events(List.of(
                        buildEvent("evt-1", "METHOD_ENTER", "order-controller", "span-1", null),
                        buildEvent("evt-2", "METHOD_ENTER", "order-service", "span-2", "span-1"),
                        buildEvent("evt-3", "CHECKPOINT", "inventory-service", "span-3", "span-2")
                ))
                .traceComplete(false)
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true));

        // Wait for processing
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> traceBuffer.getTrace(traceId).isPresent());

        var trace = traceBuffer.getTrace(traceId);
        assertThat(trace).isPresent();
        assertThat(trace.get().isComplete()).isFalse();
        assertThat(trace.get().events()).hasSize(3);
    }

    @Test
    @Order(6)
    @DisplayName("6. Ingest runtime events - complete trace")
    void step6_completeTrace() throws Exception {
        ingestGraph();
        String traceId = "trace-complete-" + UUID.randomUUID();

        // First batch
        var request1 = RuntimeEventIngestRequest.builder()
                .graphId(GRAPH_ID)
                .traceId(traceId)
                .events(List.of(
                        buildEvent("evt-1", "METHOD_ENTER", "order-controller", "span-1", null)
                ))
                .traceComplete(false)
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isAccepted());

        // Second batch - complete
        var request2 = RuntimeEventIngestRequest.builder()
                .graphId(GRAPH_ID)
                .traceId(traceId)
                .events(List.of(
                        buildEvent("evt-2", "METHOD_EXIT", "order-controller", "span-1", null)
                ))
                .traceComplete(true)
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isAccepted());

        // Wait for completion
        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> {
                    var t = traceBuffer.getTrace(traceId);
                    return t.isPresent() && t.get().isComplete();
                });

        var trace = traceBuffer.getTrace(traceId);
        assertThat(trace.get().isComplete()).isTrue();
    }

    @Test
    @Order(7)
    @DisplayName("7. Query trace by ID")
    void step7_queryTraceById() throws Exception {
        ingestGraph();
        String traceId = "trace-query-" + UUID.randomUUID();

        // Ingest events
        var request = RuntimeEventIngestRequest.builder()
                .graphId(GRAPH_ID)
                .traceId(traceId)
                .events(List.of(
                        buildEvent("evt-1", "START", "order-controller", "span-1", null)
                ))
                .traceComplete(false)
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> traceBuffer.getTrace(traceId).isPresent());

        // Query trace
        mockMvc.perform(get("/trace/" + traceId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.traceId").value(traceId))
                .andExpect(jsonPath("$.data.graphId").value(GRAPH_ID));
    }

    // ==================== Export Tests ====================

    @Test
    @Order(8)
    @DisplayName("8. Generate Cypher statements")
    void step8_generateCypherStatements() throws Exception {
        ingestGraph();

        mockMvc.perform(get("/export/neo4j/" + GRAPH_ID)
                        .param("mode", "cypher"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.graphId").value(GRAPH_ID))
                .andExpect(jsonPath("$.data.cypherStatements").isArray())
                .andExpect(jsonPath("$.data.cypherStatements.length()").value(
                        org.hamcrest.Matchers.greaterThan(0)));
    }

    // ==================== Deletion Tests ====================

    @Test
    @Order(9)
    @DisplayName("9. Delete graph and verify cleanup")
    void step9_deleteGraphAndVerify() throws Exception {
        ingestGraph();
        String traceId = "trace-delete-" + UUID.randomUUID();

        // Add a trace
        var request = RuntimeEventIngestRequest.builder()
                .graphId(GRAPH_ID)
                .traceId(traceId)
                .events(List.of(buildEvent("evt-1", "START", "order-controller", "span-1", null)))
                .traceComplete(false)
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> traceBuffer.getTrace(traceId).isPresent());

        // Delete graph
        mockMvc.perform(delete("/graphs/" + GRAPH_ID))
                .andExpect(status().isNoContent());

        // Verify cleanup
        assertThat(graphStore.exists(GRAPH_ID)).isFalse();
        assertThat(traceBuffer.getTrace(traceId)).isEmpty();
    }

    // ==================== Error Handling Tests ====================

    @Test
    @Order(10)
    @DisplayName("10. Return 404 for non-existent graph")
    void step10_return404ForNonExistentGraph() throws Exception {
        mockMvc.perform(get("/graphs/non-existent-graph"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    @Order(11)
    @DisplayName("11. Return 404 for runtime events on non-existent graph")
    void step11_return404ForRuntimeEventsOnNonExistentGraph() throws Exception {
        var request = RuntimeEventIngestRequest.builder()
                .graphId("non-existent-graph")
                .traceId("trace-1")
                .events(List.of(buildEvent("evt-1", "START", "node-1", "span-1", null)))
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("GRAPH_NOT_FOUND"));
    }

    @Test
    @Order(12)
    @DisplayName("12. Return 400 for invalid static graph request")
    void step12_return400ForInvalidRequest() throws Exception {
        var request = StaticGraphIngestRequest.builder()
                .nodes(List.of())
                .edges(List.of())
                .build(); // Missing graphId

        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    // ==================== Helper Methods ====================

    private void ingestGraph() throws Exception {
        var request = buildEcommerceOrderFlowGraph();
        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted());

        await().atMost(5, TimeUnit.SECONDS)
                .until(() -> graphStore.exists(GRAPH_ID));
    }

    private StaticGraphIngestRequest buildEcommerceOrderFlowGraph() {
        return StaticGraphIngestRequest.builder()
                .graphId(GRAPH_ID)
                .version("1.0.0")
                .nodes(List.of(
                        node("order-controller", "ENDPOINT", "OrderController.createOrder", "/api/orders"),
                        node("order-service", "METHOD", "OrderService.create", "OrderService"),
                        node("inventory-service", "METHOD", "InventoryService.reserve", "InventoryService"),
                        node("payment-service", "METHOD", "PaymentService.charge", "PaymentService"),
                        node("notification-service", "METHOD", "NotificationService.send", "NotificationService"),
                        node("order-events-topic", "TOPIC", "order-events", "Kafka")
                ))
                .edges(List.of(
                        edge("e1", "order-controller", "order-service", "CALL"),
                        edge("e2", "order-service", "inventory-service", "CALL"),
                        edge("e3", "order-service", "payment-service", "CALL"),
                        edge("e4", "order-service", "notification-service", "CALL"),
                        edge("e5", "order-service", "order-events-topic", "PRODUCES")
                ))
                .metadata(Map.of("domain", "ecommerce", "team", "orders"))
                .build();
    }

    private StaticGraphIngestRequest.NodeDto node(String id, String type, String name, String label) {
        return StaticGraphIngestRequest.NodeDto.builder()
                .nodeId(id)
                .type(type)
                .name(name)
                .label(label)
                .attributes(Map.of("createdBy", "integration-test"))
                .build();
    }

    private StaticGraphIngestRequest.EdgeDto edge(String id, String source, String target, String type) {
        return StaticGraphIngestRequest.EdgeDto.builder()
                .edgeId(id)
                .sourceNodeId(source)
                .targetNodeId(target)
                .type(type)
                .label(type.toLowerCase())
                .build();
    }

    private RuntimeEventIngestRequest.EventDto buildEvent(String eventId, String type,
                                                           String nodeId, String spanId,
                                                           String parentSpanId) {
        return RuntimeEventIngestRequest.EventDto.builder()
                .eventId(eventId)
                .type(type)
                .timestamp(Instant.now())
                .nodeId(nodeId)
                .spanId(spanId)
                .parentSpanId(parentSpanId)
                .durationMs(50L)
                .attributes(Map.of("test", true))
                .build();
    }
}

