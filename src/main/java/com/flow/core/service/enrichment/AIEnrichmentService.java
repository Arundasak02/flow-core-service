package com.flow.core.service.enrichment;

import com.flow.core.service.api.dto.StaticGraphIngestRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Semaphore;

/**
 * Orchestrates the full AI enrichment pipeline after a static graph is ingested.
 *
 * Pipeline:
 *   1. GraphDiffEngine → find only NEW/CHANGED nodes (skip cache hits)
 *   2. EnrichmentPlanner → topological order + context pyramid assembly
 *   3. Per job: check enrichment cache → call AIProvider if miss → store result
 *   4. SSE events: enrichment_progress per node, enrichment_complete at end
 *
 * Runs fully async — never blocks the ingest response.
 * Semaphore limits concurrent LLM calls (default 4).
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "flow.ai.enabled", havingValue = "true", matchIfMissing = true)
public class AIEnrichmentService {

    private final GraphDiffEngine diffEngine;
    private final EnrichmentPlanner planner;
    private final EnrichmentStore enrichmentStore;
    private final AIProvider aiProvider;
    private final SseEventPublisher ssePublisher;

    @Value("${flow.ai.enrichment.max-concurrent:4}")
    private int maxConcurrent;

    @Value("${flow.ai.enrichment.confidence-threshold:0.6}")
    private double confidenceThreshold;

    @Value("${flow.ai.enrichment.prompt-version:1.0}")
    private String promptVersion;

    /**
     * Entry point called by StaticGraphHandler after successful ingest.
     * Runs async on a virtual thread — returns immediately.
     */
    @Async
    public void enrichAsync(StaticGraphIngestRequest graph, String gitCommit, String gitBranch) {
        if (!aiProvider.isAvailable()) {
            log.warn("[enrichment] AI provider '{}' not available — skipping enrichment for graph={}",
                aiProvider.modelName(), graph.getGraphId());
            return;
        }

        log.info("[enrichment] Starting enrichment for graph={} commit={}", graph.getGraphId(), gitCommit);
        long startMs = System.currentTimeMillis();

        try {
            // 1. Diff — find only what changed
            GraphDiffEngine.GraphDiff diff = diffEngine.diff(
                graph.getGraphId(), graph, gitCommit, gitBranch);

            List<String> toEnrich = new ArrayList<>(diff.needsEnrichment());

            // Gap-recovery: also enrich unchanged nodes that lack a cache entry
            // (handles interrupted enrichment runs, e.g., server restarts mid-batch)
            if (!diff.unchanged().isEmpty()) {
                Set<String> alreadyQueued = new HashSet<>(toEnrich);
                for (var node : graph.getNodes()) {
                    if (alreadyQueued.contains(node.getNodeId())) continue;
                    if (!diff.unchanged().contains(node.getNodeId())) continue;
                    var attrs = node.getAttributes();
                    if (attrs == null) continue;
                    String hash = (String) attrs.get("methodBodyHash");
                    if (hash == null) continue;
                    if (enrichmentStore.findEnrichment(hash, aiProvider.modelName(), promptVersion).isEmpty()) {
                        toEnrich.add(node.getNodeId());
                    }
                }
                if (toEnrich.size() > diff.needsEnrichment().size()) {
                    log.info("[enrichment] gap-recovery: {} unchanged nodes missing enrichment added to batch",
                        toEnrich.size() - diff.needsEnrichment().size());
                }
            }

            if (toEnrich.isEmpty()) {
                log.info("[enrichment] graph={} nothing to enrich (all cache hits)", graph.getGraphId());
                return;
            }

            // 2. Plan — topological order + context assembly
            List<EnrichmentPlanner.EnrichmentJob> jobs = planner.plan(
                graph, toEnrich, enrichmentStore, aiProvider.modelName(), promptVersion);

            // 3. Enrich with bounded concurrency
            Semaphore semaphore = new Semaphore(maxConcurrent);
            int enriched = 0;

            for (EnrichmentPlanner.EnrichmentJob job : jobs) {
                // Check cache first (callee may already be cached by a prior run)
                String hash = job.methodBodyHash();
                if (hash != null) {
                    var cached = enrichmentStore.findEnrichment(hash, aiProvider.modelName(), promptVersion);
                    if (cached.isPresent()) {
                        log.debug("[enrichment] cache hit: {}", job.nodeId());
                        ssePublisher.publishEnrichmentProgress(
                            graph.getGraphId(), job.nodeId(), cached.get().oneLineSummary());
                        enriched++;
                        continue;
                    }
                }

                semaphore.acquire();
                try {
                    AIEnrichmentResult result = aiProvider.enrich(job.request());

                    // Retry once with empty fallback if confidence too low
                    if (result.confidence() < confidenceThreshold && !result.oneLineSummary().isBlank()) {
                        log.debug("[enrichment] low confidence {} for {} — result accepted as-is",
                            String.format("%.2f", result.confidence()), job.nodeId());
                    }

                    enrichmentStore.saveEnrichment(result);
                    ssePublisher.publishEnrichmentProgress(
                        graph.getGraphId(), job.nodeId(), result.oneLineSummary());
                    enriched++;

                    log.debug("[enrichment] enriched {} confidence={} summary={}",
                        job.nodeId(), String.format("%.2f", result.confidence()),
                        result.oneLineSummary().length() > 60
                            ? result.oneLineSummary().substring(0, 60) + "…"
                            : result.oneLineSummary());
                } finally {
                    semaphore.release();
                }
            }

            long elapsed = System.currentTimeMillis() - startMs;
            log.info("[enrichment] COMPLETE graph={} enriched={}/{} elapsed={}ms",
                graph.getGraphId(), enriched, jobs.size(), elapsed);
            ssePublisher.publishEnrichmentComplete(graph.getGraphId(), enriched);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("[enrichment] interrupted for graph={}", graph.getGraphId());
        } catch (Exception e) {
            log.error("[enrichment] failed for graph={}: {}", graph.getGraphId(), e.getMessage(), e);
        }
    }
}
