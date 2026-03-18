# Flow — Architecture Overview

**Status:** Authoritative source of truth
**Scope:** System decomposition, repo responsibilities, data flow, dependency chain

---

## The Four Repositories

```
┌──────────────────┐     ┌─────────────┐     ┌───────────────────┐
│ flow-java-adapter│────▶│ flow-engine │────▶│ flow-core-service │────▶ Flow UI (future)
│ (build-time)     │     │ (library)   │     │ (microservice)    │
│                  │     │             │     │                   │
│ Scans source,    │     │ Processes   │     │ Ingests, merges,  │
│ emits flow.json  │     │ graphs,     │     │ stores, serves    │
│                  │     │ merges,     │     │ via REST API      │
└──────────────────┘     │ exports     │     └───────────────────┘
                         └─────────────┘
                                              ┌───────────────────┐
                                              │ flow-runtime-agent│
                                              │ (Java agent)      │
                                              │                   │
                                              │ Instruments JVM,  │
                                              │ emits events      │
                                              └───────────────────┘
```

---

## Repo Responsibilities

### `flow-java-adapter` — Static Graph Producer (build-time)

- **Type:** Multi-module Maven CLI tool (shaded JAR)
- **Java:** 17
- **What:** Scans Java source code (Spring MVC + Kafka) and produces `flow.json` (GEF-like nodes + edges)
- **Plugin system:** Java SPI (`ServiceLoader<FlowPlugin>`) — currently Spring and Kafka plugins
- **Publishes to FCS:** `POST /ingest/static`
- **Key integration:** Node IDs must match what the runtime agent produces

### `flow-engine` — Pure Processing Library

- **Type:** Pure Java library JAR (zero frameworks)
- **Java:** 17
- **What:** Core graph processing: load, validate, zoom, merge runtime, extract flows, export
- **Not:** A web service, a database, an HTTP server
- **Embedded by:** `flow-core-service` as a Maven dependency
- **Pipeline:** Load → Zoom → Validate → Extract → [Merge Runtime] → Export

### `flow-core-service` — Central Microservice (ingest + query + export)

- **Type:** Spring Boot 3.2 microservice
- **Java:** 21
- **What:** HTTP service wrapping `flow-engine`, adding queuing, persistence, monitoring, REST APIs
- **Accepts:** Static graphs (`/ingest/static`), Runtime events (`/ingest/runtime`, `/ingest/runtime/batch`)
- **Stores:** In-memory `GraphStore` for graphs, `RuntimeTraceBuffer` for traces
- **Serves:** Graph queries, trace timelines, zoom-filtered views, Neo4j export

### `flow-runtime-agent` — Runtime Event Producer (run-time)

- **Type:** Java `-javaagent` using ByteBuddy
- **Java:** 11+ (customer JVM compatibility)
- **What:** Instruments selected packages, emits bounded non-blocking events
- **Ships to FCS:** `POST /ingest/runtime/batch` with `Content-Encoding: gzip`
- **Golden rule:** Must NEVER cause the customer application to fail, slow down, or behave differently
- **Safety:** Drops data rather than risks affecting the app

---

## Dependency Chain

```
flow-java-adapter  ──(produces)──▶  flow.json  ──(consumed by)──▶  flow-core-service
                                                                          │
flow-runtime-agent ──(POST events)──▶  flow-core-service                  │
                                               │                    (Maven depends on)
                                               │                          │
                                               ▼                          ▼
                                          REST API                   flow-engine
```

- `flow-core-service` depends on `flow-engine` via Maven (`com.flow:flow-engine:1.0-SNAPSHOT`)
- `flow-java-adapter` and `flow-runtime-agent` are independent — coupled only by the **nodeId contract**
- `flow-engine` has zero framework dependencies (Jackson only)

---

## Why the Architecture Split

The original `flow-core` was a monolithic Spring Boot application. It was split into:

- **`flow-engine`** — Pure Java library. Can be embedded anywhere, tested easily, no Spring overhead
- **`flow-core-service`** — Thin Spring Boot wrapper adding HTTP, queuing, monitoring, export

This follows **clean architecture**: business logic (`flow-engine`) decoupled from infrastructure (`flow-core-service`).

---

## End-to-End Data Flow

### Phase 1: Build-Time Static Analysis

```
Developer's Java Project → flow-java-adapter scans source tree
  1. JavaSourceScanner walks all .java files
  2. SpringEndpointScanner finds @GetMapping, @PostMapping, etc.
  3. KafkaScanner finds @KafkaListener, @Input, @Output
  4. MethodCallAnalyzer traces method call chains
  5. GraphModel aggregates all nodes + edges
  6. GraphExporterJson writes flow.json
```

### Phase 2: Graph Ingestion & Processing

```
flow.json ──POST /ingest/static──▶ flow-core-service
  → StaticGraphLoader (parse JSON → CoreGraph)
  → ZoomEngine (assign zoom levels 1-5)
  → GraphValidator (check structure)
  → FlowExtractor (BFS from endpoints)
  → Enriched CoreGraph stored in memory
```

### Phase 3: Runtime Event Merging

```
Running Application (with flow-runtime-agent attached)
  → Agent emits events into bounded ring buffer
  → BatchAssembler daemon flushes to FCS: POST /ingest/runtime/batch (gzip JSON)
  → FCS buffers per-trace events in RuntimeTraceBuffer
  → On trace completion/idle-timeout → MergeEngine merges static + runtime
  → Updated CoreGraph with runtime nodes, durations, errors, async hops
```

### Phase 4: Querying & Export

```
Flow UI / API Consumer
  → GET /graphs                           (list all)
  → GET /graphs/{graphId}                 (full graph)
  → GET /flow/{graphId}?zoom=N            (zoomed/sliced view)
  → GET /trace/{traceId}                  (trace timeline)
  → GET /export/neo4j/{graphId}?mode=...  (Cypher export or push)
  → JSON response (< 50ms target)
```

---

## Technology Stack

| Layer | Technology | Used By |
|-------|-----------|---------|
| **Language** | Java 17 / 21 | All projects |
| **Build** | Maven | All projects |
| **Source Parsing** | JavaParser 3.26 | flow-java-adapter |
| **CLI** | Picocli 4.7 | flow-java-adapter |
| **Plugin System** | Java SPI (ServiceLoader) | flow-java-adapter |
| **Bytecode Instrumentation** | ByteBuddy 1.14 | flow-runtime-agent |
| **JSON** | Jackson 2.16–2.20 | All projects |
| **Web Framework** | Spring Boot 3.2 | flow-core-service |
| **API Docs** | SpringDoc OpenAPI 2.3 | flow-core-service |
| **Metrics** | Micrometer + Prometheus | flow-core-service |
| **Graph Database** | Neo4j (driver 5.15) | flow-core-service (optional export) |
| **Testing** | JUnit 5, Mockito | All projects |
| **Logging** | SLF4J + Logback | All projects |
