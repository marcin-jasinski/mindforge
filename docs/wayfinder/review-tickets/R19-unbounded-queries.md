---
id: R19
title: Two repository queries have no limit
type: performance
status: open
severity: medium
assignee:
blocked_by: []
---

## Problem

`docs/standards/backend/queries.md` calls for avoiding unbounded reads. Two queries ignore it,
and one of them throws away almost everything it loads.

### 1. Conversation turns: loads all, keeps five

`src/main/java/dev/mindforge/infrastructure/persistence/adapter/InteractionStoreAdapter.java:50`
calls `findByKnowledgeBaseIdAndInteractionIdOrderByCreatedAt` — **every turn of the conversation**
— so that `QueryService.prior()` (`QueryService.java:116`) can keep the **last 5**.

A long-running chat therefore loads its entire history from the database on every single turn, to
discard all but five rows. Cost grows linearly with conversation length, forever, on the most
interactive path in the product.

**Fix:** add a derived query ordering descending with a limit, and reverse in memory:

```java
List<InteractionTurnEntity> findTop5ByKnowledgeBaseIdAndInteractionIdOrderByCreatedAtDesc(
    UUID knowledgeBaseId, UUID interactionId);
```

or `Limit.of(n)` with the count passed from `QueryService`, so the 5 is declared in one place
rather than split between the query and the caller. Prefer the latter — the current code already
owns that number.

### 2. `findLogEntries` has no limit

`src/main/java/dev/mindforge/infrastructure/persistence/jpa/IngestRunJpaRepository.java:149` —
`findLogEntries` is a grouped five-way join across all runs of a knowledge base with **no
`Limit`**. Its sibling `findRunSummaries` (`:116`) does have one, which makes the omission look
accidental rather than intended.

It runs on every export (`log.md` is a projected bundle file) and on every log render. A knowledge
base with a long ingest history pays for its whole history each time.

**Fix:** decide what `log.md` should contain. If it is the full history by contract — check
ticket T06 and the OKF bundle layout, which specify `log.md` as a projection — then the limit
belongs at the render/pagination boundary rather than on the query, and the right change is
pagination for the *log view* while export keeps the full set. If there is no such contract, add
a `Limit` matching `findRunSummaries`.

**Resolve this question before changing the query** — silently truncating an exported `log.md`
would break bundle fidelity, which matters more than the query cost.

## Why it matters

Item 1 is a straightforward waste on the hottest interactive path, and the fix is a few lines.
Item 2 needs a small decision first but is equally contained.

Neither is an N+1 — the review confirmed there are none; entities carry plain UUID columns with
no `@OneToMany`/`@ManyToOne` anywhere, and the V2-V4 migrations index foreign keys and
`WHERE`/`ORDER BY` columns thoroughly, including the partial
`idx_flashcards_kb_due ... WHERE retired_at IS NULL`.

## Acceptance criteria

- [ ] The turns query fetches at most what `QueryService` uses, with the count declared once.
- [ ] `log.md`'s contract is stated here, and `findLogEntries` is bounded or paginated accordingly.
- [ ] Exported bundles still contain whatever `log.md` is contractually required to contain.
- [ ] A test pins the turn-fetch bound — e.g. 20 turns stored, at most 5 rows read.

## Resolution

<!-- filled on close -->
