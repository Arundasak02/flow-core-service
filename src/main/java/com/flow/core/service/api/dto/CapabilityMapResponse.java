package com.flow.core.service.api.dto;

import com.flow.core.service.enrichment.CapabilityGroup;

import java.util.List;

/**
 * Response for GET /graphs/{graphId}/capabilities.
 * Provides the English-first entry point into a service: grouped capabilities
 * with plain-English descriptions, derived from AI enrichment + graph structure.
 */
public record CapabilityMapResponse(
    String graphId,

    /**
     * One or two sentences describing what this service does overall.
     * Example: "Manages pet owner profiles and their associated pets."
     */
    String serviceDescription,

    /** Capability groups, ordered by descending trigger count. */
    List<CapabilityGroup> capabilities,

    /** Total number of entry-point triggers (ENDPOINT + TOPIC nodes). */
    int totalEntryPoints,

    /** True when AI enrichment data was available to build descriptions. */
    boolean enrichmentAvailable
) {}
