---
id: P16-10
title: Record the Describe step in ADR 0013 and the architecture, document the image limits, and close Phase 16
status: ready
plan_tasks: [16.1, 16.2, 16.3]
decisions: [D01, D05]
depends_on: [P16-01, P16-02, P16-03, P16-04, P16-05, P16-06, P16-07, P16-08, P16-09]
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
The project docs describe Phase 16 as built: ADR 0013 carries a dated amendment for the new first step, the
architecture's ingest flow starts with Describe, the tech stack and deployment docs say where image bytes live and
which images are kept, and the plan, roadmap, `docs/INDEX.md` and CLAUDE.md "Project State" mark Phase 16 complete.
Documentation only — no code change.

## Context
- Code delivered by P16-01..P16-09: `ImageInput` + `ContentBlock.image`; list/dedup read paths without
  `content_blocks` (read P16-02's Notes: done or deferred, and why); `ImageExtractor` (64 px, 5 MB, 100 images);
  `PdfParser`/`DocxParser` IMAGE blocks (after the page text / holding paragraph or table); `AIGateway` image overload
  (VISION only); `ImageDescriber` (VISION, `"Image: "` marker, `describe` RunFailures); `IngestPipeline` describe step
  before Preprocess; frontend labels. Read each ticket's Notes for implementer choices before you write.
- ADRs written in this run: `docs/adr/0020-images-described-in-the-run-bytes-kept-in-content-blocks.md`,
  `docs/adr/0021-aigateway-image-overload.md` (both Accepted; do not rewrite them).
- `docs/adr/0013-ingest-typed-pipeline-no-agent-abstraction.md` has an `## Amendments` section (line ~40) with dated
  bullet entries (format: `2026-09-12, from the spec review:` then bullets with links).
- `docs/project/architecture.md`: line ~57 lists `IngestPipeline` steps ("Preprocess, Extract, Resolve, ..."); line
  ~111 starts the ingest flow `Preprocess → RelevanceGuard (SMALL; ...)`.
- `docs/project/tech-stack.md` § "Object Storage" (line ~92-96): "the original bytes are not retained".
- `docs/project/deployment.md` sections: Build, Run locally, Configuration, Railway, CLI, One live instance. No new
  setting was added in Phase 16 (limits are constants; `AI_MODEL_VISION` / `mindforge.ai.model.vision` already exists).
- `docs/standards/backend/ai_agents.md` already shows the image overload (P16-06).
- `docs/project/implementation-plan.md` § "## [ ] Phase 16 — Image Analysis" (line ~1474): tasks 16.1–16.3 and two
  completion checklist items; the TOC line 35 lists the phase.
- `docs/project/roadmap.md`: line 13 "**Next**: Phase 14 (...), then Phase 16 (Image Analysis)"; line ~110 Phase 16
  entry `- [ ]`.
- `CLAUDE.md` "Project State" paragraph (line ~111) says work resumes at Phase 14 then Phase 16. Change only that
  paragraph in CLAUDE.md.
- `docs/INDEX.md` line ~46 describes the ADR set "0001–0009 ..., 0019 records the CLI trust model"; add 0020–0021.
- House rules (`.claude/factory.md`): reports in ASD-STE100 Simplified Technical English; use `CONTEXT.md` vocabulary.
  Check `CONTEXT.md` for an entry that needs the image description (add a term only if a reader would need it).

## Decisions to apply
**D01 — Decision:** describe images inside the ingest run, as its first step, before `Preprocessor.clean` and so
before `RelevanceGuard`. Parsers store IMAGE blocks with their bytes in `documents.content_blocks` (JSONB) at upload;
the `Document` stays immutable, and every run attempt (including retries) describes the images again, under the
background permit, with `ImageDescriber.VERSION` recorded in `step_versions`. Keep `content_blocks` out of the
document list read path and out of every API response and the export.
**D01 — Implementation notes (docs):** tech-stack.md "Object Storage" states extracted image bytes live in
`content_blocks`; ADR 0020 written (already done).

**D05 — Decision:** parsers emit only decodable raster images; keep PNG and JPEG bytes as they are, re-encode every
other decodable raster to PNG, and skip EMF, WMF, SVG and anything that does not decode. Skip images narrower or
shorter than 64 px. Emit each distinct image once per document. Skip one image whose emitted bytes exceed 5 MB. After
filter and dedup, if a document has more than 100 IMAGE blocks, reject the upload with `UploadRejectedException`. No
opt-out switch in Phase 16.
**D05 — Implementation notes (docs):** add an "Images" paragraph to `docs/standards/backend/ai_agents.md` (or the
ingest section of architecture docs) and to `docs/project/deployment.md` stating the filters, the 5 MB skip and the
100-image rejection.

Q08 (answered by the plan and ADR 0013 "Amendments"): not a blocker; record a dated amendment in ADR 0013 ("Describe
(VISION) runs first, per IMAGE block") and update the ingest flow in `docs/project/architecture.md`; the same amendment
notes that the gateway gains an image-carrying single-shot completion (ADR 0021).

## Acceptance criteria
- [ ] AC1: ADR 0013 `## Amendments` has a new dated entry (2026-11-13, phase-16 factory run): Describe images
  (`ImageDescriber`, VISION) runs first, per IMAGE block, before Preprocess; `AIGateway` gains one image-carrying
  single-shot overload; links to ADR 0020 and ADR 0021.
- [ ] AC2: `docs/project/architecture.md` ingest flow starts with `Describe images (VISION; per IMAGE block; skipped for
  conversation turns) → Preprocess → RelevanceGuard ...`, and the `IngestPipeline` line names the describe step; it
  states dropped images are counted as `describe` RunFailures.
- [ ] AC3: `docs/project/tech-stack.md` "Object Storage" says extracted image bytes are kept in `content_blocks`, while
  the uploaded file's bytes are still not retained.
- [ ] AC4: An "Images" paragraph (filters: raster only, PNG/JPEG kept, others re-encoded to PNG, EMF/WMF/SVG skipped,
  < 64 px skipped, duplicates once, > 5 MB skipped, > 100 images rejects the upload) is in
  `docs/standards/backend/ai_agents.md` (or architecture.md ingest section) and in `docs/project/deployment.md`, which
  also names `AI_MODEL_VISION` as the model the image descriptions use.
- [ ] AC5: `docs/project/implementation-plan.md`: Phase 16 heading `## [x]`, tasks 16.1–16.3 `[x]`, both completion
  checklist items `[x]`, each with a short pointer to the proving test (`AIGatewayAdapterTest
  .shouldRouteVisionTierToItsConfiguredModelStringForImageCall`; `ImageIngestTest`).
- [ ] AC6: `docs/project/roadmap.md`: Phase 16 `[x]`, and the "Next" line no longer names Phase 16 (Phase 14's manual
  Langfuse check, then Phase 17).
- [ ] AC7: CLAUDE.md "Project State" paragraph states Phase 16 is complete and where work resumes; nothing else in
  CLAUDE.md changes.
- [ ] AC8: `docs/INDEX.md` ADR description covers 0020 (image bytes in content blocks, described in the run) and 0021
  (AIGateway image overload); other INDEX descriptions change only if a described file changed meaning.

## Test plan
- Documentation only. Run `mvn -q -o test -DskipFrontend=true -Djacoco.skip=true` to confirm nothing broke; check that
  every link added resolves to an existing file.

## Files likely touched
- `docs/adr/0013-ingest-typed-pipeline-no-agent-abstraction.md`
- `docs/project/architecture.md`
- `docs/project/tech-stack.md`
- `docs/project/deployment.md`
- `docs/standards/backend/ai_agents.md`
- `docs/project/implementation-plan.md`
- `docs/project/roadmap.md`
- `docs/INDEX.md`
- `CLAUDE.md` (Project State paragraph only)
- `CONTEXT.md` (only if a term is needed)

## Out of scope
- Any code or test change; editing ADR 0020/0021 decisions; new ADRs.
- Marking Phase 14 items complete.

## Notes
