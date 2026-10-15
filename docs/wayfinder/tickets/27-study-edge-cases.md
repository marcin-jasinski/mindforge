---
id: T27
title: Flashcard generation under concurrency, regeneration cost, and cold-start targeting
type: review
status: closed
assignee: claude
blocked_by: [T21]
---

## Question

Raised by the 2026-09-12 spec review. **Gap.**

**1. Concurrent generation.** Card generation takes no lease (`docs/wayfinder/tickets/08-study-artifacts-from-wiki.md:113`).
Opening the deck twice at once (two tabs, or web and Discord) generates cards for the same pages twice. That doubles the
LLM spend and hits the `(knowledge_base_id, card_id)` primary key on identical content.

**2. Regeneration has no bound.** `newPagesPerSession` limits only pages with no cards yet (`tickets/08-…:107`).
Staleness is checked "when a page's cards come due" (`:111`), so after a large ingest a single session can trigger dozens
of LARGE regenerations inline, while the learner waits.

**3. Moved sections.** Card identity excludes `section_anchor`. A still-correct card whose section moved keeps its id,
but nothing says its row's anchor is updated — and the supersession skip joins on that anchor.

**4. Cold start.** A page's weakness is "the mean of its last few scores" (`tickets/08-…:147`). "Few" is undefined, and a
knowledge base with no `study_events` has no weak pages, so the default whole-knowledge-base session has no ordering.

**5. Revived cards.** A card un-retired by a revert returns with its old `due_at`, which may be months past, flooding the
due queue.

**6. Scales.** Whether SM-2 ratings and quiz scores, both 0–5, are comparable enough to average into one weakness score.

Decide:

- **Concurrency.** *Recommended:* `ON CONFLICT DO NOTHING` on card inserts, plus an in-process per-page generation guard.
- **Regeneration budget.** *Recommended:* regenerations count against the same per-session page budget. Decide the
  deadline profile for generation.
- **Anchor updates.** *Recommended:* update `section_anchor` whenever a card id is reused.
- **Weakness.** Define N for "last few" and the ordering when there is no data (e.g. unstudied pages by creation order).
- **Revived cards.** *Recommended:* `due_at = now`.
- **Scale normalisation,** if the two scales should not be averaged as-is.

Coordinate with T21, which may replace `generated_at_revision` with a content hash.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. The staleness key is T21's content hash.

### Decisions

**1. Concurrency: an in-process per-page lock, plus inserts that tolerate a race.**

- **Per-page lock.** `FlashcardService` generates cards for a page only while holding a `ReentrantLock` taken from a
  `ConcurrentHashMap<UUID pageId, ReentrantLock>`. The map entry is removed on release.
- **Re-check after locking.** Once it holds the lock, it re-reads the page's cards and their staleness. A second tab
  therefore waits, finds fresh cards, and makes no call.
- **Why `ReentrantLock`.** Not `synchronized`: in Java 21, a virtual thread that blocks on I/O inside `synchronized` pins
  its carrier thread.
- **Race-tolerant inserts.** Card inserts are `INSERT … ON CONFLICT (knowledge_base_id, card_id) DO NOTHING`. A lost race
  on identical content never fails — including a second path in, such as a bot alongside the web app.
- **Ceiling:** single instance, as everywhere.

**2. One generation budget per session.** `ProcessingSettings.newPagesPerSession` is renamed `cardPagesPerSession`
(default 10). It caps model calls for new and stale pages together.

When a deck opens for a scope, candidates are taken in this order until the budget is spent:

1. stale pages that have due cards, oldest `due_at` first (stale means the current hash differs from the cards'
   `source_hash`, T21);
2. Concept pages with no cards, by `created_at`.

Stale pages past the budget keep serving their current cards and regenerate in a later session. That is acceptable:
the changes that make a card wrong are the ones supersession marks, and those cards are already hidden by the join (T08).
The remaining changes are mostly additions.

**Deadline and latency.**

- Generation calls run in parallel, one virtual thread per page, up to the budget.
- Each uses `DeadlineProfile.BATCH` (60 s). `INTERACTIVE`'s 10 s is too short for a LARGE call writing a page's cards.
- The deck request waits for those calls — at most `cardPagesPerSession` of them, running concurrently.
- **Ceiling:** move generation to the background, with a "preparing cards" state, if first-open latency becomes a
  complaint.

**3. A reused card's anchor follows its section.** When regeneration returns a card whose id already exists, that row's
`section_anchor` and `source_hash` take the new generation's values, and `retired_at` is cleared.

**4. Weakness.**

- A page's score is the **mean of its last 5 `study_events`**, card reviews and quiz answers together.
- A page is **weak** when that mean is below 3.0.

For the whole knowledge base — used for quiz targeting, and for choosing which pages to generate once due work is covered
— pages are ordered:

1. weak pages, by ascending mean;
2. unstudied Concept pages, by `created_at`;
3. the remaining studied pages, by ascending mean.

A cold knowledge base is therefore studied in the order it was learned.

**5. A revived card is due now.** Un-retiring a card keeps its SM-2 state — ease, interval and repetitions — and sets
`due_at = now`. A card months overdue is shown once, and its schedule then resumes from its history.

**6. The two scales are one scale, by definition.** `QuizEvaluator`'s prompt grades on SM-2's 0–5 recall-quality rubric:

- 5 — perfect;
- 3 — correct, with serious difficulty;
- below 3 — incorrect.

Code clamps the score to 0–5, so card ratings and quiz scores average without normalisation. If a real knowledge base
shows quiz scores systematically offset, weight the two kinds then.

**Revival input (with T21).** On regeneration, the generator receives:

- the page's current cards;
- the retired cards whose `source_hash` equals the page's current hash — the cards that were right for exactly this
  content.

After a revert, the model is therefore offered the very cards it should reuse verbatim. ADR 0018's revival no longer
depends on the model re-deriving identical wording by chance.

### Feeds

- **T29**:
  - 10.1: `Flashcard.sourceHash`, `cardPagesPerSession`
  - 10.2: `source_hash`, `ON CONFLICT`
  - 10.4: re-cut
  - 10.5: weakness
  - 10.7: tests
  - ADR 0018
  - `CONTEXT.md`: **Weak Page**
