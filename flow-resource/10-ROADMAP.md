# Flow — Roadmap & Current Status

**Status:** Authoritative source of truth
**Scope:** What's built, what's next, strategic phasing, SaaS milestones

---

## What's Built

| Component | Status | Notes |
|-----------|--------|-------|
| **flow-java-adapter** — Spring & Kafka scanning, CLI runner, GEF export, publish to FCS | Complete | First language adapter |
| **flow-engine** — Full pipeline: load, zoom, validate, extract, merge, export | Complete | Language-agnostic |
| **flow-engine** — Runtime engine: events, trace buffer, merge, flow extraction | Complete | Language-agnostic |
| **flow-core-service** — REST API, ingestion queue, graph store, trace buffer, Neo4j export | Complete | Language-agnostic |
| **flow-runtime-agent** — Phase 1: Method-level tracing with ByteBuddy, async transport, circuit breaker | Complete | First runtime agent |
| **flow-runtime-agent** — Distributed tracing context propagation (W3C, Flow headers, B3) | Complete | Cross-service stitching |

---

## Strategic Priorities

Based on market analysis and product vision, priorities are ordered by **impact on the core differentiator** (runtime execution + business context on the architecture graph):

### Priority 1: Things That Make the Demo Jaw-Dropping

The first time someone sees their production code executing live on the graph with business meaning at every checkpoint — that moment sells the product.

| Feature | Target | Why It Matters |
|---------|--------|----------------|
| **Flow UI** — Web-based zoomable graph visualization | New project | Without the UI, Flow is invisible. This is the product surface. |
| **Checkpoint SDK** — `Flow.checkpoint(key, value)` | flow-runtime-agent | Business annotations on the graph. The soul of the product. |
| **Variable Capture** — Essential runtime values at checkpoints | flow-runtime-agent | Production debugging without log diving. Killer feature. |
| **Business Annotations** — AI-assisted business descriptions per node | flow-core-service | Every node tells a business story, not just a technical one. |

### Priority 2: Things That Make It Production-Ready

| Feature | Target | Why It Matters |
|---------|--------|----------------|
| **Production hardening** — Adaptive sampling, TLS, JMH benchmarks, < 3% overhead | flow-runtime-agent | Teams won't deploy an unsafe agent to production. |
| **Persistent storage** — Replace in-memory with durable store | flow-core-service | In-memory is fine for demos, not for SaaS. |
| **Multi-tenancy** — Graph isolation, API keys, team workspaces | flow-core-service | SaaS requirement. |
| **OTel Bridge** — External system instrumentation (DB, Redis, Kafka hops) | flow-runtime-agent | Shows the full picture: code + infrastructure. |

### Priority 3: Things That Expand the Market

| Feature | Target | Why It Matters |
|---------|--------|----------------|
| **Python adapter + agent** | New repos | Second language proves the architecture is truly language-agnostic. |
| **Real-time WebSocket updates** | flow-core-service | Live graph animation — execution moving through the map. |
| **Graph versioning and diff** | flow-core-service | "What changed between deploys?" |
| **Framework plugins** — Redis, gRPC, RabbitMQ, database | flow-java-adapter, flow-runtime-agent | Broader coverage = more complete graph. |

### Priority 4: Things That Scale the Business

| Feature | Target | Why It Matters |
|---------|--------|----------------|
| **Horizontal scaling** — Shared queue, sharding | flow-core-service | Enterprise-grade SaaS. |
| **SSO / RBAC** | flow-core-service | Enterprise sales requirement. |
| **Advanced analytics** — Bottleneck detection, path comparison | flow-engine | High-value features for tech leads and architects. |
| **Node.js / Go adapters** | New repos | Language coverage drives adoption. |
| **Integration with Datadog / New Relic** | flow-core-service | "Open Flow from your Datadog trace" — the bridge. |

---

## Runtime Agent Phases

| Phase | Scope | Status |
|-------|-------|--------|
| **Phase 1** | Method-level tracing (enter/exit/error), async HTTP transport, circuit breaker | Done |
| **Phase 2** | Checkpoint SDK (`Flow.checkpoint(key, value)`) + variable capture | **Next** |
| **Phase 3** | OTel bridge for external systems (DB, Redis, Kafka via OpenTelemetry) | Planned |
| **Phase 4** | Production hardening (adaptive sampling, TLS, JMH benchmarks, < 3% overhead) | Planned |
| **Phase 5** | Cross-service async stitching (@Async, CompletableFuture wrapping) | Planned |
| **Phase 6** | Advanced (Java 21 virtual threads / ScopedValue, dynamic attach, remote config) | Future |

---

## SaaS Milestones

| Milestone | What It Means | Dependencies |
|-----------|---------------|-------------|
| **M1 — Alive Demo** | A user can scan their Java/Spring project, attach the agent, and see live execution on a web graph with zoom. | Flow UI, checkpoint SDK, basic hosting |
| **M2 — Business Layer** | Nodes have business annotations (AI-generated + human-refined). Checkpoints show business data. | Annotation service, checkpoint variable capture |
| **M3 — Team Ready** | Multiple developers share a workspace. Persistent storage. API keys. Basic auth. | Multi-tenancy, persistent store, auth |
| **M4 — Production Safe** | Agent proven < 3% overhead. Adaptive sampling. TLS. Circuit breaker battle-tested. | Agent hardening, benchmarks, security audit |
| **M5 — Multi-Language** | Python adapter + agent shipping. Architecture proven language-agnostic in practice. | Python repos, GEF validation |
| **M6 — Enterprise** | SSO, RBAC, horizontal scaling, Datadog/New Relic integration, SLA. | Scaling, security, integrations |

---

## Deployment Tiers

| Tier | Description | Target |
|------|-------------|--------|
| **Free / Developer** | Single developer, local mode, in-memory, open-source agent + SDK | Individual devs, evaluation |
| **Team** | Shared hosted instance, persistent storage, collaboration, basic auth | Small teams, startups |
| **Enterprise** | Multi-tenant SaaS, SSO, RBAC, horizontal scaling, dedicated support | Large organizations |

---

## Key Decisions Made

| Decision | Rationale |
|----------|-----------|
| Separate repos per component | Independent release cycles. New language = new repo, not a fork. Coupling only through contracts. |
| Engine as pure Java library | Embeddable, testable, no framework overhead. The brain doesn't need HTTP. |
| In-memory primary store (v1) | < 50ms query response. Persistent storage comes in M3, not M1. |
| Neo4j as secondary/optional | Analytics workload. Never on the critical ingestion path. |
| ByteBuddy for JVM instrumentation | Industry standard, battle-tested, safe class transformation. |
| Agent Java 11 minimum | Customer JVM compatibility. Many production apps still on 11/17. |
| flow-sdk zero dependencies | Customer classpath must never be polluted. |
| GEF as universal exchange format | Language-agnostic by design. The adapter is the only language-specific piece. |
| Business meaning as Objective 1, not future | This is the differentiator. Without it, Flow is just another visualization tool. CodeSee died without it. |
| Open-source agent, monetize platform | Agent + SDK free = adoption. Platform (hosting, viz, collaboration) = revenue. |
| Hybrid OTel approach | Flow owns method-level (ByteBuddy). OTel provides external systems. Zero overlap, maximum coverage. |

---

## Key Decisions Still Open

| Decision | Options | Considerations |
|----------|---------|----------------|
| **UI framework** | React + D3/Cytoscape vs. dedicated graph viz library | Performance at scale (10K+ nodes), zoom UX, real-time animation |
| **Persistent storage** | PostgreSQL + JSONB vs. dedicated graph DB vs. time-series hybrid | Query patterns, cost, operational complexity |
| **Business annotation source** | AI-only vs. developer-authored vs. hybrid | Quality, adoption friction, maintenance burden |
| **Variable capture PII handling** | Annotation-based (@FlowExclude) vs. pattern-based vs. allowlist-only | Safety, developer experience, regulatory compliance |
| **SaaS hosting** | AWS vs. GCP vs. self-hosted option | Cost, team expertise, customer requirements |
| **Pricing model** | Per-seat vs. per-service-graph vs. per-event-volume | Market fit, predictability, competitive positioning |
