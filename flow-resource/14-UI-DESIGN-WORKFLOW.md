# Flow UI — Design Workflow: Figma, OSS Kit, and How to See the UI Before Build

**Status:** Working document  
**Scope:** How we connect design (Figma, OSS, or static preview) so you can see and lock the UI before development.

---

## 1. Three Ways We Can Do This

| Option | What it is | Your role | My role |
|--------|------------|-----------|---------|
| **A. Figma + MCP** | You (or we) design in Figma. Cursor connects to Figma via MCP so I can read frames, variables, components. | Enable Figma MCP in Cursor; optionally create/duplicate a Flow file in Figma. | Read design context, align tokens/blueprints to your frames, suggest changes. |
| **B. OSS UI kit (shadcn) + Figma kit** | We base Flow UI on **shadcn/ui** (React + Tailwind). There are free Figma community kits that match. Design in Figma with the same components we’ll use in code. | Use the shadcn Figma kit to build screens; share Figma link or screenshots. | Document tokens, wireframes, and map your Figma to our screen blueprints. |
| **C. Static HTML preview in repo** | I add a small set of HTML/CSS files in the repo (e.g. `flow-resource/design-preview/`) that use our **design tokens** and approximate key screens. You open in browser. | Open the HTML files, review, tell me what to change. | Create and update the static pages; no Figma or MCP needed. |

**Recommendation:** Use **B + C** for speed: lock tokens and layout with a **static HTML preview** first, then either (1) you refine in **Figma** using a **shadcn**-based kit and share links, or (2) we go straight to build with shadcn. Add **A (Figma MCP)** if you want me to read your Figma file directly and keep docs in sync.

---

## 2. Option A — Connecting Figma via MCP

Figma provides an **MCP server** so Cursor (or other AI tools) can work with Figma files.

### How to enable (what you can do)

1. **Figma account**  
   You need a Figma account (free is fine for design; Dev/Full seat improves API limits if we use MCP a lot).

2. **Connect Figma MCP in Cursor**  
   - In Cursor: **Settings → MCP** (or Cursor settings where MCP servers are configured).  
   - Add the **Figma MCP server**.  
   - **Remote:** Figma hosts it at `https://mcp.figma.com/mcp` (see [Figma MCP docs](https://developers.figma.com/docs/figma-mcp-server)).  
   - **Desktop:** Alternatively, run the MCP server locally via the Figma desktop app if you use it.  
   - Follow the auth steps Figma provides (usually OAuth or token).

3. **Create a Flow design file (optional but useful)**  
   - New Figma file, e.g. `Flow — UI Lock`.  
   - One page per key screen: Documentation Home, Graph + Replay, Node Doc Card, etc.  
   - Share the file link with me (or leave it open; with MCP I may be able to reference it by file key if configured).

4. **What I can do once connected**  
   - Read frames, components, and (where available) variables from your Figma file.  
   - Align our **design tokens** and **screen blueprints** in `13-UI-BRAINSTORM.md` (and here) to match your frames.  
   - Suggest concrete changes (e.g. “use token `--color-surface` for panel background”) so design and docs stay one source of truth.

**If you don’t want to use MCP:** You can still use Figma. Share **links** or **screenshots** of key screens; I’ll document the design language and layout in the repo so implementation follows your designs.

---

## 3. Option B — Open Source UI Elements (shadcn/ui + Figma kit)

So the UI looks modern and we don’t reinvent components:

- **In code:** We use **[shadcn/ui](https://ui.shadcn.com)** (React + Tailwind, copy-paste components, accessible, MIT).  
- **In Figma:** Use a **Figma Community kit** that matches shadcn, so what you design matches what we build.

### Useful Figma kits (free, community)

- [shadcn/ui – Figma](https://ui.shadcn.com/figma) (official doc).  
- [OpenSource shadcn/ui – kit for Figma](https://www.figma.com/community/file/1426161867268046394) (Figma Community).  
- [shadcn/ui components with variables & Tailwind classes](https://www.figma.com/community/file/1342715840824755935) (variables + Tailwind alignment).

### Flow-specific layer on top

- Our **design tokens** (colours, spacing, radius) override or extend the default theme (e.g. Tailwind theme in code, matching variables in Figma if the kit supports them).  
- We still only use: **one graph canvas**, **one main panel**, **lens switcher**, **minimal chrome** — so we stay “Flow” and not generic dashboard.

**Your help:** If you like designing in Figma, use one of the shadcn kits above and build the 3–4 key screens (Doc Home, Graph + Replay, Node Doc Card). Share the file link or screenshots; I’ll write the screen blueprints and token list to match.

---

## 4. Option C — Static HTML Design Preview in Repo

So you can **see layout and design language in the browser** without Figma or a dev server:

- I add a folder, e.g. `flow-resource/design-preview/`, with:
  - `tokens.css` — our design tokens (colors, spacing, typography).  
  - `index.html` — list of preview pages.  
  - `doc-home.html`, `graph-replay.html`, `node-card.html` — one HTML file per key screen, using only those tokens and minimal layout (no real graph logic, just placeholders).

- You open the HTML files in a browser (from the repo or after a simple `npx serve` if needed).  
- You say what to change (e.g. “panel wider”, “replay path more visible”); I update the HTML and tokens until we lock.

**Pros:** No Figma or MCP setup; quick iteration; tokens and layout are in the repo as single source of truth.  
**Cons:** Not interactive like a real app; good for “look and feel” and layout, not for full interaction design.

---

## 5. Design Tokens We’ll Use (single source of truth)

So design language is consistent everywhere (Figma, HTML preview, or code):

```css
/* Flow — Design tokens v1 (light theme) */
:root {
  /* Surfaces */
  --flow-bg-base:      #ffffff;
  --flow-bg-subtle:    #f5f5f6;
  --flow-bg-muted:     #e8e8ec;
  --flow-surface:      #ffffff;
  --flow-border:       #e0e0e4;
  --flow-border-focus: #2563eb;

  /* Text */
  --flow-text-primary:   #0f0f12;
  --flow-text-secondary: #52525b;
  --flow-text-muted:     #71717a;

  /* Semantic (replay / status) */
  --flow-success: #16a34a;
  --flow-warning: #ca8a04;
  --flow-error:   #dc2626;
  --flow-path:    #2563eb;

  /* Spacing (px) */
  --flow-space-1: 4px;
  --flow-space-2: 8px;
  --flow-space-3: 12px;
  --flow-space-4: 16px;
  --flow-space-6: 24px;
  --flow-space-8: 32px;

  /* Typography */
  --flow-font-sans:   system-ui, -apple-system, sans-serif;
  --flow-font-mono:   ui-monospace, monospace;
  --flow-text-sm:     12px;
  --flow-text-base:   14px;
  --flow-text-lg:     16px;
  --flow-text-xl:     18px;

  /* Radius */
  --flow-radius: 6px;
  --flow-radius-lg: 10px;

  /* Shadow (minimal) */
  --flow-shadow-panel: 0 1px 3px rgba(0,0,0,0.08);
}
```

These can be copied into Figma (as variables or style names) or into Tailwind theme when we build. Any change to “how the UI looks” should update this block and the rest follows.

---

## 6. Key Screens to Lock (reminder)

So we’re aligned on what “see the UI” means:

1. **Documentation Home** — Service summary, top capabilities, main journeys; lens switcher visible.  
2. **Graph + Replay** — Canvas with one highlighted path; zoom control; trace selector; minimal top bar.  
3. **Node Doc Card** — Side panel with: What / Why / Inputs–Outputs / Runtime Evidence (variables on demand).  
4. **Lens comparison** — Same graph, different default zoom/labels (Business vs Learn vs Engineering); we can show this as one page with three columns or three screens.

Once these look right (in Figma or in the static HTML preview), we lock and implement.

---

## 7. What I Need From You (pick one or combine)

- **Figma MCP:** Enable Figma MCP in Cursor and, if you can, create a Flow file with the 3–4 key screens. Share the file link. I’ll align tokens and blueprints to your frames.  
- **Figma without MCP:** Design the same screens in Figma (using a shadcn kit if you like) and share **links or screenshots**. I’ll document layout and tokens to match.  
- **Static preview:** I add `flow-resource/design-preview/` with HTML + `tokens.css`. You open in browser and tell me what to change until we’re happy; then we lock.  
- **You build a quick prototype:** If you prefer to build a small React (or HTML) prototype yourself and show me, I’ll document the design language and screens from that.

Tell me which path you want (Figma + MCP, Figma only, static preview, or you prototype), and we’ll do that first. We can always add another option later (e.g. lock with static preview, then refine in Figma with shadcn kit).
