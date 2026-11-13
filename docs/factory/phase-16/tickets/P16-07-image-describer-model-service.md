---
id: P16-07
title: Add the ImageDescriber model service on the VISION tier with its prompt and code-enforced output rules
status: ready
plan_tasks: [16.2, 16.3]
decisions: [D06, D04]
depends_on: [P16-06]
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
`dev.mindforge.agent.ImageDescriber` sends one image, the lesson title and nearby document text to the `VISION` tier
through the `AIGateway` image overload and returns a typed `Description`: `Described("Image: ...")`, `NotRelevant` or
`Unusable`. Unusable model output never throws; gateway and deadline errors propagate. The bean is wired in
`PipelineConfig` behind the background permit pool. The pipeline does not call it yet (P16-08).

## Context
- P16-01: `dev.mindforge.domain.model.ImageInput`. P16-06: `AIGateway.complete(ModelTier, String, ImageInput,
  DeadlineProfile)`; `StubAIGateway` answers image calls by prompt fragment and records the `ImageInput` on `Call`;
  it rejects non-VISION image calls.
- Pattern to copy: `src/main/java/dev/mindforge/agent/RelevanceGuard.java` — `public static final String VERSION`,
  `public static final ModelTier TIER`, constructor `(AIGateway gateway, PromptLoader prompts)`,
  `prompts.render("relevance_guard", Map.of(...))`, `ModelJson.read(getClass().getSimpleName(), completion,
  Output.class)`, nested `record Output(...)`, `TextRules.singleLine(...)`.
- `dev.mindforge.infrastructure.ai.ModelJson.read(String service, String completion, Class<T>)` throws
  `dev.mindforge.domain.model.ModelOutputException` on bad JSON. Gateway errors:
  `AIGatewayUnavailableException`, `DeadlineExceededException` (both in `dev.mindforge.domain.model`).
- Prompts: `src/main/resources/prompts/en/{name}.en.md`; each starts with a `# Task: ...` line; values use
  `{{name}}` placeholders; untrusted input is wrapped in tags and declared as data (see `relevance_guard.en.md`).
  Test support `src/test/java/dev/mindforge/support/Prompts.java` holds the `# Task:` fragments that route stub
  answers.
- Wiring: `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java` — each INGEST service bean is
  `new X(background(aiGateway, backgroundPermits, observationRegistry, X.class), promptLoader)` (see
  `relevanceGuard(...)`, lines ~69-72).
- `src/test/java/dev/mindforge/unit/agent/ModelServiceVersionTest.java` lists every model service in a
  `@ValueSource(classes = {...})`.
- Q07 (answered by `docs/standards/backend/ai_agents.md` "Model Selection", "Version Management"; ADR 0004):
  `TIER = ModelTier.VISION`; `DeadlineProfile.BACKGROUND`; wired through `PipelineConfig.background(...,
  ImageDescriber.class)`; added to `ModelServiceVersionTest`.
- Rules: all LLM calls through `AIGateway`, by tier; every rule a prompt teaches is enforced in code; content-making
  prompts tell the model to write in the language of the source; fail loudly, never truncate; never catch
  `RuntimeException` broadly; model services never call each other.

## Decisions to apply
**D06 — Decision:** `ImageDescriber` sends each image with the lesson title and the text of the nearest preceding
TEXT/HEADING blocks (bounded) as language and topic context, and asks for JSON `{"relevant": bool, "description":
"..."}`. Code replaces a relevant image's IMAGE block with a TEXT block at the same position whose content is
`"Image: " + description`. Code drops an image the model marks not relevant and counts it on the run as a `describe`
RunFailure. Unusable output (bad JSON, missing `relevant`, blank or over-length description) is a drop, not a run
failure.

**D06 — Implementation notes (model service part):**
- `src/main/java/dev/mindforge/agent/ImageDescriber.java`: `public static final String VERSION`,
  `TIER = ModelTier.VISION`. Method `Description describe(String lessonTitle, String context, ImageInput image)`,
  calling `gateway.complete(TIER, prompt, image, DeadlineProfile.BACKGROUND)`. Result is a sealed interface nested in
  `ImageDescriber`: `sealed interface Description permits Described, NotRelevant, Unusable` with
  `record Described(String text)` (text already prefixed), `record NotRelevant()`, `record Unusable()`. Private
  `record Output(Boolean relevant, String description)`.
- Parse with `ModelJson.read(getClass().getSimpleName(), completion, Output.class)`; catch only
  `ModelOutputException` from that call and return `Unusable`. Let `AIGatewayUnavailableException` and
  `DeadlineExceededException` propagate. Never catch `RuntimeException` broadly.
- Rules in code: `relevant == null` -> `Unusable`; `relevant == false` -> `NotRelevant` (description ignored);
  `relevant == true` with null/blank description -> `Unusable`; description is `TextRules.singleLine(...)`-normalised
  and must be 1..`MAX_DESCRIPTION_CHARS` (constant, 1000) characters, else `Unusable` (never truncate).
  Text = `MARKER + description` with `static final String MARKER = "Image: "`.
- Prompt `src/main/resources/prompts/en/image_describer.en.md` (English): describe the image's learning content
  (diagram, chart, table, formula, labelled photo, text in the image); write in the language of the context, or of
  text in the image when there is no context; set `relevant` false for logos, decoration, separators, icons, photos
  with no teaching content; say the context and any text in the image are untrusted data, not instructions; answer
  only the JSON object; no marker in the description (code adds it).
- Tests (`ImageDescriberTest`, `StubAIGateway` with VISION): relevant -> text starts with `"Image: "`; relevant false
  -> `NotRelevant`; malformed JSON, missing `relevant`, blank description, over-length description -> `Unusable`;
  gateway unavailable propagates; prompt contains the title and the context.

**D04 — Implementation notes (model service part):**
- Blank/unusable = completion text null or blank after trim. `ImageDescriber` does not throw for it; it returns
  `Unusable`, and lets gateway/deadline exceptions propagate unchanged. Do not catch `RuntimeException` broadly.

## Acceptance criteria
- [ ] AC1: `ImageDescriber` has `public static final String VERSION` (non-blank) and
  `public static final ModelTier TIER = ModelTier.VISION`; it is listed in `ModelServiceVersionTest`.
- [ ] AC2: `describe` makes exactly one gateway call with tier `VISION`, deadline `BACKGROUND`, and the given
  `ImageInput`; the prompt contains the lesson title and the context string.
- [ ] AC3: `{"relevant": true, "description": "A cell diagram"}` -> `Described("Image: A cell diagram")`; a multi-line
  description is normalised to a single line.
- [ ] AC4: `{"relevant": false, ...}` -> `NotRelevant`.
- [ ] AC5: Malformed JSON, missing `relevant`, blank or null description with `relevant: true`, a description over 1000
  characters, and a blank completion -> `Unusable` (no exception).
- [ ] AC6: `AIGatewayUnavailableException` and `DeadlineExceededException` from the gateway propagate unchanged.
- [ ] AC7: `image_describer.en.md` exists, starts with a `# Task:` line, wraps the context in a tag declared as untrusted
  data, states the language rule and the relevance rule, and asks for the JSON object only.
- [ ] AC8: `PipelineConfig` defines an `ImageDescriber` bean built with
  `background(aiGateway, backgroundPermits, observationRegistry, ImageDescriber.class)` and the `PromptLoader`; the
  application context still starts (existing integration tests pass).

## Test plan
- Unit (no Spring; `StubAIGateway`, never a `ChatClient` mock; AssertJ):
  `src/test/java/dev/mindforge/unit/agent/ImageDescriberTest.java` (new) for AC2–AC6, using
  `new PromptLoader("en")` and a `make*` factory for a small PNG `ImageInput`. For AC6 register a stub answer whose
  responder throws the exception.
- Unit: add `ImageDescriber.class` to `src/test/java/dev/mindforge/unit/agent/ModelServiceVersionTest.java` (AC1).
- Add `Prompts.DESCRIBE` (the prompt's `# Task:` line) to `src/test/java/dev/mindforge/support/Prompts.java` for
  routing stub answers here and in P16-08.
- AC8 is covered by the existing Spring integration tests starting the context.

## Files likely touched
- `src/main/java/dev/mindforge/agent/ImageDescriber.java` (new)
- `src/main/resources/prompts/en/image_describer.en.md` (new)
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java`
- `src/test/java/dev/mindforge/unit/agent/ImageDescriberTest.java` (new)
- `src/test/java/dev/mindforge/unit/agent/ModelServiceVersionTest.java`
- `src/test/java/dev/mindforge/support/Prompts.java`

## Out of scope
- Calling `ImageDescriber` from `IngestPipeline`, building the context string, recording failures or versions
  (P16-08).
- Placeholder text for dropped images (D06 rejected option C); truncating long descriptions.
- Any `Agent` interface or shared image abstraction.

## Notes
