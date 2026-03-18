# Flow — Roadmap & Current Status

**Status:** Authoritative source of truth
**Scope:** What's built, what's next, phased delivery plans

---

## What's Built

| Component | Status |
|-----------|--------|
| **flow-java-adapter** — Spring & Kafka scanning, CLI runner, GEF export, direct publish to FCS | Complete |
| **flow-engine** — Full pipeline: load, zoom, validate, extract, merge, export | Complete |
| **flow-engine** — Runtime engine: events, trace buffer, merge, flow extraction | Complete |
| **flow-core-service** — REST API, ingestion queue, graph store, trace buffer, Neo4j export | Complete |
| **flow-runtime-agent** — Phase 1: Method-level tracing with ByteBuddy, async transport, circuit breaker | Complete |
| **flow-runtime-agent** — Distributed tracing context propagation (W3C, Flow headers, B3) | Complete |

---

## Runtime Agent Phases

| Phase | Scope | Status |
|-------|-------|--------|
| **Phase 1** | Method-level tracing (enter/exit/error), async HTTP transport, circuit breaker | Done |
| **Phase 2** | OTel bridge for external systems (DB, Redis, Kafka via OpenTelemetry) | Planned |
| **Phase 3** | Checkpoint SDK (`Flow.checkpoint(key, value)`) | Planned |
| **Phase 4** | Cross-service stitching + async support (W3C traceparent, @Async wrapping) | In Progress |
| **Phase 5** | Production hardening (adaptive sampling, TLS, JMH benchmarks, < 3% overhead) | Planned |
| **Phase 6** | Advanced (Java 21 virtual threads / ScopedValue, dynamic attach, remote config) | Future |

---

## Platform-Level Roadmap

### High Priority

| Feature | Target Repo |
|---------|-------------|
| **Flow UI** — Web-based zoomable graph visualization | New project |
| **OTel Bridge** — External system instrumentation | flow-runtime-agent |
| **Checkpoint SDK** — Developer-defined checkpoints | flow-runtime-agent |

### Medium Priority

| Feature | Target Repo |
|---------|-------------|
| Additional language adapters (Python, Node.js, .NET) | New `flow-*-adapter` repos |
| Graph compression/summarization | flow-engine |
| Cycle detection and handling | flow-engine |
| Path finding (shortest path, all paths) | flow-engine |
| Batch ingestion endpoint for agent (gzip) | flow-core-service |

### Future

| Feature | Target Repo |
|---------|-------------|
| Multi-tenancy | flow-core-service |
| Persistent storage (replace in-memory) | flow-core-service |
| Horizontal scaling (shared queue, sharding) | flow-core-service |
| Graph versioning and time-travel queries | flow-core-service |
| Real-time WebSocket updates for Flow UI | flow-core-service |
| Advanced analytics (bottleneck detection, anomaly detection) | flow-engine |
| GraphML / Protobuf export formats | flow-engine |
| Custom zoom policies | flow-engine |
| Community detection (graph clustering) | flow-engine |
| Integration with tracing systems (Jaeger, Datadog) | flow-core-service |

---

## Deployment Tiers

| Tier | Description |
|------|-------------|
| **Individual** | Single developer, local mode, in-memory only |
| **Team** | Shared FCS instance, multiple services, basic auth |
| **Enterprise** | Multi-tenant, persistent storage, SSO, horizontal scaling |

---

## Key Decisions Made

| Decision | Rationale |
|----------|-----------|
| Separate repos (adapter, engine, service, agent) | Independent release cycles, single coupling point (nodeId contract) |
| Engine as pure Java library | Embeddable, testable, no framework overhead |
| In-memory primary store | < 50ms query response, no DB latency on critical path |
| Neo4j as secondary store only | Analytics, never blocks ingestion |
| ByteBuddy for agent instrumentation | Industry standard, safe class transformation |
| Hybrid OTel approach | Flow owns method-level (ByteBuddy), OTel provides external systems — zero overlap |
| Agent Java 11 minimum | Customer JVM compatibility |
| flow-sdk zero dependencies | Goes into customer classpath, must not conflict |
