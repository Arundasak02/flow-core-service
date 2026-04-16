# Vision

> See full product vision: `flow-docs/product/vision.md` in workspace root.

## In Brief

Flow is a **live code observability platform**. It correlates static code structure (scanned at build time by `flow-adapter-java`) with runtime execution data (captured by `flow-runtime-agent`) and renders them as an interactive graph in `flow-interface`.

**The analogy:** Google Maps for code. The static graph is the map; runtime execution is the live traffic layer.

## The 5-Component System

| Component | Role |
|---|---|
| `flow-adapter-java` | Build-time CLI: Java source → `flow.json` |
| `flow-engine` | Graph library (embedded here in FCS) |
| `flow-core-service` (this service) | Central hub: ingest, merge, serve |
| `flow-runtime-agent` | JVM agent: bytecode → runtime events |
| `flow-interface` | React UI: displays live code graph |

## Core Value

- Developers understand any codebase by seeing its live call graph
- SREs correlate incidents with specific code paths
- No code changes required to instrument — just attach the JVM agent

## Current State (April 2026)

- ✅ Static scan pipeline works end-to-end
- ✅ Runtime capture pipeline works end-to-end
- ⚠️ Merge pipeline works but has minor fragility (see `06-BUGS.md`)
- ❌ UI is not yet wired to FCS (shows mock data)
- ❌ No authentication on endpoints
- ❌ In-memory only (restart = data lost)
