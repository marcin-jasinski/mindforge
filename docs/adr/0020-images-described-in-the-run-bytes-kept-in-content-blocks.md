# Images are described inside the ingest run, and their bytes are kept in the content blocks

Status: Accepted (2026-11-13).

Parsers extract images at upload as IMAGE content blocks, and the image bytes are stored with the other blocks in
`documents.content_blocks` (JSONB). The ingest run describes them as its first step, before Preprocess, so the
sequence of ADR 0013 becomes:

```
Describe images → Preprocess → RelevanceGuard → Extract → Resolve (code) → Write (×N) → Link check → commit → Supersede
```

`ImageDescriber` is a model service on the `VISION` tier. Each IMAGE block becomes a TEXT block holding its
description, at the same position. The calls run on the run worker, outside any transaction, under the global
background permit, and `ImageDescriber.VERSION` is recorded in `step_versions` when the document has an image. The
`Document` is not changed: it stays the immutable source of truth, so every attempt of a run sees the same input.

## Considered Options

- **Describe at upload, store only the descriptions**: no run exists yet, so the call has no `step_versions` entry,
  no permit and no cost on a run; the 202 upload waits for N vision calls, and a model failure loses the upload.
- **Describe in the run and rewrite the document's blocks at commit**: breaks `Document` immutability, and a retry
  sees different input from the first attempt.
- **Keep the bytes in a separate `document_images` table, or cache descriptions by image hash**: a table, a port and
  a migration for a size or cost problem not yet measured. Add when measured.

## Consequences

- `content_blocks` is no longer text only. It can grow to about 4/3 of the 50 MB upload cap. Read paths that do not
  need blocks (the document list, the dedup lookup) must not load them.
- Every run attempt, including retries and sweep re-queues, pays the vision calls again; the cost is on each run.
- Image bytes never leave the server: `DocumentResponse` excludes blocks, and the exporter does not read them.
- "The original bytes are not retained" (tech-stack.md) still holds for the uploaded file; extracted image bytes are.

Decided in [D01](../factory/phase-16/decisions/D01-when-images-are-described-and-where-bytes-live.md) by the
dark-factory run `phase-16`.
