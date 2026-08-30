---
id: T03
title: Whether wiki revisions need human approval
type: grilling
status: closed
assignee: claude
blocked_by: []
---

## Question

The demo gates every write: Ingest proposes, the human presses `y`, and on the local backend
you review `git diff` before committing. That gate exists because an LLM revising an existing
page can silently destroy correct knowledge.

MindForge's ingestion contract is the opposite: upload returns HTTP 202 immediately and a
background virtual thread does the work. There is no human at the keyboard.

Does a wiki revision land automatically, or does it wait for approval?

Consequences either way:

- **Automatic.** You need page history and rollback, because the only recovery from a bad
  revision is reverting it. That is a storage requirement (feeds T05) and probably a Flyway
  migration. It also means a user can lose knowledge to a bad model run without noticing.
- **Gated.** Ingestion stops being fire-and-forget. Something has to hold pending revisions,
  the SPA grows a review surface (fog: SPA surfaces), and the 202-and-forget contract in
  `architecture.md` changes shape.
- **Split.** New pages land automatically, revisions of existing pages are gated. Destructive
  edits are the risk; creation is not.

Related but distinct: the demo's **supersession** mechanism (ADR 0022) is the one Ingest write
that *is* gated even in an otherwise unguarded flow — marking a heading `[SUPERSEDED]` when a
newer source corrects an older claim. Decide whether MindForge adopts that, and whether it
follows the same rule.

Also decide: does the user ever edit a page by hand in the SPA? The map rules out *external*
editing, not in-app editing. If they can, "the LLM owns the wiki layer" from the LLM Wiki spec
stops being true and you need a merge story.

## Answer

**Revisions land automatically. There is no pending state anywhere in the system.** Recovery is a
per-run revert inside a window, and the only human-authored input to the wiki is a conversation turn
that Ingest treats as a source.

### The five decisions

**1. Automatic — not gated, not split.**

Gating breaks compounding, not just UX. Pending revisions from doc 2 mean doc 3 ingests against a
stale wiki, so you either block ingestion on human review — killing the 202 contract at
`architecture.md:80` — or fork the wiki into approved and proposed states and merge them. Both are
expensive; neither buys anything history does not.

Gating needs history anyway: a bad revision a tired human clicked through needs the same rollback.
Approval is therefore *additive* machinery on top of the recovery story, not an alternative to it.

And the demo's gate was compensating for a mechanism T02 already deleted. It gated because Ingest
rewrote prose in place with no undo — `git diff` *was* its revision table. We have real ones.

**Split** is rejected specifically: it pays the full cost of pending-state machinery and the
stale-wiki problem in order to protect the one case history already covers.

**2. Revert is per-run, restore-forward, and tip-only.**

- **Per-run.** The real failure is "that whole ingest was garbage" — wrong document, bad OCR, a bad
  model day — not "page 7 specifically got worse". T02's ingest-run entity already knows every page
  a run created or revised, so run-level revert is one query. Per-page revert falls out of the same
  mechanism if it is ever wanted; the UI for it is not built now.
- **Restore-forward, not rewind.** Revert writes a *new* revision carrying the old body and deletes
  pages the run created. Revision rows are never deleted. This keeps `revision` monotonic, which
  T02 hands T08 as a cache key — a rewind would make that key lie.
- **Tip-only.** Revert is offered for a page only while no later run has touched it; if every page
  the run wrote is stale, the run is not revertible at all. Undo has a window, and the window closes
  at the next ingest touching the same page. Past it the recovery path is re-uploading the source,
  not un-ringing the bell. You notice a bad run from its report, not three uploads later.

The rejected branch is a full history graph with merge — the point where this stops being a learning
platform and becomes a VCS.

**3. Supersession follows the same rule, with its own line in the run report.**

No gate, because T02 made supersession a row insert that cannot touch prose by construction, and run
revert deletes the row.

But it is the only write whose damage is **silent**. A butchered page revision is visible the moment
you read the page. A false supersession renders a marker on correct knowledge *and* — per T02's feed
to T08 — makes the flashcard cutter skip that claim. The user loses study material and never sees a
gap where it was.

So the run report has two sections: **pages written** (created or revised, each diffable against the
prior revision) and **claims superseded** (each naming the superseding page, each individually
removable). Per-item granularity earns its keep exactly here and nowhere else, because it is a row
delete rather than a prose edit.

*How* Ingest arrives at a supersession — a typed output mid-run, or a later pass — stays T04's.

**4. No hand-editing of page bodies. The LLM is the sole author of prose.**

"The LLM owns the wiki layer" therefore holds literally, not merely in spirit. There is no merge
story, no marking of human-authored spans, and no three-way merge the next time Ingest revises a
page a human has touched. This is the most expensive optional feature on the map and it is declined.

**5. All user edits go through conversation with an agent — and that is an Ingest run sourced from
the conversation turn, not a fourth Operation.**

The turn is recorded as a `Document` with a conversational `UploadSource`, and Ingest runs against
it. Everything above then applies unchanged and for free:

- it gets a run record, so it gets the run report, so it gets revert on the same tip-only window;
- `PageSource` joins the page to that turn, so provenance stays honest — *"why does this page say
  that?"* answers *"because you told me on 2026-08-30"* through exactly the same join that answers
  *"because doc 7 said so"*;
- it lands automatically like every other run: the conversation *is* the report surface, and
  "undo that" is the next message rather than a queue;
- deletion goes through the same channel, so there is no separate delete button. Deletion is a
  membership act, not authorship — T01 already put membership on code's side, and a user-commanded
  delete is code's. Its one side effect, inbound links from other pages, is already handled: T02
  reports dangling links rather than removing them, and T10 is where they surface. No new machinery.

**Cost accepted:** Ingest's input stops being homogeneous. A document is a *source*; a correction is
an *instruction*. This is a constraint on T04, recorded there.

**Rejected:** a fourth **Revise** Operation. It buys prompt clarity and costs a duplicate of the
run/report/revert machinery plus a re-opening of the settled Ingest / Query / Lint set.

**Revert stays code, not conversation.** It restores prose the LLM previously wrote, so no
human-authored prose enters a body and decision 4 is intact. Whether the user triggers it from a
control on the run report or by saying "undo that" in chat is a Phase 12 surface question, left to
the SPA fog.

### Feeds

- **T04** — Ingest must accept a conversation turn as a source, not only a parsed document. Whether
  that is free depends on the execution model: an agent loop takes it as a different opening
  message; a rigid typed pipeline takes it as a second entry shape.
- **T05** — `PageRevision` retention must support tip-only run revert: every page a run touched needs
  its pre-run revision retrievable until a later run touches that page. "Keep N" is viable; "keep
  current only" is not.
- **T07** — there is no pending or approval state to make idempotent. A re-run after failure is a new
  run, and revert is the compensating action rather than a rollback of a partial write.
- **T08** — the superseded-claim skip is what makes a false supersession silent; the run report's
  per-row removal is the correction path.
- **T10** — a user-deleted page's inbound links enter the dangling report unchanged.
- **T12** — no approval or pending tables enter the schema. `PageRevision` and the run record are the
  only history-bearing tables this ticket adds.
