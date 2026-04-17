package com.flow.core.service.enrichment;

import java.util.List;

/**
 * All context assembled for one method node before calling the AI provider.
 * Layers 1-5 of the context pyramid are populated here.
 */
public record NodeEnrichmentRequest(
    String nodeId,
    String methodName,
    String className,
    String packageName,
    String signature,
    String methodBody,
    List<String> annotations,
    // Layer 3 — call graph context
    List<String> callerNodeIds,
    List<CalleeSummary> calleeSummaries,
    String entryPointPath,           // e.g. "POST /orders → createOrder → ..."
    // Layer 4 — domain vocabulary
    List<String> siblingMethodNames, // other methods in same class
    // metadata
    String graphId,
    String promptVersion
) {
    public record CalleeSummary(String nodeId, String methodName, String oneLineSummary) {}
}
