# Flow Core Service — Service Guide

**Status:** Authoritative source of truth
**Scope:** Spring Boot microservice — ingestion, storage, querying, export, monitoring

---

## What It Is

Flow Core Service (FCS) is the **central, language-agnostic SaaS microservice** that:
- Receives static graphs from any language's adapter (Java first, Python/Go/Node.js planned)
- Receives runtime events from any runtime's agent (JVM first, others planned)
- Serves queries to Flow UI and other tools
- Merges static + runtime into enriched graphs with business context
- Exports graphs to Neo4j and other formats

FCS does not know what language produced a graph or event. It works with GEF format and the event protocol.

**Version:** 0.0.1-SNAPSHOT | **Java:** 21 | **Spring Boot:** 3.2 | **Build:** Maven

---

## Package Structure

```
com.flow.core.service/
├── FlowCoreServiceApplication.java
├── api/
│   ├── controller/           ← REST endpoints (5 controllers)
│   │   ├── StaticIngestController
│   │   ├── RuntimeIngestController
│   │   ├── GraphController
│   │   ├── TraceController
│   │   └── ExportController
│   ├── dto/                  ← Request/response models
│   ├── advice/               ← Global error handling
│   └── health/               ← Custom health indicators
├── ingest/                   ← Async ingestion pipeline
│   ├── IngestionQueue        ← Bounded queue with backpressure
│   ├── IngestionWorker       ← Worker polling the queue
│   ├── StaticGraphHandler    ← Processes static graphs
│   └── RuntimeEventHandler   ← Processes runtime events, triggers merge
├── engine/                   ← Adapters over flow-engine library
│   ├── GraphStore / InMemoryGraphStore
│   ├── MergeEngineAdapter
│   └── FlowExtractorAdapter
├── runtime/                  ← Runtime event buffering & dedup
│   ├── RuntimeTraceBuffer / InMemoryRuntimeTraceBuffer
│   └── EventDeduplicator
├── persistence/              ← Neo4j export
│   ├── Neo4jWriter
│   └── CypherBuilder
└── config/                   ← Spring configuration beans
```

---

## Core Architecture Requirements (Non-Negotiable)

1. **Very fast ingestion** — non-blocking, lightweight
2. **Never block ingestion on DB writes** — Neo4j issues cannot break ingestion
3. **UI queries must be instant (< 50ms)** — in-memory stores
4. **Tolerate duplicates/out-of-order** — dedup + stable merge
5. **Support async hops** — Kafka/queue message flows
6. **Easy to test + iterate** — clean separation of concerns

---

## In-Memory Components

### IngestionQueue

- **Type:** Bounded `BlockingQueue` (configurable, default ~10,000 items)
- **On full:** HTTP endpoints return `429 Too Many Requests`
- **Purpose:** Decouples HTTP ingestion from processing

### GraphStore

- **Type:** `Map<graphId, CoreGraph>` (ConcurrentHashMap)
- **Primary source of truth** for current static + merged graphs
- **Must be in-memory** — merging is CPU-heavy, UI queries must be fast

### RuntimeTraceBuffer

- **Type:** `Map<traceId, RuntimeTrace>`
- **TTL-based eviction** (default: 10 minutes)
- **Deduplication** by event hash or composite key

---

## Merge Pipeline (Critical Path)

```
POST /ingest/runtime
  → IngestionQueue
  → RuntimeEventHandler
  → RuntimeTraceBuffer
  → (on trace completion or batch timer)
  → MergeEngine (from flow-engine)
  → GraphStore (updated in memory)
  → Async export → Neo4jWriter
```

**Principles:**
- Merging never blocks ingestion
- Exporting never blocks merging
- Batching via explicit trace completion, timer, or manual trigger

---

## Configuration

```properties
# Server
server.port=8080

# Ingestion Queue
flow.ingest.queue.capacity=10000
flow.ingest.queue.backpressure-threshold=80
flow.ingest.worker.interval.ms=50

# Data Retention
flow.retention.trace.ttl-minutes=10
flow.retention.trace.max-traces=100000
flow.retention.graph.ttl-minutes=0

# Monitoring
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

Override via environment variables (e.g., `FLOW_INGEST_QUEUE_CAPACITY=20000`).

---

## Custom Metrics

| Metric | Description |
|--------|-------------|
| `flow.ingest.queue.size` | Current queue depth |
| `flow.ingest.queue.capacity` | Queue capacity |
| `flow.ingest.queue.utilization` | Percentage utilized |
| `flow.ingest.static.graphs` | Graphs ingested count |
| `flow.ingest.runtime.events` | Events ingested count |
| `flow.storage.graphs.count` | Graphs in memory |
| `flow.storage.traces.count` | Traces in memory |
| `flow.ingest.rejections` | Queue rejection count |

### Health Checks

- **UP** — Queue < 90% capacity
- **DEGRADED** — Queue 90-100% capacity
- **DOWN** — Queue full, backpressure active

---

## Neo4j Integration

In v1, Neo4j is strictly a **secondary store**. FCS never reads from Neo4j.

Used for: persistence snapshots, analytics, cross-graph joins, external visualization tools.

Primary read/write path is always in-memory.

---

## Build & Run

```bash
# Prerequisites: flow-engine installed in local Maven repo
cd flow-engine && mvn clean install

# Build FCS
cd flow-core-service && mvn clean package

# Run
java -jar target/flow-core-service-0.0.1-SNAPSHOT.jar

# Access
# Swagger UI: http://localhost:8080/swagger-ui.html
# Health:     http://localhost:8080/actuator/health
# Metrics:    http://localhost:8080/actuator/prometheus
```

---

## Extending FCS

### Adding a New Endpoint
1. Create DTO in `api/dto`
2. Add controller method
3. Implement service logic in `application` or `domain`
4. Add tests

### Adding a New Export Format
1. Create exporter in `persistence`
2. Add endpoint in `ExportController`
3. Test with sample graphs
