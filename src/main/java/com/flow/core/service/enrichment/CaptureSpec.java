package com.flow.core.service.enrichment;

import java.util.List;

/**
 * AI-recommended capture specification for a single variable at a method boundary.
 *
 * <p>The strategy controls how the runtime agent extracts the value — balancing
 * observability against serialization cost and client-app latency:
 *
 * <ul>
 *   <li>{@code SCALAR}  — primitive, String, or enum. Zero serialization cost. Always safe.</li>
 *   <li>{@code ID_ONLY} — extract only the {@code .id} field (or equivalent PK).
 *                         One field read, no recursion.</li>
 *   <li>{@code SUMMARY} — extract the named {@code fields} at depth 1. Low cost.
 *                         Use when a few key fields are more diagnostic than just the ID.</li>
 *   <li>{@code SKIP}    — do not capture. Use for large collections, byte[], response streams,
 *                         or anything that would serialize to thousands of bytes.</li>
 * </ul>
 *
 * <p>The agent maps this to {@link com.flow.sdk.FlowCapture} call-site overrides:
 * <pre>
 *   SCALAR   → FlowCapture not needed, raw value passed directly
 *   ID_ONLY  → FlowCapture.include("id").withMaxDepth(0)
 *   SUMMARY  → FlowCapture.include(fields).withMaxDepth(1)
 *   SKIP     → variable not captured
 * </pre>
 */
public record CaptureSpec(
    String name,            // variable name, e.g. "ownerId", "result", "owner"
    String strategy,        // SCALAR | ID_ONLY | SUMMARY | SKIP
    List<String> fields,    // for SUMMARY: which fields to extract (nullable for other strategies)
    Integer maxDepth        // override for SUMMARY depth; null = use strategy default
) {
    /** Convenience factory for zero-cost scalar capture (primitives, Strings, enums). */
    public static CaptureSpec scalar(String name) {
        return new CaptureSpec(name, "SCALAR", null, null);
    }

    /** Convenience factory for ID-only capture (entity objects where only PK matters). */
    public static CaptureSpec idOnly(String name) {
        return new CaptureSpec(name, "ID_ONLY", null, null);
    }

    /** Convenience factory for summary capture with named fields. */
    public static CaptureSpec summary(String name, List<String> fields) {
        return new CaptureSpec(name, "SUMMARY", fields, 1);
    }

    public boolean shouldSkip() {
        return "SKIP".equalsIgnoreCase(strategy);
    }
}
