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
}

