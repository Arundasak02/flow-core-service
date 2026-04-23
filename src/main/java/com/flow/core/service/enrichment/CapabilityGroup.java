package com.flow.core.service.enrichment;

import java.util.List;

/**
 * One business capability: a named group of entry points (endpoints/topics)
 * that share a common domain concept (businessNoun).
 *
 * Example: "Owner Management" groups all /owners/* endpoints.
 */
public record CapabilityGroup(
    /** Human-readable group name: "Owner Management", "Pet Management", etc. */
    String name,

    /** The domain noun that ties these triggers together: "owner", "pet", etc. */
    String noun,

    /** Ordered list of entry-point triggers in this capability group. */
    List<TriggerItem> triggers
) {
    public record TriggerItem(
        /** Node ID of the ENDPOINT node. */
        String endpointId,

        /** HTTP method: GET, POST, PUT, DELETE — or "TOPIC" for messaging. */
        String httpMethod,

        /** URL path or topic name: "/owners/{ownerId}", "order.created". */
        String path,

        /** One-sentence English description of what this trigger does. */
        String description,

        /** Node ID of the METHOD that handles this trigger (via HANDLES edge). */
        String handlerNodeId,

        /** Category from AI enrichment, or null if not enriched. */
        String category
    ) {}
}
