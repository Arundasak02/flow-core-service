package com.flow.core.service.enrichment;

import com.flow.core.graph.CoreEdge;
import com.flow.core.graph.CoreGraph;
import com.flow.core.graph.CoreNode;
import com.flow.core.graph.NodeType;
import com.flow.core.service.api.dto.CapabilityMapResponse;
import com.flow.core.service.engine.GraphStore;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds a business capability map for a service graph.
 *
 * Strategy (two-pass, no new LLM calls needed):
 *
 * Pass 1 — Entry point discovery:
 *   - Find all ENDPOINT and TOPIC nodes in the graph.
 *   - For each, follow HANDLES edges to find the handler METHOD node.
 *   - Look up the handler's AI enrichment (businessNoun, oneLineSummary).
 *
 * Pass 2 — Capability grouping:
 *   - Primary: group by businessNoun from AI enrichment.
 *   - Fallback: group by first path segment (/owners → "Owner Management").
 *   - Generate a group name from the noun ("owner" → "Owner Management").
 *
 * This produces useful results even without Ollama by using path-based grouping.
 * When AI enrichment is available, the groups and descriptions are semantically meaningful.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CapabilityMapBuilder {

    private final GraphStore graphStore;
    private final EnrichmentStore enrichmentStore;

    public Optional<CapabilityMapResponse> build(String graphId) {
        return graphStore.findById(graphId)
                .filter(g -> g instanceof CoreGraph)
                .map(g -> buildFromCoreGraph(graphId, (CoreGraph) g));
    }

    private CapabilityMapResponse buildFromCoreGraph(String graphId, CoreGraph graph) {
        // Index enrichments by nodeId for O(1) lookup
        List<AIEnrichmentResult> allEnrichments = enrichmentStore.findEnrichmentsForGraph(graphId);
        Map<String, AIEnrichmentResult> byNodeId = allEnrichments.stream()
                .collect(Collectors.toMap(AIEnrichmentResult::nodeId, e -> e, (a, b) -> a));

        boolean enrichmentAvailable = !allEnrichments.isEmpty();
        log.debug("[capability] graph={} enriched_nodes={}", graphId, allEnrichments.size());

        // Build index: nodeId → all outgoing edges (HANDLES edges live here)
        Map<String, List<CoreEdge>> outgoing = new HashMap<>();
        for (CoreEdge edge : graph.getAllEdges()) {
            outgoing.computeIfAbsent(edge.getSourceId(), k -> new ArrayList<>()).add(edge);
        }

        // Collect entry-point triggers
        List<RawTrigger> triggers = new ArrayList<>();
        for (CoreNode node : graph.getAllNodes()) {
            if (node.getType() == NodeType.ENDPOINT || node.getType() == NodeType.TOPIC) {
                String handlerNodeId = findHandlerNodeId(node.getId(), outgoing);
                AIEnrichmentResult handlerEnrichment = handlerNodeId != null ? byNodeId.get(handlerNodeId) : null;

                String description = buildDescription(node, handlerEnrichment);
                String noun = deriveNoun(node, handlerEnrichment);

                triggers.add(new RawTrigger(
                        node.getId(),
                        extractHttpMethod(node),
                        extractPath(node),
                        description,
                        handlerNodeId,
                        noun,
                        handlerEnrichment != null ? handlerEnrichment.category() : null
                ));
            }
        }

        log.info("[capability] graph={} entry_points={}", graphId, triggers.size());

        // Group by noun
        Map<String, List<RawTrigger>> grouped = triggers.stream()
                .collect(Collectors.groupingBy(t -> t.noun));

        // Convert to CapabilityGroup, sorted by descending trigger count
        List<CapabilityGroup> groups = grouped.entrySet().stream()
                .sorted(Map.Entry.<String, List<RawTrigger>>comparingByValue(
                        Comparator.comparingInt(List::size)).reversed())
                .map(entry -> toCapabilityGroup(entry.getKey(), entry.getValue()))
                .toList();

        String serviceDescription = buildServiceDescription(graphId, groups, triggers.size());

        return new CapabilityMapResponse(graphId, serviceDescription, groups, triggers.size(), enrichmentAvailable);
    }

    // ── Handler discovery ────────────────────────────────────────────────────

    private String findHandlerNodeId(String endpointNodeId, Map<String, List<CoreEdge>> outgoing) {
        return outgoing.getOrDefault(endpointNodeId, List.of()).stream()
                .filter(e -> "HANDLES".equals(e.getType().name()))
                .map(CoreEdge::getTargetId)
                .findFirst()
                .orElse(null);
    }

    // ── Description building ─────────────────────────────────────────────────

    private String buildDescription(CoreNode endpointNode, AIEnrichmentResult enrichment) {
        if (enrichment != null && enrichment.oneLineSummary() != null
                && !enrichment.oneLineSummary().isBlank()) {
            return enrichment.oneLineSummary();
        }
        // Heuristic fallback: derive from HTTP method + path
        String method = extractHttpMethod(endpointNode);
        String path = extractPath(endpointNode);
        return inferDescriptionFromPath(method, path);
    }

    private String inferDescriptionFromPath(String method, String path) {
        // e.g. POST /owners → "Creates a new owner"
        //      GET  /owners/{id} → "Retrieves a specific owner"
        //      PUT  /owners/{id} → "Updates an existing owner"
        //      DELETE /owners/{id} → "Deletes an owner"
        //      GET  /owners → "Lists all owners"
        String resource = extractResourceName(path);
        boolean hasId = path.contains("{");
        return switch (method.toUpperCase()) {
            case "POST"   -> "Creates a new " + resource;
            case "GET"    -> hasId ? "Retrieves a specific " + resource : "Lists all " + resource + "s";
            case "PUT", "PATCH" -> "Updates an existing " + resource;
            case "DELETE" -> "Deletes a " + resource;
            default       -> "Handles " + method + " " + path;
        };
    }

    // ── Noun derivation ──────────────────────────────────────────────────────

    private String deriveNoun(CoreNode endpointNode, AIEnrichmentResult enrichment) {
        // Primary: AI-provided businessNoun
        if (enrichment != null && enrichment.businessNoun() != null
                && !enrichment.businessNoun().isBlank()) {
            return enrichment.businessNoun().toLowerCase();
        }
        // Fallback: parse from URL path
        return extractResourceName(extractPath(endpointNode));
    }

    // ── Path / HTTP method extraction ────────────────────────────────────────

    private String extractHttpMethod(CoreNode node) {
        Object method = node.getMetadata("httpMethod");
        if (method != null) return method.toString();
        // Try name: "GET /owners/{id}" format
        String name = node.getName();
        if (name != null && name.contains(" ")) return name.split(" ")[0];
        return node.getType() == NodeType.TOPIC ? "TOPIC" : "GET";
    }

    private String extractPath(CoreNode node) {
        Object path = node.getMetadata("path");
        if (path != null) return path.toString();
        // Try name fallback
        String name = node.getName();
        if (name != null && name.contains(" ")) return name.split(" ", 2)[1];
        return name != null ? name : node.getId();
    }

    private String extractResourceName(String path) {
        // "/owners/{ownerId}/pets" → "pet"
        // "/petTypes" → "petType"
        if (path == null || path.isBlank()) return "resource";
        String[] segments = path.replaceAll("/\\{[^}]+}", "").split("/");
        // Find the last non-empty segment
        String last = "";
        for (String seg : segments) {
            if (!seg.isBlank()) last = seg;
        }
        if (last.isEmpty()) return "resource";
        // Remove trailing 's' for singular form: "owners" → "owner"
        // But keep "petTypes" as "petType" (camelCase)
        if (last.endsWith("Types")) return last.substring(0, last.length() - 1);
        if (last.endsWith("s") && last.length() > 2) return last.substring(0, last.length() - 1);
        return last;
    }

    // ── Group building ───────────────────────────────────────────────────────

    private CapabilityGroup toCapabilityGroup(String noun, List<RawTrigger> triggers) {
        String groupName = toGroupName(noun);
        List<CapabilityGroup.TriggerItem> items = triggers.stream()
                .sorted(Comparator.comparing(t -> httpMethodOrder(t.httpMethod)))
                .map(t -> new CapabilityGroup.TriggerItem(
                        t.endpointId, t.httpMethod, t.path,
                        t.description, t.handlerNodeId, t.category))
                .toList();
        return new CapabilityGroup(groupName, noun, items);
    }

    private String toGroupName(String noun) {
        if (noun == null || noun.isBlank()) return "General";
        // "owner" → "Owner Management"
        // "petType" → "Pet Type Reference"
        String humanized = noun.replaceAll("([A-Z])", " $1").trim();
        String capitalized = Character.toUpperCase(humanized.charAt(0)) + humanized.substring(1);
        // Heuristic: if it's a short resource name, it's probably CRUD → "Management"
        // If it ends in "type"/"config"/"setting", it's reference data
        String lower = noun.toLowerCase();
        if (lower.contains("type") || lower.contains("config") || lower.contains("setting")
                || lower.contains("ref") || lower.contains("enum")) {
            return capitalized + " Reference";
        }
        return capitalized + " Management";
    }

    private int httpMethodOrder(String method) {
        return switch (method.toUpperCase()) {
            case "GET"    -> 0;
            case "POST"   -> 1;
            case "PUT", "PATCH" -> 2;
            case "DELETE" -> 3;
            default       -> 4;
        };
    }

    // ── Service description ──────────────────────────────────────────────────

    private String buildServiceDescription(String graphId, List<CapabilityGroup> groups, int totalEndpoints) {
        if (groups.isEmpty()) return "This service has no discoverable entry points.";

        // Extract the domain nouns and build a description
        List<String> nouns = groups.stream()
                .map(g -> pluralize(g.noun()))
                .distinct()
                .limit(3)
                .toList();

        String nounPhrase = switch (nouns.size()) {
            case 1 -> nouns.get(0);
            case 2 -> nouns.get(0) + " and " + nouns.get(1);
            default -> nouns.get(0) + ", " + nouns.get(1) + ", and more";
        };

        // Clean up the graphId for display
        String serviceName = graphId.replaceAll("^spring-petclinic-", "")
                .replace("-", " ")
                .trim();

        return "Manages " + nounPhrase + " for the " + serviceName + ". "
                + totalEndpoints + " " + (totalEndpoints == 1 ? "entry point" : "entry points")
                + " across " + groups.size() + " " + (groups.size() == 1 ? "capability" : "capabilities") + ".";
    }

    private String pluralize(String noun) {
        if (noun == null || noun.isBlank()) return "resources";
        if (noun.endsWith("s")) return noun;
        if (noun.endsWith("y")) return noun.substring(0, noun.length() - 1) + "ies";
        return noun + "s";
    }

    // ── Internal DTO ─────────────────────────────────────────────────────────

    private record RawTrigger(
        String endpointId,
        String httpMethod,
        String path,
        String description,
        String handlerNodeId,
        String noun,
        String category
    ) {}
}
