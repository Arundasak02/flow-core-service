package com.flow.core.service.enrichment;

import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds an ordered list of enrichment jobs for a set of node IDs.
 *
 * Ordering strategy:
 *   1. Topological sort — callees before callers (so callee summaries are available when enriching callers)
 *   2. Priority within each topo level: ENDPOINT_HANDLER/SERVICE before REPOSITORY/UTILITY
 *   3. Only nodes in needsEnrichment set are emitted (diff-filtered by GraphDiffEngine)
 *
 * Also assembles the NodeEnrichmentRequest context pyramid for each job.
 */
@Slf4j
@Component
public class EnrichmentPlanner {

    /**
     * Returns an ordered list of EnrichmentJob, each with full context assembled.
     * Callee summaries from the enrichment cache are injected where available.
     */
    public List<EnrichmentJob> plan(
        StaticGraphIngestRequest graph,
        List<String> needsEnrichment,
        EnrichmentStore enrichmentStore,
        String modelName,
        String promptVersion
    ) {
        if (needsEnrichment.isEmpty()) return List.of();

        Set<String> targetSet = new HashSet<>(needsEnrichment);
        Map<String, StaticGraphIngestRequest.NodeDto> nodeMap = buildNodeMap(graph);
        Map<String, List<String>> callees = buildCalleeMap(graph);   // nodeId → list of callee nodeIds
        Map<String, List<String>> callers = buildCallerMap(graph);   // nodeId → list of caller nodeIds
        Map<String, List<String>> classMembers = buildClassMemberMap(nodeMap); // className → list of nodeIds

        // Build entry paths: which endpoint chain leads to each node
        Map<String, String> entryPaths = buildEntryPaths(graph, nodeMap, callees);

        List<String> topoOrder = topologicalSort(targetSet, callees);

        List<EnrichmentJob> jobs = new ArrayList<>();
        for (String nodeId : topoOrder) {
            StaticGraphIngestRequest.NodeDto node = nodeMap.get(nodeId);
            if (node == null || node.getAttributes() == null) continue;

            String methodBody = getAttribute(node, "methodBody");
            if (methodBody == null || methodBody.isBlank()) continue;

            List<String> annotations = getListAttribute(node, "annotations");
            String className = getAttribute(node, "className");

            // Assemble callee summaries from cache (already enriched via topo order)
            List<NodeEnrichmentRequest.CalleeSummary> calleeSummaries = assembleCalleeSummaries(
                callees.getOrDefault(nodeId, List.of()), nodeMap, enrichmentStore, modelName, promptVersion);

            // Sibling methods in the same class (domain vocabulary)
            List<String> siblings = classMembers.getOrDefault(className, List.of()).stream()
                .filter(id -> !id.equals(nodeId))
                .map(id -> {
                    var n = nodeMap.get(id);
                    return n != null ? getAttribute(n, "methodName") : null;
                })
                .filter(Objects::nonNull)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());

            NodeEnrichmentRequest request = new NodeEnrichmentRequest(
                nodeId,
                getAttribute(node, "methodName"),
                className,
                getAttribute(node, "packageName"),
                getAttribute(node, "signature"),
                methodBody,
                annotations,
                callers.getOrDefault(nodeId, List.of()),
                calleeSummaries,
                entryPaths.get(nodeId),
                siblings,
                graph.getGraphId(),
                promptVersion
            );

            jobs.add(new EnrichmentJob(nodeId, request, getAttribute(node, "methodBodyHash")));
        }

        log.info("[planner] graph={} total_targets={} jobs_with_body={}",
            graph.getGraphId(), needsEnrichment.size(), jobs.size());
        return jobs;
    }

    // ── Topological sort ─────────────────────────────────────────────────────

    /**
     * Kahn's algorithm. Processes callees before callers so callee summaries
     * are available in the cache when the caller's prompt is built.
     */
    private List<String> topologicalSort(Set<String> nodes, Map<String, List<String>> callees) {
        // in-degree = number of callee dependencies within target set
        Map<String, Integer> inDegree = new HashMap<>();
        Map<String, List<String>> reverseDeps = new HashMap<>(); // callee → callers

        for (String node : nodes) {
            inDegree.putIfAbsent(node, 0);
            for (String callee : callees.getOrDefault(node, List.of())) {
                if (nodes.contains(callee)) {
                    inDegree.merge(node, 1, Integer::sum);
                    reverseDeps.computeIfAbsent(callee, k -> new ArrayList<>()).add(node);
                }
            }
        }

        Queue<String> queue = new LinkedList<>();
        for (Map.Entry<String, Integer> e : inDegree.entrySet()) {
            if (e.getValue() == 0) queue.add(e.getKey());
        }

        List<String> result = new ArrayList<>();
        while (!queue.isEmpty()) {
            String node = queue.poll();
            result.add(node);
            for (String caller : reverseDeps.getOrDefault(node, List.of())) {
                int newDeg = inDegree.merge(caller, -1, Integer::sum);
                if (newDeg == 0) queue.add(caller);
            }
        }

        // Append any remaining nodes (cycles — rare in Java service code)
        for (String node : nodes) {
            if (!result.contains(node)) result.add(node);
        }
        return result;
    }

    // ── Graph structure helpers ──────────────────────────────────────────────

    private Map<String, StaticGraphIngestRequest.NodeDto> buildNodeMap(StaticGraphIngestRequest graph) {
        Map<String, StaticGraphIngestRequest.NodeDto> map = new HashMap<>();
        if (graph.getNodes() != null) {
            for (var node : graph.getNodes()) map.put(node.getNodeId(), node);
        }
        return map;
    }

    private Map<String, List<String>> buildCalleeMap(StaticGraphIngestRequest graph) {
        Map<String, List<String>> map = new HashMap<>();
        if (graph.getEdges() == null) return map;
        for (var edge : graph.getEdges()) {
            if ("CALL".equals(edge.getType())) {
                map.computeIfAbsent(edge.getSourceNodeId(), k -> new ArrayList<>()).add(edge.getTargetNodeId());
            }
        }
        return map;
    }

    private Map<String, List<String>> buildCallerMap(StaticGraphIngestRequest graph) {
        Map<String, List<String>> map = new HashMap<>();
        if (graph.getEdges() == null) return map;
        for (var edge : graph.getEdges()) {
            if ("CALL".equals(edge.getType())) {
                map.computeIfAbsent(edge.getTargetNodeId(), k -> new ArrayList<>()).add(edge.getSourceNodeId());
            }
        }
        return map;
    }

    private Map<String, List<String>> buildClassMemberMap(Map<String, StaticGraphIngestRequest.NodeDto> nodeMap) {
        Map<String, List<String>> map = new HashMap<>();
        for (var entry : nodeMap.entrySet()) {
            String className = getAttribute(entry.getValue(), "className");
            if (className != null) {
                map.computeIfAbsent(className, k -> new ArrayList<>()).add(entry.getKey());
            }
        }
        return map;
    }

    private Map<String, String> buildEntryPaths(
        StaticGraphIngestRequest graph,
        Map<String, StaticGraphIngestRequest.NodeDto> nodeMap,
        Map<String, List<String>> callees
    ) {
        Map<String, String> paths = new HashMap<>();
        if (graph.getNodes() == null) return paths;

        // Start from ENDPOINT nodes and BFS to build path strings
        for (var node : graph.getNodes()) {
            if ("ENDPOINT".equals(node.getType())) {
                String endpointLabel = node.getName() != null ? node.getName() : node.getNodeId();
                bfsEntryPath(node.getNodeId(), endpointLabel, callees, nodeMap, paths, new HashSet<>(), 0);
            }
        }
        return paths;
    }

    private void bfsEntryPath(String nodeId, String pathSoFar,
                               Map<String, List<String>> callees,
                               Map<String, StaticGraphIngestRequest.NodeDto> nodeMap,
                               Map<String, String> paths, Set<String> visited, int depth) {
        if (depth > 8 || visited.contains(nodeId)) return;
        visited.add(nodeId);
        paths.putIfAbsent(nodeId, pathSoFar);
        for (String callee : callees.getOrDefault(nodeId, List.of())) {
            var calleeNode = nodeMap.get(callee);
            String calleeName = calleeNode != null && calleeNode.getName() != null
                ? calleeNode.getName() : callee;
            bfsEntryPath(callee, pathSoFar + " → " + calleeName, callees, nodeMap, paths, visited, depth + 1);
        }
    }

    private List<NodeEnrichmentRequest.CalleeSummary> assembleCalleeSummaries(
        List<String> calleeIds,
        Map<String, StaticGraphIngestRequest.NodeDto> nodeMap,
        EnrichmentStore store,
        String modelName,
        String promptVersion
    ) {
        List<NodeEnrichmentRequest.CalleeSummary> summaries = new ArrayList<>();
        for (String calleeId : calleeIds) {
            var calleeNode = nodeMap.get(calleeId);
            if (calleeNode == null) continue;
            String hash = getAttribute(calleeNode, "methodBodyHash");
            if (hash == null) continue;
            store.findEnrichment(hash, modelName, promptVersion).ifPresent(result -> {
                if (!result.oneLineSummary().isBlank()) {
                    summaries.add(new NodeEnrichmentRequest.CalleeSummary(
                        calleeId,
                        getAttribute(calleeNode, "methodName"),
                        result.oneLineSummary()));
                }
            });
        }
        return summaries;
    }

    // ── Attribute helpers ────────────────────────────────────────────────────

    private String getAttribute(StaticGraphIngestRequest.NodeDto node, String key) {
        if (node.getAttributes() == null) return null;
        Object val = node.getAttributes().get(key);
        return val != null ? val.toString() : null;
    }

    @SuppressWarnings("unchecked")
    private List<String> getListAttribute(StaticGraphIngestRequest.NodeDto node, String key) {
        if (node.getAttributes() == null) return List.of();
        Object val = node.getAttributes().get(key);
        if (val instanceof List<?> list) return (List<String>) list;
        return List.of();
    }

    public record EnrichmentJob(String nodeId, NodeEnrichmentRequest request, String methodBodyHash) {}
}
