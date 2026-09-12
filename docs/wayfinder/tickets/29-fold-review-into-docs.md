---
id: T29
title: Fold the review answers into the destination documents
type: task
status: closed
assignee: claude
blocked_by: [T14, T15, T16, T17, T18, T19, T20, T21, T22, T23, T24, T25, T26, T27, T28]
---

## Question

Every review ticket (T14–T28) is decided; write down their answers. As with T13, this ticket records decisions and does
not make them.

Deliverables:

- **`docs/project/implementation-plan.md`** — amend the affected tasks and checklists:
  - 3b (baseline columns, `LessonIdentity` grammar);
  - 5.2–5.6 (FK actions, ports, revert);
  - 6.3–6.9 (Extract, Resolve, writer checks, supersession, lifecycle);
  - 7.1, 8, 9.7 (retry, busy responses, progress), 9b, 10.4, 11.1–11.3 and 17.
- **ADRs.** Amend 0012 (revert of a revert), 0015 (lease claim, fencing, queue) and 0018 (card staleness key), where
  their answers change them. Add a new ADR only for a new load-bearing decision.
- **`docs/project/architecture.md`** — the ingest data flow, the guard table (supersession checks, heading preservation,
  target restriction, text normalisation) and the idempotency section.
- **`CONTEXT.md`** — any term the answers add or change.
- **`docs/wayfinder/okf-wiki-map.md`** — one line per closed review ticket under *Decisions so far*; remove the reopened
  note when this ticket closes.

## Answer

Done on 2026-09-12, under the user's standing instruction to proceed with recommended options. T14–T28 were closed first,
and every document below writes down their answers. As with T13, no decision was made here.

### What was written

| Deliverable | Change |
|---|---|
| `docs/project/implementation-plan.md` | **v3.1.** 3b: `Identifier`, `TextRules`, no `documentCount`, `DomainEvent` without `documentId()`, scoped `findById`, deferred `uploaded_by`. 4.4–4.6: Extract-sized chunks, `TokenEstimate`, dedup and lesson rule under the row lock. 5.1–5.7: `MarkdownStructure`, `IngestRun` lifecycle columns, FK rule, the ports of T25, renderer vocabulary, one-transaction revert. 6: contract, sequential chunked Extract with two caps, Resolve rules, draft validation, link-check targets, Supersede inputs and checks, queue / claim / fencing / sweep / permit pool / progress, tests. 7.1 health in SQL plus Java. **Phase 8 retired** into 6 and 9. 9.6/9.7/9.9: busy and lesson 409s, retry, Lint start, synchronous revert and removal, per-knowledge-base progress. 9b: timestamp, escaping, rule 3, filename. 10: `source_hash`, one budget, per-page lock, weakness, revival. 11: edit entry, interactions migration, no `listUnredacted`. 12, 13.3, 14, 17.2, 21.3 and the dependency graph adjusted |
| ADRs | Dated **Amendments** sections on 0011 (titles, identifier grammar), 0012 (revert provenance, no revert of a revert, edit entry), 0013 (chunked Extract, Resolve, Supersede), 0014 (retention, deferred FKs, query ports), 0015 (queue, fencing, sweep, progress port), 0016 (token estimate), 0017 (insertion exclusions, link grammar), 0018 (content-hash staleness, revival, study budget). No new ADR: every answer amends an existing decision |
| `docs/project/architecture.md` | **Rewritten** around the answers: domain text rules and query ports, ingest data flow with queue, claim and fenced commits, revert, the wiki model (titles, grammar, sections, links), FK rule, idempotency and reliability, retrieval ceiling, the guard table (14 rows), trust model (article restriction, HTML escaping, retention) |
| `docs/standards/architecture/hexagonal.md` | Tenancy extended to every tenant-scoped port with the sweep exception; query ports; the run lifecycle and fenced commit example; global permit pool; token estimate; domain events vs. `ProgressNotifier` |
| `docs/standards/backend/ai_agents.md` | `ClaimExtractor` signatures; deterministic steps have no `VERSION`; the enforced-in-code list re-cut (claims, edit items, draft rules, text rules, insertions, supersession proposals, one parser, two caps); lesson identity against `Identifier` with explicit ids and `newVersion` |
| `docs/standards/backend/models.md` | Deferred FKs inside one cascade; `Persistable` for assigned ids |
| `CONTEXT.md` | **Page** (titles), **Page Path** (grammar), **Section** (new), **Lesson** (explicit versions), **Ingest Run** (T28), **Revert**, **Flashcard** (staleness), **Weak Page**, **Conversation Edit** (new) |
| `docs/project/roadmap.md`, `docs/INDEX.md`, `CLAUDE.md` | Phase 8 retired and the review noted; future considerations for heartbeat lease, transliteration, background card generation and per-document erasure; glossary index; `kbId`-first rule extended and titles added to code ownership |
| `docs/wayfinder/okf-wiki-map.md` | One decision line per review ticket; the reopened note replaced; T12's counts corrected (also in T12) |

### Not done

No code was changed and nothing was committed. Phase 3b remains the restart point.
