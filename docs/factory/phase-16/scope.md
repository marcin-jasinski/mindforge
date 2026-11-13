# Phase 16 — Image Analysis: scope

## Goal

From `docs/project/implementation-plan.md` § "Phase 16 — Image Analysis": describe images extracted from uploaded
PDFs and DOCX files with the `VISION` tier, so that their content reaches the wiki. Image descriptions become text
blocks that flow into pages like any other content (no `DocumentArtifact`, no checkpoint skip).

## In scope

- 16.1 `PdfParser` and `DocxParser` emit `ContentBlock`s with `BlockType.IMAGE` that carry the image bytes and the
  MIME type, in document order.
- 16.2 `ImageDescriber` (`dev.mindforge.agent`, tier `VISION`, `VERSION` constant): one `AIGateway` call per IMAGE
  block; each IMAGE block is replaced, in place and in document order, by a TEXT block that holds the description,
  before `RelevanceGuard`.
- The port and adapter work 16.2 needs and the plan does not name: `AIGateway` cannot send an image today (see
  "Existing state"), so the port, `AIGatewayAdapter`, `PermitGateway`, `TracingGateway` and `StubAIGateway` need an
  image-capable call (Q03).
- Wiring of `ImageDescriber` in `PipelineConfig` behind the background permit pool, and its call in `IngestPipeline`.
- 16.3 tests: `ImageDescriberTest` with `StubAIGateway` (VISION); `PdfParserTest` image extraction (and
  `DocxParserTest`, because 16.1 changes `DocxParser` too).
- Completion checklist evidence: the VISION tier routes to the configured model string for the image call; an image's
  description appears in the pages its document produced (integration test through `IngestRunTest` or a sibling).
- Docs to update for the closed phase (factory `docs_to_update`): implementation plan, roadmap, `docs/INDEX.md` if a
  description changes, `docs/project/deployment.md` if a new setting is added, CLAUDE.md "Project State", plus
  `docs/project/architecture.md` ingest data flow and an amendment to ADR 0013 (the pipeline sequence gains a step).

## Out of scope

- Images in Markdown (`![]()`) and plain-text uploads — the plan's goal names PDF and DOCX only.
- `AUDIO` / `VIDEO` blocks.
- Article fetching (Phase 17), Discord/Slack (18–19), security hardening (20), E2E/CI (21).
- Phase 14 open items (manual Langfuse check).
- Images in wiki page bodies: drafts with an image are still rejected (`docs/standards/backend/ai_agents.md`, "Typed
  outputs, verified"). The description is prose; nothing renders the picture.
- Frontend changes, except at most a label for a new run step or failure step name (Q04).

## Existing state (code is the evidence)

- `BlockType.IMAGE` already exists: `src/main/java/dev/mindforge/domain/model/BlockType.java`.
- `ContentBlock` (`src/main/java/dev/mindforge/domain/model/ContentBlock.java`) has `content`, `mediaRef` (String),
  `mediaType` (String), `metadata`, `position`. It has **no field for bytes** and no `image(...)` factory. Plan 16.1
  says "carrying bytes + MIME type" (Q02).
- `PdfParser` emits one TEXT block per non-blank page; no image extraction.
  `src/main/java/dev/mindforge/infrastructure/parsing/PdfParser.java`.
- `DocxParser` emits paragraphs, headings and tables in body order; pictures inside runs are ignored.
  `src/main/java/dev/mindforge/infrastructure/parsing/DocxParser.java`.
- `AIGateway` port has one method, `complete(ModelTier, String prompt, DeadlineProfile)` — text only.
  `src/main/java/dev/mindforge/domain/port/AIGateway.java`. Plan 16.2 writes `AIGateway.complete(VISION, ...)` as if an
  image can be sent; the code cannot do that. **Plan/code gap** (Q03).
- `AIGatewayAdapter.resolveModel` already maps `VISION` to `mindforge.ai.model.vision`
  (`AI_MODEL_VISION`, default `openai/gpt-4o`; `application-dev.yml` uses `local-model`).
  `AIGatewayAdapterTest.shouldRouteVisionTierToItsConfiguredModelString` (line 89) already proves routing for the text
  call. The image call must be routed and tested the same way.
- `ProcessingSettings.withModels` already records the VISION model id, so `RunState.version(...)` works for a VISION
  service unchanged.
- Decorators that implement `AIGateway` and must carry any new method: `PermitGateway`, `TracingGateway`
  (`src/main/java/dev/mindforge/infrastructure/ai/`), and the test double `src/test/java/dev/mindforge/support/StubAIGateway.java`
  (matches answers by prompt fragment; records `Call(tier, prompt, deadline, response)` — no image).
- `IngestPipeline.generateAndCommit` (`src/main/java/dev/mindforge/application/service/IngestPipeline.java`, ~line 157):
  `Preprocessor.clean(document.contentBlocks())` → `HeadingChunker.split` → "the document has no text" check →
  `RelevanceGuard.check(first chunk)` → Extract per chunk. Conversation turns skip the guard and use no blocks.
- `Preprocessor.clean` (`src/main/java/dev/mindforge/application/ingest/Preprocessor.java`) **drops every block whose
  `content` is blank**. An IMAGE block with null/empty content would be dropped before any later describe step. So the
  describe step must run before `Preprocessor.clean`, or `Preprocessor` must keep IMAGE blocks. Either keeps the
  plan's "before RelevanceGuard". Implementer choice; it must not lose images.
- Claims point at block `position` (`ClaimExtractor` renders `[position]`; `IngestPipeline.sourceBlocks` selects by
  position range). Replacing a block in place with the same `position` keeps this working.
- `Document.contentBlocks` is persisted as JSONB `documents.content_blocks` (`DocumentEntity`, `V1__baseline.sql`).
  Whatever an IMAGE block carries at upload time is stored there (Q01).
- `DocumentResponse` excludes `contentBlocks`; the bundle exporter does not read `ContentBlock`. Image bytes do not
  leak to the API or the export as long as this stays so.
- `ModelServiceVersionTest` lists every model service; `ImageDescriber` must be added.
- Prompts live in `src/main/resources/prompts/en/{name}.en.md`; there is no `image_describer.en.md` yet.
- No partial work, stubs or TODOs for Phase 16 exist in `src/`.

## Constraints that apply

From `CLAUDE.md` (Non-Negotiable Rules) unless stated otherwise:

- Domain is JDK only: any new type for image data in `dev.mindforge.domain` (block field, gateway input record) uses
  no framework class (`CLAUDE.md` Architecture; `docs/standards/architecture/hexagonal.md`).
- Never modify `ParserRegistry` to add a format — not needed here; parsers change, the registry does not.
- The ingest pipeline is closed: a new step is a design change (`CLAUDE.md`; ADR 0013). The plan makes that change;
  record it as an ADR 0013 amendment and in `docs/project/architecture.md` (Q08).
- All Spring beans wired in `@Configuration` (`PipelineConfig`).
- Never call a model inside a database transaction: describe images before commit 1, outside any transaction
  (`CLAUDE.md`; `docs/standards/architecture/hexagonal.md`).
- All LLM calls through `AIGateway`; request by `ModelTier.VISION`; never a provider SDK or model string in a model
  service (`CLAUDE.md`; `docs/standards/backend/ai_agents.md` "Model Selection", "LLM Gateway").
- Every call single-shot (ADR 0013; `ai_agents.md`). `AIGateway` exposes chat completion only (`ai_agents.md`).
- Background calls of an INGEST run take a permit from the global pool (`ai_agents.md` "Model Selection";
  `PipelineConfig.background`).
- `VERSION` constant; recorded in `step_versions` via `RunState.version` (`ai_agents.md` "Version Management").
- Model services never call each other; `IngestPipeline` sequences them (`ai_agents.md`).
- Prompt file `src/main/resources/prompts/en/image_describer.en.md`; English prompt; content-making prompts tell the
  model to write in the language of the source (`ai_agents.md` "Prompt Files").
- Every rule a prompt teaches is enforced in code (`CLAUDE.md` Model Services; `ai_agents.md`).
- The LLM is the sole author of page prose — the description is input to Extract/Write, not page prose itself.
- Fail loudly, never truncate; an acceptable omission is counted on the run (`ai_agents.md` "Every Rule…").
- Domain exceptions only, no bare `RuntimeException` (`CLAUDE.md` Java Conventions;
  `docs/standards/global/error-handling.md`).
- Logger named `log`; records for value types (`docs/standards/backend/java-conventions.md`).
- Never return `raw_prompt`/`raw_completion`/`cost` in responses; image bytes stay out of DTOs
  (`docs/standards/security/web-security.md`).
- Minimal implementation: no stubs for AUDIO/VIDEO, no speculative image abstraction
  (`docs/standards/global/minimal-implementation.md`).
- Tests: unit tests without Spring; `StubAIGateway`, never a `ChatClient` mock; AssertJ; `make*` factories; PDF and
  DOCX fixtures built in the test with PDFBox and POI, not stored binaries (plan 4.6); Testcontainers for integration
  (`docs/standards/testing/test-writing.md`).
- Tech stack: no new dependency without reason; PDFBox 3.0.3 and POI 5.4.0 are present; `javax.imageio` is JDK
  (`docs/project/tech-stack.md`; `docs/standards/global/conventions.md` "minimal dependencies").
- `docs/project/tech-stack.md` § "Object Storage": "the original bytes are not retained" — relevant to Q01.

## Definition of done

Plan completion checklist (Phase 16):

- [ ] `VISION` tier routes to the correct model string (for the call that carries an image).
- [ ] An image's description appears in the pages its document produced.

Plus every task 16.1–16.3 is `[x]` in the plan, the phase heading is `[x]`, roadmap and the other `docs_to_update`
files reflect it, and all quality gates pass (`mvn -B verify -DskipFrontend=true`; frontend gate only if
`frontend/**` changes).
