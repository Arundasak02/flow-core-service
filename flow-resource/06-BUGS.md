# Known Bugs (Code-Verified, April 2026)

> Only bugs verified by reading actual source code are listed here.
> Full tracker: `flow-docs/agent/03-BUGS-P0.md` and `flow-docs/issues/P0-CRITICAL.md` in workspace root.

---

## Open

### BUG-001 — MergeEngine.merge(CoreGraph) Silent No-Op (P1)
**File:** `flow-engine/.../ingest/MergeEngine.java`
**What:** The `merge(CoreGraph graph)` method is an empty stub — it validates non-null then does nothing. Any code calling this instead of `mergeStaticAndRuntime()` gets no merge.
**Fix:** Throw `UnsupportedOperationException` or remove the method.

### BUG-002 — FlowCoreEngine Deprecated Merge Path (P1)
**File:** `flow-engine/.../FlowCoreEngine.java` ~lines 71-75
**What:** `mergeRuntimeData()` calls deprecated `runtimeEventIngestor.ingest(events, graph)` where the `graph` parameter is silently ignored. Works in practice via fragile indirection.
**Fix:** Replace with direct `mergeEngine.mergeStaticAndRuntime(graph, events)` call.

### BUG-003 — SignatureNormalizer False Dedup (P1)
**File:** `flow-adapter-java/.../Model/SignatureNormalizer.java`
**What:** Two methods with parameters of different `Order` classes from different packages (e.g., `com.a.Order` vs `com.b.Order`) get the same nodeId because both simplify to `Order`.
**Fix:** Add package-level disambiguation to parameter type normalization (same fix needed in agent's `NodeIdBuilder.java`).

### BUG-004 — UI Has No FCS Wiring (P0)
**File:** `flow-interface/src/modules/fcs/` (does not exist)
**What:** All UI data is mock JSON. `fcsAdapter.ts` not implemented. FCS is never called by the UI.
**Fix:** Implement `fcsAdapter.ts` and wire `GraphExplorer.tsx`.

### BUG-005 — BatchAssembler traceComplete Always False (P2)
**File:** `flow-runtime-agent/.../pipeline/BatchAssembler.java`
**What:** The `traceComplete` field in agent batch payloads is never set to `true`. FCS relies entirely on 3s idle timeout.
**Fix:** Signal trace completion from `EntryPointAdvice` when span stack empties.

### BUG-006 — RuntimeEventHandlerRegistry Dead Code (P3)
**File:** `flow-engine/.../runtime/RuntimeEventHandlerRegistry.java`
**What:** Never instantiated or referenced. Pure dead code.
**Fix:** Delete.

---

## Items That Were In Old Gap Analysis But Are NOT Bugs

| Old claim | Reality |
|---|---|
| nodeId mismatch between adapter and agent | Both use identical FQN_PATTERN regex ✅ |
| FlowExtractor duplicate steps | `visited.add()` called before processing ✅ |
| TraceCompletionScheduler config key mismatch | Keys are correct (`flow.retention.trace.*`) ✅ |
| Graph eviction not enforced | Count-based eviction IS implemented (top 10,000) ✅ |
