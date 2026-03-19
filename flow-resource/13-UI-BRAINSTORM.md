# Flow UI — Brainstorm: Tech Stack, Features, Design & Approach

**Status:** Working document (brainstorm)
**Scope:** Decide how we build the Flow UI — stack, features, design language, and process — before writing code.

---

## 1. What We're Building Toward

**One line:** A web UI that is *Google Maps for code* — zoomable graph, live execution as a "vehicle" on the map, business meaning at every node.

**Non-goals (from vision):** No metrics dashboards, no timeseries charts, no APM clone. Flow is the *understand what happened* layer, not the *alert when something happened* layer.

**Primary users:** Backend engineers, tech leads, new team members, on-call engineers. Secondary: product managers who want to see business process mapped to code.

---

## 2. Living Documentation — And Why the UI Must Not Clutter

### The tension

Flow turns code into **living documentation** and shows **runtime behaviour** (trace playback) and **variable values**. If we surface all of that at once — every node, every event, every variable — the UI becomes **noise**. It would be "real" but **not useful**. The differentiator is not "we show runtime"; it's "you **understand** what happened." Understanding requires **curation**, not dump.

### Design stance: one story, clear layers

The UI should feel like **reading one page of documentation**, not opening a log file.

| Principle | Meaning |
|-----------|---------|
| **One narrative at a time** | One graph, one trace, one path. No "compare 5 traces" on the same canvas. One execution = one story. |
| **Structure first, evidence on demand** | The graph (structure) is always the anchor. Runtime path and variable values are **layers** the user adds when they need them, not default clutter. |
| **Variables never on the graph** | Variable values and checkpoints live **only** in the detail panel (or a dedicated "Evidence" strip), when the user focuses a node. The canvas stays a map, not a spreadsheet. |
| **Focus mode** | "Focus on this node" or "Focus on this trace" — dim or hide everything that isn’t part of the current story. The rest of the graph recedes. |
| **Documentation view vs debug view** | **Documentation:** clean graph, optional path, minimal chrome — "how does this work?" **Debug:** same graph + path + variable values in panel — "what happened in this run?" Same data, different emphasis. We can switch mode so the same UI doesn’t try to be both at once. |

### Innovation = clarity, not quantity

The UI stands out when it feels **intuitive and uncluttered** while still showing real runtime behaviour. That means:

- **Progressive disclosure:** Start with "here’s the graph." Then "here’s one path." Then "here’s what this node saw (variables)" only when the user asks.
- **Consistent design language:** Same spacing, type, color, and interaction patterns everywhere. No one-off widgets or visual surprises. Consistency makes the product feel intentional and readable.
- **Lock the design first:** Decide the design language and the key screens (graph view, trace replay, detail panel, view modes) **before** implementation. Implementation follows the lock; we don’t "design as we code."

Later we will add separate systems (e.g. business understanding, annotations, AI). Those will plug into the **same** clean canvas and panel model — same language, no extra clutter.

---

## 3. Tech Stack — Recommendation & Rationale

### Requirements (from gap analysis & vision)

| Requirement | Why it matters |
|-------------|----------------|
| **10K+ nodes** | Real services have hundreds of classes, thousands of methods. UI must not collapse. |
| **5 zoom levels** | Business → Service → Class → Method → Runtime. Progressive disclosure, not one flat view. |
| **Real-time or near-real-time** | Trace replay, live execution highlight. Animation, not just static snapshots. |
| **Language-agnostic** | UI renders GEF + events. No Java/Python-specific UI — same experience for every language. |
| **SaaS-ready** | Themed, embeddable, works behind login. Not a local-only dev tool. |
| **Fast time-to-first-paint** | Graph + trace data can be large. Need streaming or level-of-detail to avoid blank screen. |

### Option comparison

| Stack | Pros | Cons | Verdict |
|-------|------|------|--------|
| **React + Cytoscape.js** | Graph-native, layouts (cola, dagre), good for 1K–10K nodes, TypeScript-friendly | Can slow at 50K+; styling is CSS-based, not canvas | **Strong fit** for M1. Proven in bio/network tools. |
| **React + D3** | Full control, great for custom viz, no graph abstraction tax | We build graph semantics ourselves (zoom, drag, layout). Higher effort. | Good for later customizations; heavier for MVP. |
| **React + vis.js / react-vis-network** | Easy setup, looks decent | Performance and flexibility ceiling. Less "product-grade" feel. | OK for prototype only. |
| **Vue + vis.js or Cytoscape** | Same as above with Vue. | Smaller ecosystem for graph UIs; team may prefer React. | Viable if team is Vue-first. |
| **Svelte + custom WebGL** | Max performance, smallest bundle | Highest build cost; graph logic (layout, zoom, hit-test) from scratch. | Overkill for M1; consider for M2 if we hit limits. |
| **React + G6 (AntV)** | Good docs, layouts, behavior built-in | Heavier, less common in Western stack; licensing check. | Alternative to Cytoscape if we want more "dashboard" widgets. |
| **React + Sigma.js** | WebGL, great for very large graphs | Less hierarchy/zoom semantics out of the box. | Good for "one big graph" view; 5-level zoom may need more work. |

### Recommended stack for M1

- **Framework:** **React 18+** with **TypeScript**.  
  - Reason: Widest hiring and ecosystem; FCS is separate so no stack lock-in with Java. TypeScript gives us clear contracts with FCS API (OpenAPI → generated types).

- **Graph layer:** **Cytoscape.js** with **react-cytoscapejs** (or thin React wrapper).  
  - Reason: Built for directed graphs, multiple layouts (hierarchical, breadcrumb, cola), 5 zoom levels map to "show/hide by level" or separate views. Performance is good for 1K–10K nodes with level-of-detail (e.g. only render current zoom level + neighbors).

- **State:** **Zustand** or **React Query + minimal global state**.  
  - Reason: Server state = graphs, traces (React Query). UI state = selected graph, zoom level, selected trace, panel open/closed (Zustand or React context). Keep Redux out unless we need it.

- **API client:** **OpenAPI-generated client** from FCS spec.  
  - Reason: Single source of truth (FCS), type-safe, less manual sync.

- **Styling:** **Tailwind CSS** + **design tokens** (see Design below).  
  - Reason: Fast iteration, consistent spacing/color. No heavy UI library yet — we're not building a form-heavy app; we're building a graph surface.

- **Repo:** **Dedicated repo `flow-ui`**.  
  - Reason: Different release cycle, different team (frontend), can be deployed as static SPA behind FCS or separate host. FCS serves API; UI is a consumer.

### What we explicitly avoid for M1

- Heavy dashboard/BI components (we're not building Grafana).
- Building our own graph layout engine.
- Canvas/WebGL from scratch (use a library that gives us graph semantics).

---

## 4. Features — Layered by Milestone

### M1 — "Alive Demo" (first ship)

**Goal:** User sees their code graph and one execution moving through it. No login required for MVP; can be single-tenant.

| Feature | Description | Priority |
|---------|-------------|----------|
| **Graph list** | List graphs from `GET /graphs`. Choose one to open. | P0 |
| **Graph canvas** | Render nodes and edges from `GET /graphs/{graphId}` or `GET /flow/{graphId}?zoom=N`. Pan, zoom, drag. | P0 |
| **Zoom level control** | Slider or stepper: 1–5. Fetches `/flow/{graphId}?zoom=N` and re-renders. Smooth transition (crossfade or animate node visibility). | P0 |
| **Node/edge semantics** | Shape by type (endpoint=hexagon, method=rectangle, topic=diamond). Edge style by type (CALL=solid, etc.). Legend. | P0 |
| **Trace list** | For selected graph, `GET /trace` → list traceIds. Select one. | P0 |
| **Trace replay** | For selected trace, `GET /trace/{traceId}`. Animate path: highlight nodes/edges in order. Color: green=ok, red=error, yellow=slow. Show simple timeline (order + duration). | P0 |
| **Node detail panel** | Click node → side panel: nodeId, type, name, attributes. Placeholder for "business annotation" and "checkpoint data" (when backend ready). | P1 |
| **Search (basic)** | Filter nodes by name or type (client-side on loaded graph). Optional: "Go to node" to focus and highlight. | P1 |

**Out of scope for M1:** Auth, multi-workspace, business annotation editing, variable capture UI (needs backend 1B-1–1B-3), real-time WebSocket (polling or manual refresh is OK).

### M2 — "Business layer & production feel"

| Feature | Description |
|---------|-------------|
| **Checkpoint data in panel** | Show captured variables at checkpoints (from trace events). PII-safe display. |
| **Business annotation in panel** | Show and optionally edit business description per node (depends on annotation API). |
| **Real-time updates** | WebSocket or SSE: new traces, live execution highlight. |
| **Layout persistence** | Remember user's layout choice (e.g. hierarchical vs force-directed) and maybe positions for small graphs. |
| **Error/slow emphasis** | In trace replay, clearly mark failed or slow nodes; optional filter "show only errors". |

### M3+ (later)

- Multi-workspace / tenant switcher.
- Embeddable graph (iframe) for Confluence or internal portals.
- Export (PNG/SVG of current view).
- Keyboard navigation and accessibility (focus management, screen reader support).
- Comparison view (e.g. diff two traces or two graph versions).

---

## 5. Design Language & Principles — The Locked Foundation

Design and design language are **locked first**. Implementation follows; we do not "design as we code." Consistency across every screen and component is non-negotiable.

### Design principles (how we make decisions)

1. **Graph is the hero**  
   The map is the main content. Chrome (nav, panels) supports it; it doesn’t compete. No dashboards, no chart grid — one primary canvas.

2. **Progressive disclosure**  
   Match our 5 zoom levels: show only what’s needed at each level. No overwhelming "everything at once" view. Default to a zoom level that’s useful (e.g. 2 or 3 for "service + public API").

3. **Business-first wording**  
   Labels and empty states use business language where we have it ("Validate order" not only "OrderService.validate()"). Technical IDs (nodeId, traceId) available but not the default story.

4. **Clarity over cleverness**  
   Obvious actions, clear states (loading, error, empty). No jargon in the UI. Error messages actionable.

5. **Performance is a feature**  
   We don’t block the main thread. Loading states and incremental rendering (e.g. level-of-detail) are part of the design. "Fast" feels professional.

6. **Accessible by default**  
   Color is not the only differentiator (success/error/slow have icons or labels too). Keyboard and screen-reader friendly where we can. Contrast and focus visible.

7. **Neutral and professional**  
   Feels like a tool for serious teams (dev, on-call, architects). Not playful or consumer. Not dark-for-the-sake-of-dark — theme should work in daylight and in low light.

8. **One story, no clutter**  
   One narrative at a time (one trace, one path). Variables and evidence only in the panel, on demand. Focus mode dims the rest. Documentation view = clean; debug view = same canvas + evidence when needed.

### Information layers (what appears where)

| Layer | Where it appears | Default |
|-------|------------------|---------|
| **Graph (structure)** | Canvas | Always visible (at current zoom). |
| **Path (one trace)** | Canvas | Visible only when user selects a trace and replays. Highlight only; no labels on edges. |
| **Node identity** | Canvas (node label) | Short label (e.g. method name or endpoint path). No variable values. |
| **Evidence (variables, checkpoints)** | Detail panel only | Only when user opens a node. Never on the graph. |
| **Business annotation** | Detail panel (and optionally short tooltip on node) | When we have it; never long text on the canvas. |

This keeps the canvas a **map**. The panel is where we **read** the documentation and evidence.

### Design language (visual) — locked tokens and layout

- **Layout:**  
  - One main graph canvas (center).  
  - Optional top bar: graph picker, zoom control, trace picker, refresh.  
  - Side panel (right or left): node/trace detail. Collapsible.  
  - No sidebar nav with 10 items — we have 2–3 main "modes": graph view, trace replay, maybe "list of traces".

- **Typography:**  
  - Sans-serif, readable at 12–14px for node labels.  
  - Monospace only for technical IDs (nodeId, traceId) in detail panel.  
  - Clear hierarchy: one primary title per view, rest is secondary.

- **Color:**  
  - Semantic: success (green), error (red), warning/slow (amber). Use for trace replay and status.  
  - Node type: subtle fill or border by type (endpoint, method, topic) so the graph isn’t a single color.  
  - Background: light default (better for screenshots and docs); optional dark theme.  
  - Low saturation for the graph itself so the "execution path" highlight pops.

- **Motion:**  
  - Trace replay: animate along edges (e.g. 200–400 ms per step). No flashy decorations.  
  - Zoom/level change: short transition (100–200 ms) so the graph doesn’t jump.  
  - Panel open/close: simple slide or fade.

- **Design tokens (suggested):**  
  - Spacing: 4/8/16/24/32.  
  - Radius: 0 or 4px (we're not building rounded cards everywhere).  
  - Shadows: minimal; use for panels and modals, not for every node.

**Lock:** These tokens and layout rules are fixed before implementation. New screens and components use the same set; no one-off spacing or colors.

### Tone of voice

- Short, direct copy. "No traces yet" not "It looks like you don’t have any traces at the moment."
- Empty states: explain what’s missing and one next step. "Connect the Flow agent to this service and trigger a request to see traces."
- Errors: what went wrong + what to do. "Graph failed to load. Check that the service is running and try again."

---

## 6. Approach — Lock Design First, Then Build

**In short:** Treat the UI as the product surface that must stand out. To keep it intuitive and uncluttered while showing real runtime behaviour, we **lock the design and design language first**. Every screen and component uses the same foundation; implementation follows the lock and does not drift. Later systems (business understanding, annotations, AI) plug into this same clean model.

We do **not** start implementation until design is locked. Order of work:

1. **Lock design language** — Tokens, type, color, spacing, layout, and "information layers" (what appears on canvas vs panel). One source of truth (this doc or a design system file).
2. **Lock key screens and flows** — Wireframes for: graph list, graph view (no trace), graph view (with one trace path), detail panel (with and without variables). Plus view mode: Documentation vs Debug.
3. **Lock interaction rules** — How zoom works, how trace replay starts/stops, how focus mode works, where variables appear. No ambiguity.
4. **Then implement** — Build only what matches the lock. If we need to change the design, we change the lock first and document it.

### Phase 0: Align and decide (before coding)

1. **Stakeholder alignment**  
   - Confirm: M1 = one graph, one trace replay, one detail panel. No auth, no teams.  
   - Confirm: React + Cytoscape + TypeScript + Tailwind + `flow-ui` repo.  
   - Document in 10-ROADMAP.md / 11-GAP-ANALYSIS so the next person doesn’t re-debate.

2. **API contract**  
   - List exact endpoints and response shapes the UI will use:  
     `GET /graphs`, `GET /graphs/{graphId}`, `GET /flow/{graphId}?zoom=N`, `GET /trace`, `GET /trace/{traceId}`.  
   - Ensure FCS has these and that zoom/trace payloads are sufficient (e.g. node ids, types, durations, checkpoint data when we add it).  
   - Optional: OpenAPI spec and generate client so UI and backend stay in sync.

3. **Information architecture (IA)**  
   - One-page sketch: Graph list → Graph view (canvas + zoom) → Trace list → Trace replay on same canvas + detail panel.  
   - No deep nesting. Back = "go to graph list" or "clear trace".

### Phase 1: Design (low-fi first) — this is the lock

4. **Wireframes**  
   - Paper or Figma: 3–4 screens.  
     - Graph list.  
     - Graph view with zoom control and empty side panel (documentation view).  
     - Graph view with trace replay (path highlighted) and node panel open; panel shows node + optional "Evidence" (variables) section (debug view).  
   - Decide: one top bar or minimal chrome; one panel (detail) or two (detail + trace list). No extra screens without updating the lock.

5. **Interaction notes (lock these)**  
   - How does user change zoom? (Slider, buttons, dropdown.)  
   - How does user start trace replay? (Select trace from list → auto-play or play button.)  
   - What happens when user clicks a node during replay? (Panel shows that node’s detail; variables/checkpoints in an "Evidence" block inside the panel, not on the graph.)  
   - Is there an explicit "Documentation" vs "Debug" mode switch, or is it implicit (e.g. "Evidence" section in panel is collapsible and default collapsed in "doc" mode)?

6. **Design tokens**  
   - Define 10–15 tokens: primary text, secondary text, border, background, success/error/warning, spacing scale.  
   - Implement in Tailwind (theme extension) so we don’t magic-number our way through.

### Phase 2: Build in thin vertical slices

7. **Slice 1 — Static graph only**  
   - Graph list → select graph → fetch `/flow/{graphId}?zoom=2` → render with Cytoscape. Pan/zoom. No traces.  
   - Validates: stack, layout, node/edge styling, performance on a real graph (e.g. PetClinic).

8. **Slice 2 — Trace list + replay**  
   - For same graph: fetch `/trace`, pick one, fetch `/trace/{traceId}`.  
   - Animate path on the same canvas (highlight nodes/edges in order).  
   - Validates: trace API, animation, and "vehicle on the map" feel.

9. **Slice 3 — Detail panel**  
   - Click node → panel with nodeId, type, name, attributes.  
   - Later: add checkpoint data and business annotation when APIs exist.

10. **Slice 4 — Zoom level**  
    - Add zoom control; refetch or filter by level. Smooth transition.  
    - Then add search (filter nodes by name).

Each slice is end-to-end (UI → FCS → UI). We don’t build "all layers" at once.

### Phase 3: Validate and iterate

11. **Dogfood on PetClinic**  
    - Run FCS + PetClinic; open flow-ui; pick customers-service graph; replay a create-owner trace.  
    - Does it feel like "Google Maps for code"? Where is it confusing or slow?

12. **Document and refine**  
    - Update this doc with "what we chose and why."  
    - Capture open questions (e.g. "Do we need real-time WebSocket for M1?" → probably no).

---

## 7. Open Questions to Resolve

| Question | Options | Recommendation |
|----------|---------|----------------|
| **Layout algorithm** | Hierarchical (dagre/elk) vs force-directed (cola) for zoom 2–4 | Start hierarchical for "service → method" feel; allow toggle later. |
| **Where does UI live in deployment?** | Same origin as FCS (e.g. FCS serves static) vs separate app (e.g. flow-ui.flow.com) | Separate app for M1 (simple CORS); FCS can serve static later for on-prem. |
| **Auth for M1?** | None vs API key in header vs simple login | None for M1; add API key or login when we do multi-tenant. |
| **Real-time in M1?** | Polling vs WebSocket | Polling or manual refresh for M1; WebSocket in M2. |
| **Mobile / responsive?** | Desktop-only vs responsive | Desktop-first; graph canvas is not great on phone. Optional responsive for list/panel only. |

---

## 8. Summary

| Dimension | Decision |
|-----------|----------|
| **Product stance** | Living documentation: one story at a time, structure first and evidence on demand. Variables never on the graph; focus mode and view modes (Documentation vs Debug) avoid clutter. |
| **Stack** | React 18 + TypeScript + Cytoscape.js + Tailwind; OpenAPI client; repo `flow-ui`. |
| **Features M1** | Graph list, graph canvas (zoom 1–5), trace list, trace replay (one path), node detail panel with optional Evidence block, basic search. |
| **Design** | Design language locked first (tokens, layout, information layers). Graph-first, progressive disclosure, one story, no clutter. Consistent everywhere. |
| **Approach** | **Lock design** (language + screens + interactions) → then align API and IA → then build in thin slices. No implementation before the lock. |

Next concrete step: **Lock the design language and key screens** (wireframes + interaction notes + tokens). Sign off. Then API checklist and Slice 1 implementation that adheres to the lock.
