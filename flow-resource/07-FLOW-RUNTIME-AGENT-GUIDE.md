# Flow Runtime Agent — Agent Guide

**Status:** Authoritative source of truth
**Scope:** Java agent — instrumentation, safety, pipeline, transport, configuration

---

## What It Is

A **Java agent** (`-javaagent:flow-agent.jar`) that observes method executions inside a customer's JVM and sends lightweight events to Flow Core Service so the static architecture graph can be animated in real time.

**One sentence:** *The bridge between a running Java application and the Flow architecture graph.*

**Type:** `-javaagent` shaded JAR | **Java:** 11+ (customer JVM compatibility) | **Instrumentation:** ByteBuddy

---

## The Golden Rule

> **The agent must NEVER cause the customer application to fail, slow down, or behave differently.**
> If in doubt, DROP data rather than risk affecting the app.

---

## Module Layout

```
flow-runtime-agent/
├── pom.xml                          ← parent POM
├── flow-agent/                      ← the -javaagent module
│   └── src/main/java/com/flow/agent/
│       ├── FlowAgent.java                  ← premain() entry point
│       ├── config/                         ← AgentConfig, ConfigLoader
│       ├── instrumentation/                ← FlowTransformer, MethodAdvice, NodeIdBuilder, ProxyResolver
│       ├── context/                        ← FlowContext (ThreadLocal), SpanInfo, TraceIdGenerator
│       ├── pipeline/                       ← FlowEventSink, RuntimeEvent, EventRingBuffer, BatchAssembler
│       ├── transport/                      ← HttpBatchSender, CircuitBreaker, PayloadSerializer
│       ├── filter/                         ← FilterChain, PackageFilter, MethodExcludeFilter, BridgeMethodFilter
│       ├── sampling/                       ← Sampler interface, AlwaysSampler
│       └── monitor/                        ← AgentMetrics (AtomicLong counters)
└── flow-sdk/                        ← Checkpoint SDK (zero dependencies)
    └── src/main/java/com/flow/sdk/
        └── Flow.java                       ← Flow.checkpoint(key, value) — no-op without agent
```

---

## Three-Layer Architecture

### Layer 1 — Bytecode Instrumentation (ByteBuddy)

- `FlowTransformer` installs advice on customer-package classes only
- `MethodAdvice` inlines into every instrumented method (must be nanosecond-fast)
- `NodeIdBuilder` constructs nodeId from runtime class + method (cached per Method)
- `ProxyResolver` resolves CGLIB/JDK proxies to real classes
- `FilterChain` decides at class-load time (zero per-call overhead)

### Layer 2 — Event Pipeline (In-Process)

- `FlowEventSink` — static entry point for emitting events from advice
- `EventRingBuffer` — bounded, non-blocking (`offer()`, never `put()`)
- `BatchAssembler` — daemon thread: drain ring buffer, assemble batches, flush on count or timer trigger

### Layer 3 — Transport & Delivery

- `HttpBatchSender` — async HTTP POST to FCS (`java.net.http.HttpClient`)
- `PayloadSerializer` — Jackson JSON + GZIP compression
- `CircuitBreaker` — CLOSED → OPEN (after N failures) → HALF_OPEN → CLOSED

---

## Configuration

### Minimum Viable Config

```bash
java -javaagent:flow-agent.jar \
     -Dflow.server.url=http://localhost:8080 \
     -Dflow.graph-id=order-service \
     -Dflow.packages.include=com.greens.order \
     -jar my-app.jar
```

### Configuration Sources (Priority Order)

1. System properties (`-Dflow.server.url=...`) — highest
2. Environment variables (`FLOW_SERVER_URL=...`)
3. Config file (`flow-agent.yml` via `-Dflow.config=/path`)
4. Built-in defaults — lowest

### Full Configuration Schema

```yaml
flow:
  enabled: true                          # Kill switch
  server:
    url: "http://localhost:8080"         # REQUIRED — FCS base URL
    api-key: "${FLOW_API_KEY}"
    connect-timeout-ms: 5000
    read-timeout-ms: 5000
  graph-id: "order-service"              # REQUIRED
  packages:
    include: ["com.greens.order"]        # REQUIRED
    exclude: []
  filter:
    skip-getters-setters: true
    skip-constructors: true
    skip-private-methods: false
    skip-synthetic: true
  sampling:
    rate: 1.0                            # 1.0 = 100%
  pipeline:
    buffer-size: 8192
    batch-size: 100
    flush-interval-ms: 200
  circuit-breaker:
    failure-threshold: 3
    reset-timeout-ms: 30000
```

---

## Safety Checklist (Non-Negotiable)

```
□ All @Advice code wrapped in try-catch — exceptions NEVER propagate
□ Ring buffer uses offer() (non-blocking) — NEVER put() (blocking)
□ HTTP sends are async (sendAsync) — NEVER synchronous
□ Circuit breaker drops events when OPEN — NEVER queues unboundedly
□ FlowContext.clear() called on every entry point exit — NEVER left stale
□ Only daemon threads (2 max) — pipeline + metrics
□ No unbounded collections — all data structures have size caps
□ No data capture — no args, no return values, no payloads, no PII
□ Class transformation failure → log + skip — NEVER crash the app
□ Per-method overhead < 300ns — NEVER do I/O on the app thread
□ Total agent heap < 30MB
```

---

## Performance Budget

| Metric | Ceiling |
|--------|---------|
| Per-method enter advice | < 100ns |
| Per-method exit advice | < 200ns |
| Total per method call | < 300ns |
| Max events/sec (pre-sampling) | 50,000 |
| Max batches/sec to FCS | 10 |
| Max HTTP payload size | 100KB compressed |
| Agent daemon threads | 2 |
| Agent heap usage | < 30MB |

---

## Phased Delivery

| Phase | Scope |
|-------|-------|
| **Phase 1** | Method-level tracing — enter/exit/error events for customer code |
| **Phase 2** | OTel bridge — external systems (DB, Redis, Kafka) via OpenTelemetry |
| **Phase 3** | Checkpoint SDK — `Flow.checkpoint(key, value)` |
| **Phase 4** | Cross-service + async — W3C traceparent, @Async wrapping |
| **Phase 5** | Production hardening — adaptive sampling, TLS, benchmarks |
| **Phase 6** | Advanced — virtual threads (ScopedValue), dynamic attach, remote config |

---

## Kill Switch

```bash
java -javaagent:flow-agent.jar -Dflow.enabled=false -jar my-app.jar
```

Or simply remove the `-javaagent` flag and restart.
