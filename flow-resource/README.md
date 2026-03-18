# Flow — Source of Truth

> **Flow is Google Maps for code** — a SaaS platform that visualizes how code executes in real time, with business context at every step.

This folder is the **single authoritative repository** of all Flow platform documentation.
All repos (`flow-engine`, `flow-core-service`, `flow-java-adapter`, `flow-runtime-agent`, and future language adapters/agents) reference this folder as the canonical source.

If any repo changes a contract, payload shape, endpoint path, or architectural decision — **update these documents in the same change-set.**

---

## Document Index

| # | Document | Scope |
|---|----------|-------|
| 01 | [Vision & Product](01-VISION-AND-PRODUCT.md) | What Flow is, the three objectives, market positioning, target users, what Flow is NOT |
| 02 | [Architecture Overview](02-ARCHITECTURE-OVERVIEW.md) | Language-agnostic core vs language-specific periphery, repo responsibilities, data flow, tech stack |
| 03 | [Contracts & Protocols](03-CONTRACTS-AND-PROTOCOLS.md) | nodeId contract, graphId contract, event payloads, GEF format, API contracts, backpressure rules |
| 04 | [Flow Engine Guide](04-FLOW-ENGINE-GUIDE.md) | Language-agnostic graph processing library: data model, pipeline, zoom levels, merge engine, export |
| 05 | [Flow Core Service Guide](05-FLOW-CORE-SERVICE-GUIDE.md) | Central SaaS microservice: ingestion, storage, querying, export, monitoring, configuration |
| 06 | [Flow Java Adapter Guide](06-FLOW-JAVA-ADAPTER-GUIDE.md) | First language adapter (Java): build-time scanner, plugins, GEF output |
| 07 | [Flow Runtime Agent Guide](07-FLOW-RUNTIME-AGENT-GUIDE.md) | First runtime agent (JVM): instrumentation, safety rules, pipeline, transport, phased delivery |
| 08 | [Coding Standards](08-CODING-STANDARDS.md) | Design patterns, SOLID principles, conventions, testing strategy, build order |
| 09 | [API Reference](09-API-REFERENCE.md) | Complete REST API documentation with request/response examples |
| 10 | [Roadmap](10-ROADMAP.md) | Current status, strategic priorities, SaaS milestones, key decisions |
| 11 | [Gap Analysis & Detailed Roadmap](11-GAP-ANALYSIS-AND-DETAILED-ROADMAP.md) | Audit results, PetClinic validation, low-level checklist, brainstorm points |
| 12 | [Deferred Feature Tracker](12-FEATURE-TRACKER.md) | Features intentionally deferred as separate tracks (including CI/CD graph push) |

---

## Rules for This Folder

1. **This is the truth.** If code and docs disagree, update the docs (or fix the code).
2. **Cross-repo contracts** (nodeId, graphId, GEF format, event payloads) must be updated here before implementation.
3. **No duplicate docs.** Individual repos should NOT have their own copies of this information.
4. **Number-prefixed ordering** ensures documents are read in logical sequence.
5. **Language-agnostic first.** Core contracts (GEF, events, API) are defined language-agnostically. Language-specific details are noted as examples within the relevant adapter/agent guides.
