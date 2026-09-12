---
kind: wayfinder:map
title: Integrate LLM Wiki + OKF into MindForge
created: 2026-08-29
---

# Integrate LLM Wiki + OKF into MindForge

## Destination

A revised MindForge architecture and roadmap — `docs/project/architecture.md`,
`docs/project/vision.md`, `docs/project/implementation-plan.md` re-cut, plus supporting
ADRs — describing MindForge as: **uploaded documents ingest into a per-`KnowledgeBase`
OKF wiki that compounds; flashcards, quizzes and Query are cut from the wiki; the bundle
is exportable as a conformant OKF bundle.**

The map is done when every decision below is made and those documents can be written
without further design work.

## Notes

**Domain.** MindForge is a Java 21 / Spring Boot 4.1 hexagonal learning platform, currently
at Phase 3 of 21 — domain records, JPA persistence and `AIGateway` exist; parsers, pipeline,
agents, Neo4j, API, quiz engine and frontend do not. Almost everything this map touches is
still on paper, which is why it is worth doing now.

**Source material.**
- `docs/wiki/llm_wiki.md` — the LLM Wiki pattern (this repo).
- `D:\Dokumenty\Projekty\llm-wiki-okf-demo` — a working reference implementation. Its
  `README.md`, `CONTEXT.md`, `docs/concepts/okf_spec.md` and 27 ADRs under `docs/adr/`.
- `docs/INDEX.md` — MindForge's own standards index. Read it before any ticket.

**Settled before charting** (do not re-litigate):
- MindForge remains a *learning* platform. The wiki is the substrate, not the product.
- One `KnowledgeBase` = one OKF bundle.
- Operations adopted: **Ingest**, **Query**, **Lint**. Verify is out.
- Export is a product feature. Import is not.
- The SPA is the only writer. No external editing surface.

**Skills.** `/grilling` and `/domain-modeling` for the decision tickets; `/research` for
the research ticket; `/ponytail` throughout — MindForge's phase plan is already 21 phases
long and this change should make it shorter, not longer.

**This map carries execution.** Its final ticket writes the destination documents rather
than handing off. Every other ticket is a decision.

**Tracker convention.** Tickets are markdown files in `tickets/`, one per file, with YAML
frontmatter carrying `id`, `type`, `status`, `assignee` and `blocked_by`. A ticket is
*claimed* by filling `assignee`; *closed* by setting `status: closed` and filling the
`## Answer` section. The **frontier** is every ticket with `status: open`, empty `assignee`,
and every id in `blocked_by` closed:

```
grep -l 'status: open' docs/wayfinder/tickets/*.md
```

## Decisions so far

<!-- one line per closed ticket -->

- [What transfers from the demo agent to a Java server](tickets/01-demo-transfer-research.md) —
  model-failure ADRs transfer, runtime ADRs don't (9 / 6 / 12); the four that change the design:
  store links as page identity not text, supersession is the whole compounding story now Verify
  is out, a run that wrote nothing must fail, and the LLM proposes content while membership and
  deletion stay code's. Notes: [`docs/wiki/demo-transfer-notes.md`](../wiki/demo-transfer-notes.md).
- [What a wiki page is in the domain model](tickets/02-wiki-page-domain-model.md) — a typed record
  with an opaque prose body: identity, links, provenance and supersession are fields, prose is one
  `String`. Links are truth in the body (rows derived, resolved by join at read); metadata is truth
  in rows (frontmatter projected on export). `PageType` is a normalising value object;
  `KnowledgeBase` stays thin with `kbId` first on every `WikiStore` method; `index.md` and `log.md`
  are projections; pages are mutable with `PageRevision` beside them; supersession never touches the
  superseded page's prose. `DocumentArtifact`, `SummaryData` and `ConceptMapData` die — the run
  record is a new ingest-run entity.
- [Whether wiki revisions need human approval](tickets/03-human-approval-of-revisions.md) — automatic,
  with no pending state anywhere in the system. Recovery is a per-run revert: restore-forward, and offered
  only while the run is still the tip, so undo has a window that closes at the next ingest touching the same
  page. Supersession follows the same rule, gated by nothing but given its own removable line in the run
  report because it is the only write whose damage is silent. No hand-editing of page bodies — the LLM stays
  sole author of prose, so the ownership claim holds literally and there is no merge story. All user edits,
  deletion included, are an Ingest run sourced from a conversation turn, not a fourth Operation.

- [Whether Ingest is an agent loop or a typed pipeline](tickets/04-ingest-execution-model.md) — a typed
  pipeline with a dynamic fan-out over pages: Extract (LLM) -> Resolve (code, retrieval) -> Write (LLM, xN)
  -> Supersede (LLM, x1). Retrieval replaces the loop's wandering and Lint replaces its iteration, so every
  call stays single-shot and `AIGateway`, `DeadlineProfile` and `CostTier` survive untouched. T01 §24's whole
  guard inventory becomes unrepresentable rather than guarded. `SummarizerAgent` and `ConceptMapperAgent`
  die, flashcards and quizzes relocate to on-demand services, and the `Agent` / `AgentContext` /
  `AgentResult` / `AgentCapability` abstraction is deleted for four concrete services — `ParserRegistry`
  stays, as the only genuinely open extension point. Bodies generate outside a transaction and commit in
  one; partial success lands, zero pages fails the run; ingest serializes per `KnowledgeBase`; the claim set
  is capped at Extract and a hit fails loudly rather than truncating.
- [Where the wiki bundle physically lives](tickets/05-wiki-storage.md) — Postgres rows only; the bundle exists
  only as an export output and `StoragePort` plays no part. History is an append-only post-write snapshot
  (title, type, body) stamped with its run and kept forever; `PageSource` and `PageSupersession` carry the run
  id too, so revert is restore-forward plus a delete by run. Deletion hard-deletes the page row behind a
  tombstone revision while history tables outlive it without an FK, so no read path filters `deleted_at`.
  Tenancy is `knowledge_base_id` on every table plus a composite key, not RLS; the store scopes and never
  authorizes, holds links in canonical form without rewriting, and leaves the transaction on the application
  service.
- [The page taxonomy and bundle layout](tickets/06-page-taxonomy.md) — two types, `Concept` and `Source Summary`,
  fixed by MindForge and assigned by code, so the model never classifies. A page's **path** (renamed from slug)
  is its identity and its OKF Concept ID: directory fixed by immutable type, name transliterated from the title
  once, never renamed. The model authors title, a new one-sentence `description`, and prose in the KB's
  language; paths, types and headings are English protocol. One projected root `index.md` serves export and the
  model alike; `log.md` is projected from runs for export only. Frontmatter is OKF's recommended keys with no
  extensions, and `# Citations` is projected from `page_sources`.
- [What replaces step-fingerprint checkpointing](tickets/07-idempotency-and-failure.md) — nothing: a re-run is a
  new run, so nothing is skipped. Identical uploads dedup on `UNIQUE (kb, content_hash)`, which also fixes a
  cross-tenant dedup bug in today's port. A revised upload is a new `Document` of the same lesson and a new run,
  with no retraction. The ingest run is the one record: statuses RUNNING/WRITTEN/COMPLETED/FAILED with a startup
  sweep, revert is a run of its own, counts are derived from revisions, and `step_versions` is where prompt
  versioning now lives. A lease column on `knowledge_bases` serializes every page-writing run and pending
  documents are the queue. No outbox table: `AFTER_COMMIT` listeners, as Phase 8 already said. Fingerprints,
  checkpoints and `DocumentStatus` are deleted.
- [How flashcards and quizzes are cut from the wiki](tickets/08-study-artifacts-from-wiki.md) — never wiki pages.
  Flashcards are rows cut lazily from Concept pages, a bounded number of new pages per session. Each is identified
  by a hash of its content and restamped when its page's revision moves, so unchanged cards keep their SM-2
  history, changed answers reset, and revert brings the old cards back with their history. Superseded sections are
  skipped by joining on the card's `section_anchor`. Quizzes are one generation call per session, held only in the
  server-side session row with their reference answers. Scope is the whole KB, a lesson or a page, and weakness is
  a per-page event log targeted through `page_links`, with no Graph RAG. Nothing study-related reaches an export.
- [What Query does to pgvector and Neo4j](tickets/09-query-retrieval-neo4j.md) — both go. The rendered index is the
  retrieval system: Postgres has no Polish stemmer and no lexical tier matches synonyms, so every miss would be a
  duplicate page. Extract reads the index and proposes a target path per claim, and Resolve stays code and verifies
  the paths (a refinement of T04 with no new LLM call). A trigram prefilter waits behind a 20K-token ceiling, and
  vector search returns only on measured prefilter misses. The graph is `page_links` in SQL. Query replaces Phase
  11's RAG as two single-shot calls per turn and never files answers back — "save that" is a conversation edit.
  Citations stop at pages. There is no cache in front of the wiki, and the CLI and bot phases only swap `/search`
  for `/ask` and drop the Neo4j backfill.
- [When and how Lint runs](tickets/10-lint-operation.md) — three tiers. Structural checks (dangling, wrong-directory,
  orphan, duplicate title, dangling supersession) are live SQL with no run. A SMALL link check runs inside every ingest
  on in-memory bodies before commit, so the ingest keeps its own revert window. A full Lint runs only on request as a
  `LINT` run behind the lease. Lint never writes prose: the model proposes `LinkInsertion`s, code applies them and
  verifies the text is unchanged once links are stripped, and headings are never linked. Contradictions are reported,
  never fixed. Suggestions stay as study prompts, never generated pages. Findings go to a health view, not `log.md`.
- [How a bundle gets exported](tickets/11-bundle-export.md) — a synchronous zip built with the standard library, with no
  git and no new dependency. It holds the wiki layer only: raw sources are excluded because MindForge does not keep the
  bytes, and for size, copyright and privacy. Pages are SnakeYAML frontmatter plus the body verbatim, with supersession
  notes as a blockquote under the heading (anchors stay stable, amending T02) and a `# Citations` section projected from
  `page_sources`. The `log.md` vocabulary is final. Rendered files are checked against OKF §9 before sending, and a
  violation is a 500, never a shipped bundle. Nothing is stripped because study tables are never read. Everything comes
  from one read-only REPEATABLE READ snapshot, without the lease.
- [What of Phases 0-3 survives](tickets/12-existing-code-fate.md) — a new Phase 3b cleanup, shaped like 2b: delete 22 of
  the 72 main files and 4 of the 9 test classes, change 14, and squash V1–V7 into one baseline holding only the
  surviving tables — a dated, one-time, pre-deployment exception. The rule: delete a dead design, change what is wrong,
  keep what the new design uses unchanged. Contradicting T04's assumption, `AIGateway` changes: `embed` goes. The
  cross-tenant dedup lookup gets a regression test. `dev.mindforge.agent` keeps its name as the home of services that
  call a model. `FlashcardData` is deleted now and written as `Flashcard` in Phase 10.
- [Re-cut the architecture and roadmap docs](tickets/13-recut-architecture-and-roadmap.md) — the destination documents
  are written:
  - rewritten: architecture, vision, roadmap, tech stack, the hexagonal and model-service standards;
  - `implementation-plan.md` v3.0 — new 3b and 9b, 5/6/11 re-cut, 7 reused for Lint;
  - new: ADRs 0010–0018, with 0005 and 0006 superseded;
  - updated: `CLAUDE.md`, `INDEX.md` and four other standards.

  Three small gaps were filled while writing and are flagged in the ticket: conversation edits as an explicit action,
  the reshaping of Phases 16–17, and the phase numbering. No code changed.

**From the 2026-09-12 review** (see *Review* below):

- [What the supersession step reads and what code verifies](tickets/14-supersession-inputs-and-guards.md) — Supersede reads
  this run's claims and the sections of live Concepts one `page_links` hop from the pages it wrote, read after commit 1
  and trimmed in rank order to a token budget with the omission counted. Code keeps a proposal only if it names a section
  the detector was shown and a Concept the run revised.
- [How a conversation edit finds its pages](tickets/15-conversation-edit-entry.md) — through Extract with an edit prompt
  returning claims, `Delete` and `Retitle` items that Resolve verifies against live Concepts; no RelevanceGuard, no Source
  Summary. Turns share the reserved lesson `conversation`; an edit that names no page fails and is not retryable.
- [What revert does to provenance](tickets/16-revert-provenance.md) — sources and supersessions are deleted only for the
  pages a revert restores; a REVERT run cannot be reverted (retry instead); removing one supersession is a REVERT run
  under the lease; `supersession_count` keeps log lines stable; uploaded text is retained, because revisions keep what it
  taught.
- [The run lifecycle](tickets/17-run-lifecycle.md) — every run is born `QUEUED` (overriding the recommended 409); a claim is
  the lease plus `QUEUED` → `RUNNING` in one transaction; commits are fenced on status; one sweep at startup and every
  minute settles runs this process is not executing and re-queues interrupted ingests up to three attempts; a retry
  endpoint; one global permit pool for background calls.
- [Lesson ids and one identifier grammar](tickets/18-lesson-and-path-identity.md) — an existing lesson id is a new version
  only with `newVersion`, otherwise 409; one `Identifier` grammar for page names, lesson ids and anchors, shared with the
  validator; `slugify` maps Greek letters and appends a hash wherever it drops a letter.
- [Normalising model text](tickets/19-rendering-model-text.md) — `TextRules` makes titles and descriptions single-line and
  bounded at write time; renderers escape link text; rule 3 is re-worded; the index always has both sections; `timestamp`
  is `updated_at`.
- [Progress and domain events](tickets/20-progress-and-domain-events.md) — progress is a best-effort `ProgressNotifier`
  port streamed per knowledge base; the only run event is `IngestRunQueued`; `DomainEvent` drops `documentId()`; Phase 8
  is retired into 6 and 9.
- [Revision guards](tickets/21-revision-guards.md) — the writer gets superseded sections as a list beside the body; a
  document ingest must keep every section of a Concept; an unchanged draft writes nothing and a run fails only when no page
  task succeeded; cards go stale on a content hash, not a revision.
- [Resolve and title rules](tickets/22-resolve-and-title-rules.md) — one task per final path; claims target live Concepts
  only; every claim carries a title; titles are fixed at creation except by `Retitle`; duplicate titles are a finding, not
  an invariant.
- [Long documents and the index ceiling](tickets/23-extract-long-documents.md) — Extract per heading-aware chunk,
  sequentially, seeing pages planned so far; a claim cap per call and a page-task cap per run; writers get claims plus
  source blocks; tokens are characters ÷ 3; the ceiling shows in the health view.
- [Persistence mechanics](tickets/24-persistence-mechanics.md) — cross-cascade FKs are deferred; a busy knowledge base
  cannot be deleted; dedup is check-then-insert under the row lock the lesson rule already takes; `document_count` is
  derived.
- [The port read surface](tickets/25-port-read-surface.md) — `WikiStore` stays page-shaped beside `RunReportQuery`,
  `WikiHealthQuery` and `BundleQuery`; `kbId` first on every tenant-scoped port, with two named sweep methods excepted.
- [Heading and link grammar](tickets/26-markdown-and-link-rules.md) — one `MarkdownStructure` parser; only level-1 headings
  outside fences are sections; an internal link has exactly one form and any other page link fails the draft; external
  links are allowed; insertion excludes fences, autolinks and tags.
- [Study edge cases](tickets/27-study-edge-cases.md) — a per-page lock plus conflict-tolerant inserts; one generation
  budget for new and stale pages with `BATCH` deadlines; anchors follow reused cards; weakness is the last 5 events below
  3.0, cold start in creation order; revived cards are due now.
- [Minor findings](tickets/28-minor-review-findings.md) — articles are create-only and off by default; `cost` stays NULL
  until Phase 14; the interactions migration; `Preprocessor` is plain code; diffs in the SPA; no admin role; a Polish
  `Collator`; T12's counts corrected.
- [Fold the review into the documents](tickets/29-fold-review-into-docs.md) — the plan is v3.1 with Phase 8 retired; the
  architecture and the hexagonal and model-service standards are rewritten; ADRs 0011–0018 carry dated amendments;
  `CONTEXT.md` gains Section and Conversation Edit. No code changed.

## Review (2026-09-12)

A review of the finished spec against the code on `feature/llm-wiki-okf` and the OKF spec found seven blockers (T14–T20),
seven gaps (T21–T27) and nine minor findings (T28). Each was decided above, and T29 wrote the answers into the
destination documents.

## Not yet specified

> **Map complete (2026-09-10; review folded in 2026-09-12).** Every ticket is closed and the destination documents are written. The patches below
> are not open decisions on this route; each is handed to the phase that can settle it: the prompt layer → Phase 6.1,
> SPA surfaces → Phase 12, cost and latency → Phase 14 once a real run exists.

- **The prompt layer.** MindForge versions Markdown prompts under `prompts/pl/`. T06 settled that there is no
  per-`KnowledgeBase` conventions layer (the taxonomy is fixed and assigned by code) and that Polish pages are
  OKF-conformant. T04 narrowed it to four ingest prompts and removed `PROMPT_VERSION`'s home along with the
  `Agent` interface; T07 put versioning back as a `VERSION` constant per service, recorded on each run. What is
  left: section conventions per page type, and which language the prose is written in when a source's
  language differs from the prompt locale.
- **SPA surfaces.** Page browser, cross-link graph view, the **run report** (pages written with diffs,
  claims superseded with per-row removal, revert control), and the **conversational edit surface** T03
  made the only way a user changes a page. T03 replaced the approval queue with an after-the-fact report,
  so what Phase 12 owes is a diff view and a revert control rather than a queue — and whether revert is a
  control there or an "undo that" in chat is open. The taxonomy is fixed now (T06: two types, paths, one root index),
  T09 put the graph view on `page_links` and the chat on Query, T08 added the study scopes, and T10 added a
  knowledge-base **health view** (live SQL findings plus the latest full Lint's findings and suggestions). What
  remains is layout and flow, which Phase 12 can design directly.
- **Cost and latency.** Mostly resolved by T04: calls stay single-shot so `DeadlineProfile` and `CostTier`
  need no re-cutting, N is bounded by the claim cap, and writes fan out in parallel over virtual threads.
  What is left is empirical — whether the cap sits in the right place, and what one upload actually costs
  once a real run exists. No per-run cost budget until then.

## Out of scope

- **The Verify Operation, Evidence Tree and drift schema** (demo ADRs 0018, 0019, 0023, 0026,
  0027). A learning platform has no authoritative corpus to check pages against — the raw
  uploads *are* the only evidence, and Ingest has already read them.
- **External editing**: xWiki as a wiki store, Obsidian write-back, git as a storage backend,
  concurrent-writer conflict handling (demo ADRs 0011–0013, 0017, 0021). The SPA is the only
  writer.
- **OKF import and bundle merge.** Export ships; ingesting someone else's bundle, conformance
  validation on the way in and overlap merging do not.
