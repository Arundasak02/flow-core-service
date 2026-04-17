package com.flow.core.service.ingest;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import com.flow.core.service.enrichment.EnrichmentStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * On startup, reloads all graph snapshots from SQLite back into InMemoryGraphStore.
 *
 * This means FCS survives restarts without requiring re-scans. The enrichment
 * pipeline will run gap-recovery (all cache hits), so no Ollama calls are made.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphSnapshotReloader implements ApplicationRunner {

    private final EnrichmentStore enrichmentStore;
    private final IngestionQueue ingestionQueue;
    private final ObjectMapper objectMapper;

    @Override
    public void run(ApplicationArguments args) {
        Map<String, String> snapshots = enrichmentStore.loadAllGraphSnapshots();
        if (snapshots.isEmpty()) {
            log.info("[snapshot] No saved graphs to reload");
            return;
        }

        log.info("[snapshot] Reloading {} saved graph(s) from SQLite...", snapshots.size());

        for (Map.Entry<String, String> entry : snapshots.entrySet()) {
            String graphId = entry.getKey();
            String json = entry.getValue();
            try {
                StaticGraphIngestRequest request = objectMapper.readValue(json, StaticGraphIngestRequest.class);
                IngestionWorkItem.StaticGraphWorkItem item =
                    new IngestionWorkItem.StaticGraphWorkItem(graphId, request);
                boolean queued = ingestionQueue.enqueue(item, 5000);
                if (queued) {
                    log.info("[snapshot] Reloaded graph={} nodes={}", graphId,
                        request.getNodes() != null ? request.getNodes().size() : 0);
                } else {
                    log.warn("[snapshot] Queue full — could not reload graph={}", graphId);
                }
            } catch (Exception e) {
                log.error("[snapshot] Failed to reload graph={}: {}", graphId, e.getMessage());
            }
        }
    }
}
