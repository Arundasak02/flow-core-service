# Flow — Vision & Product Definition

**Status:** Authoritative source of truth
**Scope:** Business objectives, product boundaries, target users

---

## What Flow Is

Flow is a platform for **understanding how execution moves through Java applications**. It combines:

- **Static analysis** (what *could* happen) — a directed graph extracted from source code at build time.
- **Runtime observation** (what *did* happen) — lightweight per-method / per-hop events emitted from running applications.
- **Merged enrichment** (what *actually* happened, projected onto the static model) — structure enriched with durations, counts, errors, and async stitching.

**One sentence:** *"When a user hits `POST /orders`, what methods get called, what messages get published, what failed, and how long did each step take?"*

---

## The Two Objectives — Nothing More

```
OBJECTIVE 1 — REAL-TIME SYSTEM VISIBILITY
  Show how a running system actually behaves — live, on the architecture graph.
  Which paths are executing. Which nodes are being hit. In real time.
  Not dashboards. Not charts. The CODE GRAPH, animated with live execution.

OBJECTIVE 2 — BUSINESS MEANING OF CODE  (future)
  Show what the code means in business terms.
  Map technical execution paths to business processes / user journeys.
  Bridge the gap between engineering and product/business teams.
```

---

## Product Lens

| Dimension | Description |
|-----------|-------------|
| **Primary UX** | Zoomable graph exploration (high-level architecture → deeper method/runtime detail) |
| **Primary analytics** | Trace timeline + hotspots, optional Neo4j export for deep graph queries |
| **Zoom levels** | 5-level hierarchy for progressive disclosure (Business → Service → Public → Private → Runtime) |

---

## The Litmus Test for Every Feature

Before adding anything to the roadmap:

> *Does this feature make the ARCHITECTURE GRAPH more accurate, more complete, or more alive at runtime?*
>
> **YES** → Consider it. **NO** → Drop it, regardless of how useful it seems in isolation.

---

## What Flow Is NOT

| Out of Scope | Rationale |
|---|---|
| **Performance monitoring / APM** | Datadog, New Relic already do this. `durationMs` is captured as a side effect of knowing enter/exit — not as a product feature. |
| **Alerting & anomaly detection** | Not our problem. Customers use existing APM tools. |
| **Metrics dashboards** | No charts, no timeseries, no p50/p95/p99 displays. |
| **Log aggregation** | Not our concern. |
| **Error tracking / crash reporting** | Sentry, Rollbar do this. Errors only annotate the graph node. |
| **Distributed tracing as standalone** | Trace context propagated only to stitch the graph across services. Not a Jaeger/Zipkin replacement. |
| **Infrastructure monitoring** | CPU, memory, disk — not our concern. |
| **Security / threat detection** | Not our concern. |

---

## Target Users

| User | What They Get |
|------|---------------|
| **Backend engineers** | See exactly how their code executes, what calls what, where time is spent |
| **Tech leads / architects** | Understand system decomposition, identify coupling, visualize async hops |
| **New team members** | Rapidly learn how a codebase works by exploring the live graph |
| **Product/business (future)** | Map technical execution to business processes |

---

## Core Concepts

| Concept | Definition |
|---------|------------|
| **Static Graph** | Directed graph extracted from source code: classes, methods, endpoints, Kafka topics, and their relationships |
| **Runtime Events** | Live execution data: method enter/exit, message produce/consume, errors, checkpoints |
| **Merged Graph** | Static structure + runtime evidence combined — shows what actually ran, how long it took, what failed |
| **Zoom Levels** | 5-level hierarchy (Business → Service → Public → Private → Runtime) for progressive disclosure |
| **Flow** | A single execution path through the graph — e.g., one API request traced from endpoint to database |
| **graphId** | Identifier for a service/project's architecture graph (e.g., `"order-service"`) |
| **nodeId** | Unique identifier for a single node — the critical join key between static and runtime |
| **traceId** | UUID identifying a single request/execution across all events |

---

## The Problem Flow Solves

Modern Java applications (especially Spring Boot + Kafka microservices) are hard to understand:

- Dozens of REST endpoints, each calling layers of services and repositories
- Asynchronous hops through Kafka topics that break linear tracing
- No single tool shows the full picture from endpoint → service → database → message broker

Flow makes the invisible visible.
