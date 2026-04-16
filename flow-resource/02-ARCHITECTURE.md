# Architecture Overview

> Full architecture detail: `flow-docs/technical/architecture.md` in workspace root.

## System Diagram

```
flow-adapter-java ──────POST /ingest/static───────────────────────┐
  (build-time CLI)                                                  │
                                                                    ▼
flow-runtime-agent ──POST /ingest/runtime/batch──▶  flow-core-service (this)
  (JVM bytecode agent)                               ┌─────────────────────┐
                                                     │  flow-engine (lib)  │
flow-interface ──────────GET /flow/{graphId}────────▶│                     │
  (React UI)                                         └─────────────────────┘
```

## This Service's Responsibility

1. **Receive** static graphs from adapter (`POST /ingest/static`)
2. **Receive** runtime event batches from agent (`POST /ingest/runtime/batch`)
3. **Merge** them via embedded flow-engine (after 3s idle)
4. **Serve** enriched graphs to UI (`GET /flow/{graphId}`)

## Internal Architecture

### Static Ingestion Pipeline
```
IngestionController → IngestionQueue → IngestionQueueProcessor
  → StaticGraphHandler → FlowCoreEngine.process() → InMemoryGraphStore
```

### Runtime Ingestion + Merge Pipeline
```
RuntimeIngestController → RuntimeTraceBuffer
  → TraceCompletionScheduler (every 5s, triggers at 3s idle)
  → MergeEngineAdapter → FlowCoreEngine.process(graph, events)
  → InMemoryGraphStore (overwrites existing graph)
```

### Query Pipeline
```
FlowQueryController → InMemoryGraphStore → FlowExtractor (BFS + zoom filter) → JSON response
```

## Key Technologies

- **Java 21** with virtual threads enabled (`spring.threads.virtual.enabled=true`)
- **Spring Boot 3.2** for HTTP, DI, Actuator
- **ConcurrentHashMap** for all in-memory state
- **flow-engine** embedded as Maven dependency for all graph processing
- **Neo4j** optional export (disabled by default)

## Ports

| Environment | Port |
|---|---|
| Spring embedded (local) | 8080 |
| Docker (Dockerfile EXPOSE) | 7070 |
| UI dev proxy target | 7070 |

## Graph Storage

- **Engine:** `InMemoryGraphStore` using `ConcurrentHashMap<String, CoreGraph>`
- **Eviction:** Count-based (max 10,000 graphs, evicts oldest by `lastUpdated`)
- **TTL:** Disabled by default (`flow.retention.graph.ttl-minutes=0`)
- **Restart behavior:** All graphs lost on restart
