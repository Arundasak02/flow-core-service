# Flow — Architecture Overview

**Status:** Authoritative source of truth
**Scope:** System decomposition, repo responsibilities, data flow, dependency chain, language strategy

---

## Design Philosophy

Flow's architecture enforces a strict boundary between **language-agnostic core** and **language-specific periphery**:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     LANGUAGE-AGNOSTIC CORE                          │
│                                                                     │
│   flow-engine          flow-core-service         Flow UI (future)   │
│   (graph processing)   (ingest, merge, API)      (visualization)    │
│                                                                     │
│   These components work identically regardless of what language      │
│   produced the graph or events. The protocol is the contract.       │
└────────────────────────────────┬────────────────────────────────────┘
                                 │
                    GEF format + Event protocol
                                 │
┌────────────────────────────────┴────────────────────────────────────┐
│                     LANGUAGE-SPECIFIC PERIPHERY                      │
│                                                                     │
│   Adapters (one per language)      Agents (one per runtime)         │
│   ┌──────────────────────┐         ┌──────────────────────┐        │
│   │ flow-java-adapter    │         │ flow-runtime-agent   │        │
│   │ (Java, first)        │         │ (JVM, first)         │        │
│   ├──────────────────────┤         ├──────────────────────┤        │
│   │ flow-python-adapter  │         │ flow-python-agent    │        │
│   │ (future)             │         │ (future)             │        │
│   ├──────────────────────┤         ├──────────────────────┤        │
│   │ flow-node-adapter    │         │ flow-node-agent      │        │
│   │ (future)             │         │ (future)             │        │
│   └──────────────────────┘         └──────────────────────┘        │
│                                                                     │
│   Framework Plugins (per adapter/agent)                             │
│   Spring, Kafka, Redis, gRPC, Django, Express, FastAPI...           │
└─────────────────────────────────────────────────────────────────────┘
```

This split means: adding a new language requires only a new adapter + agent. The core never changes.

---

## The Four Repositories (Current)

| Repository | Type | Language | Role |
|------------|------|----------|------|
| **flow-engine** | Pure Java library | Java 17 | Graph processing, merge, zoom, flow extraction, export |
| **flow-core-service** | Spring Boot microservice | Java 21 | Central service: ingest, store, merge, serve REST APIs |
| **flow-java-adapter** | CLI tool (shaded JAR) | Java 17 | Build-time static scanner for Java source code |
| **flow-runtime-agent** | Java agent (-javaagent) | Java 11+ | Runtime instrumentation for JVM applications |

---

## Repo Responsibilities

### `flow-engine` — Core Graph Processing Library

- **Type:** Pure Java library JAR (zero frameworks)
- **Java:** 17
- **What:** The brain. Loads, validates, zooms, merges, extracts, and exports graphs.
- **Not:** A web service, a database, an HTTP server.
- **Embedded by:** `flow-core-service` as a Maven dependency.
- **Language-aware:** No. It processes `CoreGraph` objects — it doesn't know what language produced them.
- **Pipeline:** Load → Zoom → Validate → Extract → [Merge Runtime] → Export

### `flow-core-service` — Central SaaS Microservice

- **Type:** Spring Boot 3.2 microservice
- **Java:** 21
- **What:** The hub. Receives graphs and events from any adapter/agent, merges via flow-engine, serves REST APIs.
- **Accepts:** Static graphs (`/ingest/static`), Runtime events (`/ingest/runtime/batch`) — from any language's adapter/agent.
- **Stores:** In-memory `GraphStore` for graphs, `RuntimeTraceBuffer` for traces.
- **Serves:** Graph queries, trace timelines, zoom-filtered views, Neo4j export.
- **Language-aware:** No. It works with GEF format and event protocol. The source language is metadata.
- **SaaS readiness:** Multi-tenancy, API keys, persistent storage are on the roadmap.

### `flow-java-adapter` — Java Static Scanner (First Language)

- **Type:** Multi-module Maven CLI tool (shaded JAR)
- **Java:** 17
- **What:** Scans Java source code and produces `flow.json` (GEF format) for ingestion.
- **Plugin system:** Java SPI (`ServiceLoader<FlowPlugin>`) — Spring MVC, Kafka plugins built, Redis/gRPC planned.
- **Publishes to FCS:** `POST /ingest/static`
- **Language-specific:** Yes. This is the Java adapter. Future adapters for Python, Go, Node.js will produce the same GEF output.
- **Critical contract:** Node IDs must match what the Java runtime agent produces.

### `flow-runtime-agent` — JVM Runtime Agent (First Runtime)

- **Type:** Java `-javaagent` using ByteBuddy
- **Java:** 11+ (customer JVM compatibility)
- **What:** Instruments selected packages in a running JVM, emits events to FCS.
- **Ships to FCS:** `POST /ingest/runtime/batch` with `Content-Encoding: gzip`
- **Language-specific:** Yes. This is the JVM agent. Future agents will use language-native instrumentation (e.g., Python `sys.settrace`, Node.js `async_hooks`).
- **Golden rule:** Must NEVER cause the customer application to fail, slow down, or behave differently. Drop data over risk.

---

## Dependency Chain

```
flow-java-adapter  ──(produces)──▶  flow.json (GEF)  ──(consumed by)──▶  flow-core-service
                                                                                │
flow-runtime-agent ──(POST events)──────────────────▶  flow-core-service        │
                                                               │          (Maven depends on)
                                                               │                │
                                                               ▼                ▼
                                                          REST API         flow-engine
```

- `flow-core-service` depends on `flow-engine` via Maven (`com.flow:flow-engine:1.0-SNAPSHOT`)
- `flow-java-adapter` and `flow-runtime-agent` are independent — coupled only by the **nodeId contract**
- `flow-engine` has zero framework dependencies (Jackson only)
- Future adapters/agents will follow the same pattern: independent repos, coupled only by GEF + event protocol

---

## End-to-End Data Flow

### Phase 1: Build-Time Static Analysis

```
Developer's Project → language-specific adapter scans source
  (Java example using flow-java-adapter)
  1. JavaSourceScanner walks all .java files
  2. SpringEndpointScanner finds @GetMapping, @PostMapping, etc.
  3. KafkaScanner finds @KafkaListener, kafkaTemplate.send()
  4. MethodCallAnalyzer traces method call chains
  5. GraphModel aggregates all nodes + edges
  6. GraphExporterJson writes flow.json (GEF format)
  7. GraphPublisher POSTs to FCS (optional)
```

### Phase 2: Graph Ingestion & Processing

```
flow.json ──POST /ingest/static──▶ flow-core-service
  → StaticGraphLoader (parse GEF JSON → CoreGraph)
  → ZoomEngine (assign zoom levels 1–5 based on node type + visibility)
  → GraphValidator (check structure integrity)
  → FlowExtractor (BFS from business-level nodes to discover complete flows)
  → Enriched CoreGraph stored in memory
```

### Phase 3: Runtime Event Merging

```
Running Application (with language-specific agent attached)
  → Agent emits events into bounded, non-blocking pipeline
  → BatchAssembler flushes to FCS: POST /ingest/runtime/batch (gzip JSON)
  → FCS buffers per-trace events in RuntimeTraceBuffer
  → On trace completion/idle-timeout → MergeEngine merges static + runtime
  → Updated CoreGraph with runtime nodes, durations, errors, async hops, variable captures
```

### Phase 4: Querying & Visualization

```
Flow UI / API Consumer
  → GET /graphs                           (list all graphs)
  → GET /graphs/{graphId}                 (full graph with runtime enrichment)
  → GET /flow/{graphId}?zoom=N            (zoomed/sliced view for UI rendering)
  → GET /trace/{traceId}                  (trace timeline with checkpoints + variables)
  → GET /export/neo4j/{graphId}?mode=...  (Cypher export or direct push)
  → JSON response (< 50ms target for in-memory queries)
```

---

## Technology Stack

| Layer | Technology | Used By |
|-------|-----------|---------|
| **Language** | Java 17 / 21 | All current projects |
| **Build** | Maven | All current projects |
| **Source Parsing** | JavaParser 3.26 | flow-java-adapter |
| **CLI** | Picocli 4.7 | flow-java-adapter |
| **Plugin System** | Java SPI (ServiceLoader) | flow-java-adapter |
| **Bytecode Instrumentation** | ByteBuddy 1.14 | flow-runtime-agent |
| **JSON** | Jackson 2.16–2.20 | All projects |
| **Web Framework** | Spring Boot 3.2 | flow-core-service |
| **API Docs** | SpringDoc OpenAPI 2.3 | flow-core-service |
| **Metrics** | Micrometer + Prometheus | flow-core-service |
| **Graph Database** | Neo4j (driver 5.15) | flow-core-service (optional export) |
| **Testing** | JUnit 5, Mockito, Testcontainers | All projects |
| **Logging** | SLF4J + Logback | All projects |

---

## Why This Architecture

| Decision | Rationale |
|----------|-----------|
| Separate repos (adapter, engine, service, agent) | Independent release cycles, single coupling point (nodeId/GEF contract). New language = new repo, not a fork. |
| Engine as pure Java library | Embeddable, testable, no framework overhead. Can run in CLI, in tests, in serverless. |
| In-memory primary store | < 50ms query response. No DB latency on the critical read path. |
| Neo4j as secondary store only | Analytics and deep graph queries. Never blocks ingestion. |
| GEF as universal exchange format | Any adapter in any language produces the same JSON. The core is truly language-agnostic. |
| Event protocol as universal transport | Any agent in any runtime sends the same event shape. The core merges identically. |
| ByteBuddy for JVM agent | Industry standard, safe class transformation, proven at scale. |
| Agent Java 11 minimum | Customer JVM compatibility — many production apps still run 11/17. |
| flow-sdk zero dependencies | Goes into customer classpath. Must never conflict with customer libraries. |
