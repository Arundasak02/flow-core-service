package com.flow.core.service.enrichment;

import java.util.List;

/**
 * What the AI provider returns for a single method node.
 * These 4 fields (+ metadata) are stored in SQLite and served at every zoom level.
 */
public record AIEnrichmentResult(
    String nodeId,
    // User-visible
    String oneLineSummary,    // L2/L3: one sentence, always shown
    String detailedLogic,     // L4: full plain-English, proportional to method complexity
    // Internal — used by EnrichmentPlanner + agent
    String businessNoun,      // e.g. "order", "payment", "inventory"
    String businessVerb,      // e.g. "places", "charges", "reserves"
    String category,          // ENDPOINT_HANDLER | SERVICE | REPOSITORY | UTILITY | MAPPER
    boolean shouldInstrument,
    List<CaptureSpec> captureVariables,  // structured specs: strategy + optional field list
    double confidence,        // 0.0 - 1.0
    // Enrichment metadata
    String modelName,
    String promptVersion,
    String methodBodyHash
) {}
