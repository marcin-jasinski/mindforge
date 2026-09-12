---
id: T10
title: When and how Lint runs
type: grilling
status: closed
assignee: claude
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

**Inherited from T05.** The dangling-link report is one query: `page_links LEFT JOIN wiki_pages` on
`(knowledge_base_id, target_slug)` where no page matches. A supersession whose superseding page is gone, or
whose `section_anchor` no longer matches a heading, is the same shape of finding.

**Inherited from T06.** Paths never change and nothing can rename, so Lint cannot fix a link by moving a page. A
link to the wrong directory (`/sources/mitoza.md` when the page is `concepts/mitoza`) is dangling and must not
be silently "corrected" by guessing (demo ADR 0021). Titles duplicated across live pages are a new structural
finding. The linkable index Lint would hand a model is the projected root index.

**Inherited from T07.** The run-record question is half-answered: any Lint that writes pages is an `ingest_runs`
row (add `LINT` to `kind`), takes the `knowledge_bases.active_run_id` lease, and so queues behind a running
ingest. Revert then works on Lint writes unchanged. Whether Lint writes at all is still this ticket's call. A
report-only Lint writes no pages and needs no lease.

**Inherited from T09.** No Neo4j and no embeddings. Orphans (no inbound `page_links`), dangling links and duplicate
titles are SQL. Any Lint step that needs judgment gets the same rendered index Extract and Query get, under the same
20K-token ceiling and lexical prefilter. Query never writes, so every page-writing path is Ingest, a conversation
edit, a revert, or Lint.

## Answer

All decisions taken on the recommended option under the user's standing instruction to proceed with
recommendations.

**Lint is three things at three costs.**

- **Structural checks are SQL**, computed live whenever they are looked at: no LLM, no run.
- **A link check runs inside every ingest**, after the writes and before the commit.
- **A full Lint runs only when the user asks.** It is a run of its own and produces a report.

Lint never writes prose. The model proposes link insertions and code applies them, so "additive only" is a type, not a
length check. Content judgments are reported and never fixed, which keeps the demo's line.

```
Ingest:  … Write (LLM, ×N) ─▶ Link check (SMALL, chunks) ─▶ commit ─▶ Supersede
                              in-memory bodies of this run's pages only; failure skips it

record LinkInsertion(String pagePath, String phrase, String targetPath, String fragment) {}
```

### The seven decisions

**1. Keep the demo's line, and draw it tighter: Lint may only add links.**

Of the demo's three structural fixes, MindForge has one left:

- **Missing `type:`** is unrepresentable — type is a typed field assigned by code (T02, T06).
- **Missing index entries** are unrepresentable — the index is a projection (T02).
- **Missing cross-links** — mentions that should link, and orphan pages — are what remains.

So Lint's only write is **wrapping an existing phrase in a link**. The model returns `List<LinkInsertion>`, never a
body. Code accepts an insertion only if all of these hold:

- the phrase occurs in the page body, outside existing links, code spans and headings;
- the target path is live;
- the target is not the page itself.

Code then wraps the first such occurrence and asserts `stripLinks(after) == stripLinks(before)`. A proposal that
fails verification is dropped and counted.

The demo's "reject a write >10% shorter" guard was a heuristic stand-in for this, needed because its Lint rewrote whole
files. Here the failure it guarded against — "stripped relevant prose while fixing structure" — cannot be expressed in
Lint's output type. T01 §7's rule, "the LLM proposes content; membership, ordering and deletion are code's", holds
literally, and T03 decision 4 (the LLM is the sole author of prose) is untouched: no word of prose changes.

- **Headings are never linked.** A supersession anchors to `slugify(heading text)` (T06), and wrapping heading text in
  a link would silently move the anchor.
- **Contradictions and stale claims are reported, never fixed.** Lint does not insert supersessions — supersession is
  Ingest's step (T04), and a Lint that could mark knowledge superseded unsupervised is exactly the unreviewed content
  judgment ADR 0007 refused.

**2. Structural checks are SQL, always on, and never a run.**

| Finding | Query |
|---|---|
| Dangling link | `page_links` with no live page at `(knowledge_base_id, target_path)` (T05) |
| Wrong-directory link | a dangling link whose final segment matches a live page in the other directory (T06); reported, never auto-corrected |
| Orphan page | a live `Concept` with no inbound `page_links` |
| Duplicate title | two live pages with the same title (T06) |
| Dangling supersession | `section_anchor` matches no heading of the superseded page, or the superseding page is gone (T05) |

These are computed when the SPA shows a knowledge base's health — a few indexed queries, always current. Nothing is
stored, so nothing is stale.

**3. The link check runs inside every ingest run, on in-memory bodies, before the commit.**

T04 made Lint the second pass that buys back single-shot writes' inability to self-correct. The cheapest correct place
for that pass is **between Write and commit**: SMALL-tier calls over this run's generated bodies (chunked, about ten
bodies per call) plus the index, including the paths the run is about to create (Resolve knows them before fan-out,
T09). Insertions apply in memory, and the commit lands one revision per page exactly as T04 and T05 designed.

Why here and not as a follow-up run: a second write after the commit would put a newer run on the tip of every page it
touched. Each ingest would then **lose its own revertibility to its own Lint** within seconds (T03's tip-only rule). In
memory, before the commit, the checked links are simply part of the run's writes — reverted with it, reported with it.

- Scope is **outbound links from this run's pages only**. Links *into* this run's new pages from untouched older pages
  are the full Lint's job. (A forward link an older page already had goes live on its own the moment the target lands,
  T02.)
- **Failure degrades, it does not fail:** a link-check error commits the bodies without extra links and records
  `{"step":"linkCheck", …}` in `ingest_runs.failures`, the same shape as T07's `supersession_skipped`.
- Cost is bounded by T04's claim cap, since N written pages bounds the chunks.

**4. The full Lint is on demand only, one at a time, and a run of its own.**

The user asks ("check my knowledge base"); the SPA shows the page count first. It is an `ingest_runs` row with
`kind = LINT`, and takes the lease (T07), so it queues behind a running ingest and serializes with everything else
that writes.

It reads every live `Concept` body in chunks, with the index (prefiltered past T09's 20K-token ceiling), and produces:

- **link insertions**, applied as in decision 1 — including links into pages nothing links to yet, which is what
  repairs orphans;
- **content findings** (LARGE tier): contradictions between pages and claims that look superseded but have no
  supersession, each naming the pages involved;
- **suggestions** (decision 6).

The run report shows the insertions, revertible under the usual tip-only rule. Findings and suggestions are stored on
the run as `ingest_runs.findings JSONB`.

**Not scheduled.** Spring's `@Scheduled` exists — Phase 10.2 already uses it — so this is not about missing a job runner.
A cron job spending LLM calls on knowledge bases nobody opened is spend without a reader. **Not after every ingest**
either: whole-bundle cost on every upload is what decision 3 makes unnecessary.

Cost accepted: a full Lint that inserts a link into a page closes the revert window for the ingest run that last wrote
that page. It is user-triggered and says so in its report, so the trade is visible when it is made.

**5. Findings go to a knowledge-base health view, not to `log.md` and not to a notification.** It combines the live
SQL findings (decision 2) with the latest full Lint's stored findings and suggestions. `log.md` records changes (T06),
and a finding is not a change. MindForge has no notification system, and inventing one for this is not justified. The
health view's shape belongs to the SPA fog.

**6. Suggestions stay in Lint — as study prompts, never as generated pages.** The full Lint's suggestions are:

- "*Cell Division* mentions *mejoza*, but there is no page on it";
- "these three pages disagree about X";
- "questions worth investigating".

For a learning platform these are the most on-theme output Lint has, and they cost nothing extra: the same pass already
read the bundle. They are **not** spun out, and they are not turned into pages automatically. A page written from the
model's own knowledge, with no uploaded source behind it, is ungrounded content in a wiki whose whole claim is being
grounded in what the learner uploaded. The action a suggestion points to is *find a source* or *ask Query*. If the user
answers one in chat, that is T03's conversation edit, sourced from the user.

**7. Run record: the link check rides the ingest run; the full Lint is its own `LINT` run.** That answers the ticket's
last question in both directions, for the reason in decision 3 — tip-only revert makes "a separate run right after" the
wrong shape for anything automatic.

### Feeds

- **T11** — `log.md` gains `* **Lint**: N links added.` for `COMPLETED` `LINT` runs with at least one insertion. A Lint that
  added nothing changed nothing and is not logged. Findings never reach export.
- **T12** — `ingest_runs` gains `findings JSONB NOT NULL DEFAULT '[]'` and `kind` includes `LINT`; there is no Lint code
  today.
- **T13** — a Lint phase is new: the link-check step inside ingest (a `LinkChecker` service, SMALL, and the fifth
  concrete ingest service after T04's four), the on-demand full Lint service, the SQL health queries, and a health view
  in the SPA. `architecture.md`'s guard table gains "Lint writes are link insertions only — enforced by output type and
  the strip-links equality check".
