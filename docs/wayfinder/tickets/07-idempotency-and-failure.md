---
id: T07
title: What replaces step-fingerprint checkpointing
type: grilling
status: open
assignee:
blocked_by: [T04, T05]
---

## Question

Step fingerprinting is MindForge's entire reliability story: each step checksums its inputs,
prompt version and model id, and unchanged steps are skipped on rerun. `CLAUDE.md` makes it
non-negotiable that a pipeline checkpoint and its outbox event share one `@Transactional`
boundary.

That model assumes a step's output is a pure function of its input. Ingest is not: it mutates
a wiki that other documents have already changed, and *should* produce different results on
rerun because the wiki has moved on.

Decide:

- **What does re-uploading the same document do?** Skip as a duplicate (fingerprints demote to
  source-level dedup), or re-integrate against the current wiki? These give different products.
- **What does re-uploading a *revised* document do?** The old version's contributions are
  already woven into N pages. Is there a retraction story, or does the wiki just accumulate
  both and rely on supersession (T03)?
- **What is the transactional boundary** when Ingest writes 12 pages and fails at 5? Options:
  all-or-nothing (needs T05 to support it), page-at-a-time with a partially-updated wiki as a
  legal state, or a staging area that commits atomically.
- **Is a partially-ingested wiki a legal state?** If yes, `log.md` and the index have to be
  honest about it. If no, and pages are files, you need something transaction-shaped over
  object storage.
- **What survives of `StepCheckpoint`, `StepFingerprint` and `Hashes`?** They exist in the
  domain today.
- **Does the outbox rule still bind?** If Neo4j's projection changes shape (T09), what the
  outbox carries changes with it.

## Answer
