# flow-core-service — Developer Guide

## Key Classes

| Class | Package | Role |
|---|---|---|
| `FlowCoreServiceApplication` | root | Spring Boot main |
| `IngestionController` | controller | `POST /ingest/static` |
| `RuntimeIngestController` | controller | `POST /ingest/runtime/batch` |
| `FlowQueryController` | controller | `GET /flow/**` |
| `StaticGraphHandler` | handler | Validates + passes to engine |
| `IngestionQueue` | queue | Async buffer for static graphs |
| `IngestionQueueProcessor` | queue | Drains queue, triggers handler |
| `RuntimeTraceBuffer` | buffer | Holds events per traceId |
| `TraceCompletionScheduler` | scheduler | 5s check for 3s-idle traces → merge |
| `MergeEngineAdapter` | adapter | Bridge to flow-engine `MergeEngine` |
| `InMemoryGraphStore` | store | `ConcurrentHashMap<graphId, CoreGraph>` |
| `GraphEvictionManager` | store | Count eviction (max 10,000 graphs) |

## Configuration (application.properties)

```properties
server.port=8080

# When to trigger merge for a trace
flow.retention.trace.idle-timeout-ms=3000
flow.retention.trace.completion-check-interval-ms=5000

# Graph store limits
flow.retention.graph.max-graphs=10000
flow.retention.graph.ttl-minutes=0

# Java 21 virtual threads
spring.threads.virtual.enabled=true

# Optional Neo4j (disabled by default)
# spring.neo4j.uri=bolt://localhost:7687
```

## Static Ingestion Flow (Step by Step)

1. `POST /ingest/static` arrives at `IngestionController`
2. Controller validates non-empty body, extracts `graphId`
3. Enqueues to `IngestionQueue` → returns HTTP 200 immediately
4. `IngestionQueueProcessor` (virtual thread worker) dequeues
5. `StaticGraphHandler.handle(graphId, jsonContent)` is called
6. Calls `FlowCoreEngine.process(graphId, jsonContent)` [from flow-engine]
7. Engine parses GEF 1.1, validates, builds indexes, returns `CoreGraph`
8. `InMemoryGraphStore.put(graphId, coreGraph)` stores it

## Runtime Merge Flow (Step by Step)

1. `POST /ingest/runtime/batch` arrives at `RuntimeIngestController`
2. Controller deduplicates events (by `spanId + type`)
3. `RuntimeTraceBuffer.buffer(traceId, events)` appends to trace's list
4. Controller returns HTTP 200 immediately
5. `TraceCompletionScheduler` runs every 5s:
   - Finds traces where last event time > 3s ago
   - Calls `MergeEngineAdapter.merge(traceId)`
6. Adapter gets events from buffer and existing graph from store
7. Calls `FlowCoreEngine.process(graphId, existingJson, events)` [flow-engine]
8. Engine runs `MergeEngine.mergeStaticAndRuntime(graph, events)` (6 stages)
9. `InMemoryGraphStore.put(graphId, enrichedGraph)` overwrites previous

## Known Issues

See `06-BUGS.md` in this folder.

## Build & Run

```bash
# Build (requires flow-engine in local .m2 first)
mvn clean package -DskipTests

# Run
java -jar target/flow-core-service-0.0.1-SNAPSHOT.jar

# Docker
docker build -t flow-core-service .
docker run -p 7070:8080 flow-core-service
```
