---
id: Q01
status: decided
decision: D01
impact: high
blocks: [W01, W02, W03, W05, W06]
---
## Question
Are images described inside the ingest run (so the image bytes are stored in `documents.content_blocks` until then),
or at upload time before the document is saved (so only the description is ever stored)?

## Context
- Plan 16.2 (`docs/project/implementation-plan.md` § Phase 16): the describer replaces each IMAGE block with a TEXT
  block "before `RelevanceGuard`". Roadmap: "turns image blocks into text blocks before extraction".
- Code: parsers run at upload in `IngestionService` (before its transaction); `Document.contentBlocks` is saved as
  JSONB (`DocumentEntity`, `V1__baseline.sql`). The run (`IngestPipeline.generateAndCommit`) reads the blocks back
  later, on the run worker. So if the run describes images, the bytes must be in the stored blocks.
- `docs/project/tech-stack.md` § "Object Storage": "Uploaded documents are parsed at upload and stored as extracted
  text and content blocks in PostgreSQL; the original bytes are not retained". ADR 0014 amendment: "Uploaded text is
  retained". Neither forbids image bytes in JSONB, but storing them moves away from "text only".
- Uploads can be 50 MB (`mindforge.upload.max-size`); a deck of images, base64 in JSONB, grows ~33 %.
- A re-run (retry, sweep re-queue, up to three attempts) is a new run against the current wiki (ADR 0015); with
  in-run description every attempt pays the vision calls again, and `step_versions`/cost record them on the run.
- Upload today returns 202 quickly; calls at upload would hold the HTTP request (or the CLI) for N vision calls and
  are outside the run's permit pool, cost meter and `step_versions`.

## Known options
- A: Describe in the run, as the first step (before Preprocess/RelevanceGuard). IMAGE blocks with bytes are stored
  in `content_blocks`. Matches the plan literally; recorded on the run; retries re-describe.
- B: Describe at upload in `IngestionService`, after parsing and before the transaction; store only TEXT blocks.
  No bytes stored; upload becomes slow and can fail on the model; VERSION cannot be recorded on a run that does not
  exist yet. Contradicts "before RelevanceGuard" only in place, not in order.
- C: Describe in the run and keep bytes only until then — e.g. the run's commit rewrites the document's blocks
  replacing images with descriptions. Breaks `Document` immutability ("Source of truth for the ingestion pipeline")
  and makes a retry see different input.

## Constraints
- Never call a model inside a database transaction (`CLAUDE.md`).
- Background calls take a permit from the global pool; `VERSION` recorded in `step_versions` (`docs/standards/backend/ai_agents.md`).
- 202 semantics for upload (plan 4.5).
- Image bytes never in an API response or the export (`docs/standards/security/web-security.md`; `DocumentResponse`).
