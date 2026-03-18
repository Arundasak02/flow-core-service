# Flow — Gap Analysis & Detailed Roadmap

**Status:** Working document
**Scope:** What's built, what's broken, what's missing, what to build next
**Reference project:** Spring PetClinic Microservices (real-world validation target)

---

## Part 1: Reality Check — What's Actually Built vs. What the Vision Requires

### The Honest Assessment

We audited every class across all four repositories. Here's the truth.

---

### flow-engine (Core Library)

| Component | Documented Status | Actual Status | Gap |
|-----------|-------------------|---------------|-----|
| **MergeEngine — 6 stages** | Complete | All 6 stages implemented | **BUG:** `FlowCoreEngine.mergeRuntimeData()` calls `merge(graph)` which is a no-op. The actual `mergeStaticAndRuntime(graph, events)` is never called from the main pipeline. |
| **ZoomEngine — 5 levels** | Complete | Implemented correctly | Minor: ZoomPolicy doesn't define level 5; MergeEngine sets it for runtime nodes |
| **FlowExtractor — BFS** | Complete | Implemented | **BUG:** Duplicate steps — nodes reachable via multiple paths appear multiple times. `visited` set is updated too late. |
| **RuntimeEngine** | Complete | Implemented | **DEAD CODE:** `RuntimeEventHandlerRegistry` and all 6 handlers are never used. RuntimeEngine uses MergeEngine directly. |
| **Exporters** | Neo4j + others | Only Neo4j works | **MISSING:** GraphViz, CSV, JSON exporters are commented out stubs |
| **Neo4jExporter** | Complete | Implemented | **INCOMPLETE:** Doesn't export metadata (durations, checkpoints, errors). Escaping is incomplete. |
| **GraphValidator** | Complete | Implemented | Works correctly |
| **Tests** | JUnit | All tests are `main()` based | **NOT REAL TESTS:** None run via Maven Surefire. No CI coverage. |

**Critical fix needed:** The merge pipeline is broken at the orchestrator level. MergeEngine stages work individually but `FlowCoreEngine` never calls them correctly.

---

### flow-core-service (Central Microservice)

| Component | Documented Status | Actual Status | Gap |
|-----------|-------------------|---------------|-----|
| **REST Controllers** | 5 controllers | All implemented | **BUG:** GraphController casts to `InMemoryGraphStore` — breaks abstraction |
| **IngestionQueue** | Complete | Implemented correctly | Works |
| **IngestionWorker** | Complete | Implemented correctly | Works |
| **GraphStore** | In-memory with eviction | In-memory, NO eviction | **MISSING:** `maxCount` and `ttlMinutes` for graphs are defined but never enforced. No eviction. Graphs accumulate forever. |
| **RuntimeTraceBuffer** | Complete with dedup | Implemented | **INCOMPLETE:** TTL eviction works. `maxCount` eviction NOT implemented. Async hops logged but never stored. |
| **EventDeduplicator** | Complete | Implemented correctly | Works |
| **MergeEngineAdapter** | Complete | Implemented | **RISK:** Events with null `nodeId` cause NPE |
| **TraceCompletionScheduler** | Complete | Implemented | **BUG:** Config keys mismatch — uses `flow.trace.*` but `application.yml` defines `flow.retention.trace.*`. Always uses defaults. |
| **Agent batch endpoint** | Complete | Implemented | **BUG:** `traceComplete` is always `false` — agent traces never auto-complete from batch endpoint |
| **Neo4jWriter** | Complete | Implemented | No reconnection logic. Export counts use fragile heuristics. |
| **Multi-tenancy** | Planned | Not started | **MISSING** |
| **Persistent storage** | Planned | Not started | **MISSING** |
| **WebSocket real-time** | Planned | Not started | **MISSING** |
| **Test coverage** | Integration tests | Partial | **GAPS:** No tests for dedup, TTL eviction, queue backpressure (429), agent batch, GZIP, merge correctness |

---

### flow-java-adapter (Static Scanner)

| Component | Documented Status | Actual Status | Gap |
|-----------|-------------------|---------------|-----|
| **JavaSourceScanner** | Complete | Implemented | **LIMITATIONS:** Single srcRoot only, no exclusion patterns, no multi-module support |
| **MethodCallAnalyzer** | Complete | Implemented | **LIMITED:** Direct calls only, no transitive chains, no `MethodReferenceExpr` (e.g., `this::method`) |
| **SpringEndpointScanner** | Complete | Implemented | **GAPS:** `@RequestMapping(method=GET)` returns "REQUEST" not "GET". Multiple paths only use first. |
| **KafkaScanner** | Complete | Implemented | **FRAGILE:** `kafkaTemplate.send()` detected by `toString().contains("kafkaTemplate")` — breaks if variable named differently. Multiple topics only use first. |
| **SignatureNormalizer** | Complete | Implemented | **CRITICAL MISMATCH:** Only simplifies `java.lang.*` and `java.util.*`. Agent simplifies ALL types. Node IDs won't match for methods with custom parameter types. |
| **GraphPublisher** | Complete | Implemented | Works. No `--api-key` in CLI. |
| **Plugin SPI** | Complete | Implemented | Works. Spring + Kafka plugins discovered correctly. |
| **GEF Export** | Complete | Implemented | Works |

**Critical fix needed:** SignatureNormalizer must align with agent's NodeIdBuilder. This is the #1 contract that makes Flow work.

---

### flow-runtime-agent (JVM Agent)

| Component | Documented Status | Actual Status | Gap |
|-----------|-------------------|---------------|-----|
| **FlowAgent premain** | Complete | Implemented | Works. Full safety wrapping. |
| **FlowTransformer** | Complete | Implemented | **MISSING:** WebClient instrumentation documented but not wired |
| **MethodAdvice** | Complete | Implemented | Works |
| **EntryPointAdvice** | Complete | Implemented | **GAP:** Kafka `ConsumerRecord` headers not extracted — Kafka entry points always start new trace (no cross-service stitching via Kafka) |
| **NodeIdBuilder** | Complete | Implemented | **CRITICAL MISMATCH:** Strips ALL package prefixes. Scanner keeps FQN for custom types. Node IDs diverge. |
| **ProxyResolver** | Complete | Implemented | Works |
| **FlowContext** | Complete | Implemented | **RISK:** Thread pool reuse without entry point can leak stale context |
| **EventRingBuffer** | Complete | Implemented | Works |
| **BatchAssembler** | Complete | Implemented | Works |
| **HttpBatchSender** | Complete | Implemented | **GAPS:** `readTimeoutMs` config ignored (hardcoded 5s). No retries. `batchesFailed` metric never incremented. |
| **CircuitBreaker** | Complete | Implemented | Works |
| **FilterChain** | Complete | Implemented | **GAP:** `skipPrivateMethods` config exists but is never used |
| **CheckpointInterceptor** | Complete | Implemented | Works — but no variable capture yet (Phase 2) |
| **OutgoingHttpAdvice** | 6 clients | 5 clients | **MISSING:** WebClient not instrumented |
| **ObjectExtractor** | Complete | Implemented | Works — PII-safe |
| **Distributed tracing** | W3C, B3, Flow | Implemented | **INCOMPLETE:** Micrometer tracing requires manual registration. Kafka headers not extracted. |

---

## Part 2: Spring PetClinic Microservices — Real-World Gap Test

### What PetClinic Looks Like

```
                    ┌─────────────────┐
                    │   API Gateway    │  (Spring Cloud Gateway)
                    │   Port 8080      │
                    └────┬───┬───┬────┘
                         │   │   │
              ┌──────────┘   │   └──────────┐
              ▼              ▼              ▼
     ┌────────────┐  ┌────────────┐  ┌────────────┐
     │ Customers  │  │   Vets     │  │  Visits    │
     │ Service    │  │  Service   │  │  Service   │
     │            │  │            │  │            │
     │ JPA/HSQLDB │  │ JPA/HSQLDB │  │ JPA/HSQLDB │
     └────────────┘  └────────────┘  └────────────┘
              │              │              │
              └──────────────┼──────────────┘
                             │
                    ┌────────┴────────┐
                    │ Config Server   │  (Spring Cloud Config)
                    │ Discovery Server│  (Eureka)
                    │ Tracing Server  │  (Zipkin/OTel)
                    └─────────────────┘
```

**Patterns Flow must handle in PetClinic:**

| Pattern | PetClinic Usage | Flow Support |
|---------|----------------|--------------|
| REST controllers (`@RestController`, `@GetMapping`, etc.) | All 3 business services | **YES** — scanner + agent handle this |
| Service-to-service REST calls | Gateway → Customers/Vets/Visits | **PARTIAL** — agent traces outgoing HTTP (Java11 HttpClient, RestTemplate) but not WebClient |
| Spring Cloud Gateway routing | API Gateway routes to services | **NO** — Gateway uses reactive WebFlux, not `@RestController`. Scanner/agent miss reactive endpoints. |
| Eureka service discovery | All services register | **NO** — Flow doesn't understand service discovery. `nodeId` is per-method, not per-service-instance. |
| Circuit breaker (Resilience4j) | API Gateway fallbacks | **NO** — Flow doesn't trace circuit breaker states or fallback paths |
| JPA / database queries | All 3 business services | **NO** — No database call tracing. Can't see "this method hit the DB" |
| Spring Cloud Config | Centralized config | N/A — infrastructure, not code flow |
| Micrometer / OTel tracing | Built-in | **PARTIAL** — Agent has Micrometer stub but not auto-wired. OTel bridge planned (Phase 3) |
| Spring AI / GenAI | GenAI service | **NO** — No plugin for AI/LLM calls |
| Multi-module Maven | 7+ modules | **PARTIAL** — Scanner takes single `srcRoot`. Need to scan each module separately or support multi-root. |

### PetClinic Gaps Summary

**If we ran Flow against PetClinic today:**

1. **Scanner would work** for Customers, Vets, Visits services (standard Spring MVC + JPA)
2. **Scanner would miss** API Gateway (reactive/WebFlux), GenAI service (Spring AI)
3. **Agent would trace** method enter/exit in business services
4. **Agent would NOT stitch** cross-service calls through the Gateway (WebClient not instrumented)
5. **Agent would NOT show** database queries, circuit breaker paths, or service discovery
6. **Node IDs would mismatch** for any method with custom parameter types (e.g., `Owner`, `Pet`, `Visit` objects)
7. **No business annotations** — the graph would be technically accurate but meaningless

---

## Part 3: Detailed Roadmap — Low-Level Checklist

### Phase 0: Fix What's Broken (Before Building Anything New)

> These are bugs in existing code that will cause failures in any real deployment.

- [ ] **P0-1: Fix FlowCoreEngine merge pipeline** — `mergeRuntimeData()` calls the no-op `merge()` instead of `mergeStaticAndRuntime()`. Nothing merges in the orchestrated pipeline.
  - **Where:** `flow-engine` → `FlowCoreEngine.java`
  - **What:** Replace `mergeEngine.merge(graph)` with proper event retrieval + `mergeStaticAndRuntime(graph, events)`
  - **Impact:** Without this, runtime events are ingested but never merged into the graph

- [ ] **P0-2: Fix nodeId contract mismatch** — Scanner and agent produce different node IDs for methods with custom types.
  - **Where:** `flow-java-adapter` → `SignatureNormalizer` AND `flow-runtime-agent` → `NodeIdBuilder`
  - **What:** Decide on ONE normalization strategy (recommend: both simplify all types, matching the agent's current behavior). Update scanner to match. Add cross-repo contract test.
  - **Impact:** Without this, runtime events for ~30-50% of methods in real projects will never link to graph nodes

- [ ] **P0-3: Fix TraceCompletionScheduler config keys** — Scheduler uses `flow.trace.*` but config defines `flow.retention.trace.*`. Scheduler always uses hardcoded defaults.
  - **Where:** `flow-core-service` → `TraceCompletionScheduler`
  - **What:** Align property keys with `application.yml`
  - **Impact:** Trace completion timing is not configurable

- [ ] **P0-4: Fix agent batch traceComplete flag** — Agent batch endpoint always sets `traceComplete=false`.
  - **Where:** `flow-core-service` → `RuntimeIngestController`
  - **What:** Respect the agent's signal or allow trace-level completion flags
  - **Impact:** Agent traces never auto-complete; rely entirely on idle timeout

- [ ] **P0-5: Fix FlowExtractor duplicate steps** — BFS produces duplicate steps for nodes reachable via multiple paths.
  - **Where:** `flow-engine` → `FlowExtractor`
  - **What:** Add target to `visited` set before adding step
  - **Impact:** Flow views show duplicate entries

- [ ] **P0-6: Fix GraphController abstraction leak** — Casts to `InMemoryGraphStore`, breaks if store implementation changes.
  - **Where:** `flow-core-service` → `GraphController`
  - **What:** Add `getAllGraphIds()` or similar to `GraphStore` interface

---

### Phase 1: Make the Demo Jaw-Dropping (Milestone M1 — "Alive Demo")

> Goal: A user scans their Java/Spring project, attaches the agent, and sees live execution on a web graph with zoom and business context.

#### 1A: Flow UI — The Product Surface

- [ ] **1A-1: Choose UI tech stack** — *BRAINSTORM NEEDED*
  - Options: React + Cytoscape.js, React + D3.js, Vue + vis.js, Svelte + custom WebGL
  - Criteria: 10K+ nodes performance, smooth zoom (5 levels), real-time animation, edge bundling
  - Decision needed: dedicated repo (`flow-ui`) or monorepo with FCS?

- [ ] **1A-2: Implement graph renderer** — Render nodes and edges from `/graphs/{graphId}` response
  - Layout algorithm: hierarchical (business→service→method) or force-directed?
  - Node shapes by type (endpoint=hexagon, method=rectangle, topic=diamond, etc.)
  - Edge styles by type (CALL=solid, PRODUCES/CONSUMES=dashed, ASYNC_HOP=dotted)

- [ ] **1A-3: Implement zoom UX** — Progressive disclosure via `/flow/{graphId}?zoom=N`
  - Zoom 1: Business overview (endpoints + topics only)
  - Zoom 2: Service view (add classes/services)
  - Zoom 3: Public API (add public methods)
  - Zoom 4: Implementation (add private methods)
  - Zoom 5: Runtime (add runtime-discovered nodes)
  - Smooth transitions between zoom levels (not hard cuts)

- [ ] **1A-4: Implement trace replay** — Show execution path animation on the graph
  - Fetch trace from `/trace/{traceId}`
  - Animate "vehicle moving through the map" — highlight nodes/edges in execution order
  - Color coding: green=success, red=error, yellow=slow
  - Show duration at each node

- [ ] **1A-5: Implement search and navigation** — Find nodes by name, type, business annotation
  - Graph search (method name, class name, endpoint path)
  - Click-to-zoom (click a service → zoom into its methods)

#### 1B: Checkpoint SDK — Business Context

- [ ] **1B-1: Implement `Flow.checkpoint()` variable capture** — The agent already intercepts checkpoints. Add structured variable capture with PII safety.
  - **Where:** `flow-runtime-agent` → `CheckpointInterceptor`, `ObjectExtractor`
  - **What:** Capture the `value` parameter using ObjectExtractor, emit as CHECKPOINT event with `captures` map
  - PII filtering: honor `@FlowExclude`, `@FlowInclude`, global patterns

- [ ] **1B-2: Propagate checkpoint data through merge** — MergeEngine's CheckpointStage must store captures in node metadata.
  - **Where:** `flow-engine` → `MergeEngine.CheckpointStage`
  - **What:** Store `captures` map alongside existing checkpoint metadata

- [ ] **1B-3: Expose checkpoint data in API** — Trace endpoint should return captures.
  - **Where:** `flow-core-service` → `TraceController`, `TraceDetailResponse`
  - **What:** Include `captures` in checkpoint events in the trace response

- [ ] **1B-4: Display checkpoints in UI** — Click a node to see checkpoint data.
  - **Where:** `flow-ui`
  - **What:** Side panel showing business label + captured variables when clicking a node

#### 1C: Business Annotations — *BRAINSTORM NEEDED*

- [ ] **1C-1: Define business annotation model** — How are annotations stored, authored, and served?
  - Options:
    - **SDK annotation:** `@FlowBusiness("Validates inventory and reserves stock")` in code
    - **API-authored:** UI lets users type business descriptions per node
    - **AI-generated:** LLM reads method code + name + context and generates descriptions
    - **Hybrid:** AI generates draft, human refines in UI
  - Decision needed: where do annotations live? In the graph (GEF)? In a separate store? In the UI?

- [ ] **1C-2: Implement annotation storage and API**
  - **Where:** `flow-core-service`
  - **What:** CRUD endpoints for business annotations per nodeId

- [ ] **1C-3: Implement AI annotation generation** — *BRAINSTORM NEEDED*
  - Input: method source code, class name, package, incoming/outgoing edges
  - Output: 1-2 sentence business description
  - Model: OpenAI API, local LLM, or pluggable?
  - When: on graph ingestion (automatic) or on-demand?

---

### Phase 2: Make It Real (Milestone M2 — "Production Ready")

#### 2A: Agent Hardening

- [ ] **2A-1: Wire `skipPrivateMethods` config** — Config exists, never used
  - **Where:** `flow-runtime-agent` → `MethodExcludeFilter`

- [ ] **2A-2: Add WebClient instrumentation** — Documented but missing
  - **Where:** `flow-runtime-agent` → `FlowTransformer`, new `WebClientAdvice`
  - **Impact:** Required for Spring Cloud Gateway and reactive service calls (PetClinic!)

- [ ] **2A-3: Fix Kafka distributed tracing** — Extract trace context from `ConsumerRecord.headers()`
  - **Where:** `flow-runtime-agent` → `EntryPointAdvice`, `TraceContextExtractor`
  - **Impact:** Without this, Kafka consumers always start new traces — no cross-service stitching

- [ ] **2A-4: Fix HttpBatchSender config** — Use `readTimeoutMs` from config instead of hardcoded 5s
  - **Where:** `flow-runtime-agent` → `HttpBatchSender`

- [ ] **2A-5: Add adaptive sampling** — Currently AlwaysSampler (100%). Need rate-based and priority-based sampling.
  - **Where:** `flow-runtime-agent` → new `RateLimitingSampler`, `PrioritySampler`

- [ ] **2A-6: JMH benchmarks** — Prove < 3% overhead claim
  - **Where:** `flow-runtime-agent` → new `benchmark/` module
  - **What:** Benchmark per-method overhead, ring buffer throughput, serialization cost

#### 2B: OTel Bridge — External Systems

- [ ] **2B-1: Database call tracing** — Show "this method hit the DB" on the graph
  - **Where:** `flow-runtime-agent` → new `DatabasePlugin`
  - **What:** Intercept JDBC calls (DataSource.getConnection, Statement.execute, etc.)
  - **Output:** New node type `DATABASE` + edge type `QUERIES`
  - **Impact:** PetClinic uses JPA → HSQLDB/MySQL. Without this, 50% of the picture is missing.

- [ ] **2B-2: Redis call tracing**
  - **Where:** `flow-runtime-agent` → new `RedisPlugin`
  - **What:** Intercept RedisTemplate / Jedis / Lettuce operations

- [ ] **2B-3: External HTTP call tracing** — Already partly done via OutgoingHttpAdvice, but needs to create EXTERNAL_SERVICE nodes on the graph
  - **Where:** `flow-engine` → new `NodeType.EXTERNAL_SERVICE`

#### 2C: Scanner Improvements

- [ ] **2C-1: Multi-module Maven support** — PetClinic has 7+ modules
  - **Where:** `flow-java-adapter` → `ScanCommand`
  - **What:** Accept multiple `--src` paths or auto-discover modules from parent POM

- [ ] **2C-2: Fix `@RequestMapping` HTTP method detection**
  - **Where:** `flow-java-adapter` → `SpringEndpointScanner`
  - **What:** Extract `method` attribute from `@RequestMapping`

- [ ] **2C-3: Fix multi-path/multi-topic support** — Only first path/topic is used
  - **Where:** `flow-java-adapter` → `SpringEndpointScanner`, `KafkaScanner`

- [ ] **2C-4: Add JPA/Repository scanner plugin** — Detect `@Repository`, `JpaRepository` interfaces, query methods
  - **Where:** `flow-java-adapter` → new `flow-jpa-plugin`
  - **Output:** DATABASE nodes + QUERIES edges

- [ ] **2C-5: Make KafkaScanner detect any producer variable** — Currently hardcoded to `kafkaTemplate`
  - **Where:** `flow-java-adapter` → `KafkaScanner`
  - **What:** Type-based detection instead of variable name matching

#### 2D: Core Service Hardening

- [ ] **2D-1: Implement graph eviction** — maxCount and TTL for graphs
  - **Where:** `flow-core-service` → `InMemoryGraphStore`

- [ ] **2D-2: Implement trace maxCount eviction**
  - **Where:** `flow-core-service` → `InMemoryRuntimeTraceBuffer`

- [ ] **2D-3: Handle null nodeId in MergeEngineAdapter** — Prevent NPE
  - **Where:** `flow-core-service` → `MergeEngineAdapter`

- [ ] **2D-4: Add real tests** — Cover dedup, eviction, backpressure, agent batch, GZIP, merge correctness
  - **Where:** `flow-core-service` → `src/test`

---

### Phase 3: Multi-Service & PetClinic Victory (Milestone M3)

> Goal: Flow works end-to-end with Spring PetClinic Microservices — all services visible, cross-service traces stitched, database calls shown.

- [ ] **3-1: Spring Cloud Gateway support** — Reactive endpoints, WebFlux
  - Scanner: new `flow-webflux-plugin` for `@RouterFunction`, `RouterBuilder`
  - Agent: WebFlux reactive pipeline instrumentation (non-trivial)
  - *BRAINSTORM NEEDED:* How to handle reactive pipelines? Mono/Flux chains break linear tracing.

- [ ] **3-2: Multi-service graph federation** — Multiple graphIds displayed as one federated view
  - PetClinic has 3+ services. Each produces its own graph. The UI must show them as ONE interconnected system.
  - **Where:** `flow-core-service` → new federation endpoint
  - **What:** Cross-graph edge linking via shared endpoints/topics

- [ ] **3-3: Service discovery awareness** — Understand that "customers-service" in Eureka maps to graphId
  - Not code changes — configuration convention + documentation

- [ ] **3-4: Circuit breaker visibility** — Show Resilience4j states on the graph
  - Scanner plugin: detect `@CircuitBreaker`, `@Retry`, `@RateLimiter` annotations
  - Agent: intercept circuit breaker state changes, emit as events

- [ ] **3-5: Run PetClinic end-to-end demo** — Scan all services, attach agents, see the full system live
  - This is the **validation milestone.** If PetClinic works, Flow works.

---

### Phase 4: SaaS Foundation (Milestone M4)

- [ ] **4-1: Persistent storage** — Replace in-memory stores
  - *BRAINSTORM NEEDED:* PostgreSQL + JSONB vs. dedicated graph DB vs. hybrid
  - Criteria: query patterns (graph traversal, zoom filtering, trace lookup), cost, ops complexity

- [ ] **4-2: Multi-tenancy** — Graph isolation per tenant, API keys, team workspaces
  - **Where:** `flow-core-service`

- [ ] **4-3: Authentication & authorization** — API keys for agents, JWT for UI users
  - **Where:** `flow-core-service`

- [ ] **4-4: Real-time WebSocket** — Push graph updates to UI as events arrive
  - **Where:** `flow-core-service` → WebSocket endpoint
  - **What:** Subscribe to graphId, receive merge events in real-time

- [ ] **4-5: Graph versioning** — Track graph changes across deploys
  - **What:** Diff between v1 and v2 of a graph. "What changed in this deploy?"

---

### Phase 5: Multi-Language (Milestone M5)

- [ ] **5-1: Python adapter** — AST-based scanner for Python (Flask, FastAPI, Django)
  - New repo: `flow-python-adapter`
  - Output: Same GEF format

- [ ] **5-2: Python agent** — `sys.monitoring` (Python 3.12+) or `sys.settrace` based
  - New repo: `flow-python-agent`
  - Output: Same event protocol

- [ ] **5-3: Validate language-agnostic core** — Python graphs merge and display identically to Java graphs
  - This proves the architecture

---

## Part 4: Innovation & Brainstorm Points

These are areas where creative thinking could differentiate Flow dramatically.

### BRAINSTORM 1: The "Google Maps Moment" — Live Execution Animation

How should execution look on the graph? Options:
- **Particle flow:** Small dots moving along edges in execution order (like network traffic visualization)
- **Pulse/glow:** Nodes glow when active, intensity = call frequency
- **Path highlight:** The entire execution path lights up, fades over time
- **Timeline scrubber:** Drag a timeline slider to replay execution moment-by-moment

**Key question:** Real-time streaming (WebSocket) or replay-based (fetch trace, animate)?

### BRAINSTORM 2: Business Annotation Generation

How to make every node meaningful without manual work?
- **LLM reads source code:** "This method validates the shopping cart by checking item availability and calculating total price"
- **Checkpoint-derived:** If developer writes `Flow.checkpoint("cart_validated", total)`, AI infers "Cart validation step"
- **Usage-pattern-derived:** Methods called during `POST /orders` → "Part of the order creation flow"
- **Crowdsourced:** Team members annotate in the UI, annotations persist

**Key question:** How much annotation quality is "good enough" for the demo? 80% AI + 20% human?

### BRAINSTORM 3: Variable Capture UX

What runtime values are worth showing on the graph?
- **Checkpoint values only** (explicit `Flow.checkpoint(key, value)`) — safest, lowest overhead
- **Method arguments** (opt-in via `@FlowInclude`) — more data, higher risk
- **Return values** — useful for debugging, PII risk
- **Exception details** — stack traces on error nodes

**Key question:** Default-off (only captures what developer explicitly marks) vs. default-on (capture everything, redact PII)?

### BRAINSTORM 4: Reactive/Async Pipeline Tracing

Spring WebFlux, CompletableFuture, Project Reactor — these break linear tracing.
- `Mono.flatMap(this::processOrder).flatMap(this::chargePayment)` — each step may run on different threads
- How to maintain trace context across reactive boundaries?
- Options: Reactor Context, ThreadLocal propagation hooks, `ScopedValue` (Java 21)

**Key question:** Is reactive support a P1 requirement (PetClinic uses it in Gateway) or P2 (defer until Java-specific patterns are solid)?

### BRAINSTORM 5: Federated Multi-Service Graph

When PetClinic has 3+ services, each with its own graph:
- How to display them as ONE system?
- How to show a request flowing from Gateway → Customers → Visits?
- Cross-service edges: linked by endpoint URL? By trace context? By shared topic?

**Key question:** Is federation a UI concern (overlay multiple graphs) or a backend concern (merge graphs into one)?

### BRAINSTORM 6: Flow as the "Observability Bridge"

The pitch: "When Datadog alerts, open Flow to understand what happened."
- Can we auto-link from Datadog/New Relic alerts to Flow traces?
- Can we import OTel traces and project them onto the static graph?
- Can we export Flow data to existing observability tools?

**Key question:** Build the integration, or let the quality of Flow's standalone experience drive adoption first?

---

## Part 5: PetClinic Validation Checklist

> This is the concrete test plan: "Can Flow handle Spring PetClinic Microservices end-to-end?"

### Scanner Validation

- [ ] Scan `customers-service` → valid flow.json with `OwnerResource`, `PetResource` endpoints, service methods, JPA repos
- [ ] Scan `vets-service` → valid flow.json with `VetResource` endpoints
- [ ] Scan `visits-service` → valid flow.json with `VisitResource` endpoints
- [ ] Scan `api-gateway` → valid flow.json with Gateway routes (requires WebFlux plugin)
- [ ] All node IDs match between scanner output and agent-generated IDs
- [ ] Multi-module scan works (scan all 3 business services in one run or federate)

### Agent Validation

- [ ] Attach agent to `customers-service` → method traces for `OwnerResource.findOwner()`, `OwnerRepository.findById()`, etc.
- [ ] Attach agent to `visits-service` → method traces for visit creation
- [ ] Cross-service trace: request through Gateway → Customers → see stitched trace
- [ ] Database calls visible (JPA → HSQLDB)
- [ ] Checkpoint in a method → business data visible in trace

### Merge Validation

- [ ] Static graph + runtime events merge correctly for all 3 services
- [ ] Zoom levels work: business view shows only endpoints, drill down shows methods
- [ ] Trace timeline shows correct execution order and durations

### UI Validation

- [ ] Full PetClinic system visible as one federated graph
- [ ] Click on `POST /owners` → see execution path through service layer to DB
- [ ] Trace replay animates the execution path
- [ ] Business annotations visible at each node

---

## Part 6: Priority Matrix

| Priority | Items | Milestone | Why First |
|----------|-------|-----------|-----------|
| **P0 — Fix bugs** | P0-1 through P0-6 | Pre-M1 | Nothing works reliably without these |
| **P1 — Demo** | 1A (UI), 1B (Checkpoints), 1C (Annotations) | M1 | The demo sells the product |
| **P2 — Real projects** | 2A (Agent), 2B (OTel), 2C (Scanner), 2D (Service) | M2 | Real projects need these to work |
| **P3 — PetClinic** | 3-1 through 3-5 | M3 | Proves the platform against a real system |
| **P4 — SaaS** | 4-1 through 4-5 | M4 | Revenue requires SaaS infrastructure |
| **P5 — Multi-lang** | 5-1 through 5-3 | M5 | Market expansion |
