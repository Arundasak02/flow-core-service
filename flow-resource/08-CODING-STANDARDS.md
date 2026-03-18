# Flow — Coding Standards & Design Patterns

**Status:** Authoritative source of truth
**Scope:** Design patterns, SOLID principles, coding conventions, testing strategy

---

## Design Principles

### SOLID

1. **Single Responsibility** — Each class has one reason to change
2. **Open/Closed** — Open for extension, closed for modification
3. **Liskov Substitution** — Subtypes are substitutable for parent types
4. **Interface Segregation** — Clients depend on specific, focused interfaces
5. **Dependency Inversion** — Depend on abstractions, not concrete implementations

### Clean Architecture

- Business logic (`flow-engine`) is decoupled from infrastructure (`flow-core-service`)
- The engine is language-agnostic — it processes graphs without knowing what language produced them
- The engine can be embedded in any Java application
- The service can swap persistence layers without touching business logic
- Independent versioning and testing

### Language-Agnostic Core

- Core contracts (GEF format, event protocol) are defined independently of any language
- Language-specific logic lives only in adapters and agents, never in the engine or service
- New language support = new adapter repo + new agent repo. Zero changes to core.

---

## Design Patterns in Use

### Strategy Pattern — Graph Exporters

```java
public interface GraphExporter {
    String export(CoreGraph graph);
}
```

Implementations: `Neo4jExporter`, `GEFExporter` (future: GraphViz, CSV, JSON, Protobuf).

Extend by implementing `GraphExporter` and registering via `ExporterFactory`.

### Strategy Pattern — Runtime Event Handlers

```java
public interface RuntimeEventHandler {
    void handle(RuntimeEvent event, CoreGraph graph);
    boolean canHandle(EventType eventType);
}
```

Implementations: `MethodEnterHandler`, `MethodExitHandler`, `ProduceTopicHandler`, `ConsumeTopicHandler`, `CheckpointHandler`, `ErrorHandler`.

Extend by implementing the interface and registering in `RuntimeEventHandlerRegistry`.

### Factory Pattern — Exporter Creation

```java
GraphExporter exporter = ExporterFactory.getExporter(Format.NEO4J);
```

Type-safe, enum-based selection. Custom exporters registered at runtime.

### Registry Pattern — Event Handler Lookup

```java
RuntimeEventHandlerRegistry registry = new RuntimeEventHandlerRegistry();
RuntimeEventHandler handler = registry.getHandler(event.getType());
```

### Pipeline Pattern — MergeEngine Stages

Six independent, idempotent stages: RuntimeNode → RuntimeEdge → Duration → Checkpoint → AsyncHop → Error.

Each stage operates on a shared `MergeContext` and can be tested independently.

---

## Patterns NOT Used (and Why)

| Pattern | Reason Not Needed |
|---------|-------------------|
| **Decorator (on CoreNode)** | Metadata field is simpler and sufficient |
| **Chain of Responsibility (for merge)** | Only 6 stages, no conditional execution needed |
| **CoreGraph Interface** | Simple POJO, no alternative implementations |
| **Builder (for CoreNode)** | Current constructor works fine; consider if construction becomes complex |

---

## Coding Conventions

### Style

- 4-space indentation
- JavaDoc on all public APIs
- No empty catch blocks
- Null checks with `Objects.requireNonNull()`
- Clear naming — method/variable names describe intent

### Code Quality

- Small, focused methods — single responsibility
- Immutability where appropriate — use final fields and records
- Modern Java features — records, sealed classes, pattern matching (where JDK allows)
- No side effects — pure functions preferred in the engine

### What NOT to Add

- No comments that merely narrate what code does
- No unused imports
- No `System.out.println` (use SLF4J logging)
- No unbounded collections without eviction
- No synchronized blocks where concurrent data structures suffice

---

## Testing Strategy

### Unit Tests

Each component independently testable:

| Area | Key Tests |
|------|-----------|
| `NodeIdBuilder` | Proxy resolution, generics, bridge methods, overloads, primitives |
| `FlowContext` | Init, pushSpan, popSpan, clear, thread isolation |
| `EventRingBuffer` | Offer, overflow drops, drainTo |
| `CircuitBreaker` | CLOSED→OPEN→HALF_OPEN→CLOSED transitions |
| `MergeEngine` stages | Each stage in isolation |
| `GraphValidator` | All validation rules |
| `StaticGraphLoader` | End-to-end JSON → CoreGraph |

### Integration Tests

- End-to-end trace processing
- Static graph load → zoom → validate → extract → merge
- Agent sends events → mock FCS receives correctly
- Multi-service flows with async hop stitching

### The Critical nodeId Contract Test

```java
@Test
void agentNodeIdsMustMatchScannerNodeIds() {
    Set<String> scannerNodeIds = loadNodeIdsFromFlowJson("flow.json");
    Set<String> agentNodeIds = buildAgentNodeIds("com.greens.order");
    assertEquals(scannerNodeIds, agentNodeIds);
}
```

If this test fails, nothing works — events will never match graph nodes.

### Safety Tests (Agent)

- Advice exception does not propagate to customer code
- Ring buffer overflow does not block application thread
- Circuit breaker drops events when OPEN
- FlowContext cleared after request (no trace corruption)

---

## Build Order (Respects Dependencies)

```bash
# 1. Build the engine library first
cd flow-engine && mvn clean install

# 2. Build the adapter (standalone)
cd flow-java-adapter && mvn -T 1C -DskipTests clean package

# 3. Build the core service (depends on flow-engine)
cd flow-core-service && mvn clean package

# 4. Build the runtime agent (standalone)
cd flow-runtime-agent && mvn clean package -DskipTests
```
