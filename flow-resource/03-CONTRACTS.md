# Contracts & Protocols

> Full specification: `flow-docs/agent/02-CONTRACTS.md` and `flow-docs/technical/contracts-protocols.md` in workspace root.

## nodeId Format (Critical)

The nodeId is the **join key** between static graph nodes (from adapter) and runtime events (from agent). Must be identical in both places.

```
{fullyQualifiedClassName}#{methodName}({shortParam1}, {shortParam2}):{shortReturn}
```

**Example:** `com.greens.order.core.OrderService#placeOrder(OrderRequest):OrderResponse`

Rules:
- Class name = fully qualified (keep package)
- Param/return types = simple names (strip package prefix)
- Params separated by `, ` (comma + space)
- Always include parens, even for no-arg methods `()`

## REST API

| Method | Path | Description |
|---|---|---|
| POST | `/ingest/static` | Receive `flow.json` (GEF 1.1) from adapter |
| POST | `/ingest/runtime/batch` | Receive `RuntimeEvent[]` batch from agent |
| GET | `/flow/{graphId}` | Get merged graph |
| GET | `/flow/{graphId}?zoom=N` | Get graph filtered to zoom level (1-5) |
| GET | `/flow/graphs` | List all graph IDs |
| GET | `/actuator/health` | Health check |

## GEF 1.1 (flow.json schema)

```json
{
  "graphId": "string",
  "version": "1",
  "metadata": {},
  "nodes": [{ "id": "nodeId", "type": "METHOD", "name": "...", "data": {} }],
  "edges": [{ "id": "...", "from": "nodeId", "to": "nodeId", "type": "CALL", "data": {} }]
}
```

**Node types:** ENDPOINT, TOPIC, CLASS, SERVICE, INTERFACE, METHOD, PRIVATE_METHOD
**Edge types:** CALL, HANDLES, PRODUCES, CONSUMES, DEFINES, BELONGS_TO

## Runtime Batch Schema

```json
{
  "traceId": "UUID",
  "traceComplete": false,
  "events": [{
    "traceId": "UUID", "spanId": "UUID", "parentSpanId": "UUID|null",
    "nodeId": "string", "type": "METHOD_ENTER|METHOD_EXIT|ERROR|CHECKPOINT",
    "timestamp": 0, "durationMs": 0, "errorType": null, "data": {}
  }]
}
```

## Zoom Levels

| Level | Node Types |
|---|---|
| 1 | ENDPOINT, TOPIC |
| 2 | + SERVICE, CLASS |
| 3 | + public METHOD |
| 4 | + PRIVATE_METHOD |
| 5 | + RUNTIME |
