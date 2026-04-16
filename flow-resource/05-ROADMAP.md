# Roadmap

> Full roadmap: `flow-docs/product/roadmap.md` in workspace root.
> This file has been rewritten from scratch. The old `10-ROADMAP.md` was **discarded** — it claimed things as "Complete" that have known bugs.

## Current Phase: Phase 0 — Bug Fixes

Fix the 3 P1 bugs in the merge pipeline before building further:

1. `MergeEngine.merge(CoreGraph)` is a silent no-op — fix: throw or remove
2. `FlowCoreEngine.mergeRuntimeData()` uses deprecated path — fix: call `mergeStaticAndRuntime()` directly
3. `SignatureNormalizer` false dedup (same simple class name from different packages) — fix: add disambiguation

## Phase 1 — Demo Ready

Goal: real live graph visible in `flow-interface` from actual FCS data.

- Implement `fcsAdapter.ts` in flow-interface
- Wire `GraphExplorer.tsx` to live API
- Fix 10 known UX issues
- End-to-end demo possible

## Phase 2 — Production Ready

- Auth on FCS endpoints
- Persistence (Redis/PostgreSQL option)
- Rate limiting, structured logging, metrics
- Security review

## Phase 3 — Multi-Service

- W3C `traceparent` propagation
- Multi-graph trace correlation
- Cross-service edge detection

## Milestones

See `flow-docs/product/milestones.md` for target criteria and status.
