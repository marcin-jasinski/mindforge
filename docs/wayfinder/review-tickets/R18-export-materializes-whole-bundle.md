---
id: R18
title: Export materializes the whole bundle behind a streaming facade
type: performance
status: open
severity: medium
assignee:
blocked_by: []
---

## Problem

`src/main/java/dev/mindforge/infrastructure/export/BundleExporter.java:74-89` builds the complete
bundle as a `Map<String, String>` — every page body, `index.md`, `log.md`, every projected file —
validates it, and then hands it to a `StreamingResponseBody`
(`api/controller/ExportController.java:39-44`).

The response *looks* streamed, but the whole bundle is already in heap before the first byte is
written, and it stays referenced for the duration of the write. Peak heap is roughly **2x the
wiki** per concurrent export — once for the map, once for the zip buffer.

## Why it matters

Medium, and bounded by the same growth curve as R17: it scales with wiki size, on a product
designed for wikis that compound. A handful of concurrent exports of a large knowledge base is a
plausible OOM on a small container — and `docs/project/deployment.md` describes exactly that, a
single modest instance on Railway or Render.

The validation ordering is **correct and must be preserved**: `BundleConformanceValidator` runs
before a byte is written, which is what closes zip-slip and what guarantees an invalid bundle is
never partially sent. Any fix has to keep that property.

## Fix

The tension is real — OKF §9 conformance is validated over the whole bundle, and a zip that fails
validation must never reach the client. Two workable shapes:

### Option A — two passes (recommended)

1. Pass one: validate from the database without materialising bodies — check paths, frontmatter
   keys, citation integrity and the entry names that zip-slip depends on. Most of OKF §9 is
   structural and needs metadata, not prose.
2. Pass two: stream page bodies into the `ZipOutputStream` one at a time, holding one body at a
   time.

Anything in §9 that genuinely needs full text can be checked per-entry during pass two, failing
the stream before that entry is written.

### Option B — spool to a temp file

Build and validate the zip on disk, then stream the file and delete it. Simpler and keeps
validation exactly as it is; trades heap for disk and needs cleanup on failure. Acceptable if
Option A's split turns out to be intrusive.

Do not add a queue, a job table or an async export flow — Phase 9b deliberately specified a
**synchronous** export and that decision stands.

## Acceptance criteria

- [ ] Peak heap during an export does not scale with the number of pages.
- [ ] An invalid bundle still fails before any byte reaches the client — prove it with the
      existing conformance test.
- [ ] `BundleExportTest`'s three cases still pass, including
      `anIngestCommittedDuringAnExportDoesNotTearTheBundle` (the snapshot guarantee).
- [ ] Export stays synchronous.
- [ ] Measure peak heap before and after on a large knowledge base; record it here.

## Resolution

<!-- filled on close -->
