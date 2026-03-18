# Flow Core Service — API Reference

**Status:** Authoritative source of truth
**Scope:** Complete REST API documentation with request/response examples

---

## Base URL & Authentication

```
Base URL: http://localhost:8080
Authentication: None (configure in production)
Content-Type: application/json
```

Interactive docs: `http://localhost:8080/swagger-ui.html`
OpenAPI spec: `http://localhost:8080/v3/api-docs`

---

## 1. Static Graph Ingestion

### `POST /ingest/static`

Ingest a static graph definition from the adapter.

**Request:**
```json
{
  "graphId": "order-service-v1.2.3",
  "version": "1.2.3",
  "nodes": [
    {
      "nodeId": "com.ecommerce.OrderController#createOrder",
      "type": "ENDPOINT",
      "name": "OrderController.createOrder",
      "label": "POST /api/orders",
      "attributes": { "httpMethod": "POST", "path": "/api/orders" }
    }
  ],
  "edges": [
    {
      "edgeId": "e1",
      "sourceNodeId": "com.ecommerce.OrderController#createOrder",
      "targetNodeId": "com.ecommerce.OrderService#processOrder",
      "type": "CALL"
    }
  ],
  "metadata": { "application": "order-service", "team": "orders" }
}
```

**Responses:** `202 Accepted` | `400 Bad Request` | `429 Queue Full`

---

## 2. Runtime Event Ingestion

### `POST /ingest/runtime`

Ingest runtime events for a specific trace.

**Request:**
```json
{
  "graphId": "order-service-v1.2.3",
  "traceId": "trace-550e8400-e29b-41d4-a716-446655440000",
  "events": [
    {
      "eventId": "evt-001",
      "type": "METHOD_ENTER",
      "timestamp": "2025-12-05T10:30:00.100Z",
      "nodeId": "com.ecommerce.OrderController#createOrder",
      "spanId": "span-001",
      "parentSpanId": null
    },
    {
      "eventId": "evt-002",
      "type": "METHOD_EXIT",
      "timestamp": "2025-12-05T10:30:00.400Z",
      "nodeId": "com.ecommerce.OrderController#createOrder",
      "spanId": "span-001",
      "durationMs": 300,
      "attributes": { "httpStatus": 201 }
    }
  ],
  "traceComplete": true
}
```

**Responses:** `202 Accepted` | `400 Bad Request` | `404 Graph Not Found` | `429 Queue Full`

---

## 3. Graph Management

### `GET /graphs`

List all stored graphs with summary information.

**Response (200):**
```json
{
  "success": true,
  "data": [
    {
      "graphId": "order-service-v1.2.3",
      "nodeCount": 45,
      "edgeCount": 67,
      "hasRuntimeData": true,
      "traceCount": 1523
    }
  ]
}
```

### `GET /graphs/{graphId}`

Get complete graph with all nodes and edges.

**Response (200):** Full graph object with nodes, edges, and metadata.
**Response (404):** `{ "success": false, "error": { "code": "NOT_FOUND" } }`

### `DELETE /graphs/{graphId}`

Delete a graph and all associated traces.

**Response:** `204 No Content`

---

## 4. Flow Extraction

### `GET /flow/{graphId}?zoom={level}`

Get a zoom-level filtered view for UI rendering.

| Zoom | Description | Shows |
|------|-------------|-------|
| 0 | Business Overview | Endpoints, Topics |
| 1 | Service View | Public methods |
| 2 | Component View | Protected methods |
| 3 | Detailed View | Private methods |
| 4-5 | Debug View | All nodes |

**Response (200):**
```json
{
  "success": true,
  "data": {
    "graphId": "order-service-v1.2.3",
    "zoomLevel": 1,
    "nodes": [ ... ],
    "edges": [ ... ]
  }
}
```

---

## 5. Trace Queries

### `GET /trace/{traceId}`

Get complete trace with events, checkpoints, errors, and async hops.

**Response (200):**
```json
{
  "success": true,
  "data": {
    "traceId": "trace-550e8400",
    "graphId": "order-service-v1.2.3",
    "durationMs": 300,
    "completed": true,
    "hasErrors": false,
    "events": [ ... ],
    "checkpoints": [ ... ],
    "errors": [ ... ]
  }
}
```

---

## 6. Neo4j Export

### `GET /export/neo4j/{graphId}?mode=cypher`

Returns Cypher statements as JSON.

### `GET /export/neo4j/{graphId}?mode=push`

Pushes graph directly to configured Neo4j instance. Returns `202 Accepted`.

---

## 7. Health & Metrics

### `GET /actuator/health`

```json
{
  "status": "UP",
  "components": {
    "ingestionQueue": { "status": "UP", "details": { "size": 42, "capacity": 10000 } }
  }
}
```

### `GET /actuator/prometheus`

Prometheus-compatible metrics scraping endpoint.

---

## Test Workflow (Complete E-commerce Example)

```bash
# 1. Ingest static graph
curl -X POST http://localhost:8080/ingest/static -H "Content-Type: application/json" -d @flow.json

# 2. Ingest runtime events
curl -X POST http://localhost:8080/ingest/runtime -H "Content-Type: application/json" -d @events.json

# 3. Query graph
curl http://localhost:8080/graphs
curl "http://localhost:8080/flow/order-service?zoom=1"

# 4. Query trace
curl http://localhost:8080/trace/trace-001

# 5. Export
curl "http://localhost:8080/export/neo4j/order-service?mode=cypher"

# 6. Health
curl http://localhost:8080/actuator/health
```
