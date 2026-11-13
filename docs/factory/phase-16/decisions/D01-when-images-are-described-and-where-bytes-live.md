---
id: D01
question: Q01
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-16/ballots/Q01.r1.tally.md]
adr: docs/adr/0020-images-described-in-the-run-bytes-kept-in-content-blocks.md
---
## Decision
Use A: describe images inside the ingest run, as its first step, before `Preprocessor.clean` and so before
`RelevanceGuard`. Parsers store IMAGE blocks with their bytes in `documents.content_blocks` (JSONB) at upload; the
`Document` stays immutable, and every run attempt (including retries) describes the images again, under the
background permit, with `ImageDescriber.VERSION` recorded in `step_versions`. Keep `content_blocks` out of the
document list read path and out of every API response and the export.

## Why
- The run already reads the stored blocks outside a transaction (`IngestPipeline.java:162`) and records versions via
  `state.version(...)` (`:170`, `:184`); the describe step reuses all of it.
- B has no run at upload: no `step_versions` entry (ai_agents.md:33), no permit (ai_agents.md:49), slow 202, model
  failure loses the upload, and it runs before the dedup check (`IngestionService.java:84-90`).
- C rewrites a `Document` that is "Source of truth ... Immutable" (`Document.java:7-9`) and breaks retry input.
- tech-stack.md:93-95 "original bytes are not retained" refers to the uploaded file; extracted blocks are stored in
  PostgreSQL by the same sentence. No ADR forbids image bytes in blocks.
- Matches plan 16.2 literally ("before `RelevanceGuard`").

## Rejected
- B: model call outside any run — no VERSION record, no permit, breaks fast 202 upload.
- C: mutates the immutable `Document`; a retry sees different input.
- D (cache by image hash / separate `document_images` table): speculative until cost or size is measured; can be added later.

## Dissent
None on the choice. Skeptic's risk to watch: `content_blocks` is an eager JSONB column and the document list
(`IngestionService.documents` -> `listByKnowledgeBase`) and `findByContentHash` load it, so image bytes inflate every
document-list GET and every upload's dedup check. Retries re-pay vision calls.

## Implementation notes
- `ContentBlock` (domain, JDK only) carries image bytes per Q02; serialized into the existing JSONB, no migration.
- `ImageDescriber` in `dev.mindforge.agent`: `public static final String VERSION`, `TIER = ModelTier.VISION`,
  `AIGateway.complete(VISION, ...)`; wired in `PipelineConfig` with the background (permit) gateway.
- `IngestPipeline.generateAndCommit`: describe before `Preprocessor.clean(...)`; replace each IMAGE block with
  `ContentBlock` TEXT at the same position, in document order; call
  `state.version(ImageDescriber.class, ImageDescriber.VERSION, ImageDescriber.TIER)` only when at least one IMAGE
  block exists; skip for CONVERSATION. No model call inside any transaction.
- A blank or failed description is a loud `IngestRunFailedException` or a counted omission on the run; never dropped silently.
- Read amplification: make `listByKnowledgeBase` and `findByContentHash` not load `content_blocks` (projection or
  lazy basic fetch), or document why deferred. The run's own document load keeps the blocks.
- Tests: `ImageDescriberTest` with `StubAIGateway(VISION)`; pipeline test that IMAGE blocks become TEXT before
  RelevanceGuard and VISION appears in `step_versions`; retry test re-describes; integration test that
  `DocumentResponse` JSON for a document with an image has no base64 bytes; export test has no image bytes.
- Docs: tech-stack.md "Object Storage" states extracted image bytes live in `content_blocks`; ADR 0020 written.
