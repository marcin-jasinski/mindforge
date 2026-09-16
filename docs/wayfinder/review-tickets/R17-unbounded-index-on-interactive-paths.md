---
id: R17
title: The whole index is rendered into every Query and every Lint
type: performance
status: open
severity: high
assignee:
blocked_by: []
---

## Problem

Two paths load the entire wiki on every invocation, and the guard that was designed for exactly
this is not wired to either.

### 1. `QueryService` renders the full index into every question

`src/main/java/dev/mindforge/application/service/QueryService.java:65` renders the **complete**
index into the prompt of every single question.

`HealthService` already computes the 20,000-token ceiling and a `prefilterDue` flag — the
mechanism ADR 0016 specifies:

> retrieval cost discipline (rendered index first, lexical prefilter past 20K tokens, no vector
> store)

...but `QueryService` **never reads it and never truncates**. The ceiling exists only as a
dashboard warning. So past a few hundred pages, every Query turn's prompt grows linearly with
the size of the wiki — in latency, in tokens, and in money — with nothing but a warning on a
health page to show for it.

This compounds with R09: the Query endpoint is unrated and unmetered, and each turn costs two
LLM calls carrying the whole wiki.

### 2. `LintService` loads every body, then goes sequential

`src/main/java/dev/mindforge/application/service/LintService.java:123-126` loads every `CONCEPT`
**and** `SOURCE_SUMMARY` body into a `HashMap<String, String>` before chunking. Then `:131-166`
makes **two sequential LLM calls per chunk** in a loop.

So a full Lint is: whole-wiki heap, plus serial latency proportional to wiki size. The ingest
pipeline already solved the same shape — `IngestPipeline.writeAll` fans out over a
`newVirtualThreadPerTaskExecutor` (`IngestPipeline.java:274`) — and Lint does not.

## Why it matters

High. This is the one finding in the review that gets **worse as the product succeeds**. The
whole premise of MindForge is a wiki that compounds with every upload (`docs/project/vision.md`);
these two paths get slower and more expensive on exactly the axis the product is designed to
grow along. A knowledge base large enough to be valuable is a knowledge base where Query is slow
and Lint is expensive.

The system already knows this — `docs/standards/architecture/hexagonal.md` documents the
retrieval cost discipline, and `HealthService` implements the measurement. Only the enforcement
is missing.

## Fix

### Query

Wire `QueryService` to the ceiling that already exists:

1. Read the same token estimate `HealthService` computes.
2. Under the ceiling, keep today's behaviour — render the full index. It is the cheapest correct
   thing and ADR 0016 endorses it.
3. Over the ceiling, apply the lexical prefilter ADR 0016 specifies (trigram) to shortlist
   candidate pages, and render only those into the prompt.

Do not build a vector store. ADR 0016 rules it out, and the prefilter is the documented answer.

### Lint

1. Stream or page the bodies rather than materialising every one into a map. Chunk as you read.
2. Fan the per-chunk calls out over a virtual-thread executor, mirroring
   `IngestPipeline.writeAll` — same shape, same bounded concurrency, same failure collection into
   `failures`.

Keep Lint's existing semantics exactly: ADR 0017 says Lint writes only link insertions, and that
must not change here.

## Acceptance criteria

- [ ] `QueryService` truncates or prefilters past the 20K-token ceiling instead of ignoring it.
- [ ] A test with a wiki over the ceiling asserts the prompt is bounded.
- [ ] `LintService` no longer holds every page body in memory simultaneously.
- [ ] Lint's per-chunk calls run concurrently, with failures still recorded per chunk.
- [ ] ADR 0017 still holds — Lint's only body write remains `LinkInsertionApplier` output.
- [ ] Measure before and after on a synthetic wiki of ~1,000 pages and record the numbers here.

## Resolution

<!-- filled on close -->
