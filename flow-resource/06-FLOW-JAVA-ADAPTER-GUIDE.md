# Flow Java Adapter — Scanner Guide

**Status:** Authoritative source of truth
**Scope:** Build-time source code scanner — architecture, plugins, output format

---

## What It Is

A multi-module Maven CLI tool that **scans Java source code** (Spring MVC + Kafka) and extracts an architectural graph into `flow.json` for ingestion by Flow Core Service.

**Type:** Shaded JAR (single executable) | **Java:** 17

---

## Module Layout

```
flow-java-adapter/
├── flow-adapter/          ← Core framework (GraphModel, ScanCommand, plugin SPI)
├── flow-spring-plugin/    ← Spring annotation scanner (@GetMapping, @PostMapping, etc.)
├── flow-kafka-plugin/     ← Kafka annotation scanner (@KafkaListener, @Input, @Output)
├── flow-runner/           ← Executable shaded JAR (bundles all modules)
├── sample/greens-order/   ← Sample Spring/Kafka project for testing
└── pom.xml                ← Parent POM (multi-module)
```

---

## Plugin Architecture

Uses **Java SPI (ServiceLoader)** for extensibility:

```
META-INF/services/com.flow.adapter.FlowPlugin
  → com.flow.plugin.spring.SpringEndpointPlugin
  → com.flow.plugin.kafka.KafkaPlugin
```

New language/framework scanners can be added as plugins without modifying core code.

---

## Key Classes

| Class | Role |
|-------|------|
| `ScanCommand` | CLI entry point; orchestrates scanning |
| `GraphModel` | Aggregate data model (methods, endpoints, topics, edges) |
| `JavaSourceScanner` | Walks source tree and delegates to plugins |
| `SpringEndpointScanner` | Extracts HTTP endpoints with produces/consumes metadata |
| `KafkaScanner` | Extracts Kafka topics and messaging edges |
| `MethodCallAnalyzer` | Analyzes method bodies to find call relationships |
| `GraphExporterJson` | Writes GraphModel to JSON (GEF 1.1 format) |
| `SignatureNormalizer` | Normalizes method signatures — **must match agent's NodeIdBuilder** |
| `FlowPlugin` | SPI contract interface for scanner plugins |
| `GraphPublisher` | POSTs flow.json to FCS (`POST /ingest/static`) |

---

## Key Technologies

- **JavaParser 3.26** — AST parsing of Java source files
- **Picocli 4.7** — CLI argument parsing
- **Jackson** — JSON serialization
- **SLF4J + Logback** — Logging
- **Maven Shade** — Fat JAR packaging

---

## Build & Run

```bash
# Build
mvn -T 1C -DskipTests clean package

# Run scanner
java -jar flow-runner/target/flow-runner-0.3.0.jar scan \
  --src /path/to/java/project/src \
  --out flow.json \
  --project my-service

# Optional: publish directly to FCS
java -jar flow-runner/target/flow-runner-0.3.0.jar scan \
  --src /path/to/java/project/src \
  --out flow.json \
  --project my-service \
  --server http://localhost:8080
```

---

## Output Format

Produces a **GEF 1.1** JSON file. See `03-CONTRACTS-AND-PROTOCOLS.md` for the full schema.

```json
{
  "graphId": "payment-service-project",
  "nodes": [
    { "id": "com.example.OrderService#placeOrder", "type": "METHOD", "name": "...", "data": {...} },
    { "id": "endpoint:POST /api/orders", "type": "ENDPOINT", "name": "...", "data": {...} },
    { "id": "topic:order.events", "type": "TOPIC", "name": "...", "data": {...} }
  ],
  "edges": [
    { "from": "endpoint:POST /api/orders", "to": "...OrderService#placeOrder", "type": "HANDLES" },
    { "from": "...OrderService#placeOrder", "to": "topic:order.events", "type": "PRODUCES" }
  ]
}
```

---

## Design Principles

- Framework-agnostic static parsing — no Spring dependencies at compile time
- Plugin extensibility via SPI — new scanners added without modifying core
- Each module declares only its required dependencies (no transitive bloat)
- Output is a clean JSON graph suitable for architecture visualization tools
