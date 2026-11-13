---
id: P16-08
title: Describe a document's images as the ingest run's first step and replace each IMAGE block in place
status: done
plan_tasks: [16.2]
decisions: [D06, D04]
depends_on: [P16-04, P16-07]
size: L
issue: 18
commit: c52c990
fix_rounds: 0
---
## Goal
An INGEST run of a document with IMAGE blocks first calls `ImageDescriber` per IMAGE block, in document order, before
`Preprocessor.clean` and `RelevanceGuard`. A described image becomes a TEXT block `"Image: ..."` at the same position;
a not-relevant or unusable image is dropped and counted as a `describe` RunFailure; gateway and deadline errors fail
the run as retryable; an image-only document whose images were all dropped fails retryable with "no image could be
described". `ImageDescriber.VERSION` is in `step_versions`. An uploaded PDF's image description appears in the page
its document produced (Phase 16 completion checklist item 2).

## Context
- `src/main/java/dev/mindforge/application/service/IngestPipeline.java`:
  - constructor `(DocumentRepository, WikiStore, IngestRunRepository, RelevanceGuard, ClaimExtractor, PageWriter,
    LinkChecker, SupersessionDetector, ProgressNotifier, TransactionOperations, ProcessingSettings)`; the only
    construction site is `PipelineConfig.ingestPipeline(...)` (`src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java:~127-131`).
  - `run(IngestRun)` catches `IngestRunFailedException` (uses its message and `retryable()`), and any other
    `RuntimeException` via `IngestRunFailedException.reasonFor(e)` as retryable (so `AIGatewayUnavailableException`
    and `DeadlineExceededException` already fail the run retryable with their fixed domain message).
  - `generateAndCommit(RunState)` (~line 157): loads the document with `documents.findById(kbId, ...)` (outside any
    transaction), then `List<ContentBlock> blocks = Preprocessor.clean(document.contentBlocks());`, then for a
    non-conversation document `HeadingChunker.split`, throws `IngestRunFailedException("the document has no text",
    false)` if no chunk, then `guard.check(...)` and `state.version(RelevanceGuard.class, RelevanceGuard.VERSION,
    RelevanceGuard.TIER)`, then Extract per chunk. Conversation turns (`UploadSource.CONVERSATION`) use no blocks.
  - `step(state, name, done, total)` sends a progress event; existing names: `extract`, `resolve`, `write`,
    `linkCheck`, `supersede`.
  - `RunState` (`application/service/RunState.java`): `version(Class<?>, String, ModelTier)`, `addFailure(RunFailure)`.
  - `RunFailure.ofItem(String step, String item, String path, String reason)` (`domain/model/RunFailure.java:22`).
- `Preprocessor.clean` (`application/ingest/Preprocessor.java`) drops blocks with blank content, so IMAGE blocks
  (content `""`) must be replaced before it runs. Positions are not renumbered; gaps are normal.
- Claims cite block `position` (`ClaimExtractor` renders `[position] text`; `IngestPipeline.sourceBlocks` selects by
  position range), so the replacement TEXT block keeps the IMAGE block's position.
- P16-01: `ContentBlock.image()` / `ImageInput`. P16-04: `PdfParser` emits IMAGE blocks after each page's TEXT block.
  P16-07: `ImageDescriber` bean in `PipelineConfig`; `Description describe(String lessonTitle, String context,
  ImageInput image)` returning `Described(text)` (text already starts with `"Image: "`), `NotRelevant()` or
  `Unusable()`; `ImageDescriber.VERSION`, `ImageDescriber.TIER` (`VISION`); `Prompts.DESCRIBE` in test support.
- ADR 0020 (`docs/adr/0020-images-described-in-the-run-bytes-kept-in-content-blocks.md`) and D01 (binding, applied
  across this phase): describe inside the run as its first step, before `Preprocessor.clean`; the `Document` stays
  immutable — every attempt, including retries, describes again; under the background permit; record
  `state.version(ImageDescriber.class, ImageDescriber.VERSION, ImageDescriber.TIER)` only when at least one IMAGE
  block exists; skip for CONVERSATION; no model call inside any transaction. D01 tests: pipeline test that IMAGE
  blocks become TEXT before RelevanceGuard and `ImageDescriber` appears in `step_versions`; retry test re-describes;
  export test has no image bytes.
- Rules: never call a model inside a transaction; model services never call each other (the pipeline sequences
  them); domain exceptions only; fixed failure text, never provider output.
- Integration tests: `src/test/java/dev/mindforge/integration/application/IngestRunTest.java` extends
  `TestContainerBase`, autowires `IngestionService`, `StubAIGateway gateway`, `WikiStore`, `IngestRunRepository`;
  routes answers with `gateway.answer(Prompts.X, ...)`; uploads via `ingestion.ingest(new DocumentUpload(...))`;
  waits with `awaitRun(kbId, documentId, RunStatus)`. `IngestionService.retry(kbId, documentId)` re-queues a failed
  document. `src/test/java/dev/mindforge/integration/export/BundleExportTest.java` exports a knowledge base.

## Decisions to apply
**D06 — Decision:** `ImageDescriber` sends each image with the lesson title and the text of the nearest preceding
TEXT/HEADING blocks (bounded) as context. Code replaces a relevant image's IMAGE block with a TEXT block at the same
position whose content is `"Image: " + description`. Code drops an image the model marks not relevant and counts it on
the run as a `describe` RunFailure, the same way D04 counts an unusable description.

**D06 — Implementation notes (pipeline part):**
- Context: built in `IngestPipeline` from the raw `document.contentBlocks()` before `Preprocessor.clean`: the content
  of the nearest preceding TEXT/HEADING blocks, concatenated in document order, keeping the last `CONTEXT_CHARS`
  (constant, 1500) characters (cut from the start). Empty string when the image is first. Title from
  `document.lessonIdentity().title()`. Do not include earlier image descriptions in the context.
- Describe step (before `Preprocessor.clean`): `Described` -> `ContentBlock.text(text, position)` at the IMAGE
  block's position; `NotRelevant` -> drop, `state.addFailure(RunFailure.ofItem("describe", "block " + position, null,
  "not relevant"))`; `Unusable` -> drop with reason `"no usable description"`. Fixed text only. Do not renumber
  positions. The all-dropped guard counts both kinds of drop: IMAGE blocks existed, all dropped, no non-blank TEXT left
  -> `IngestRunFailedException("no image could be described", true)`. Record `state.version(ImageDescriber...)` when
  any IMAGE block existed.
- Tests: one decorative and one relevant image -> one `describe` RunFailure "not relevant", the TEXT block
  "Image: ..." reaches Extract at the image's position; context is the preceding text cut to the cap; image-only
  document with all images not relevant -> FAILED retryable "no image could be described".

**D04 — Decision:** when the vision model returns blank or unusable output for one image, drop that IMAGE block,
record a `RunFailure` with step `describe` on the run, and continue with the other blocks in document order.
Infrastructure errors are not per-image: let `AIGatewayUnavailableException` and `DeadlineExceededException`
propagate so the whole run fails as retryable. If the document had IMAGE blocks, every one was dropped, and no TEXT
block remains, fail the run with a fixed, retryable reason, not "the document has no text".

**D04 — Implementation notes (pipeline part):**
- Per dropped image, `state.addFailure(RunFailure.ofItem("describe", "block " + position, null, "no usable
  description"))`. Remaining blocks keep their order.
- After describing: if at least one IMAGE block existed, all were dropped, and no non-blank TEXT block remains, throw
  `new IngestRunFailedException("no image could be described", true)`.
- `state.version(ImageDescriber...)` is still recorded when any IMAGE block existed, even if all were dropped.
- Do not catch `RuntimeException` broadly in the describe step.
- Tests: blank output for one of two images -> run completes, one `describe` RunFailure with the block position, the
  other description reaches Extract; gateway unavailable -> run FAILED retryable, no `describe` drop recorded;
  deadline exceeded -> run FAILED retryable; image-only document with all images blank -> FAILED retryable with "no
  image could be described"; text plus all images blank -> run continues on text with the drops recorded.

Implementer choices (record in Notes): put the context builder and block replacement in a small helper in
`dev.mindforge.application.ingest` if it makes them unit-testable; send a progress `step(state, "describe", done,
total)` before/while describing (the frontend label is P16-09). Keep the version recorded once per run.

## Acceptance criteria
- [x] AC1: `IngestPipeline` takes `ImageDescriber` (constructor) and `PipelineConfig` passes the bean; for a
  non-conversation document with IMAGE blocks, every IMAGE block is sent to `ImageDescriber` in document order before
  `Preprocessor.clean` and before `RelevanceGuard`, outside any transaction. Documents without IMAGE blocks and
  conversation turns make no VISION call and record no `ImageDescriber` version (existing `step_versions` assertions
  in `IngestRunTest` still pass).
- [x] AC2: A `Described` image becomes a TEXT block with content `"Image: ..."` at the IMAGE block's position; the
  Extract prompt shows it as `[<position>] Image: ...`.
- [x] AC3: The context passed for an image is the preceding TEXT/HEADING content in document order, at most the last
  1500 characters, no earlier descriptions, `""` for a first-position image; the title is the lesson title.
- [x] AC4: `NotRelevant` and `Unusable` images are dropped, each adding one `RunFailure` with step `describe`, item
  `"block <position>"`, reason `"not relevant"` / `"no usable description"`; the run continues.
- [x] AC5: `AIGatewayUnavailableException` or `DeadlineExceededException` during describe fails the run retryable with
  no `describe` RunFailure recorded.
- [x] AC6: An image-only document whose images are all dropped fails with reason "no image could be described",
  retryable = true; a document with text plus all images dropped completes on its text with the drops recorded.
- [x] AC7: `step_versions` contains `ImageDescriber` = `VERSION@<vision model>` whenever the document had an IMAGE block
  (also when all were dropped).
- [x] AC8: A retry of a failed run of an image document describes the images again (VISION calls recorded on the
  stub for the second attempt).
- [x] AC9 (checklist): an uploaded PDF (built in the test with PDFBox, one page of text plus one image) completes, and
  the description text appears in the `markdownBody` of a page its document produced.
- [x] AC10: The export bundle of that knowledge base contains no base64 of the image bytes.

## Test plan
- Integration (Testcontainers, `StubAIGateway`): new
  `src/test/java/dev/mindforge/integration/application/ImageIngestTest.java` (sibling of `IngestRunTest`, same setup
  and helpers pattern) for AC1–AC9. Upload real PDF bytes (`application/pdf`) built with PDFBox and
  `LosslessFactory`. Route `Prompts.DESCRIBE` answers by fragment (a responder can vary by call order or by context in
  the prompt); route Extract to cite the image's position; make the Write responder return a body that includes the
  description it finds in its prompt, so AC9 proves the description travelled Extract -> Write -> page. Assert
  `stubCall.image()` is non-null and tier is `VISION` for describe calls.
- Integration: extend `src/test/java/dev/mindforge/integration/export/BundleExportTest.java` (or assert in
  `ImageIngestTest` through the exporter) for AC10.
- Unit (plain JUnit): if a helper is extracted to `dev.mindforge.application.ingest`, test the context cap and
  in-place replacement in `src/test/java/dev/mindforge/unit/application/` with `make*` factories.

## Files likely touched
- `src/main/java/dev/mindforge/application/service/IngestPipeline.java`
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java`
- `src/main/java/dev/mindforge/application/ingest/` (optional new helper)
- `src/test/java/dev/mindforge/integration/application/ImageIngestTest.java` (new)
- `src/test/java/dev/mindforge/integration/export/BundleExportTest.java`
- `src/test/java/dev/mindforge/unit/application/` (optional helper test, new)

## Out of scope
- Mutating or re-saving the `Document` or its `content_blocks` (D01 rejected option C); caching descriptions.
- Placeholder text for dropped images; truncating descriptions; renumbering positions.
- Frontend labels (P16-09); docs and ADR 0013 amendment (P16-10).
- Changes to `RelevanceGuard`, `ClaimExtractor`, `PageWriter` or their prompts/VERSIONs.

## Notes
- Decided: the context builder is `application.ingest.ImageContext.before(blocks, index)` (unit-tested), joining blocks with "\n\n" like the PDF page separator, because it keeps the cap and the skip of IMAGE blocks testable without a model; the replacement loop stays in `IngestPipeline.describeImages` since it calls the describer.
- Decided: progress `step(state, "describe", done, total)` is sent before each image, because the frontend label (P16-09) can then show "n of total".
- Decided: the describe step runs before `wiki.listIndex`, because the index is then read after the slow vision calls and is fresher for Extract.
- Decided: `ImageDescriber` version is recorded once, after the loop and before the all-dropped check, because it then appears also on a run that fails "no image could be described"; a gateway or deadline error mid-loop records no version (the run fails anyway).
- Decided: AC10 is asserted in `ImageIngestTest` through `ExportedBundle`, not in `BundleExportTest`, because it needs the image bytes the run actually sent.
- Tester r1 AC map: AC1 ImageIngestTest.aDocumentWithoutImagesMakesNoVisionCall + first test (order before guard); AC2 first test (Extract "[2] Image: ..."); AC3 ImageContextTest + first test (context/title); AC4 first two tests; AC5 anUnavailableGateway.../anExceededDeadline...; AC6 anImageOnly... tests + aDocumentWithTextCompletes...; AC7 first test + all-dropped tests; AC8 anImageOnlyDocumentWithNoRelevantImage...AndARetryDescribesAgain; AC9 first test (page body); AC10 first test (ExportedBundle).
