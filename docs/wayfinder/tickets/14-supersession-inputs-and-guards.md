---
id: T14
title: What the supersession step reads and what code verifies about its output
type: review
status: closed
assignee: claude
blocked_by: [T26]
---

## Question

Raised by the 2026-09-12 spec review. **Blocker** — the step cannot be built as written.

**1. Its input no longer exists.** T04 decision 4 feeds the detector "the claim set plus the sections retrieval
flagged as *related but not revised*" (`docs/wayfinder/tickets/04-ingest-execution-model.md:156`,
`docs/project/implementation-plan.md:658`). T09 then removed the retrieval that would flag them: Extract proposes one
target path per claim and Resolve only verifies paths (`docs/wayfinder/tickets/09-query-retrieval-neo4j.md:102`).
Nothing produces candidate sections, so the step has no defined input, no candidate bound and no token budget.

**2. Its output is never verified.** The guard table (`docs/project/architecture.md:211`) and the rule "every rule a
prompt teaches is enforced in code" (`docs/standards/backend/ai_agents.md:76`) cover paths, types and link insertions,
but not supersession rows. As written, a run can commit a supersession that is wrong on day one:

- its `section_anchor` matches no heading;
- the superseded page is the superseding page;
- it targets a Source Summary.

Decide:

- **The candidate set.**
  - *Recommended:* live Concept pages within one `page_links` hop of the pages this run wrote (either direction),
    plus the existing pages Extract targeted, trimmed to a `TokenBudget`. Bodies are read as committed by commit 1.
  - *Alternative:* a SMALL selection call over the index. This adds an LLM call T04 set out to avoid.
- **Candidates over budget.** Fail loudly (the claim-cap rule), or set `supersession_skipped` with a reason.
- **Code checks on every proposed row before commit 2.** *Recommended:*
  - both pages are live, and they are not the same page;
  - the superseded page is a `Concept`;
  - `section_anchor` equals the anchor of an existing heading in the superseded body, as T26 defines "heading";
  - the superseding page was written by this run.

  A row that fails is dropped and counted in `ingest_runs.failures`.
- Add the chosen checks to the guard table.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. The over-budget choice, which the
ticket left open, is argued below.

**Supersede reads the claims that became pages and the sections of Concept pages one link away from them. Code checks
every proposed row against exactly the candidates the model was shown.**

### Decisions

**1. The input.**

- **Superseding side:** this run's claims, each with the path Resolve gave it (T22). Only claims whose page task
  produced a revision count.
- **Superseded side:** the level-1 sections (T26) of the **candidate pages**, each shown with its path, anchor and
  heading.
- **Candidate pages:** live `Concept` pages that this run wrote, or that sit within one `page_links` hop of a page it
  wrote, in either direction.
- **When read:** bodies and links are read after commit 1, under the lease, so they are exactly what committed.
- **Excluded:** sections that already have a live supersession. A section is superseded once.

There is no selection call. The retrieval is the link graph the run just wrote and checked (T10 link check). That is the
relatedness signal T04's "sections retrieval flagged" was standing in for.

**2. Budget.** Candidate sections are capped at `ProcessingSettings.supersessionContextTokens` (default 30 000),
estimated with T23's method. Candidate pages are ranked by the number of links joining them to this run's written pages,
then by path. Whole pages are added in that order until the next one would not fit.

**Over budget, decided here: trim in rank order and record what was left out.** The omission is written as
`{"step":"supersede","omittedPages":N}` in `ingest_runs.failures` and shown in the run report. Neither option in the
question holds up:

- **Failing loudly is not possible after commit 1.** T17 would turn it into `COMPLETED` + `supersession_skipped`
  anyway.
- **Skipping the whole step** would mean a run that touches any hub page never supersedes anything. The compounding
  story would fail for exactly the best-connected wikis.

This is not the silent truncation the claim-cap rule forbids. The omission is counted and shown, the model is never asked
to judge completeness, and the full Lint reports unmarked supersessions (T10 decision 4).

**3. Output and checks.** `SupersessionDetector` returns
`List<SupersessionProposal(supersededPath, sectionAnchor, supersedingPath)>`. Before commit 2, code keeps a proposal
only if all of these hold:

- `(supersededPath, sectionAnchor)` is one of the candidate sections shown. That already implies the page is live, is a
  `Concept`, and has that level-1 anchor.
- `supersedingPath` is a `Concept` for which this run wrote a revision.
- The two paths differ.
- No live supersession already covers that section, and no earlier proposal in the same output does.

Anything else is dropped and counted as `{"step":"supersede","dropped":N}` in `failures`. Commit 2 inserts the
survivors and stores their number on the run (T16).

Supersede does not run for a run with no revisions (T21) or for an `ARTICLE` document (T28).

**4. Guard table.** New row: *a supersession names a section the detector was shown and a Concept this run wrote*. It is
enforced by the proposal check before commit 2.

### Feeds

- **T16** — commit 2 sets `ingest_runs.supersession_count` from the rows it inserted.
- **T17** — a Supersede exception or a failed commit 2 ends the run as `COMPLETED` + `supersession_skipped`.
- **T23** — supplies the token estimate.
- **T29** — 6.7 re-cut; the architecture guard table; `ProcessingSettings.supersessionContextTokens`.
