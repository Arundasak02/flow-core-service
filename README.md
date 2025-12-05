# Flow Core Service

A Spring Boot microservice that manages the ingestion, merging, and querying of flow graphs and runtime traces.

**Version**: 0.0.1-SNAPSHOT  
**Java**: 21  
**Spring Boot**: 3.2.0  
**Build Tool**: Maven

## Documentation

| Document | Description |
|----------|-------------|
| [API Documentation](API_DOCUMENTATION.md) | Complete API reference with examples |
| [Quick Reference](API_QUICK_REFERENCE.md) | Cheat sheet for common operations |
| [Architecture](FlowCoreService-ARCHITECTURE.md) | System design and architecture |
| [Flow Engine API](FLOW_ENGINE_API.md) | Flow Engine library documentation |

## Overview

Flow Core Service (FCS) is the central component of the Flow ecosystem that:

- **Ingests static graphs** at build time from Flow Adapter
- **Ingests runtime events** at execution time from Flow Runtime Plugin
- **Serves queries** to Flow UI and other tools
- **Merges** static definitions with runtime evidence to produce enriched flow graphs
- **Exports** graphs to Neo4j and other formats for analytics

## Quick Start

### Prerequisites

- Java 21 or later
- Maven 3.8 or later
- flow-engine library installed in local Maven repository

### Build

```bash
mvn clean package
```

### Run

```bash
java -jar target/flow-core-service-0.0.1-SNAPSHOT.jar
```

The service will start on `http://localhost:8080`.

### Access API Documentation

| Resource | URL |
|----------|-----|
| Swagger UI | http://localhost:8080/swagger-ui.html |
| OpenAPI Spec | http://localhost:8080/v3/api-docs |
| Health Check | http://localhost:8080/actuator/health |
| Metrics | http://localhost:8080/actuator/prometheus |

```
http://localhost:8080/v3/api-docs
```

## Configuration

Configuration is managed via `application.properties` and can be customized via environment variables or command-line arguments.

### Key Configuration Properties

```properties
# Server
server.port=8080

# Ingestion Queue
flow.ingest.queue.capacity=10000                     # Max items in queue
flow.ingest.queue.backpressure-threshold=80          # Percentage before backpressure
flow.ingest.worker.interval.ms=50                    # Worker polling interval

# Data Retention
flow.retention.trace.ttl-minutes=10                  # Trace TTL
flow.retention.trace.max-traces=100000               # Max traces in memory
flow.retention.graph.ttl-minutes=0                   # Graph TTL (0 = no eviction)

# Metrics & Monitoring
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

Override properties via environment variables (e.g., `FLOW_INGEST_QUEUE_CAPACITY=20000`) or command line:

```bash
java -jar target/flow-core-service-0.0.1-SNAPSHOT.jar \
  --flow.ingest.queue.capacity=20000 \
  --flow.retention.trace.ttl-minutes=15
```

## API Endpoints

### Static Graph Ingestion

**POST /ingest/static**

Ingest a static graph definition.

```bash
curl -X POST http://localhost:8080/ingest/static \
  -H "Content-Type: application/json" \
  -d '{
    "graphId": "payment-service:v1",
    "name": "Payment Service Graph",
    "nodes": {...},
    "edges": {...}
  }'
```

**Response**: `202 Accepted`

### Runtime Event Ingestion

**POST /ingest/runtime**

Ingest runtime execution events for a trace.

```bash
curl -X POST http://localhost:8080/ingest/runtime \
  -H "Content-Type: application/json" \
  -d '{
    "traceId": "req-12345",
    "graphId": "payment-service:v1",
    "events": [
      {
        "eventId": "evt-1",
        "timestamp": "2025-12-05T10:30:00Z",
        "type": "start",
        "nodeId": "node-1"
      }
    ]
  }'
```

**Response**: `202 Accepted`

### Query Graphs

**GET /graphs**

List all available graphs.

```bash
curl http://localhost:8080/graphs
```

**Response**: Array of graph summaries

**GET /graphs/{graphId}**

Get a complete graph with nodes and edges.

```bash
curl http://localhost:8080/graphs/payment-service:v1
```

**Response**: Complete graph object

**GET /flow/{graphId}?zoom={level}**

Get a zoomed/sliced view of a graph.

```bash
curl http://localhost:8080/flow/payment-service:v1?zoom=2
```

### Query Traces

**GET /trace/{traceId}**

Get detailed information about a runtime trace.

```bash
curl http://localhost:8080/trace/req-12345
```

**Response**: Trace with events, checkpoints, errors, and async hops

### Export Graphs

**GET /export/neo4j/{graphId}?mode=cypher**

Export a graph as Cypher statements.

```bash
curl http://localhost:8080/export/neo4j/payment-service:v1?mode=cypher
```

**Response**: Cypher statements

**GET /export/neo4j/{graphId}?mode=push**

Push a graph to Neo4j (background job).

```bash
curl http://localhost:8080/export/neo4j/payment-service:v1?mode=push
```

**Response**: `202 Accepted`

### Health & Metrics

**GET /actuator/health**

Check service health including queue status.

```bash
curl http://localhost:8080/actuator/health
```

**GET /actuator/metrics**

Get available metrics.

```bash
curl http://localhost:8080/actuator/metrics
```

**GET /actuator/metrics/flow.ingest.queue.utilization**

Get specific metric value.

```bash
curl http://localhost:8080/actuator/metrics/flow.ingest.queue.utilization
```

**GET /metrics (Prometheus format)**

Prometheus-compatible metrics endpoint.

```bash
curl http://localhost:8080/actuator/prometheus
```

## Architecture

### Core Components

#### In-Memory Storage

- **GraphStore**: `Map<graphId, CoreGraph>` – primary source of static + merged graphs
- **RuntimeTraceBuffer**: `Map<traceId, RuntimeTrace>` – temporary storage for runtime traces with TTL-based eviction
- **IngestionQueue**: Bounded queue for decoupling HTTP ingestion from processing

#### Ingestion Pipeline

1. HTTP request → Controller
2. Validation → IngestionQueue
3. Worker dequeues → StaticGraphHandler / RuntimeEventHandler
4. Data persisted to in-memory stores
5. Async export to Neo4j (if enabled)

#### Package Structure

```
com.flow.core.service
├── FlowCoreServiceApplication          # Main Spring Boot entry point
├── domain/
│   ├── model/
│   │   ├── graph/                     # CoreGraph, CoreNode, CoreEdge
│   │   └── runtime/                   # RuntimeTrace, RuntimeEvent, etc.
│   └── repository/                    # Repository interfaces
├── application/
│   └── ingest/                        # Ingestion pipeline components
├── infrastructure/
│   ├── persistence/                   # Repository implementations
│   ├── health/                        # Health indicators
│   └── monitoring/                    # Metrics collection
├── presentation/
│   ├── controller/                    # REST endpoints
│   ├── dto/                           # Data transfer objects
│   └── advice/                        # Global exception handling
└── config/                            # Spring configuration
```

## Principles & Constraints

### SOLID Design Principles

1. **Single Responsibility** – Each class has one reason to change
2. **Open/Closed** – Open for extension, closed for modification
3. **Liskov Substitution** – Subtypes are substitutable for parent types
4. **Interface Segregation** – Clients depend on specific interfaces
5. **Dependency Inversion** – Depend on abstractions, not concrete implementations

### Key Constraints

- **Fast ingestion** – Non-blocking, bounded queue prevents backlog
- **No blocking on DB** – All critical paths are in-memory only
- **Sub-50ms query response** – In-memory graphs serve fast queries
- **Eventual consistency** – Neo4j export is asynchronous
- **Deduplication** – Runtime events are deduplicated by hash
- **Multi-tenant ready** – Design supports tenant isolation in future

## Development

### Building

```bash
mvn clean package
```

### Running Tests

```bash
mvn test
```

### Running with Debug Logging

```bash
java -jar target/flow-core-service-0.0.1-SNAPSHOT.jar \
  --logging.level.com.flow=DEBUG
```

### Code Quality

- **Small, readable methods** – Methods are focused and well-documented
- **Immutability** – Immutable types used where appropriate
- **Modern Java features** – Records, sealed classes, pattern matching (Java 25+)
- **Spring Boot 4.0 features** – Native compilation support, virtual threads ready

## Monitoring & Observability

### Metrics

FCS exposes custom metrics via Micrometer:

- `flow.ingest.queue.size` – Current queue depth
- `flow.ingest.queue.capacity` – Queue capacity
- `flow.ingest.queue.utilization` – Percentage utilized
- `flow.ingest.static.graphs` – Count of graphs ingested
- `flow.ingest.runtime.events` – Count of events ingested
- `flow.storage.graphs.count` – Current number of graphs in memory
- `flow.storage.traces.count` – Current number of traces in memory
- `flow.ingest.rejections` – Count of queue rejections

### Health Checks

Custom health indicator for IngestionQueue:
- **UP** – Queue normal, < 90% capacity
- **DEGRADED** – Queue 90-100% capacity
- **DOWN** – Queue full, backpressure active

### Logging

Structured logging with SLF4J + Logback. Key loggers:

- `com.flow.core.service.application.ingest` – Ingestion details
- `com.flow.core.service.presentation.controller` – API request/response
- `com.flow.core.service.infrastructure.persistence` – Storage operations

## Extending FCS

### Adding a New Endpoint

1. Create DTO classes in `presentation.dto`
2. Create controller method in appropriate controller class
3. Implement service logic in `application` or `domain`
4. Add tests
5. Document in this README

### Adding a New Export Format

1. Create exporter class in `infrastructure.persistence`
2. Add endpoint in `ExportController`
3. Implement conversion logic
4. Test with sample graphs

### Adding Metrics

Use `FlowMetrics` component:

```java
@Autowired
private FlowMetrics flowMetrics;

// Record a metric
flowMetrics.recordStaticGraphIngestion(graphId);
```

## Troubleshooting

### Queue Full (429 Responses)

The ingestion queue is at capacity. Either:
- Reduce ingestion rate
- Increase `flow.ingest.queue.capacity`
- Check if workers are processing items (logs, metrics)

### High Memory Usage

Monitor trace count. Traces are evicted after TTL expires. Reduce `flow.retention.trace.ttl-minutes` or increase worker throughput.

### Slow Queries

Graphs should be fast. If slow:
- Check graph size (node/edge count in metrics)
- Check Neo4j connectivity if export is enabled
- Review logs for errors

## Future Enhancements

- [ ] Multi-tenant support (tenant ID in all entities)
- [ ] Persistent storage (replace in-memory with database for v2)
- [ ] Horizontal scaling (shared queue, state management)
- [ ] Graph versioning and time-travel queries
- [ ] Real-time WebSocket updates for Flow UI
- [ ] Advanced analytics (bottleneck detection, anomaly detection)
- [ ] Integration with tracing systems (Jaeger, Datadog)

## Contributing

See individual CONTRIBUTING guidelines in the repository.

## License

Apache 2.0

## Support

For issues, questions, or suggestions, open an issue on GitHub or contact the Flow Development Team.

---

**Last Updated**: December 5, 2025  
**Maintainer**: Flow Development Team

