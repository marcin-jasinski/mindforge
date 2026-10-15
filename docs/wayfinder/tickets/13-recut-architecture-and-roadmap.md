---
id: T13
title: Re-cut the architecture and roadmap docs
type: task
status: closed
assignee: claude
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

**Inherited from T05–T06.** `CONTEXT.md` now exists at the repo root as the glossary (created during T06);
keep it in step with the final documents and add it to `docs/INDEX.md`. Naming across all documents follows
it — notably **page path**, not slug (T06 renamed it after T02 and T05 were written).

**Inherited from T07.**

- `CLAUDE.md`'s outbox rule is re-cut to: *a run's commit and its domain events are published in the same
  `@Transactional` boundary; listeners run after commit and must tolerate missing an event.*
- `hexagonal.md`'s *Pipeline Idempotency* and *Transactional Outbox* sections and `architecture.md`'s
  *Idempotency & Reliability* section are rewritten. Phase 4.5's dedup and revision bullets change. Phase 5's
  checkpointing goes. Phase 8 stands, re-pointed at run events.
- The **guard layer** fog closed here. Its placements are now all decided, and `architecture.md` should list them
  in one place, in the spirit of the demo's "every rule stated in the prompt and enforced in code":

  | Rule | Enforced by |
  |---|---|
  | Slug/path uniqueness, cross-bundle links, dedup, revert-of-deletion path check | DB constraints (T05, T07) |
  | Zero pages fails the run, claim cap | Service checks recorded on the run (T04) |
  | Per-KB serialization | Lease column (T07) |
  | Source immutability | A type (T04) |

**Inherited from T08.**

- **Phase 6** loses 6.5 `FlashcardGeneratorAgent`, 6.7 `QuizGeneratorAgent` and 6.8 `QuizEvaluatorAgent`.
- **Phase 10** gains them as the `FlashcardGenerator`, `QuizGenerator` and `QuizEvaluator` services.
- **Phase 10.1** adds `StudyScope`.
- **Phase 10.4** replaces "Graph RAG question targeting" and `RetrievalPort.findWeakConcepts()` with weak-page SQL
  over `study_events` and `page_links`.
- **Phase 10.5** gains lazy generation with the per-session new-page limit.
- **`vision.md`'s** "track retention over time" is now per page.

**Inherited from T09.**

- **Phases:** Phase 7 (Neo4j) is deleted; Phase 11 is re-cut to Query; 8.2 `GraphIndexingListener` and 15.2's
  backfill CLI are deleted; 18/19 swap `/search` for `/ask`.
- **ADRs:** 0005 (pgvector) and 0006 (Neo4j) are marked superseded by a new retrieval ADR — the rendered index as the
  retrieval system, with named re-add conditions.
- **Retrieval rule:** `CLAUDE.md`'s and `hexagonal.md`'s "graph first → lexical second → vector last" is replaced
  with "the rendered index first; a lexical prefilter past 20K tokens; no vector store".
- **Stack docs:** `tech-stack.md` and `architecture.md` drop Neo4j and pgvector, including the External Integrations
  table and Docker Compose services. `vision.md` drops "queryable knowledge graph" as a separate capability; the
  cross-link graph view is the wiki's.
- **Extract:** its prompt takes the index and returns a proposed target path per claim — T09's refinement of T04's
  Resolve.

**Inherited from T10.**

- **A new Lint phase:** the `LinkChecker` step inside ingest (SMALL; the fifth concrete ingest service), the on-demand
  full Lint service, the SQL health queries, and a health view in the SPA.
- **Guard table:** add "Lint writes are link insertions only — enforced by the output type and the strip-links
  equality check".
- **`vision.md`:** the "study partner" framing belongs to the full Lint's suggestions.

**Inherited from T11.** Export is a new, small phase: `BundleExporter` (renderers shared with the in-app index),
`BundleConformanceValidator`, `GET /api/knowledge-bases/{kbId}/export`, and an SPA button. No new dependency.
Supersession notes render as a blockquote under the heading everywhere (amends T02 decision 8's heading prefix).

**Inherited from T12.**

- **Phase 3b — Wiki pivot cleanup** is inserted before Phase 4 and executes T12's table. It ends with `mvn verify`
  green, and its checklist says to drop and recreate local databases after the squash.
- `migrations.md` gains a dated note recording the one-time pre-deployment squash.
- CLAUDE.md's `agent` row is reworded: the package holds services that call a model, and there is no `Agent`
  interface.
- `AIGateway.embed` is gone, so no document may describe it.

## Answer

Done on 2026-09-10, under the user's standing instruction to proceed with recommended options. Every blocking ticket
(T04, T07–T12) was closed first, and every document below writes down their answers.

### What was written

| Deliverable | Change |
|---|---|
| `docs/project/architecture.md` | **Rewritten.** Layers with model services; ingest / query / study / revert / export data flows; PostgreSQL as the only store; the wiki model; history, revert and deletion; idempotency and the lease; retrieval; the guard table; security; integrations |
| `docs/project/vision.md` | **Rewritten.** Value loop is now "upload → compounding wiki → study cut from it"; principles; Phase 0–13 and 14–21 goals re-cut; evolution note |
| `docs/project/implementation-plan.md` | **v3.0.** Phases 0–3 kept as built history. New **3b** (T12's table) and **9b** (Export). **5** re-cut to Wiki Domain & Store; **6** re-cut to Ingest Pipeline; **7** number reused for Lint (Neo4j deleted); **11** re-cut to Query; 4, 8, 9, 10, 12–19, 21 changed in place. Every changed heading says so plainly. Dependency graph redrawn |
| `docs/project/roadmap.md` | **Rewritten** to match, with a change table up front and a Future Considerations list carrying ADR 0016's named re-add conditions |
| `docs/project/tech-stack.md` | Neo4j, pgvector and object storage removed; export libraries added; dependency table corrected to the real artifact ids |
| `docs/adr/0010`–`0018` | **New**, one per load-bearing decision: knowledge model (T02), taxonomy and path identity (T06), automatic revisions and revert (T03), typed pipeline (T04), Postgres storage (T05), runs / lease / no outbox (T07), index retrieval (T09), Lint link insertions (T10), flashcard identity (T08). 0005 and 0006 marked **superseded by 0016** |
| `docs/standards/architecture/hexagonal.md` | **Rewritten.** Open/Closed scoped to parsers; structural tenancy; ingest runs replace pipeline idempotency; index-first retrieval; model-service communication; domain events with no outbox |
| `docs/standards/backend/ai_agents.md` | **Rewritten** as model-service standards. `Agent` interface section gone; `VERSION` recorded on runs; the gateway, `ModelTier`, prompt files and lesson identity sections stay; adds "every rule stated in the prompt is enforced in code" |
| `docs/standards/backend/migrations.md` | Dated note recording the one-time pre-deployment squash |
| `web-security.md`, `api.md`, `test-writing.md`, `java-conventions.md` | Examples that cited `DocumentArtifact`, `AgentResult`, checkpoints or Neo4j replaced; `web-security.md` gains "an export is a client too" |
| `CLAUDE.md` | Overview; `agent` row reworded; Open/Closed scoped to parsers; outbox rule re-cut; "no model call inside a transaction"; `kbId` first; model-service rules including sole prose authorship; study data never exported; Testcontainers PostgreSQL only; Phase 3b as the restart point |
| `docs/INDEX.md` | Descriptions updated; new *Domain Language and Decisions* section indexing `CONTEXT.md`, the ADRs, this map and `docs/wiki/` |
| `CONTEXT.md` | Created during T06 and kept current through T11 — the glossary every document above uses |

### Gaps filled while writing — flagged, not hidden

This ticket was meant to write down decisions, not make them. Three places needed a small call that no ticket had made
explicitly. Each took the option most consistent with the closed tickets, and each is marked in the plan:

1. **Conversation edits are an explicit action in chat, not a classified message** (Phase 11.3). T03 put edits in
   conversation but did not say how a question is told apart from an instruction. An explicit action needs no
   router model call. Reopen it if the UX demands free-form routing.
2. **Phase 16 image descriptions become text blocks before extraction**, and **Phase 17 fetched articles become
   `Document`s of their own** (`UploadSource.ARTICLE`). Both follow mechanically from `DocumentArtifact`'s death (T02)
   and per-document provenance (T07); no ticket addressed those phases.
3. **Phase numbering**: 7 is reused for Lint because it occupies the vacated slot in the dependency graph; Export is
   `9b` after the API it needs. Both are stated in each heading and in the roadmap's change table.

### Handed off, not decided

The map's remaining fog — section conventions and prose language (Phase 6.1), SPA layout (Phase 12), and real
per-run cost (Phase 14) — is written into those phases' tasks. None of it blocks writing a line of Phase 3b.

### Not done

No code was changed: Phase 3b is implementation and lies past this map's destination. Nothing was committed.
