package com.flow.core.service.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.core.service.api.dto.RuntimeEventIngestRequest;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Smoke tests for Flow Core Service.
 *
 * Tests basic functionality of all endpoints.
 */
@SpringBootTest
@AutoConfigureMockMvc
class FlowCoreServiceSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void contextLoads() {
        // Context loads successfully
    }

    @Test
    void healthEndpointWorks() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").exists());
    }

    @Test
    void swaggerUiAvailable() throws Exception {
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void listGraphsReturnsEmptyInitially() throws Exception {
        mockMvc.perform(get("/graphs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void ingestStaticGraph_validRequest_returns202() throws Exception {
        StaticGraphIngestRequest request = StaticGraphIngestRequest.builder()
                .graphId("test-graph-1")
                .version("1.0.0")
                .nodes(List.of(
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("node-1")
                                .type("service")
                                .name("UserService")
                                .build(),
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("node-2")
                                .type("service")
                                .name("OrderService")
                                .build()
                ))
                .edges(List.of(
                        StaticGraphIngestRequest.EdgeDto.builder()
                                .edgeId("edge-1")
                                .sourceNodeId("node-1")
                                .targetNodeId("node-2")
                                .type("calls")
                                .build()
                ))
                .metadata(Map.of("environment", "test"))
                .build();

        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").value("test-graph-1"));
    }

    @Test
    void ingestStaticGraph_missingGraphId_returns400() throws Exception {
        StaticGraphIngestRequest request = StaticGraphIngestRequest.builder()
                .nodes(List.of())
                .edges(List.of())
                .build();

        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"));
    }

    @Test
    void ingestRuntimeEvents_graphNotFound_returns404() throws Exception {
        RuntimeEventIngestRequest request = RuntimeEventIngestRequest.builder()
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

    @Test
    void getGraph_notFound_returns404() throws Exception {
        mockMvc.perform(get("/graphs/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void getTrace_notFound_returns404() throws Exception {
        mockMvc.perform(get("/trace/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void listTraces_returnsEmptyInitially() throws Exception {
        mockMvc.perform(get("/trace"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    void deleteGraph_nonExistent_returns204() throws Exception {
        mockMvc.perform(delete("/graphs/non-existent"))
                .andExpect(status().isNoContent());
    }

    @Test
    void exportNeo4j_graphNotFound_returns404() throws Exception {
        mockMvc.perform(get("/export/neo4j/non-existent"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void flowEndpoint_graphNotFound_returns404() throws Exception {
        mockMvc.perform(get("/flow/non-existent")
                        .param("zoom", "0"))
                .andExpect(status().isNotFound());
    }

    @Test
    void runtimeSummary_graphNotFound_returns404() throws Exception {
        mockMvc.perform(get("/graphs/non-existent/runtime-summary"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void runtimeSummary_forExistingGraph_returnsKpis() throws Exception {
        String graphId = "runtime-smoke-graph";
        String traceId = "runtime-smoke-trace";

        StaticGraphIngestRequest graphRequest = StaticGraphIngestRequest.builder()
                .graphId(graphId)
                .version("1.0.0")
                .nodes(List.of(
                        StaticGraphIngestRequest.NodeDto.builder()
                                .nodeId("node-1")
                                .type("service")
                                .name("GatewayService")
                                .build()
                ))
                .edges(List.of())
                .build();

        mockMvc.perform(post("/ingest/static")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(graphRequest)))
                .andExpect(status().isAccepted());

        RuntimeEventIngestRequest runtimeRequest = RuntimeEventIngestRequest.builder()
                .graphId(graphId)
                .traceId(traceId)
                .events(List.of(
                        RuntimeEventIngestRequest.EventDto.builder()
                                .eventId("evt-runtime-1")
                                .type("METHOD_ENTER")
                                .timestamp(Instant.now())
                                .nodeId("node-1")
                                .durationMs(12L)
                                .build(),
                        RuntimeEventIngestRequest.EventDto.builder()
                                .eventId("evt-runtime-2")
                                .type("ERROR")
                                .timestamp(Instant.now())
                                .nodeId("node-1")
                                .durationMs(9L)
                                .errorType("RuntimeException")
                                .errorMessage("boom")
                                .attributes(Map.of("stackTrace", "sample"))
                                .build()
                ))
                .traceComplete(true)
                .build();

        mockMvc.perform(post("/ingest/runtime")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(runtimeRequest)))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/graphs/" + graphId + "/runtime-summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.requestsPerHour").isNumber())
                .andExpect(jsonPath("$.data.errorsPerHour").isNumber())
                .andExpect(jsonPath("$.data.activeFlows").isNumber())
                .andExpect(jsonPath("$.data.avgLatencyMs").isNumber())
                .andExpect(jsonPath("$.data.updatedAt").exists());
    }
}

