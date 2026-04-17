package com.flow.core.service.enrichment;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Persistence interface for enrichment cache and deployment history.
 * Default implementation: SqliteEnrichmentStore.
 * Future: PostgresEnrichmentStore (swap via Spring profile).
 */
public interface EnrichmentStore {

    // ── Enrichment cache (content-addressed) ────────────────────────────────

    /**
     * Store enrichment result keyed on (methodBodyHash, modelName, promptVersion).
     * A cache hit on this triple means: same code + same model + same prompt = reuse.
     */
    void saveEnrichment(AIEnrichmentResult result);

    /**
     * Load cached enrichment for a method body hash.
     * Returns empty if code changed, model swapped, or prompt version updated.
     */
    Optional<AIEnrichmentResult> findEnrichment(String methodBodyHash, String modelName, String promptVersion);

    // ── Node tracking per graph/deployment ──────────────────────────────────

    /**
     * Upsert a node record with its current body hash and git commit.
     */
    void upsertNode(String graphId, String nodeId, String methodBodyHash, String gitCommit);

    /**
     * Get all node records for a graph — used by GraphDiffEngine.
     */
    List<NodeRecord> getNodes(String graphId);

    // ── Deployment history ───────────────────────────────────────────────────

    void recordDeployment(String graphId, String gitCommit, String gitBranch,
                          int addedCount, int modifiedCount, int removedCount,
                          String diffJson);

    // ── Graph snapshot (survive restarts) ────────────────────────────────────

    /** Persist the raw ingest JSON so FCS can reload on startup without re-scanning. */
    void saveGraphSnapshot(String graphId, String snapshotJson);

    /** Load all saved snapshots. Returns map of graphId → snapshotJson. */
    Map<String, String> loadAllGraphSnapshots();

    record NodeRecord(String nodeId, String methodBodyHash, String gitCommit) {}

    // ── Enrichment lookup by graph ───────────────────────────────────────────

    /**
     * Fetch all enrichment results for nodes currently tracked under a graphId.
     * Joins graph_node → node_enrichment on method_body_hash.
     * Nodes not yet enriched are omitted.
     */
    List<AIEnrichmentResult> findEnrichmentsForGraph(String graphId);
}
