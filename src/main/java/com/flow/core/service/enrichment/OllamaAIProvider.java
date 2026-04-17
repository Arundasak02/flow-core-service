package com.flow.core.service.enrichment;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * AI provider that calls a local Ollama instance.
 * Uses the OpenAI-compatible /v1/chat/completions endpoint.
 *
 * Active when: flow.ai.provider=ollama (default)
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "flow.ai.provider", havingValue = "ollama", matchIfMissing = true)
public class OllamaAIProvider implements AIProvider {

    private final String baseUrl;
    private final String model;
    private final int timeoutSeconds;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public OllamaAIProvider(
        @Value("${flow.ai.ollama.base-url:http://localhost:11434}") String baseUrl,
        @Value("${flow.ai.ollama.model:qwen2.5-coder:7b}") String model,
        @Value("${flow.ai.ollama.timeout-seconds:120}") int timeoutSeconds,
        ObjectMapper objectMapper
    ) {
        this.baseUrl = baseUrl;
        this.model = model;
        this.timeoutSeconds = timeoutSeconds;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    @Override
    public AIEnrichmentResult enrich(NodeEnrichmentRequest req) {
        String prompt = buildPrompt(req);
        log.debug("[ollama] → {} prompt_chars={}", req.nodeId(), prompt.length());
        log.trace("[ollama] full prompt for {}:\n{}", req.nodeId(), prompt);
        String raw = callOllama(prompt);
        log.trace("[ollama] raw response for {}:\n{}", req.nodeId(), raw);
        return parseResponse(raw, req);
    }

    @Override
    public String modelName() {
        return model;
    }

    @Override
    public boolean isAvailable() {
        try {
            HttpRequest ping = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/api/tags"))
                .timeout(Duration.ofSeconds(5))
                .GET().build();
            HttpResponse<Void> response = httpClient.send(ping, HttpResponse.BodyHandlers.discarding());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.warn("Ollama not reachable at {}: {}", baseUrl, e.getMessage());
            return false;
        }
    }

    // ── Prompt ───────────────────────────────────────────────────────────────

    private String buildPrompt(NodeEnrichmentRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior Java engineer analyzing source code to explain it in plain English for documentation.\n\n");

        sb.append("METHOD: ").append(req.className()).append(".").append(req.methodName()).append("\n");
        sb.append("SIGNATURE: ").append(req.signature()).append("\n");
        if (!req.annotations().isEmpty()) {
            sb.append("ANNOTATIONS: ").append(String.join(", ", req.annotations())).append("\n");
        }

        if (req.entryPointPath() != null) {
            sb.append("ENTRY PATH: ").append(req.entryPointPath()).append("\n");
        }

        if (!req.calleeSummaries().isEmpty()) {
            sb.append("\nCALLEE SUMMARIES (already analyzed):\n");
            for (var callee : req.calleeSummaries()) {
                sb.append("- ").append(callee.methodName()).append(": ").append(callee.oneLineSummary()).append("\n");
            }
        }

        if (!req.siblingMethodNames().isEmpty()) {
            sb.append("\nOTHER METHODS IN THIS CLASS: ")
              .append(String.join(", ", req.siblingMethodNames())).append("\n");
        }

        sb.append("\nSOURCE CODE:\n```java\n").append(req.methodBody()).append("\n```\n\n");

        sb.append("""
            Respond ONLY with a JSON object. No explanation, no markdown, no code blocks.
            
            {
              "oneLineSummary": "<one sentence, business-focused, active voice>",
              "detailedLogic": "<plain English explanation proportional to code complexity. Cover every branch, exception, and side effect. 1 sentence for trivial methods, 3-6 sentences for complex ones.>",
              "businessNoun": "<the main domain concept this method touches, e.g. order, payment, user>",
              "businessVerb": "<what this method does to that concept, e.g. creates, charges, validates>",
              "category": "<one of: ENDPOINT_HANDLER, SERVICE, REPOSITORY, UTILITY, MAPPER>",
              "shouldInstrument": <true if this method is business-significant and worth runtime tracing, else false>,
              "captureVariables": [
                <array of capture specs — one object per variable worth capturing at runtime.
                Each spec MUST include:
                  "name"     : variable name as it appears in source (e.g. "ownerId", "result")
                  "strategy" : one of SCALAR | ID_ONLY | SUMMARY | SKIP
                               SCALAR  = primitive, String, or enum — zero cost, always safe
                               ID_ONLY = entity/object, extract only the .id field — very cheap
                               SUMMARY = extract specific named fields at depth 1 — moderate cost
                               SKIP    = large collection, byte[], stream, or anything too large — do not capture
                  "fields"   : (only for SUMMARY) array of field names to extract, e.g. ["id", "firstName"]
                
                Rule of thumb:
                  - int / long / String / enum → SCALAR
                  - Entity parameter (e.g. Owner, Pet) → ID_ONLY unless a few extra fields are diagnostic → SUMMARY
                  - Return value of type Optional<Entity> or Entity → ID_ONLY or SUMMARY
                  - List / Page / Collection → SKIP (too expensive)
                  - BindingResult / Model / HttpServletRequest → SKIP
                
                Example: [{"name":"ownerId","strategy":"SCALAR"},{"name":"result","strategy":"SUMMARY","fields":["id","firstName","lastName"]}]>
              ],
              "confidence": <0.0 to 1.0, how confident you are in this analysis>
            }
            """);

        return sb.toString();
    }

    // ── HTTP call ────────────────────────────────────────────────────────────

    private String callOllama(String prompt) {
        try {
            Map<String, Object> body = Map.of(
                "model", model,
                "messages", List.of(Map.of("role", "user", "content", prompt)),
                "stream", false,
                "options", Map.of("temperature", 0.1)
            );
            String bodyJson = objectMapper.writeValueAsString(body);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(baseUrl + "/v1/chat/completions"))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(bodyJson))
                .build();

            log.debug("[ollama] POST {} model={} body_chars={}", baseUrl + "/v1/chat/completions", model, bodyJson.length());
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            log.debug("[ollama] ← status={} response_chars={}", response.statusCode(), response.body().length());

            if (response.statusCode() != 200) {
                log.warn("Ollama returned status {}: {}", response.statusCode(), response.body());
                return null;
            }
            return response.body();

        } catch (Exception e) {
            log.error("Ollama call failed: {}", e.getMessage());
            return null;
        }
    }

    // ── Response parsing ─────────────────────────────────────────────────────

    private AIEnrichmentResult parseResponse(String raw, NodeEnrichmentRequest req) {
        if (raw == null) return fallback(req);
        try {
            JsonNode root = objectMapper.readTree(raw);
            String content = root.path("choices").path(0).path("message").path("content").asText("");
            if (content.isBlank()) return fallback(req);

            // Strip any accidental markdown fence
            content = content.strip();
            if (content.startsWith("```")) {
                content = content.replaceFirst("```[a-z]*\\n?", "").replaceAll("```$", "").strip();
            }

            JsonNode json = objectMapper.readTree(content);

            List<CaptureSpec> captureVars = parseCaptureVariables(json.path("captureVariables"));
            String category = validateCategory(json.path("category").asText("SERVICE"), req.methodName(), req.nodeId());
            boolean shouldInstrument = validateShouldInstrument(json.path("shouldInstrument").asBoolean(false), category, req.nodeId());

            return new AIEnrichmentResult(
                req.nodeId(),
                json.path("oneLineSummary").asText(""),
                json.path("detailedLogic").asText(""),
                json.path("businessNoun").asText(""),
                json.path("businessVerb").asText(""),
                category,
                shouldInstrument,
                captureVars,
                json.path("confidence").asDouble(0.5),
                model,
                req.promptVersion(),
                extractHash(req)
            );
        } catch (Exception e) {
            log.warn("Failed to parse Ollama response for {}: {}", req.nodeId(), e.getMessage());
            return fallback(req);
        }
    }

    private AIEnrichmentResult fallback(NodeEnrichmentRequest req) {
        return new AIEnrichmentResult(
            req.nodeId(), "", "", "", "", "SERVICE",
            false, List.of(), 0.0, model, req.promptVersion(), extractHash(req));
    }

    /**
     * Rule-based category correction for well-known misclassification patterns.
     * Applied after AI parsing to prevent bootstrapping methods being labelled ENDPOINT_HANDLER etc.
     */
    private String validateCategory(String aiCategory, String methodName, String nodeId) {
        if ("main".equals(methodName)) return "UTILITY";
        if (nodeId.contains("Test#") || nodeId.contains("Test.") || nodeId.endsWith("Test")) return "UTILITY";
        return aiCategory;
    }

    /**
     * Rule-based shouldInstrument override.
     * UTILITY nodes carry no business signal; test nodes and bootstrap entry points must never be traced.
     */
    private boolean validateShouldInstrument(boolean aiDecision, String category, String nodeId) {
        if ("UTILITY".equals(category)) return false;
        if (nodeId.contains("Test#") || nodeId.contains("Test.") || nodeId.endsWith("Test")) return false;
        if (nodeId.endsWith("#main(String[]):void") || nodeId.endsWith("#main(String[])")) return false;
        return aiDecision;
    }

    /**
     * Parses captureVariables from AI response with backward compatibility.
     * New format: [{"name":"x","strategy":"SCALAR"}, {"name":"y","strategy":"SUMMARY","fields":["a","b"]}]
     * Legacy format (plain strings): ["ownerId", "result"] → each wrapped as SCALAR spec
     */
    private List<CaptureSpec> parseCaptureVariables(JsonNode node) {
        if (node == null || node.isMissingNode() || !node.isArray()) return List.of();
        List<CaptureSpec> result = new ArrayList<>();
        for (JsonNode elem : node) {
            if (elem.isTextual()) {
                // Legacy plain-string format — treat as SCALAR for safety
                result.add(CaptureSpec.scalar(elem.asText()));
            } else if (elem.isObject()) {
                String name = elem.path("name").asText("");
                if (name.isBlank()) continue;
                String strategy = elem.path("strategy").asText("SCALAR").toUpperCase();
                List<String> fields = null;
                if (elem.has("fields") && elem.path("fields").isArray()) {
                    fields = new ArrayList<>();
                    for (JsonNode f : elem.path("fields")) fields.add(f.asText());
                }
                Integer maxDepth = elem.has("maxDepth") ? elem.path("maxDepth").asInt() : null;
                result.add(new CaptureSpec(name, strategy, fields, maxDepth));
            }
        }
        return result;
    }

    private String extractHash(NodeEnrichmentRequest req) {
        // The hash was put in the request by EnrichmentPlanner
        return req.methodBody() != null ? sha256(req.methodBody()) : null;
    }

    private static String sha256(String input) {
        try {
            var digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) hex.append(String.format("%02x", b));
            return hex.toString();
        } catch (Exception e) {
            return null;
        }
    }
}
