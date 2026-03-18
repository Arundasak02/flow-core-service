# Flow — Source of Truth

This folder is the **single authoritative repository** of all Flow platform documentation.
All four repos (`flow-java-adapter`, `flow-engine`, `flow-core-service`, `flow-runtime-agent`) reference this folder as the canonical source.

If any repo changes a contract, payload shape, endpoint path, or architectural decision — **update these documents in the same change-set.**

---

## Document Index

| # | Document | Scope |
|---|----------|-------|
| 01 | [Vision & Product](01-VISION-AND-PRODUCT.md) | Business objectives, product boundaries, what Flow is and isn't, target users |
| 02 | [Architecture Overview](02-ARCHITECTURE-OVERVIEW.md) | System decomposition, repo responsibilities, dependency chain, end-to-end data flow, tech stack |
| 03 | [Contracts & Protocols](03-CONTRACTS-AND-PROTOCOLS.md) | nodeId contract, graphId contract, event payloads, GEF format, API contract summary, backpressure rules |
| 04 | [Flow Engine Guide](04-FLOW-ENGINE-GUIDE.md) | Pure Java library: data model, pipeline, zoom levels, merge engine, export |
| 05 | [Flow Core Service Guide](05-FLOW-CORE-SERVICE-GUIDE.md) | Spring Boot microservice: ingestion, storage, querying, export, monitoring, configuration |
| 06 | [Flow Java Adapter Guide](06-FLOW-JAVA-ADAPTER-GUIDE.md) | Build-time scanner: architecture, plugins, output format |
| 07 | [Flow Runtime Agent Guide](07-FLOW-RUNTIME-AGENT-GUIDE.md) | Java agent: instrumentation, safety rules, pipeline, transport, configuration, phased delivery |
| 08 | [Coding Standards](08-CODING-STANDARDS.md) | Design patterns, SOLID principles, conventions, testing strategy, build order |
| 09 | [API Reference](09-API-REFERENCE.md) | Complete REST API documentation with request/response examples |
| 10 | [Roadmap](10-ROADMAP.md) | Current status, what's next, phased delivery, key decisions |

---

## Rules for This Folder

1. **This is the truth.** If code and docs disagree, update the docs (or fix the code).
2. **Cross-repo contracts** (nodeId, graphId, event payloads) must be updated here before implementation.
3. **No duplicate docs.** Individual repos should NOT have their own copies of this information.
4. **Number-prefixed ordering** ensures documents are read in logical sequence.
