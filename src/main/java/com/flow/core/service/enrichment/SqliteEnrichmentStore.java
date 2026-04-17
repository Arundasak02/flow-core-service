package com.flow.core.service.enrichment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * SQLite-backed enrichment store.
 *
 * Tables:
 *   node_enrichment  — content-addressed cache keyed on (body_hash, model, prompt_version)
 *   graph_node       — per-graph node registry with current body hash and git commit
 *   graph_deployment — deployment history with diff summary
 *
 * SQLite WAL mode is enabled on startup to allow concurrent reads during background writes.
 */
@Slf4j
@Repository
@RequiredArgsConstructor
public class SqliteEnrichmentStore implements EnrichmentStore {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    @PostConstruct
    void init() {
        enableWalMode();
        createTables();
        purgeStaleEnrichments();
        log.info("SqliteEnrichmentStore initialized");
    }

    private void enableWalMode() {
        jdbc.execute("PRAGMA journal_mode=WAL");
    }

    private void createTables() {
        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS node_enrichment (
                method_body_hash   TEXT NOT NULL,
                model_name         TEXT NOT NULL,
                prompt_version     TEXT NOT NULL,
                node_id            TEXT NOT NULL,
                one_line_summary   TEXT,
                detailed_logic     TEXT,
                business_noun      TEXT,
                business_verb      TEXT,
                category           TEXT,
                should_instrument  INTEGER DEFAULT 0,
                capture_variables  TEXT,
                confidence         REAL DEFAULT 0.0,
                created_at         INTEGER NOT NULL,
                PRIMARY KEY (method_body_hash, model_name, prompt_version)
            )
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS graph_node (
                graph_id           TEXT NOT NULL,
                node_id            TEXT NOT NULL,
                method_body_hash   TEXT,
                git_commit         TEXT,
                updated_at         INTEGER NOT NULL,
                PRIMARY KEY (graph_id, node_id)
            )
            """);

        jdbc.execute("""
            CREATE TABLE IF NOT EXISTS graph_deployment (
                graph_id       TEXT NOT NULL,
                git_commit     TEXT NOT NULL,
                git_branch     TEXT,
                added_count    INTEGER DEFAULT 0,
                modified_count INTEGER DEFAULT 0,
                removed_count  INTEGER DEFAULT 0,
                diff_json      TEXT,
                ingested_at    INTEGER NOT NULL,
                PRIMARY KEY (graph_id, git_commit)
            )
            """);

        jdbc.execute("""                                                                    
            CREATE TABLE IF NOT EXISTS graph_snapshot (
                graph_id      TEXT PRIMARY KEY,
                snapshot_json TEXT NOT NULL,
                stored_at     INTEGER NOT NULL
            )
            """);

        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_node_enrichment_node_id ON node_enrichment(node_id)");
        jdbc.execute("CREATE INDEX IF NOT EXISTS idx_graph_node_graph_id ON graph_node(graph_id)");
    }

    /**
     * Purges enrichment rows produced by obsolete prompt versions.
     * These rows have stale captureVariables format (plain strings) and cannot be reused safely.
     * Affected nodes will be re-enriched on the next scan automatically.
     */
    private void purgeStaleEnrichments() {
        int deleted = jdbc.update("DELETE FROM node_enrichment WHERE prompt_version < '2.0'");
        if (deleted > 0) {
            log.info("[migration] Purged {} stale enrichment rows (prompt_version < 2.0) — will be re-enriched on next scan", deleted);
        }
    }

    // ── Enrichment cache ─────────────────────────────────────────────────────

    @Override
    public void saveEnrichment(AIEnrichmentResult r) {
        String captureVarsJson = toJson(r.captureVariables());
        jdbc.update("""
            INSERT OR REPLACE INTO node_enrichment
              (method_body_hash, model_name, prompt_version, node_id,
               one_line_summary, detailed_logic, business_noun, business_verb,
               category, should_instrument, capture_variables, confidence, created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
            """,
            r.methodBodyHash(), r.modelName(), r.promptVersion(), r.nodeId(),
            r.oneLineSummary(), r.detailedLogic(), r.businessNoun(), r.businessVerb(),
            r.category(), r.shouldInstrument() ? 1 : 0, captureVarsJson,
            r.confidence(), System.currentTimeMillis());
    }

    @Override
    public Optional<AIEnrichmentResult> findEnrichment(String methodBodyHash, String modelName, String promptVersion) {
        List<AIEnrichmentResult> results = jdbc.query("""
            SELECT * FROM node_enrichment
            WHERE method_body_hash = ? AND model_name = ? AND prompt_version = ?
            """,
            (rs, i) -> new AIEnrichmentResult(
                rs.getString("node_id"),
                rs.getString("one_line_summary"),
                rs.getString("detailed_logic"),
                rs.getString("business_noun"),
                rs.getString("business_verb"),
                rs.getString("category"),
                rs.getInt("should_instrument") == 1,
                fromJson(rs.getString("capture_variables")),
                rs.getDouble("confidence"),
                modelName, promptVersion,
                rs.getString("method_body_hash")
            ),
            methodBodyHash, modelName, promptVersion);
        return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
    }

    @Override
    public List<AIEnrichmentResult> findEnrichmentsForGraph(String graphId) {
        return jdbc.query("""
            SELECT ne.node_id, ne.one_line_summary, ne.detailed_logic,
                   ne.business_noun, ne.business_verb, ne.category,
                   ne.should_instrument, ne.capture_variables, ne.confidence,
                   ne.model_name, ne.prompt_version, ne.method_body_hash
            FROM graph_node gn
            JOIN node_enrichment ne ON gn.method_body_hash = ne.method_body_hash
                AND ne.confidence = (
                    SELECT MAX(ne2.confidence) FROM node_enrichment ne2
                    WHERE ne2.method_body_hash = gn.method_body_hash
                )
            WHERE gn.graph_id = ?
            GROUP BY ne.node_id
            ORDER BY ne.should_instrument DESC, ne.confidence DESC
            """,
            (rs, i) -> new AIEnrichmentResult(
                rs.getString("node_id"),
                rs.getString("one_line_summary"),
                rs.getString("detailed_logic"),
                rs.getString("business_noun"),
                rs.getString("business_verb"),
                rs.getString("category"),
                rs.getInt("should_instrument") == 1,
                fromJson(rs.getString("capture_variables")),
                rs.getDouble("confidence"),
                rs.getString("model_name"),
                rs.getString("prompt_version"),
                rs.getString("method_body_hash")
            ),
            graphId);
    }

    // ── Node tracking ────────────────────────────────────────────────────────

    @Override
    public void upsertNode(String graphId, String nodeId, String methodBodyHash, String gitCommit) {
        jdbc.update("""
            INSERT OR REPLACE INTO graph_node (graph_id, node_id, method_body_hash, git_commit, updated_at)
            VALUES (?, ?, ?, ?, ?)
            """,
            graphId, nodeId, methodBodyHash, gitCommit, System.currentTimeMillis());
    }

    @Override
    public List<NodeRecord> getNodes(String graphId) {
        return jdbc.query("""
            SELECT node_id, method_body_hash, git_commit FROM graph_node WHERE graph_id = ?
            """,
            (rs, i) -> new NodeRecord(
                rs.getString("node_id"),
                rs.getString("method_body_hash"),
                rs.getString("git_commit")),
            graphId);
    }

    // ── Deployment history ───────────────────────────────────────────────────

    @Override
    public void recordDeployment(String graphId, String gitCommit, String gitBranch,
                                 int addedCount, int modifiedCount, int removedCount,
                                 String diffJson) {
        jdbc.update("""
            INSERT OR REPLACE INTO graph_deployment
              (graph_id, git_commit, git_branch, added_count, modified_count, removed_count, diff_json, ingested_at)
            VALUES (?,?,?,?,?,?,?,?)
            """,
            graphId, gitCommit, gitBranch, addedCount, modifiedCount, removedCount,
            diffJson, System.currentTimeMillis());
    }

    // ── Graph snapshots ───────────────────────────────────────────────────────

    @Override
    public void saveGraphSnapshot(String graphId, String snapshotJson) {
        jdbc.update("""
            INSERT INTO graph_snapshot (graph_id, snapshot_json, stored_at)
            VALUES (?, ?, ?)
            ON CONFLICT(graph_id) DO UPDATE SET snapshot_json = excluded.snapshot_json,
                                                stored_at     = excluded.stored_at
            """, graphId, snapshotJson, System.currentTimeMillis());
        log.debug("[snapshot] saved graph={} chars={}", graphId, snapshotJson.length());
    }

    @Override
    public Map<String, String> loadAllGraphSnapshots() {
        List<Map<String, Object>> rows = jdbc.queryForList(
            "SELECT graph_id, snapshot_json FROM graph_snapshot ORDER BY stored_at ASC");
        Map<String, String> result = new HashMap<>();
        for (var row : rows) {
            result.put((String) row.get("graph_id"), (String) row.get("snapshot_json"));
        }
        return result;
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private String toJson(Object value) {
        if (value == null) return null;
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private List<CaptureSpec> fromJson(String json) {
        if (json == null || json.isBlank()) return List.of();
        try {
            JsonNode node = objectMapper.readTree(json);
            if (!node.isArray()) return List.of();
            List<CaptureSpec> result = new java.util.ArrayList<>();
            for (JsonNode elem : node) {
                if (elem.isTextual()) {
                    // Backward compat: legacy plain-string format → treat as SCALAR
                    result.add(CaptureSpec.scalar(elem.asText()));
                } else if (elem.isObject()) {
                    String name = elem.path("name").asText("");
                    if (name.isBlank()) continue;
                    String strategy = elem.path("strategy").asText("SCALAR").toUpperCase();
                    List<String> fields = null;
                    if (elem.has("fields") && elem.path("fields").isArray()) {
                        fields = new java.util.ArrayList<>();
                        for (JsonNode f : elem.path("fields")) fields.add(f.asText());
                    }
                    Integer maxDepth = elem.has("maxDepth") ? elem.path("maxDepth").asInt() : null;
                    result.add(new CaptureSpec(name, strategy, fields, maxDepth));
                }
            }
            return result;
        } catch (Exception e) {
            return List.of();
        }
    }
}
