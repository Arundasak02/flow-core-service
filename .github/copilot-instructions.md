# Copilot Instructions — flow-core-service (FCS)

## What This Repo Does

`flow-core-service` (FCS) is the **central REST API hub** for the Flow platform. It:
- Receives `flow.json` from `flow-adapter-java` via `POST /ingest/static`
- Receives runtime event batches from `flow-runtime-agent` via `POST /ingest/runtime/batch`
- Embeds `flow-engine` to process and merge graphs
- Stores enriched graphs in-memory (`InMemoryGraphStore`)
- Serves graphs to `flow-interface` via `GET /flow/{graphId}`

Java 21 with virtual threads enabled (`spring.threads.virtual.enabled=true`).

## Full Context

Complete documentation lives in the workspace at:
- **Quick start:** `flow-docs/agent/00-QUICK-START.md`
- **System map:** `flow-docs/agent/01-SYSTEM.md`
- **nodeId contract (critical):** `flow-docs/agent/02-CONTRACTS.md`
- **Current bugs:** `flow-docs/agent/03-BUGS-P0.md`
- **This repo deep-dive:** `flow-docs/agent/repos/flow-core-service.md`

## Critical Rules

1. **nodeId is the join key** — `event.nodeId` must exactly match `graph.nodeId` for merge to work. See `flow-docs/agent/02-CONTRACTS.md` for the exact format.

2. **Ingestion is async** — `POST /ingest/static` enqueues to `IngestionQueue` and returns immediately. Do not expect graphs to be immediately queryable after the HTTP 200 response.

3. **Runtime merge is deferred** — events are buffered; merge triggers after 3s idle (`flow.retention.trace.idle-timeout-ms`) OR when `traceComplete=true` in batch (currently never sent — Bug #5).

4. **FCS embeds flow-engine** — do not add graph processing logic to FCS controllers. All graph logic belongs in `flow-engine`.

5. **In-memory only** — FCS stores nothing to disk. Restart = all graphs lost. Do not add stateful assumptions without a persistence layer.

## Key Config Properties

```properties
flow.retention.trace.idle-timeout-ms=3000
flow.retention.trace.completion-check-interval-ms=5000
flow.retention.graph.max-graphs=10000
flow.retention.graph.ttl-minutes=0
spring.threads.virtual.enabled=true
```

## When You Change Code — Checklist

- [ ] Added/changed a REST endpoint → update `flow-docs/agent/02-CONTRACTS.md` §4 + `flow-docs/technical/api-reference.md`
- [ ] Changed ingestion pipeline stages → update `flow-docs/agent/01-SYSTEM.md` (FCS Internal Processing Pipeline)
- [ ] Added/changed a config property → update `flow-docs/agent/repos/flow-core-service.md` (Config section)
- [ ] Fixed a P0 bug → update `flow-docs/agent/03-BUGS-P0.md` + `flow-docs/issues/P0-CRITICAL.md` + `flow-docs/issues/resolved.md`

## Git Workflow — Required for Every Change

Before making **any** code change in this repo:

1. **Create a feature branch** — never commit directly to `main`/`master`.
   ```
   git checkout -b feature/<short-description>   # new feature
   git checkout -b fix/<short-description>        # bug fix
   git checkout -b chore/<short-description>      # refactor / docs / config
   ```

2. **Keep changes focused** — one concern per branch.

3. **Commit atomically** — one logical change, one commit.
   Message format: `<type>(<scope>): <short summary>`
   e.g. `fix(ingestion): correct idle-timeout calculation`

4. **Push immediately** after committing:
   ```
   git push -u origin <branch-name>
   ```

5. **Raise a Pull Request** against `main` and report the PR URL to the user.

> Do NOT push directly to `main`, force-push, or squash history without user approval.
- [ ] Changed `InMemoryGraphStore` eviction logic → update `flow-docs/agent/repos/flow-core-service.md` (Graph Eviction section)
