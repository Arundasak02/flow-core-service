# Flow — Deferred Feature Tracker

**Status:** Working backlog
**Scope:** Features intentionally deferred while building the agent-first delivery path

---

## Deferred Tracks

### DF-001 — CI/CD Static Graph Push (Separate Track)

- **Status:** Deferred (not part of agent-first MVP)
- **Owner:** Platform team
- **Reason:** Agent-first delivery already provides zero-config enterprise onboarding.
- **Target window:** After agent-first rollout is stable in production.

#### Scope

1. **GitHub Action** to scan and push `flow.json` during CI
2. **Jenkins plugin/step** for enterprise pipelines
3. **CLI push command** (`flow-adapter push --server ... --api-key ... flow.json`)

#### Success Criteria

- Graph is available in FCS before deployment starts
- Build failure behavior configurable (`fail-open` vs `fail-closed`)
- Supports monorepos and multi-module builds
- Uses hash dedup to avoid duplicate pushes

#### Explicit Non-Goals (for this track)

- Replacing agent-first static delivery
- Coupling runtime event delivery to CI success

---

## Notes

- Agent-first remains the default and recommended delivery mode.
- CI/CD push is additive and optional.
