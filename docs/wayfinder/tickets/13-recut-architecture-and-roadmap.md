---
id: T13
title: Re-cut the architecture and roadmap docs
type: task
status: open
assignee:
blocked_by: [T04, T07, T08, T09, T10, T11, T12]
---

## Question

The destination. Every decision is made; write it down.

Deliverables:

- **`docs/project/architecture.md`** — layers, the `WikiStore` port, the data-flow diagram
  redrawn as `upload → Ingest → wiki pages`, the revised roles of PostgreSQL, Neo4j, pgvector
  and object storage, and the revised idempotency section.
- **`docs/project/vision.md`** — the core value loop restated. It currently reads
  "upload → artifacts → quiz". It becomes "upload → compounding wiki → study artifacts cut from
  it". The goals lists for Phases 0–13 and 14–21 both need re-cutting.
- **`docs/project/implementation-plan.md`** and **`docs/project/roadmap.md`** — Phases 4–21
  re-cut. Expect this to get *shorter*: Summarizer and ConceptMapper collapse into Ingest,
  Phase 7 shrinks or goes, Phase 11 folds into Query. Lint and Export are new. Keep phase
  numbering stable where a phase is unchanged, and say plainly where it is not.
- **ADRs under `docs/adr/`** for the load-bearing decisions — at minimum the knowledge model
  (T02), the storage seam (T05), the Ingest execution model (T04) and the approval policy (T03).
  One ADR per decision, linking back to its ticket.
- **`docs/INDEX.md`** updated if any document is added or its scope changes.
- **`CLAUDE.md`** — its Non-Negotiable Rules name step fingerprinting, the outbox boundary, the
  seven agents and the retrieval order. Several will be stale.

Do not start until every blocking ticket is closed. Read each one's `## Answer` first — this
ticket writes down decisions, it does not make them.

**Inherited from T04.** Concrete document work now known.

- `docs/standards/backend/ai_agents.md` is largely rewritten: the `Agent` interface section and the
  `AgentCapability` example go; the `AIGateway`, `ModelTier`, prompt-file and lesson-identity sections stay
  verbatim.
- CLAUDE.md's agent rules survive except the open/closed line, which must be re-scoped to parsers.
- Phases 5 and 6 are re-cut around four concrete services and a fan-out, not seven agents and a DAG.
- `architecture.md`'s data-flow block is rewritten end to end: the step-fingerprint line, the
  `DocumentArtifact` checkpoint line and the seven-agent pipeline all go.

## Answer
