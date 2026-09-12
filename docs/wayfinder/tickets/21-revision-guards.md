---
id: T21
title: What code guards on a page rewrite, and what counts as a change
type: review
status: closed
assignee: claude
blocked_by: [T26]
---

## Question

Raised by the 2026-09-12 spec review. **Gap** — the design guards Lint's writes tightly and ingest rewrites not at all.

**1. The writer rereads corrected claims as fact.** Supersession never touches prose (ADR 0010). PageWriter receives
"task, existing body, linkable index" (`docs/project/implementation-plan.md:648`) with nothing marked. Query and study
mark or strip superseded sections (`:893`, `:933`); Write does not. The next rewrite can repeat the corrected claim
under a heading no supersession covers.

**2. A rewrite can drop knowledge, and nothing checks.** A single-shot rewrite of a page other documents built can drop
their content or rename headings. Renamed headings silently break supersessions and flashcard `section_anchor`s
(`docs/wayfinder/tickets/08-study-artifacts-from-wiki.md:135`). The only check is a diff in a run report, and its
revert window closes at the next ingest touching the page. T10 retired the reference implementation's shrink guard
because Lint cannot write prose — but Ingest can.

**3. Unchanged drafts still add a revision** (`docs/wayfinder/tickets/05-wiki-storage.md:112`). That:
- closes earlier runs' revert windows;
- inflates the "revised" count;
- bumps `revision`, which triggers flashcard regeneration.

**4. Link-only revisions also trigger regeneration.** Card staleness is `page.revision > card.generated_at_revision`
(`tickets/08-…:111`). A full Lint adding links to 300 pages means 300 LARGE regenerations of cards whose text did not
change.

Decide:

- **The writer's input.** *Recommended:* superseded sections rendered as notes, as in Query, with the instruction that
  their claims are wrong and must not be restated.
- **Heading preservation.** *Recommended:* code checks that every top-level heading of the existing body appears in the
  draft; otherwise the write fails and is recorded in `failures`. Decide whether superseded headings may be dropped,
  and if so, whether the commit deletes the now-dangling supersession row.
- **Size signal.** A guard, or just a size delta in the run report? *Recommended:* the report only.
- **Unchanged drafts.** *Recommended:* if title, description and body are all unchanged, write no revision and no
  `page_sources` row (T05 says every source row has a matching revision).
- **Card staleness key.** *Recommended:* a hash of title plus `stripLinks(body)` on the card, replacing
  `generated_at_revision`; `revision` stays the revert key. This amends ADR 0018 (see T27).

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. The writer-input rendering is refined
to avoid a copying hazard.

**In short:** the writer is told which sections are superseded; a document ingest may not drop or rename a Concept's
sections; a draft identical to the page writes nothing; and cards go stale on a change of content, not of revision.

### Decisions

**1. The writer's input marks superseded sections beside the body, not inside it.** `PageWriter` receives:

- the task;
- the existing body, verbatim;
- a list of `SupersededSection(heading, anchor, supersedingPath, supersedingTitle)` for that body;
- the linkable index.

The prompt says the claims in those sections are corrected by the named pages and must not be restated as current.

*This refines the recommendation.* Inline notes, as Query renders them, would invite the model to copy
`> Superseded by …` into prose, and the note would then render twice. A separate list gives it nothing to copy.

What a writer does with a superseded claim is prose, which code cannot check. The structure around it is checked below.

**2. Sections survive a document ingest.** The check applies when an `INGEST` run revises an existing `Concept` and the
run's document is not `CONVERSATION`. Every level-1 anchor (T26) of the existing body must appear among the draft's
level-1 anchors, superseded sections included. Otherwise the page task fails with
`{"step":"write","path":…,"reason":"dropped sections: …"}`.

- **Heading text may change** as long as its anchor does not (case, diacritics, punctuation).
- **New sections may be added.**
- **Superseded headings may not be dropped either.** A superseded section stays, its note keeps rendering, and an ingest
  never deletes a supersession row. That keeps revert exact. Deleting the row at commit would leave a reverted page with
  its wrong claim back and unmarked — the same unrecoverable-row problem T16 ruled out.

The check does not apply to:

- **Conversation edits.** The user asked for the change ("remove the section about X"). Any supersession or card anchor
  left pointing at a removed section dangles harmlessly: the health view lists it, and it heals if a revert brings the
  section back.
- **Source Summaries.** They carry no supersession or card anchors, so a new lesson version may restructure its digest.

**3. Size is a signal, not a guard.** The run report shows each written page's body length before and after. A shrink
guard would reject legitimate condensing, and heading preservation already catches the damaging case, lost sections.

**4. An unchanged draft writes nothing.** A draft is unchanged when its title, description and normalised body (T19) equal
the live page. Then:

- no revision, no `page_sources` row and no link re-derivation are written;
- earlier runs keep their revert windows;
- counts stay honest;
- cards stay fresh.

The **zero-writes rule** is restated: a run fails when **no page task succeeded**. A task succeeds when:

- its draft passed validation, changed or not;
- its delete was applied; or
- its retitle was applied.

A run whose successful drafts were all unchanged is `COMPLETED` with no revisions. It skips Supersede and is absent from
`log.md` (T11's zero-change rule).

**5. Card staleness key: content, not revision.** This amends ADR 0018 and is coordinated with T27.

- `flashcards.generated_at_revision` becomes `source_hash CHAR(16)` = `sha256(title + "\n" + stripLinks(body))[:16]`,
  taken from the page when the card was generated or reused.
- A page's cards are stale when the page's current hash differs.

Consequences:

- A Lint that adds links to 300 pages regenerates nothing.
- A supersession changes neither body nor hash; its cards are hidden by the join (T08).
- `revision` stays the revert and tip key and is no longer used by study.

### Feeds

- **T27** — the regeneration budget and revival read `source_hash`.
- **T29**:
  - 6.5 — writer input and checks;
  - 6.4 and 6.8 — the zero-writes rule;
  - 9.7 — size delta in the run report;
  - 10.1, 10.2 and 10.4 — `source_hash`;
  - ADR 0018 — amended;
  - guard table — "sections survive an ingest".
