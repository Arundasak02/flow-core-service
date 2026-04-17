package com.flow.core.service.enrichment;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * Computes the diff between an incoming graph and the previously stored version.
 *
 * Only METHOD/PRIVATE_METHOD nodes with a methodBodyHash are diffed.
 * ENDPOINT and TOPIC nodes are structural — they don't drive enrichment.
 *
 * Result is used by EnrichmentPlanner to enqueue only changed/new methods.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GraphDiffEngine {

    private final EnrichmentStore enrichmentStore;
    private final ObjectMapper objectMapper;

    public GraphDiff diff(String graphId, StaticGraphIngestRequest incoming, String gitCommit, String gitBranch) {
        Map<String, String> previousHashes = buildPreviousHashMap(graphId);
        Map<String, String> incomingHashes = buildIncomingHashMap(incoming);

        List<String> added    = new ArrayList<>();
        List<String> modified = new ArrayList<>();
        List<String> removed  = new ArrayList<>();
        List<String> unchanged = new ArrayList<>();

        for (Map.Entry<String, String> entry : incomingHashes.entrySet()) {
            String nodeId = entry.getKey();
            String newHash = entry.getValue();
            String prevHash = previousHashes.get(nodeId);

            if (prevHash == null) {
                added.add(nodeId);
            } else if (!prevHash.equals(newHash)) {
                modified.add(nodeId);
            } else {
                unchanged.add(nodeId);
            }
        }

        for (String nodeId : previousHashes.keySet()) {
            if (!incomingHashes.containsKey(nodeId)) {
                removed.add(nodeId);
            }
        }

        // Persist updated node records
        for (Map.Entry<String, String> entry : incomingHashes.entrySet()) {
            enrichmentStore.upsertNode(graphId, entry.getKey(), entry.getValue(), gitCommit);
        }

        GraphDiff result = new GraphDiff(graphId, gitCommit, gitBranch, added, modified, removed, unchanged);

        // Record deployment history
        enrichmentStore.recordDeployment(graphId, gitCommit, gitBranch,
            added.size(), modified.size(), removed.size(), toJson(result));

        log.info("[diff] graph={} commit={} added={} modified={} removed={} unchanged={}",
            graphId, gitCommit, added.size(), modified.size(), removed.size(), unchanged.size());

        return result;
    }

    private Map<String, String> buildPreviousHashMap(String graphId) {
        Map<String, String> map = new HashMap<>();
        for (EnrichmentStore.NodeRecord record : enrichmentStore.getNodes(graphId)) {
            if (record.methodBodyHash() != null) {
                map.put(record.nodeId(), record.methodBodyHash());
            }
        }
        return map;
    }

    private Map<String, String> buildIncomingHashMap(StaticGraphIngestRequest incoming) {
        Map<String, String> map = new HashMap<>();
        if (incoming.getNodes() == null) return map;
        for (StaticGraphIngestRequest.NodeDto node : incoming.getNodes()) {
            // Only diff method nodes that carry a methodBodyHash
            String type = node.getType();
            if (type != null && (type.contains("METHOD"))) {
                Object hash = node.getAttributes() != null ? node.getAttributes().get("methodBodyHash") : null;
                if (hash != null) {
                    map.put(node.getNodeId(), hash.toString());
                }
            }
        }
        return map;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return "{}";
        }
    }

    public record GraphDiff(
        String graphId,
        String gitCommit,
        String gitBranch,
        List<String> added,
        List<String> modified,
        List<String> removed,
        List<String> unchanged
    ) {
        /** All nodeIds that require AI enrichment (new or body changed). */
        public List<String> needsEnrichment() {
            List<String> result = new ArrayList<>(added);
            result.addAll(modified);
            return result;
        }

        public boolean hasChanges() {
            return !added.isEmpty() || !modified.isEmpty() || !removed.isEmpty();
        }
    }
}
