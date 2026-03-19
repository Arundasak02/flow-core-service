# Flow UI Visual OS Case Study and Design Lock

**Status:** Draft v1 (pre-development lock)  
**Date:** 2026-03-19  
**Intent:** Lock a cohesive visual language before building the Flow UI as a visual operating system for execution flows.

---

## Product Framing (Memory Lock)

You are not building "a website for Flow".  
You are building a **visual operating system for execution flows**.

That means:

- The graph is the primary workspace, not a decorative widget.
- Every interaction must reduce uncertainty in one click.
- Readability and operational clarity are higher priority than visual effects.

---

## What We Must Lock Before Development

### 0) Icon system and asset strategy

**Icon libraries:**

- Primary: **Lucide** (clean, dev-friendly, consistent stroke style).
- Secondary: **Heroicons** (fallback for missing symbols or filled variants).

**Rules:**

- Default icon size: `16` in dense surfaces, `18` in regular surfaces, `20` in prominent actions.
- Default stroke width for Lucide style: `1.75`.
- Do not mix stroke and filled icons in same row unless semantic distinction is intentional.

**PNG asset sourcing plan:**

- Product logos and vendor marks: official brand kits (license-safe).
- Environment and service logos: Simple Icons where available, otherwise internal SVG exports.
- Runtime state illustrations: in-house exports from Figma.
- Store assets in:
  - `flow-resource/design-preview/assets/png/`
  - `flow-resource/design-preview/assets/svg/`

**How we keep this maintainable:**

- Every asset has source URL/license note in `assets/ASSET-SOURCES.md`.
- Prefer SVG for app UI; PNG only for photos/brand locks or raster effects.
- Generate 1x/2x PNG variants only when needed.

### 1) Typography scale (xs -> xl)

- `xs` 11/16: micro labels, edge metadata, helper text.
- `sm` 12/18: secondary labels, menu items.
- `md` 14/20: default body, node secondary lines.
- `lg` 16/24: key content, card bodies.
- `xl` 20/28: panel titles, section headers.
- `2xl` 28/36: hero headings only.

Rules:

- Minimum readable graph text: `12px`.
- Max two font sizes per node card.
- Tight letter spacing only on large headings, never on body.

### 2) Color system (semantic, not random)

Use role-based color tokens, not ad-hoc per component.

- Background: `bg.canvas`, `bg.panel`, `bg.elevated`
- Text: `text.primary`, `text.secondary`, `text.muted`
- Border: `border.default`, `border.focus`, `border.strong`
- States: `state.success`, `state.warning`, `state.error`, `state.info`
- Graph entity kinds:
  - `node.service`
  - `node.endpoint`
  - `node.method.public`
  - `node.method.private`
  - `node.decision`
  - `node.db`
  - `node.cache`
  - `node.kafka`
- Edge kinds:
  - `edge.sync`
  - `edge.async`
  - `edge.event`

Rules:

- Keep one neutral base palette + one accent family.
- Semantic colors must map to meaning consistently across tabs.
- No standalone hex values inside component files.

#### Palette buckets (implementation-friendly)

- `primary`: interactive brand emphasis and selected states.
- `surface`: canvas, panel, and elevated backgrounds.
- `border`: default, strong, and focus separators.
- `muted`: secondary/tertiary text and subtle labels.
- `success`: healthy state, pass state, successful flow checkpoints.
- `error`: failed state, broken edge/node, critical incidents.

### 3) Spacing system (4px/8px grid)

- Base unit: `4px`.
- Primary rhythm: `8px`.
- Component spacing steps: `4, 8, 12, 16, 24, 32`.
- Panel padding default: `16`.
- Card internal spacing default: `12`.

Rules:

- No odd spacing values unless intentional and documented.
- Dense mode still follows the same scale; it only uses lower steps.

### 4) Shape and depth

- Radius: `6` (small), `10` (default), `14` (large).
- Border strategy: subtle always-on border + stronger hover/focus border.
- Shadow strategy:
  - Graph cards: low blur + slight glow.
  - Overlays: medium depth, restrained.

Rules:

- Shadows communicate hierarchy, not decoration.
- Avoid layered neon glows in dense graph states.

---

## Core Component Contract

### Button

- Variants: `primary`, `secondary`, `ghost`.
- Sizes: `sm`, `md`, `lg`.
- Required states: default, hover, active, focus-visible, disabled, loading.
- Icon placement: left/right, fixed spacing.

### Input / Select / TextArea

- Uniform field height and radius.
- Label + helper + error pattern identical for all fields.
- Keyboard-first behavior and clear focus ring.

### Modal / Drawer

- Modal for interruptive decisions.
- Drawer for contextual details while graph remains visible.
- Single close behavior and escape key support.

### Tooltip

- Short, contextual, non-blocking.
- Delay + position strategy must be consistent.

### Badge / Tag

- Use semantic tones only.
- Same color token in graph legend and node metadata.

### Loader / Skeleton

- Skeleton for content placeholders.
- Spinner only for user-triggered short actions.

---

## Core Layouts to Lock

### Sidebar navigation (critical)

- Dense, keyboard-friendly, icon + label format.
- Collapsible with persistent pinning.
- "Current context" always visible.

### Top header

- Global search (primary command point).
- User/profile, quick actions, environment indicator.
- Never overlaps graph controls.

### Content area

- Predictable scrolling zones.
- Stable content widths for readability.
- No jumpy relayout when panels open.

### Split panels (debugging / flows)

- Resizable with snap points.
- Remember last sizes per route/mode.
- Must preserve graph readability at all split states.

### Required layout pattern (always on)

- Left: navigation.
- Center: main graph/content workspace.
- Right: inspector/details panel.

---

## Benchmark Case Study (Inspiration Sources)

### A) Linear app

**What to learn:**

- High-density, low-noise interface.
- Excellent keyboard-first interaction model.
- Strong hierarchy through restrained color and spacing.
- Information appears where needed, not everywhere.

**Apply to Flow:**

- Build graph-first dense mode that stays readable.
- Keep controls compact and predictable.
- Emphasize one focused task per viewport state.

### B) Stripe dashboard language

**What to learn:**

- Clear information architecture for complex financial operations.
- Strong semantic color usage and explicit status communication.
- Trust-oriented component clarity (tables, statuses, actions).

**Apply to Flow:**

- Treat runtime and business evidence as operational data, not decoration.
- Build strong semantic states for flow health and incidents.

### C) Vercel UI language

**What to learn:**

- Bold but disciplined visual style.
- Excellent contrast and section framing.
- Strong brand identity without sacrificing legibility.

**Apply to Flow:**

- Keep dark theme premium and modern, but maintain practical contrast.
- Use restrained accents to guide attention.

### D) Raycast language

**What to learn:**

- "Command-first" interaction.
- Fast scanning with compact typography.
- Utility-first clarity with low cognitive overhead.

**Apply to Flow:**

- Make command palette and one-click actions central.
- Optimize for power users and rapid troubleshooting.

---

## Screenshot References

Captured references are stored in:

- `flow-resource/design-preview/inspiration/inspiration-linear.png`
- `flow-resource/design-preview/inspiration/inspiration-stripe.png`
- `flow-resource/design-preview/inspiration/inspiration-vercel.png`

Source URLs:

- https://linear.app
- https://stripe.com
- https://vercel.com
- https://raycast.com
- https://docs.stripe.com/dashboard/basics

---

## Flow-Specific UI Principles (Non-Negotiable)

### 1) Graph always readable

- No node overlap in default layout.
- No text collision at standard zoom.
- Auto-truncate + progressive detail reveal.

### 2) Node interaction always clear

- Clear click target area and hover state.
- One-click card for "what, where, why" summary.
- Focus mode dims unrelated graph.

### 3) No overlap of controls

- Header, tabs, search, legend, zoom controls must not block each other.
- Strict z-index and pointer-event map.

### 4) "Auto Fit Graph" control (required)

Add a dedicated button for readability reset:

- Fits graph to viewport with safe padding.
- Runs collision pass.
- Restores recommended semantic level view.
- Centers selected node/path if any.

Keyboard shortcut: `F`.

### 5) One-click understanding

A single node click should answer:

- What is this node?
- What does it do in business terms?
- What are its immediate dependencies?

---

## Zoom Readability Contract (Draft)

- `L1`: Service map only.
- `L2`: Key endpoints and high-value edges.
- `L3`: Method-level labels, short text only.
- `L4`: Detailed method cards for focused neighborhood.
- `L5`: Full technical detail panel and evidence overlays for selected path only.

Rules:

- Never reveal all details globally at deep zoom.
- At L3+, non-focused elements fade aggressively.
- Overlays auto-compact in dense graph states.

---

## Implementation Order (Recommended)

1. Design token package (type, color, spacing, radius, elevation).
2. Primitive components (button/input/select/tooltip/badge/skeleton).
3. Layout shells (sidebar/header/content/split panel).
4. Graph readability mechanics (collision, semantic zoom, focus mode, auto-fit).
5. Node cards and detail panels.
6. QA pass with readability and click-clarity gates.

---

## Delivery Plan (Step by step)

Target sequence:

1. Design system ✅  
2. Layout shell ✅  
3. Flow canvas (basic) ✅  
4. Node interactions ✅  
5. Observability ✅  
6. Polish + consistency ⏳

For this phase, we are entering **step 6** with strict consistency and readability gates.

---

## Acceptance Criteria Before Coding Core Screens

- Tokens and component variants documented and reviewed.
- Zoom readability rules agreed for L1-L5.
- Auto Fit Graph interaction approved.
- Node click contract approved (one-click comprehension).
- Overlay interaction map approved (no control overlap).

If any of these are not locked, implementation should not begin.
