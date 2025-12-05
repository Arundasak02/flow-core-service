# Flow Engine - Library Documentation

> **For Flow Core Service Developers**
>
> This document provides complete API documentation for using `flow-engine` as a dependency in `flow-core-service`.

---

## 📦 Dependency

Add to your `pom.xml`:

```xml
<dependency>
  <groupId>com.flow</groupId>
  <artifactId>flow-engine</artifactId>
  <version>1.0-SNAPSHOT</version>
</dependency>
```

**Requirements:**
- Java 17+
- Jackson Databind 2.16.0 (transitive dependency)

---

## 🏗️ What is Flow Engine?

**Flow Engine** is a pure Java library that provides the core graph processing capabilities for the Flow platform. It is **NOT** a web service - it's a library meant to be embedded in `flow-core-service`.

### Responsibilities

| Flow Engine (Library) | Flow Core Service (Wrapper) |
|-----------------------|-----------------------------|
| Parse static graphs (JSON) | HTTP endpoints |
| Validate graph structure | Request/response handling |
| Assign zoom levels | Queue management |
| Merge runtime events | Database persistence |
| Extract flows (BFS) | Caching & TTL |
| Export to Neo4j Cypher | Multi-tenancy |

---

## 🧩 Package Structure

```
com.flow.core/
├── FlowCoreEngine.java           ← Main orchestrator (entry point)
│
├── graph/                        ← Core data model
│   ├── CoreGraph.java           ← Graph container (nodes + edges)
│   ├── CoreNode.java            ← Node with type, visibility, zoom
│   ├── CoreEdge.java            ← Edge with type, execution count
│   ├── NodeType.java            ← ENDPOINT, METHOD, TOPIC, etc.
│   ├── EdgeType.java            ← CALL, PRODUCES, CONSUMES, etc.
│   ├── Visibility.java          ← PUBLIC, PRIVATE, PROTECTED
│   └── GraphValidator.java      ← Validates structure integrity
│
├── ingest/                       ← Input processing
│   ├── StaticGraphLoader.java   ← Parses flow.json → CoreGraph
│   ├── RuntimeEventIngestor.java← Buffers runtime events
│   └── MergeEngine.java         ← Merges static + runtime data
│
├── runtime/                      ← Runtime event handling
│   ├── RuntimeEvent.java        ← Runtime event model
│   ├── EventType.java           ← METHOD_ENTER, CHECKPOINT, etc.
│   └── RuntimeTraceBuffer.java  ← In-memory trace buffer
│
├── zoom/                         ← Zoom level assignment
│   ├── ZoomEngine.java          ← Assigns levels to nodes
│   ├── ZoomPolicy.java          ← Type → zoom level mappings
│   └── ZoomLevel.java           ← BUSINESS, SERVICE, PUBLIC, etc.
│
├── flow/                         ← Flow extraction
│   ├── FlowExtractor.java       ← BFS traversal from endpoints
│   ├── FlowModel.java           ← Complete flow representation
│   └── FlowStep.java            ← Single step in a flow
│
└── export/                       ← Output generation
    ├── GraphExporter.java       ← Exporter interface
    ├── ExporterFactory.java     ← Factory for exporters
    └── Neo4jExporter.java       ← Cypher export implementation
```

---

## 🚀 Quick Start

### Basic Usage

```java
import com.flow.core.FlowCoreEngine;
import com.flow.core.graph.CoreGraph;

// Create engine instance
FlowCoreEngine engine = new FlowCoreEngine();

// Process static graph (flow.json content)
String flowJson = "{ \"version\": \"1\", \"nodes\": [...], \"edges\": [...] }";
CoreGraph graph = engine.process(flowJson);

// Export to Neo4j Cypher
String cypher = engine.exportToNeo4j(graph);
```

### With Runtime Events

```java
import com.flow.core.FlowCoreEngine;
import com.flow.core.graph.CoreGraph;
import java.util.List;
import java.util.Map;

FlowCoreEngine engine = new FlowCoreEngine();

// Static graph
String flowJson = "{ \"version\": \"1\", ... }";

// Runtime events (from Flow Runtime Plugin)
List<Map<String, Object>> runtimeEvents = List.of(
    Map.of(
        "traceId", "trace-123",
        "timestamp", System.currentTimeMillis(),
        "type", "METHOD_ENTER",
        "nodeId", "com.example.Service#process",
        "spanId", "span-1",
        "parentSpanId", null
    ),
    Map.of(
        "traceId", "trace-123",
        "timestamp", System.currentTimeMillis() + 100,
        "type", "METHOD_EXIT",
        "nodeId", "com.example.Service#process",
        "spanId", "span-1"
    )
);

// Process with runtime merge
CoreGraph enrichedGraph = engine.process(flowJson, runtimeEvents);
```

---

## 📊 Core Data Model

### CoreGraph

The main container holding nodes and edges. Thread-safe via `ConcurrentHashMap`.

```java
import com.flow.core.graph.CoreGraph;
import com.flow.core.graph.CoreNode;
import com.flow.core.graph.CoreEdge;

CoreGraph graph = new CoreGraph("1.0.0");

// Add nodes
graph.addNode(node);

// Add edges (nodes must exist first!)
graph.addEdge(edge);

// Query
CoreNode node = graph.getNode("node-id");
Collection<CoreNode> allNodes = graph.getAllNodes();
Collection<CoreEdge> allEdges = graph.getAllEdges();

// Navigation
List<CoreEdge> outgoing = graph.getOutgoingEdges("node-id");
List<CoreEdge> incoming = graph.getIncomingEdges("node-id");

// Zoom filtering
List<CoreNode> businessNodes = graph.getNodesByZoomLevel(1);

// Stats
int nodeCount = graph.getNodeCount();
int edgeCount = graph.getEdgeCount();
String version = graph.getVersion();
```

### CoreNode

Represents a graph node (method, class, endpoint, topic, etc.).

```java
import com.flow.core.graph.*;

CoreNode node = new CoreNode(
    "com.example.Service#process",  // id
    "Service.process",               // name
    NodeType.METHOD,                 // type
    "com.example.Service",           // serviceId (class name)
    Visibility.PUBLIC                // visibility
);

// Zoom level (1-5, set by ZoomEngine)
node.setZoomLevel(3);
int zoom = node.getZoomLevel();

// Check visibility
boolean isPublic = node.isPublic();

// Metadata (for runtime data)
node.setMetadata("duration", 150L);
node.setMetadata("errorCount", 2);
Object duration = node.getMetadata("duration");
Map<String, Object> allMeta = node.getAllMetadata();
```

### NodeType

```java
public enum NodeType {
    ENDPOINT,        // REST endpoint, HTTP handler
    TOPIC,           // Kafka topic, message queue
    SERVICE,         // Service class
    CLASS,           // Regular class
    METHOD,          // Public method
    PRIVATE_METHOD,  // Private method
    INTERFACE,       // Interface
    FIELD,           // Field
    CONSTRUCTOR      // Constructor
}
```

### CoreEdge

Represents a connection between two nodes.

```java
import com.flow.core.graph.*;

CoreEdge edge = new CoreEdge(
    "edge-1",                        // id
    "com.example.A#foo",             // sourceId
    "com.example.B#bar",             // targetId
    EdgeType.CALL                    // type
);

// Execution count (runtime data)
edge.setExecutionCount(42);
edge.incrementExecutionCount(1);
long count = edge.getExecutionCount();
```

### EdgeType

```java
public enum EdgeType {
    CALL,          // Method calls method
    HANDLES,       // Controller handles endpoint
    PRODUCES,      // Method produces to topic
    CONSUMES,      // Method consumes from topic
    BELONGS_TO,    // Method belongs to class
    DEFINES,       // Class defines method
    RUNTIME_CALL,  // Runtime-discovered call
    DEPENDS_ON,    // Dependency relationship
    FLOWS_TO       // Generic flow connection
}
```

### Visibility

```java
public enum Visibility {
    PUBLIC,
    PRIVATE,
    PROTECTED,
    PACKAGE_PRIVATE
}
```

---

## 🔄 Processing Pipeline

The `FlowCoreEngine` processes graphs through an ordered pipeline:

```
1. LOAD        → StaticGraphLoader.load(json)
2. ZOOM        → ZoomEngine.assignZoomLevels(graph)
3. VALIDATE    → GraphValidator.validate(graph)
4. EXTRACT     → FlowExtractor.extractFlows(graph)
5. INGEST      → RuntimeEventIngestor.ingest(events) [optional]
6. MERGE       → MergeEngine.merge(graph) [optional]
7. EXPORT      → Neo4jExporter.export(graph)
```

### Using Individual Components

For more control, use components directly:

```java
import com.flow.core.ingest.StaticGraphLoader;
import com.flow.core.zoom.ZoomEngine;
import com.flow.core.graph.GraphValidator;
import com.flow.core.flow.FlowExtractor;

// 1. Load
StaticGraphLoader loader = new StaticGraphLoader();
CoreGraph graph = loader.load(jsonString);

// 2. Assign zoom levels
ZoomEngine zoomEngine = new ZoomEngine();
zoomEngine.assignZoomLevels(graph);

// 3. Validate
GraphValidator validator = new GraphValidator();
validator.validate(graph);

// 4. Extract flows
FlowExtractor extractor = new FlowExtractor();
List<FlowModel> flows = extractor.extractFlows(graph);
```

---

## 🔍 Zoom Levels

Zoom levels control UI visualization granularity:

| Level | Name | Node Types |
|-------|------|------------|
| 1 | BUSINESS | ENDPOINT, TOPIC |
| 2 | SERVICE | SERVICE, CLASS |
| 3 | PUBLIC | METHOD (public) |
| 4 | PRIVATE | METHOD (private), PRIVATE_METHOD |
| 5 | RUNTIME | Runtime-discovered nodes |

### Custom Zoom Policy

```java
import com.flow.core.zoom.ZoomEngine;
import com.flow.core.zoom.ZoomPolicy;

ZoomPolicy customPolicy = new ZoomPolicy();
// Configure custom mappings...

ZoomEngine engine = new ZoomEngine(customPolicy);
engine.assignZoomLevels(graph);
```

---

## 📥 Static Graph Input Format (flow.json)

The expected JSON structure from Flow Adapter:

```json
{
  "version": "1",
  "graphId": "my-service",
  "nodes": [
    {
      "id": "com.example.Controller#handleRequest",
      "type": "METHOD",
      "name": "Controller.handleRequest",
      "data": {
        "visibility": "PUBLIC",
        "className": "Controller",
        "packageName": "com.example",
        "moduleName": "api",
        "signature": "handleRequest(Request):Response"
      }
    },
    {
      "id": "POST /api/orders",
      "type": "ENDPOINT",
      "name": "Create Order",
      "data": {
        "httpMethod": "POST",
        "path": "/api/orders"
      }
    }
  ],
  "edges": [
    {
      "id": "edge-1",
      "from": "POST /api/orders",
      "to": "com.example.Controller#handleRequest",
      "type": "HANDLES"
    }
  ]
}
```

---

## ⚡ Runtime Events

### RuntimeEvent

```java
import com.flow.core.runtime.RuntimeEvent;
import com.flow.core.runtime.EventType;

RuntimeEvent event = new RuntimeEvent(
    "trace-abc-123",                    // traceId
    System.currentTimeMillis(),         // timestamp
    EventType.METHOD_ENTER,             // type
    "com.example.Service#process",      // nodeId
    "span-001",                         // spanId
    "span-000",                         // parentSpanId (null for root)
    Map.of("input", "value")            // data (optional)
);
```

### EventType

```java
public enum EventType {
    METHOD_ENTER,    // Method invocation start
    METHOD_EXIT,     // Method invocation end
    PRODUCE_TOPIC,   // Message sent to topic
    CONSUME_TOPIC,   // Message received from topic
    CHECKPOINT,      // Developer-defined checkpoint
    ERROR            // Exception/error occurred
}
```

### RuntimeTraceBuffer

In-memory buffer for runtime events, grouped by traceId:

```java
import com.flow.core.runtime.RuntimeTraceBuffer;

// Default: 5 minute expiration
RuntimeTraceBuffer buffer = new RuntimeTraceBuffer();

// Custom expiration (10 minutes)
RuntimeTraceBuffer buffer = new RuntimeTraceBuffer(600000);

// Add events
buffer.addEvent(event);
buffer.addEvents(eventList);

// Query
List<RuntimeEvent> events = buffer.getEventsByTrace("trace-123");
Set<String> allTraces = buffer.getAllTraceIds();
int traceCount = buffer.getTraceCount();

// Cleanup
buffer.clearTrace("trace-123");
buffer.expireOldTraces();  // Remove expired traces
buffer.clear();            // Clear all
```

---

## 🔗 Merge Engine

Merges static graphs with runtime data using a pipeline of stages:

```java
import com.flow.core.ingest.MergeEngine;
import com.flow.core.runtime.RuntimeEvent;

MergeEngine mergeEngine = new MergeEngine();

List<RuntimeEvent> events = // ... from buffer
CoreGraph enrichedGraph = mergeEngine.mergeStaticAndRuntime(staticGraph, events);
```

### Merge Stages

1. **RuntimeNodeStage** - Adds runtime-discovered nodes (zoom=5)
2. **RuntimeEdgeStage** - Creates RUNTIME_CALL edges
3. **DurationStage** - Calculates method execution durations
4. **CheckpointStage** - Applies developer checkpoints
5. **AsyncHopStage** - Stitches async message flows (Kafka)
6. **ErrorStage** - Attaches error information

### Merge Rules

- Runtime nodes **always** get zoom level 5
- Static nodes are **never** overwritten
- Each stage is **idempotent**

---

## 📤 Export

### Neo4j Cypher Export

```java
import com.flow.core.export.ExporterFactory;
import com.flow.core.export.GraphExporter;

GraphExporter exporter = ExporterFactory.getExporter(ExporterFactory.Format.NEO4J);
String cypher = exporter.export(graph);

// Output example:
// CREATE (ncom_example_Service_process:Node {id: "com.example.Service#process", ...});
// CREATE (ncom_example_Service_process)-[:CALL {executionCount: 0}]->(ncom_example_Repo_save);
```

### Using FlowCoreEngine

```java
FlowCoreEngine engine = new FlowCoreEngine();
CoreGraph graph = engine.process(json);

// Neo4j export
String cypher = engine.exportToNeo4j(graph);

// Or use format enum
String output = engine.export(graph, ExporterFactory.Format.NEO4J);
```

### Supported Formats

```java
public enum Format {
    NEO4J,     // ✅ Implemented
    GRAPHVIZ,  // 🔜 Planned
    CSV,       // 🔜 Planned
    JSON       // 🔜 Planned
}
```

### Custom Exporter

```java
import com.flow.core.export.GraphExporter;
import com.flow.core.export.ExporterFactory;

public class MyCustomExporter implements GraphExporter {
    @Override
    public String export(CoreGraph graph) {
        // Custom export logic
        return "...";
    }
}

// Register
ExporterFactory.registerExporter(ExporterFactory.Format.JSON, MyCustomExporter.class);
```

---

## 📈 Flow Extraction

Extracts execution flows from business-level nodes using BFS:

```java
import com.flow.core.flow.FlowExtractor;
import com.flow.core.flow.FlowModel;
import com.flow.core.flow.FlowStep;

FlowExtractor extractor = new FlowExtractor();

// Extract all flows (from zoom level 1 nodes)
List<FlowModel> flows = extractor.extractFlows(graph);

// Extract single flow from specific node
FlowModel flow = extractor.extractFlow(graph, startNode);

// FlowModel structure
String flowId = flow.getFlowId();
String startNodeId = flow.getStartNodeId();
List<FlowStep> steps = flow.getSteps();

// FlowStep structure
String nodeId = step.getNodeId();
String nodeName = step.getName();
int zoomLevel = step.getZoomLevel();
int depth = step.getDepth();
List<String> parentIds = step.getParentNodeIds();
```

---

## ✅ Validation

```java
import com.flow.core.graph.GraphValidator;

GraphValidator validator = new GraphValidator();

// Standard validation
validator.validate(graph);

// Strict mode (more checks)
GraphValidator strictValidator = new GraphValidator(true);
strictValidator.validate(graph);
```

### Validation Checks

- ✅ No null nodes or edges
- ✅ Valid node/edge IDs
- ✅ No duplicate IDs
- ✅ Edge source/target nodes exist
- ✅ No self-loops (in strict mode)
- ✅ Zoom levels assigned (1-5)

---

## 🎯 Integration with Flow Core Service

### Recommended Architecture

```java
@Service
public class GraphStoreAdapter {
    
    private final FlowCoreEngine engine;
    private final Map<String, CoreGraph> graphStore;
    
    public GraphStoreAdapter() {
        this.engine = new FlowCoreEngine();
        this.graphStore = new ConcurrentHashMap<>();
    }
    
    public CoreGraph ingestStatic(String graphId, String flowJson) {
        CoreGraph graph = engine.process(flowJson);
        graphStore.put(graphId, graph);
        return graph;
    }
    
    public CoreGraph getGraph(String graphId) {
        return graphStore.get(graphId);
    }
    
    public String exportToNeo4j(String graphId) {
        CoreGraph graph = graphStore.get(graphId);
        return engine.exportToNeo4j(graph);
    }
}
```

### Runtime Event Buffer Integration

```java
@Component
public class RuntimeTraceManager {
    
    private final RuntimeTraceBuffer buffer;
    private final MergeEngine mergeEngine;
    
    public RuntimeTraceManager() {
        this.buffer = new RuntimeTraceBuffer(600000); // 10 min TTL
        this.mergeEngine = new MergeEngine();
    }
    
    public void ingestEvent(RuntimeEvent event) {
        buffer.addEvent(event);
    }
    
    public CoreGraph mergeTrace(String traceId, CoreGraph staticGraph) {
        List<RuntimeEvent> events = buffer.getEventsByTrace(traceId);
        return mergeEngine.mergeStaticAndRuntime(staticGraph, events);
    }
    
    @Scheduled(fixedRate = 60000)
    public void cleanupExpiredTraces() {
        buffer.expireOldTraces();
    }
}
```

---

## 🧪 Testing

Flow Engine is designed for easy testing:

```java
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FlowEngineTest {
    
    @Test
    void shouldProcessStaticGraph() {
        FlowCoreEngine engine = new FlowCoreEngine();
        
        String json = """
            {
              "version": "1",
              "nodes": [
                {"id": "n1", "type": "ENDPOINT", "name": "API"}
              ],
              "edges": []
            }
            """;
        
        CoreGraph graph = engine.process(json);
        
        assertEquals(1, graph.getNodeCount());
        assertNotNull(graph.getNode("n1"));
        assertEquals(1, graph.getNode("n1").getZoomLevel());
    }
}
```

---

## ⚠️ Error Handling

All components throw `IllegalArgumentException` for invalid input:

```java
try {
    CoreGraph graph = engine.process(invalidJson);
} catch (IllegalArgumentException e) {
    // Handle: "Failed to load graph from JSON: ..."
}

try {
    graph.addNode(null);
} catch (IllegalArgumentException e) {
    // Handle: "Node cannot be null"
}

try {
    node.setZoomLevel(10);
} catch (IllegalArgumentException e) {
    // Handle: "Zoom level must be between 1 and 5"
}
```

---

## 📋 Version History

| Version | Changes |
|---------|---------|
| 1.0-SNAPSHOT | Initial release with core graph processing |

---

## 📚 Related Documents

- [README.md](./README.md) - Project overview
- [DEVELOPER_GUIDE.md](./DEVELOPER_GUIDE.md) - Development setup
- [DESIGN_PATTERNS.md](./DESIGN_PATTERNS.md) - Architecture patterns
- [FlowCoreService-ARCHITECTURE.md](./FlowCoreService-ARCHITECTURE.md) - Service integration guide

---

## 🤝 Support

For questions about:
- **Flow Engine internals** → Check this document
- **Flow Core Service integration** → See FlowCoreService-ARCHITECTURE.md
- **Flow Adapter output** → Consult Flow Adapter documentation

