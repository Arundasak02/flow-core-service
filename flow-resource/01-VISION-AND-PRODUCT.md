# Flow — Vision & Product Definition

**Status:** Authoritative source of truth
**Scope:** Business objectives, product boundaries, target users, market positioning

---

## What Flow Is

**Flow is Google Maps for code.**

In Google Maps, the map exists statically (roads, buildings, landmarks). Vehicles move through it in real time. You zoom from country level to street level. Every location has meaning — a hospital, a restaurant, not just "building #4327."

In Flow:

- The **static graph** is the map — classes, methods, endpoints, topics, and their relationships, extracted from source code at build time.
- **Execution** is the vehicle moving through the map — real-time events showing which paths are active, what succeeded, what failed.
- **Zoom** takes you from business process down to private method — progressive disclosure at 5 levels.
- **Every node has business meaning** — not just `processOrder()` but "validates inventory and reserves stock for the customer."

**One sentence:** *"Flow is a SaaS platform that visualizes how code executes in real time, with business context at every step — a new layer in observability that makes code understandable to everyone."*

---

## The Three Objectives

```
OBJECTIVE 1 — BUSINESS MEANING OF CODE
  Every method, every endpoint, every checkpoint tells a business story.
  Not technical jargon. Business context.
  "What does this code DO for the business?"
  This is the soul of the product. Without it, Flow is just another tracing tool.

OBJECTIVE 2 — REAL-TIME SYSTEM VISIBILITY
  Show how a running system actually behaves — live, on the architecture graph.
  Execution moves through the code like a vehicle on a map.
  Which paths are active. What succeeded. What failed. With real values.
  Not dashboards. Not charts. The CODE GRAPH, animated with live execution.

OBJECTIVE 3 — LIVING DOCUMENTATION
  Replace stale Confluence pages and outdated JIRA descriptions.
  Flow IS the documentation — always current, always accurate,
  because it's derived from actual code and actual execution.
  A new developer understands the entire codebase by exploring Flow.
```

---

## The Three Problems Flow Solves

### 1. "What does this code actually do?"

New developers spend 3–6 months reading stale Confluence docs, tracing through code manually, and asking senior engineers. Flow lets them open the graph, see the entire codebase from service level down to private method, with business annotations and real execution data. Onboarding drops from months to days.

### 2. "What just happened in production?"

Datadog fires an alert. The team digs through flat trace spans and log lines with no business context. Flow shows the exact execution path on the architecture graph — what succeeded, what failed, with real variable values at each checkpoint. No more log archaeology.

### 3. "What does the business logic look like?"

Product managers ask "how does checkout work?" and get a whiteboard drawing that was outdated six months ago. Flow maps technical execution paths to business processes. The gap between "what we think the code does" and "what it actually does" disappears.

---

## Product Lens

| Dimension | Description |
|-----------|-------------|
| **Product type** | SaaS platform (hosted graph storage, visualization, team collaboration) |
| **Primary UX** | Zoomable graph exploration — high-level architecture down to method-level runtime detail |
| **Core differentiator** | Runtime execution + business context on the code graph. Nobody else does this. |
| **Primary analytics** | Trace timeline, execution path replay, checkpoint business data, runtime variable inspection |
| **Zoom levels** | 5-level hierarchy for progressive disclosure (Business → Service → Public → Private → Runtime) |

---

## Language-Agnostic Core

Flow is **not a Java tool**. Flow is a code intelligence platform. Java is the first language we support.

The architecture is split by design:

| Layer | Language-Specific | Language-Agnostic |
|-------|-------------------|-------------------|
| **Static scanner** (adapter) | One per language: Java, Python, Go, Node.js... | Graph Exchange Format (GEF) is universal |
| **Runtime agent** | One per runtime: JVM, CPython, V8... | Event protocol is universal |
| **Framework plugins** | Spring, Kafka, Redis, Django, Express... | Plugin enriches the same graph model |
| **Core engine** | — | Graph processing, merge, zoom, export |
| **Core service** | — | Ingestion, storage, API, visualization |
| **UI** | — | Renders any language's graph identically |

Every adapter and agent produces the same GEF format and event protocol. The core doesn't know or care what language generated the graph.

---

## The Litmus Test for Every Feature

Before adding anything to the roadmap:

> *Does this feature make Flow more accurate, more alive at runtime, or more meaningful in business terms?*
>
> **YES** → Consider it. **NO** → Drop it, regardless of how useful it seems in isolation.

---

## What Flow Is NOT

| Out of Scope | Rationale |
|---|---|
| **Performance monitoring / APM** | Datadog, New Relic own this. Flow captures `durationMs` as a side effect, not as a product feature. Flow complements APM — when Datadog alerts, you open Flow to understand what happened. |
| **Alerting & anomaly detection** | Not our problem. Customers use existing APM/alerting. |
| **Metrics dashboards** | No charts, no timeseries, no p50/p95/p99 displays. |
| **Log aggregation** | Not our concern. |
| **Error tracking / crash reporting** | Sentry, Rollbar do this. Errors annotate the graph node with business context. |
| **Distributed tracing as standalone** | Trace context is propagated to stitch the graph across services, not as a Jaeger/Zipkin replacement. |
| **AI code assistant** | Cursor, Windsurf explain code statically. Flow shows runtime truth with business meaning. Different problem. |
| **Engineering metrics / productivity** | Jellyfish, LinearB measure team output. Flow shows what the code does, not how fast the team ships. |

### The Positioning Statement

**Flow is a new layer in observability.** It sits between "something happened" (Datadog) and "I understand what happened and what it means" (Flow). It is not a replacement for any existing tool. It is the missing piece.

---

## Target Users

| User | What They Get |
|------|---------------|
| **Backend engineers** | See exactly how their code executes, what calls what, debug production issues with real variable values on the graph |
| **Tech leads / architects** | Understand system decomposition, identify coupling, visualize async hops, validate that code matches intended design |
| **New team members** | Rapidly learn how a codebase works by exploring the live graph — from business process to implementation detail |
| **Product managers** | See business processes mapped onto actual code execution. No more stale documentation. The graph IS the documentation. |
| **On-call engineers** | When an alert fires, see the execution path with business context and variable values. Resolve incidents faster. |

---

## Core Concepts

| Concept | Definition |
|---------|------------|
| **Static Graph** | Directed graph extracted from source code: classes, methods, endpoints, topics, and their relationships |
| **Runtime Events** | Live execution data: method enter/exit, message produce/consume, errors, checkpoints, variable captures |
| **Merged Graph** | Static structure + runtime evidence combined — shows what actually ran, how long it took, what failed, with business context |
| **Business Annotation** | Human or AI-generated description of what a node means in business terms |
| **Checkpoint** | Developer-placed marker (`Flow.checkpoint("cart_validated", cartTotal)`) that attaches business data to the graph |
| **Variable Capture** | Essential runtime variable values captured at checkpoints for production debugging (PII-safe) |
| **Zoom Levels** | 5-level hierarchy (Business → Service → Public → Private → Runtime) for progressive disclosure |
| **Flow** | A single execution path through the graph — e.g., one API request traced from endpoint through all layers |
| **graphId** | Identifier for a service/project's architecture graph (e.g., `"order-service"`) |
| **nodeId** | Unique identifier for a single node — the critical join key between static and runtime |
| **traceId** | UUID identifying a single request/execution across all events and services |

---

## Market Context

Flow operates at the intersection of three markets:

| Market | Flow's Position |
|--------|----------------|
| **Observability** (~$3.5B, growing 12–16% CAGR) | New complementary layer — not competing with Datadog/New Relic |
| **AI Developer Tools** (~$9.3B, growing 26% CAGR) | Runtime truth + business context — what AI code assistants cannot provide |
| **Developer Productivity** | Onboarding, debugging, documentation — all improved by the same graph |

**The gap Flow fills:** No tool today combines runtime execution understanding with business context on an architecture graph. AI IDEs handle static understanding. APM tools handle metrics and traces. Flow is the layer that explains *what happened and what it means.*

### Competitive Landscape

| Competitor | Status | Why Flow Is Different |
|---|---|---|
| **CodeSee** | Acquired by GitKraken, shut down Feb 2025. Static-only, couldn't scale. | Flow is runtime + business context. CodeSee had neither. |
| **AppMap** | Active, $10M raised, niche. Developer-local IDE plugin. | Flow is SaaS platform, live system visualization, business layer. |
| **Datadog / New Relic** | Dominant APM. Flat traces, no code graph, no business context. | Flow complements APM — the "understand what happened" layer. |
| **AI IDEs (Cursor, Windsurf)** | Dominant for static code understanding. | AI explains code on demand. Flow shows runtime truth live. Different problem. |

---

## Deployment Model

| Tier | Description |
|------|-------------|
| **Free / Developer** | Single developer, local mode, in-memory, open-source agent + SDK |
| **Team** | Shared hosted instance, multiple services, collaboration features |
| **Enterprise** | Multi-tenant SaaS, persistent storage, SSO, horizontal scaling, advanced analytics |

The agent and SDK are free and open-source. The platform (hosting, visualization, collaboration, business annotations) is the paid product.
