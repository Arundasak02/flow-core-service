package com.flow.core.service.enrichment;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * No-op AI provider — returns empty enrichment for every node.
 * Active when flow.ai.provider=noop or flow.ai.enabled=false.
 * Ensures the graph pipeline works even without an AI model configured.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "flow.ai.provider", havingValue = "noop")
public class NoOpAIProvider implements AIProvider {

    @Override
    public AIEnrichmentResult enrich(NodeEnrichmentRequest request) {
        return new AIEnrichmentResult(
            request.nodeId(), "", "", "", "", "SERVICE",
            false, List.of(), 0.0, modelName(), request.promptVersion(), null);
    }

    @Override
    public String modelName() {
        return "noop";
    }

    @Override
    public boolean isAvailable() {
        return true;
    }
}
