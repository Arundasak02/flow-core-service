# Flow — Contracts & Protocols

**Status:** Authoritative source of truth
**Scope:** Critical cross-repo contracts that must never be broken without coordinated changes

> **Language Note:** These contracts are language-agnostic at the protocol level. The GEF format and event payload
> shape are identical regardless of which language's adapter/agent produced them. Language-specific details
> (e.g., Java nodeId format) are noted as examples — each language adapter defines its own nodeId construction
> rules while conforming to the same structural contract.

---

## 1. The graphId Contract

`graphId` is the **join key** between:
- Static graphs ingested from the adapter
- Runtime event batches emitted by the agent

If you version graphs, the agent must send the matching versioned `graphId`.

---

## 2. The nodeId Contract (Static ↔ Runtime Correlation)

> **This is the single most critical contract in the entire system.**
> If the agent produces a nodeId that differs by even one character from what the scanner produced, the MergeEngine cannot link the runtime event to the graph node. That node stays dark.

### Format

```
{fully.qualified.ClassName}#{methodName}({paramType1}, {paramType2}):{returnType}
```

### Examples

```
com.greens.order.core.OrderService#placeOrder(String):String
com.greens.order.core.OrderService#validateCart(String):void
com.greens.order.core.PaymentService#charge(String):void
com.greens.order.config.KafkaProducerConfig#kafkaTemplate():KafkaTemplate<String, String>
com.greens.order.messaging.OrderConsumer#listen(String):void
```

### Type Simplification Rules

Both the scanner and agent must apply **identical** simplification:

| JVM Type | Simplified |
|---|---|
| `java.lang.String` | `String` |
| `java.lang.Integer` | `Integer` |
| `java.lang.Long` | `Long` |
| `java.lang.Boolean` | `Boolean` |
| `java.lang.Object` | `Object` |
| `java.lang.Double` | `Double` |
| `java.lang.Float` | `Float` |
| `java.lang.Byte` | `Byte` |
| `java.lang.Short` | `Short` |
| `java.lang.Character` | `Character` |
| `java.util.List` | `List` |
| `java.util.Map` | `Map` |
| `java.util.Set` | `Set` |
| `java.util.Optional` | `Optional` |
| `void` | `void` |
| All primitives | As-is |
| Everything else | Fully qualified |

### Generics Preservation

The agent MUST use `getGenericReturnType()` and `getGenericParameterTypes()` — NOT erased types.

### Proxy Resolution

| Proxy Pattern | Resolution |
|---|---|
| `OrderService$$SpringCGLIB$$0` | Walk superclass chain → `OrderService` |
| `com.sun.proxy.$Proxy123` | Inspect interfaces → customer-package interface |
| Lambdas (`$$Lambda$42`) | **Skip** — not in static graph |
| Bridge methods | **Skip** — `method.isBridge()` |
| Synthetic methods | **Skip** — `method.isSynthetic()` |

### External System nodeId Formats

| System | Pattern | Example |
|---|---|---|
| HTTP endpoint | `endpoint:{METHOD} {path}` | `endpoint:POST /api/orders/{id}` |
| Kafka topic | `topic:{topicName}` | `topic:orders.v1` |
| Database | `db:{dataSource}:query:{table}` | `db:primary:query:orders` |
| Redis | `cache:redis:{host}:{op}:{pattern}` | `cache:redis:localhost:GET:order:*` |
| gRPC | `grpc:{service}/{method}` | `grpc:PaymentService/Charge` |

---

## 3. Runtime Event Payload Format

### Batch Payload (Agent → FCS)

```
POST {flow.server.url}/ingest/runtime/batch
Content-Type: application/json
Content-Encoding: gzip
Authorization: Bearer {api-key}
```

```json
{
  "graphId": "order-service",
  "agentVersion": "0.1.0",
  "batch": [
    {
      "traceId": "550e8400-e29b-41d4-a716-446655440000",
      "spanId": "7a2b3c4d5e6f",
      "parentSpanId": null,
      "nodeId": "com.greens.order.core.OrderService#placeOrder(String):String",
      "type": "METHOD_ENTER",
      "timestamp": 1735412200100,
      "durationMs": 0,
      "errorType": null
    }
  ]
}
```

### Event Types

| Type | When Emitted | durationMs | errorType |
|---|---|---|---|
| `METHOD_ENTER` | Method begins | Always `0` | `null` |
| `METHOD_EXIT` | Method completes normally | Actual ms | `null` |
| `ERROR` | Method throws exception | Actual ms | Exception class name |
| `PRODUCE_TOPIC` | Kafka message produced | `0` | `null` |
| `CONSUME_TOPIC` | Kafka message consumed | `0` | `null` |
| `CHECKPOINT` | Developer checkpoint (`Flow.checkpoint(key, value)`) | `0` | `null` |
| `VARIABLE_CAPTURE` | Runtime variable snapshot at checkpoint (planned) | `0` | `null` |

### Checkpoint & Variable Capture Payload (Planned)

When a developer calls `Flow.checkpoint("cart_validated", cartData)`, the agent emits:

```json
{
  "traceId": "550e8400-e29b-41d4-a716-446655440000",
  "spanId": "7a2b3c4d5e6f",
  "nodeId": "com.greens.order.core.OrderService#validateCart(String):void",
  "type": "CHECKPOINT",
  "timestamp": 1735412200200,
  "data": {
    "key": "cart_validated",
    "businessLabel": "Cart validation completed",
    "captures": {
      "cartTotal": 250.50,
      "itemCount": 3,
      "customerId": "***REDACTED***"
    }
  }
}
```

**PII Rules:** Fields matching `@FlowExclude` annotations or global patterns (`*password*`, `*email*`, `*ssn*`) are redacted at the agent before transmission. Only `@FlowInclude` or explicitly captured fields are sent.

---

## 4. Runtime Event Semantics (Merge Expectations)

At merge time, the engine expects to:

- **Pair enter/exit events** to compute durations
- **Attach errors** to the relevant node/span
- **Aggregate checkpoints** onto nodes
- **Stitch async hops** (e.g., Kafka) using shared topic identifier/correlation

---

## 5. Backpressure & Safety Rules

| Rule | Rationale |
|---|---|
| Agent must never block the application thread | Drop data over risk |
| Agent ring buffer uses non-blocking `offer()` | Full → event silently dropped |
| Agent HTTP sends are async (`sendAsync`) | Never sync on hot path |
| Agent circuit breaker drops events when OPEN | Never queues unboundedly |
| Core-service ingestion must not block on Neo4j | DB issues never break ingestion |
| Core-service must tolerate duplicates/out-of-order | Dedup + stable merge |
| FCS returns `429 Too Many Requests` when queue full | Explicit backpressure signal |

---

## 6. Graph Exchange Format (GEF 1.1)

### flow.json Structure

```json
{
  "graphId": "payment-service",
  "nodes": [
    {
      "id": "unique-node-id",
      "type": "METHOD|ENDPOINT|TOPIC|CLASS|SERVICE|PRIVATE_METHOD",
      "name": "Display Name",
      "data": {
        "visibility": "public|private",
        "className": "...",
        "packageName": "...",
        "signature": "...",
        "httpMethod": "...",
        "path": "..."
      }
    }
  ],
  "edges": [
    {
      "id": "edge-id",
      "from": "source-node-id",
      "to": "target-node-id",
      "type": "CALL|HANDLES|PRODUCES|CONSUMES|BELONGS_TO|DEFINES"
    }
  ]
}
```

### Node Types

| Type | Description | Zoom Level |
|------|-------------|------------|
| `ENDPOINT` | HTTP REST endpoint | 1 (Business) |
| `TOPIC` | Kafka/messaging topic | 1 (Business) |
| `SERVICE` | Service class | 2 (Service) |
| `CLASS` | Regular class | 2 (Service) |
| `METHOD` | Public method | 3 (Public) |
| `PRIVATE_METHOD` | Private method | 4 (Private) |
| `RUNTIME_CALL` | Runtime-discovered | 5 (Runtime) |

### Edge Types

| Type | Description |
|------|-------------|
| `CALL` | Method invokes method |
| `HANDLES` | Endpoint handled by method |
| `PRODUCES` | Method publishes to topic |
| `CONSUMES` | Method consumes from topic |
| `BELONGS_TO` | Method belongs to class |
| `DEFINES` | Class defines method |
| `RUNTIME_CALL` | Runtime-observed method call |
| `ASYNC_HOP` | Asynchronous message hop (Kafka) |

---

## 7. FCS REST API Contract Summary

| Method | Endpoint | Purpose | Response |
|--------|----------|---------|----------|
| `POST` | `/ingest/static` | Ingest static graph | `202 Accepted` |
| `POST` | `/ingest/runtime` | Ingest runtime events | `202 Accepted` |
| `POST` | `/ingest/runtime/batch` | Ingest gzipped batch | `202 Accepted` |
| `GET` | `/graphs` | List all graphs | `200 OK` |
| `GET` | `/graphs/{graphId}` | Get complete graph | `200 OK` / `404` |
| `DELETE` | `/graphs/{graphId}` | Delete graph + traces | `204 No Content` |
| `GET` | `/flow/{graphId}?zoom=N` | Zoomed view | `200 OK` / `404` |
| `GET` | `/trace/{traceId}` | Trace timeline | `200 OK` / `404` |
| `GET` | `/export/neo4j/{graphId}?mode=cypher\|push` | Neo4j export | `200` / `202` |
| `GET` | `/actuator/health` | Health check | `200 OK` |
| `GET` | `/actuator/prometheus` | Prometheus metrics | `200 OK` |

### Standard Response Envelope

```json
{
  "success": true|false,
  "data": { ... },
  "error": { "code": "...", "message": "...", "details": "..." },
  "timestamp": "ISO-8601"
}
```

### Error Codes

| Code | HTTP | Meaning |
|------|------|---------|
| `VALIDATION_ERROR` | 400 | Bad request |
| `NOT_FOUND` | 404 | Resource not found |
| `GRAPH_NOT_FOUND` | 404 | Graph doesn't exist |
| `QUEUE_FULL` | 429 | Ingestion queue at capacity |
| `NEO4J_EXPORT_DISABLED` | 503 | Export feature disabled |
| `INTERNAL_ERROR` | 500 | Unexpected server error |
