---
id: T22
title: The rules Resolve applies, and who owns a page's title
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Gap** — implementers would each fill these in differently.

**1. Two tasks can hit one page.** T09 collapses only claims proposing the *same new path*
(`docs/wayfinder/tickets/09-query-retrieval-neo4j.md:105`). Take a `new: Mitoza` claim (it slugifies to the existing
`concepts/mitoza`, so it becomes a revision) and a claim that targets `concepts/mitoza` directly. They yield two tasks
and two parallel writes to one page. The result is either two revisions in one run — breaking "pre-run state is
`r − 1`" (`docs/wayfinder/tickets/05-wiki-storage.md:116`) — or last write wins and a claim is lost.

**2. Claims can target another lesson's Source Summary.** Nothing restricts a claim's target to `concepts/`
(`docs/project/implementation-plan.md:642`), so one upload can rewrite another lesson's Source Summary.

**3. A hallucinated path has no title.** It "becomes a create" (`tickets/09-…:104`), but a claim carries a path, not a
title to derive the new page's path and title from.

**4. The title rules contradict each other.**
- A Source Summary's title is the code-assigned `lessonTitle` (`docs/wayfinder/tickets/06-page-taxonomy.md:146`).
- "Two live pages must not share a title" (`tickets/06-…:147`).

A lesson called "Mitoza" next to the Concept page `Mitoza` breaks the second rule in ordinary use. Nothing enforces it
except a Lint finding.

**5. Who owns the title is unspecified.** PageWriter returns a title (`implementation-plan.md:649`). It is not said
whether that title is ignored for Source Summaries, or whether the writer may retitle an existing Concept (drifting from
its path, or colliding with another title). A new page's path also comes from Extract's `new: <title>`, while its stored
title comes from the writer — two titles for one creation.

Decide:

- **One task per path.** *Recommended:* after verifying and deriving paths, Resolve groups claims by final path into
  exactly one `PageWriteTask` each.
- **Target restriction.** *Recommended:* claims may target only `concepts/`; any other target is treated as
  hallucinated.
- **A title for creates.** *Recommended:* every claim carries a title candidate beside its proposed path, used whenever
  the path is not live.
- **Duplicate titles.** Drop the "must not share a title" rule (keep the duplicate-title finding for Concepts only), or
  enforce it in code.
- **Writer titles.** Ignored for Source Summaries. For existing Concepts: may change, or fixed after creation?

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. The two points left open — duplicate
titles and writer titles — are argued below.

**In short:**

- Resolve turns claims into exactly one task per final path.
- Claims target Concepts only.
- Every claim carries a title.
- A page's title is set once, at creation. It changes only through an explicit conversation retitle.

```java
record Claim(String text, String title, String targetPath /* nullable */, int firstBlock, int lastBlock) {}
record PageWriteTask(String path, PageType type, String title, boolean create,
                     List<Claim> claims) {}
```

### Decisions

**1. Resolve, in order.** Code only, no model.

1. **Target.** `targetPath` counts only if it is a live `Concept` path, or a path this run already planned from an
   earlier chunk (T23). Anything else is ignored: missing, hallucinated, a `sources/` path, a malformed string.
2. **Path.** With no valid target, the path is `concepts/` + `Identifier.slugify(title)` (T18). If that path is a live
   page, the task is a revision (T06's exact-path rule). Otherwise it is a create.
3. **Group.** Claims are grouped by final path, giving **exactly one `PageWriteTask` per path**. A create takes its title
   from the group's first claim in document order, normalised by `TextRules`.
4. **Caps and edits.** Apply, in order:
   - the page-task cap (T23), counting Concept tasks;
   - `Delete` and `Retitle` from an edit (T15);
   - the Source Summary task, for a document that is not a conversation.

One task per path means one draft and one revision per page per run, so T05's "pre-run state is `r − 1`" holds. The case
in the question — `new: Mitoza` plus a claim targeting `concepts/mitoza` — lands in one task.

**2. Claims target `concepts/` only.** A `sources/` target is ignored at step 1, and the claim lands on a Concept derived
from its title. So a claim cannot reach another lesson's Source Summary. Only the pipeline writes a Source Summary, and
only for its own document (T06).

**3. A title on every claim.** `new: <title>` stops being a separate shape. Every claim carries the title of the thing it
is about, plus an optional `targetPath`. A creation therefore has one title, and it is the title the path was derived
from, so path and title agree on the day the page is born.

**4. No title uniqueness invariant.** The rule "two live pages must not share a title" is dropped. The **duplicate-title**
health finding stays, for live Concepts only.

- A Source Summary legitimately shares a name with a Concept: a lesson called "Mitoza" next to the page *Mitoza*.
- Two Concepts with one title are a duplicate page, which is exactly what the health view should show the learner.
- Enforcing uniqueness at write time would fail a page task and lose its claims, to prevent a problem that only a later
  merge by conversation edit can fix.

**5. Code owns titles.**

- **Source Summary:** the `lessonTitle` of the run's document, assigned by code on every write.
- **Concept:** fixed at creation. `PageWriter` returns `PageDraft(description, body)` with no title field, so the writer
  cannot retitle a page — the type rules it out. The only change is an explicit `Retitle` in a conversation edit (T15),
  and the path never moves.

Why fixed: a writer allowed to retitle on every ingest would:

- churn the index that Extract matches against;
- drift titles away from paths;
- collide with other pages' titles;
- regenerate cards for cosmetic renames, since a card's hash covers the title (T21).

Why not frozen outright: a page born with a bad title could otherwise be fixed only by deleting and re-creating it, which
loses its prose.

### Feeds

- **T15** — `Retitle`.
- **T19** — title normalisation and caps.
- **T23** — planned paths across chunks.
- **T29**:
  - re-cut 6.3–6.5;
  - 7.1: duplicate titles for Concepts only;
  - amend ADR 0011: titles fixed except by `Retitle`;
  - `CONTEXT.md`: **Page**;
  - guard table rows "one task per path" and "claims target Concepts only".
