---
id: T10
title: When and how Lint runs
type: grilling
status: open
assignee:
blocked_by: [T06]
---

## Question

Lint is a chosen feature with no current MindForge equivalent — it is what stops a compounding
wiki from rotting. The demo's ADR 0007 draws a sharp line: Lint *writes fixes* for structural
issues (orphan pages, missing cross-references, concepts mentioned but undocumented) and only
*reports* content judgments (contradictions, stale claims). Never auto-resolves the latter.

Decide:

- **Does MindForge keep that line?** It is the reason Lint is safe to run unattended.
- **When does it run?** After every ingest, on a schedule, on user request, or when a bundle
  crosses a size threshold. Post-ingest is cheapest to reason about; scheduled means a job
  runner MindForge does not have.
- **Where do reported-not-fixed findings go?** A page, a notification, a badge in the SPA, or
  `log.md`. This is the one place a user is told their knowledge base has a problem.
- **Does Lint suggest new sources or questions?** The spec argues this is where a wiki turns
  from passive into a study partner. For a *learning* platform that is unusually on-theme —
  "you have a page on X mentioning Y but nothing on Y" is a study prompt, not just a lint.
  This may be the strongest product argument in the whole map; decide whether to keep it here
  or spin it out.
- **Cost.** Lint reads the whole bundle. On demand is affordable; after every ingest may not be.
- **Does a Lint run get a run record?** T03 killed the approval gate, so the live question is not
  gating but bookkeeping: a self-healing Lint write is a revision like any other, so it needs a run
  record to be revertible on the same tip-only window. Decide whether a Lint pass is its own run or
  rides the ingest run that triggered it.

**Inherited from T04.** Lint gained real load. T04 chose single-shot page writes, which cannot
self-correct, and bought that back explicitly by making **Lint the second pass**. That is now the argument
for running Lint after ingest rather than purely on demand — and it collides directly with this ticket's
cost concern, since Lint reads the whole bundle. Reconciling the two is the live question: a cheaper
ingest-scoped Lint over only the pages a run touched, versus a full-bundle pass on demand, versus both.

Supersession is **not** Lint's — T04 made it its own ingest step, scoped by retrieval.

## Answer
