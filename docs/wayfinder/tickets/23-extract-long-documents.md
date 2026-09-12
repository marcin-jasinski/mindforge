---
id: T23
title: How Extract and Write handle long documents, and what happens at the index ceiling
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Gap.**

**1. Extract's shape is undefined for long documents.** Uploads go up to 50 MB (`docs/project/implementation-plan.md:488`),
and the chunker "feeds Extract on long documents" (`:506`; `docs/wayfinder/tickets/12-existing-code-fate.md:124`). But T04
places the cap where extraction is "one call in" (`docs/wayfinder/tickets/04-ingest-execution-model.md:189`), and the
architecture shows Extract as a single call. A 300-page PDF does not fit one context. Unspecified:
- one call, or one per chunk;
- whether the claim cap applies per chunk or per document;
- how same-topic claims from different chunks merge — Resolve collapses identical paths, not synonyms;
- the cost of repeating the ~9K-token index in every chunk;
- how the 300 s `BACKGROUND` deadline applies.

**2. The writer's inputs are unspecified.**
- Is a Concept page written from claims alone, losing the source's examples and detail, or from claims plus the source
  blocks they came from?
- What does the Source Summary writer receive — the whole document, the claims, or chunk digests?

**3. Nothing happens at the 20K-token ceiling.** The prefilter "waits behind a 20K-token ceiling"
(`docs/wayfinder/tickets/09-query-retrieval-neo4j.md:110`). Until it is built, nothing notices a knowledge base crossing
the line: the index just grows inside every Extract and PageSelector prompt. "Fail loudly, never truncate" argues for at
least a check. No method for estimating tokens is specified either, and `TokenBudget` needs one.

**4. Hitting the cap on a legitimately long document.** Such a document fails every time. Is that run `retryable`?
What is the cap's value?

Decide:

- **Extract's shape.** *Recommended:* one call per heading-aware chunk, each with the index. The cap applies to the
  document total. Resolve merges claims by final path.
- **The cap.** Its value, and `retryable = false` when it is exceeded.
- **Writer inputs.** *Recommended:* claims carry the positions of their source blocks, so the writer gets claims plus
  those blocks; the Source Summary writer gets the whole document, or per-chunk digests past a size limit.
- **The ceiling check.** *Recommended:* estimate index tokens as characters ÷ 4 at render time; over 20K, log a WARN
  and record it on the run. Crossing the ceiling is what makes the prefilter task due.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. Two caps replace the recommended
single cap; the reason is in decision 2.

**Summary:**

- Extract runs once per heading-aware chunk, sequentially. Each call sees the index plus the pages earlier chunks
  planned.
- Two caps bound a run: claims per call (against a bad extraction) and page tasks per run (against cost).
- A writer gets its claims plus the source blocks those claims came from.
- Tokens are estimated in one place, and the index ceiling shows in the health view.

### Decisions

**1. Extract's shape.**

- **Chunks.** The Phase 4 chunker splits the preprocessed blocks at heading boundaries, into chunks of at most
  `ProcessingSettings.chunkSizeTokens`. The defaults change from 800 tokens with 100 overlap to **12 000 tokens with
  overlap 0**. The old sizes were meant for RAG, a consumer that no longer exists. Overlap would duplicate claims, and
  heading boundaries make it unnecessary. A short document is one chunk and one call.
- **Sequential, not parallel.** Chunk *k* receives its blocks (each marked with its `position`), the rendered index, and
  the paths and titles planned by chunks 1…*k−1*. Because it can target those, *Podział mitotyczny* in chunk 3 lands on
  *Mitoza* planned in chunk 1 instead of becoming a second page. Resolve accepts a planned path as a valid target (T22).
  Parallel chunks would each invent their own synonym. Extract is a background step, so the sequential latency is
  acceptable.
- **Merging.** Claims from all chunks feed one Resolve, which groups them by final path (T22).
- **Deadline.** `DeadlineProfile.BACKGROUND` (300 s) applies **per call**. A run has no overall deadline; the caps bound
  its size.
- **Cost.** The index is repeated in every chunk: about 13 × 9K tokens for a 150K-token document. This is accepted.
  Provider prompt caching is the answer if it ever matters, not a design change.

**2. Two caps, both failing loudly.**

| Cap | Default (`ProcessingSettings`) | Guards against | On hit |
|---|---|---|---|
| `maxClaimsPerExtractCall` | 40 | a degenerate extraction returning fragments (T04's reason) | run `FAILED`, `retryable = true` — a bad sample may not repeat |
| `maxPageTasksPerRun` | 100 Concept tasks after grouping | an upload that would write more pages than one run should | run `FAILED`, `retryable = false`, reason *"would write N pages (limit 100) — split the document"* |

A single claim cap cannot do both jobs:

- Counted per call, it misses cost: a long document can still write far too many pages across many chunks.
- Counted per document, it fails every long document once chunks multiply, and it misses one bad chunk inside a long one.

The page-task cap is what actually bounds a run's cost, because writes are per page. It is checked in Resolve, before any
write call.

**3. Writer inputs.**

- **Concept task.** The writer gets its claims, plus the source blocks from each claim's `[firstBlock, lastBlock]`:
  - blocks are de-duplicated and kept in document order;
  - they are trimmed to `ProcessingSettings.writerSourceTokens` (default 16 000) by dropping whole blocks from the end;
  - claims themselves are never trimmed, since the blocks add detail, not facts;
  - a block range outside the chunk that produced the claim is ignored, and the claim is kept without blocks.
- **Source Summary task.** If the whole preprocessed document fits `chunkSizeTokens`, the writer gets all of it.
  Otherwise each Extract call also returns a one-paragraph `chunkDigest` (no extra call), and the writer gets the digests
  in order.

**4. Token estimate and the ceiling.** `TokenEstimate.of(String) = ceil(chars / 3)` is one function in the domain. It is
used by `TokenBudget`, the chunker, the Supersede budget (T14) and the ceiling check. The divisor is 3, not the ticket's 4:
Polish tokenizes more densely than English, and overestimating tokens is the safe side of every budget.

Past the 20K-token ceiling:

- `IndexRenderer` logs a WARN with the knowledge base id and the estimate.
- The health view shows the index estimate against the ceiling ("≈ 21K tokens — prefilter due").

Nothing truncates the index. A prompt that no longer fits fails its call, and the run fails loudly. Crossing the ceiling
is the trigger ADR 0016 names for building the prefilter.

The ceiling is recorded in the health view rather than on each run. That needs no new column and puts it where both the
learner and the developer look.

### Feeds

- **T14** — `TokenEstimate`.
- **T29**:
  - 4.4 — chunk defaults;
  - 6.3 — sequential chunks and caps;
  - 6.4 — page-task cap;
  - 6.5 — writer inputs;
  - 7.1 — index estimate in the health view;
  - 11.1 — `TokenBudget` uses `TokenEstimate`.
