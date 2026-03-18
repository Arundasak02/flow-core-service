# Flow Engine — Library Guide

**Status:** Authoritative source of truth
**Scope:** Pure Java graph processing library — data model, pipeline, zoom, merge, export

---

## What It Is

`flow-engine` is the **language-agnostic brain** of the Flow platform — a pure Java library (no Spring, no HTTP, no frameworks) that processes graphs regardless of what language or adapter produced them. It is embedded as a Maven dependency by `flow-core-service`.

```xml
<dependency>
  <groupId>com.flow</groupId>
  <artifactId>flow-engine</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

**Requirements:** Java 17+, Jackson Databind (transitive)

> **Language-agnostic:** The engine operates on `CoreGraph` objects (nodes + edges). It does not know or care
> whether those nodes came from a Java scanner, a Python scanner, or a Go scanner. The GEF format is the contract.

---

## Processing Pipeline

```
Input (flow.json) → Load → Zoom → Validate → Extract → [Merge Runtime] → Export
```

| Stage | Component | What It Does |
|-------|-----------|-------------|
| **Load** | `StaticGraphLoader` | Parses GEF JSON into `CoreGraph` |
| **Zoom** | `ZoomEngine` + `ZoomPolicy` | Assigns zoom levels 1–5 based on node type |
| **Validate** | `GraphValidator` | Checks structure integrity, zoom assignment, edge references |
| **Extract** | `FlowExtractor` | BFS traversal from endpoint nodes to discover complete flows |
| **Merge** | `MergeEngine` | Merges runtime events into static graph (6 pipeline stages) |
| **Export** | `Neo4jExporter`, `GEFExporter` | Generates Cypher queries or GEF JSON output |

**Critical ordering:** Zoom assignment happens BEFORE validation. Nodes start at zoom=-1.

---

## Package Structure

```
com.flow.core/
├── FlowCoreEngine.java           ← Main orchestrator (entry point)
├── graph/                        ← Core data model
│   ├── CoreGraph.java            ← Graph container (nodes + edges)
│   ├── CoreNode.java             ← Node with type, visibility, zoom, metadata
│   ├── CoreEdge.java             ← Edge with type, execution count
│   ├── NodeType.java             ← ENDPOINT, METHOD, TOPIC, etc.
│   ├── EdgeType.java             ← CALL, PRODUCES, CONSUMES, etc.
│   ├── Visibility.java           ← PUBLIC, PRIVATE, PROTECTED, PACKAGE_PRIVATE
│   └── GraphValidator.java       ← Validates structure integrity
├── ingest/                       ← Input processing
│   ├── StaticGraphLoader.java    ← Parses flow.json → CoreGraph
│   ├── RuntimeEventIngestor.java ← Buffers runtime events
│   └── MergeEngine.java          ← Merges static + runtime data
├── runtime/                      ← Runtime event handling
│   ├── RuntimeEvent.java         ← Event model
│   ├── EventType.java            ← METHOD_ENTER, CHECKPOINT, etc.
│   ├── RuntimeTraceBuffer.java   ← In-memory trace buffer
│   ├── RuntimeEngine.java        ← Orchestrator
│   ├── RuntimeEventHandler.java  ← Strategy interface per event type
│   └── RuntimeEventHandlerRegistry.java
├── zoom/                         ← Zoom level assignment
│   ├── ZoomEngine.java
│   ├── ZoomPolicy.java
│   └── ZoomLevel.java
├── flow/                         ← Flow extraction
│   ├── FlowExtractor.java       ← BFS traversal from endpoints
│   ├── FlowModel.java
│   └── FlowStep.java
└── export/                       ← Output generation
    ├── GraphExporter.java        ← Strategy interface
    ├── ExporterFactory.java      ← Factory for format selection
    ├── Neo4jExporter.java
    └── GEFExporter.java
```

---

## Core Data Model

### CoreGraph

Thread-safe container (ConcurrentHashMap) holding nodes and edges.

```java
CoreGraph graph = new CoreGraph("1.0.0");
graph.addNode(node);
graph.addEdge(edge);

CoreNode node = graph.getNode("node-id");
List<CoreEdge> outgoing = graph.getOutgoingEdges("node-id");
List<CoreNode> businessNodes = graph.getNodesByZoomLevel(1);
```

### CoreNode

```java
CoreNode node = new CoreNode(id, name, NodeType.METHOD, serviceId, Visibility.PUBLIC);
node.setZoomLevel(3);
node.setMetadata("durationMs", 150L);
node.setMetadata("checkpoints", Map.of("cart_total", 250.50));
```

### CoreEdge

```java
CoreEdge edge = new CoreEdge(id, sourceId, targetId, EdgeType.CALL);
edge.setExecutionCount(42);
edge.incrementExecutionCount(1);
```

---

## Zoom Level System

| Level | Name | Node Types | Use Case |
|-------|------|-----------|----------|
| 1 | BUSINESS | ENDPOINT, TOPIC | High-level architecture overview |
| 2 | SERVICE | CLASS, SERVICE | Service-level view |
| 3 | PUBLIC | METHOD (public) | API-level detail |
| 4 | PRIVATE | PRIVATE_METHOD | Implementation detail |
| 5 | RUNTIME | Runtime-discovered nodes | Execution-level instrumentation |

For METHOD nodes, visibility further refines: `isPublic=true` → zoom 3, `isPublic=false` → zoom 4.

---

## MergeEngine — Pipeline Stages

The merge decomposes into 6 independent, idempotent stages:

| Stage | Responsibility |
|-------|---------------|
| **RuntimeNodeStage** | Creates runtime-discovered nodes (always zoom=5) |
| **RuntimeEdgeStage** | Creates RUNTIME_CALL and PRODUCES edges |
| **DurationStage** | Calculates method execution durations from enter/exit pairs |
| **CheckpointStage** | Attaches checkpoint data to nodes |
| **AsyncHopStage** | Stitches async message flows (Kafka producer ↔ consumer) |
| **ErrorStage** | Attaches error information to nodes |

**Rules:** Runtime nodes always get zoom=5. Static nodes are never overwritten. Each stage is idempotent.

---

## Runtime Engine

| Component | Responsibility |
|-----------|---------------|
| `RuntimeEvent` | Model: traceId, timestamp, eventType, nodeId, spanId, data |
| `RuntimeTraceBuffer` | Thread-safe in-memory buffer, groups by traceId, auto-expires |
| `RuntimeEngine` | Orchestrator: accepts events, triggers merge, extracts flows |
| `RuntimeFlowExtractor` | Generates ordered execution paths from events |
| `RuntimeEventHandler` | Strategy interface — extensible per event type |
| `RuntimeEventHandlerRegistry` | Registry of handlers (enter/exit, produce/consume, checkpoint, error) |

---

## Export

### Neo4j Cypher

```java
GraphExporter exporter = ExporterFactory.getExporter(ExporterFactory.Format.NEO4J);
String cypher = exporter.export(graph);
```

### Custom Exporter

```java
public class MyExporter implements GraphExporter {
    @Override
    public String export(CoreGraph graph) { ... }
}
ExporterFactory.registerExporter(Format.JSON, MyExporter.class);
```

---

## Error Handling

All components throw `IllegalArgumentException` for invalid input:
- Failed to load graph from JSON
- Node/edge cannot be null
- Zoom level must be between 1 and 5
- Edge references non-existent node
