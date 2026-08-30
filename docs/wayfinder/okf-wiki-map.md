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

## Not yet specified

- **The prompt layer.** MindForge versions Markdown prompts under `ai/prompts/pl/`; the demo
  uses overridable per-Operation prompt files plus a per-wiki conventions doc. Whether
  MindForge needs a per-`KnowledgeBase` conventions layer, and whether Polish-locale pages
  can stay OKF-conformant, only sharpens after the taxonomy is fixed.
- **SPA surfaces.** Page browser, cross-link graph view, the **run report** (pages written with diffs,
  claims superseded with per-row removal, revert control), and the **conversational edit surface** T03
  made the only way a user changes a page. T03 replaced the approval queue with an after-the-fact report,
  so what Phase 12 owes is a diff view and a revert control rather than a queue — and whether revert is a
  control there or an "undo that" in chat is open. Shape still depends on the taxonomy decision.
- **Cost and latency.** One upload becoming 10–15 LLM-driven page writes instead of 7 agent
  calls changes the budget shape. `DeadlineProfile` and `CostTier` may need re-cutting; can't
  say how until the Ingest execution model is decided.
- **The guard layer.** The demo's reliability comes from a dozen mechanical guards around the
  model (a required write must *succeed*, Lint may only grow a page, index membership is code's,
  a dangling link is reported not removed). Which of those MindForge needs, and at what layer —
  domain invariant, DB constraint, service check — only sharpens once the Ingest execution model
  is decided. See T01's notes.
- **Caffeine's role** once the unit of caching is a wiki page rather than a query result.
- **Whether the CLI, Discord and Slack phases (15, 18–19) shift** once Query is the read path.

## Out of scope

- **The Verify Operation, Evidence Tree and drift schema** (demo ADRs 0018, 0019, 0023, 0026,
  0027). A learning platform has no authoritative corpus to check pages against — the raw
  uploads *are* the only evidence, and Ingest has already read them.
- **External editing**: xWiki as a wiki store, Obsidian write-back, git as a storage backend,
  concurrent-writer conflict handling (demo ADRs 0011–0013, 0017, 0021). The SPA is the only
  writer.
- **OKF import and bundle merge.** Export ships; ingesting someone else's bundle, conformance
  validation on the way in and overlap merging do not.
