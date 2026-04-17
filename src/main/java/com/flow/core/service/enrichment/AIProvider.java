package com.flow.core.service.enrichment;

/**
 * Pluggable AI provider interface.
 * Implementations: OllamaAIProvider, OpenAICompatibleProvider, NoOpAIProvider.
 */
public interface AIProvider {

    /**
     * Enrich a single method node.
     * Implementations must be thread-safe — called concurrently from the enrichment pool.
     */
    AIEnrichmentResult enrich(NodeEnrichmentRequest request);

    /** Human-readable name used in enrichment cache key (e.g. "qwen2.5-coder:7b"). */
    String modelName();

    /** Returns true if this provider is expected to work in the current environment. */
    boolean isAvailable();
}
