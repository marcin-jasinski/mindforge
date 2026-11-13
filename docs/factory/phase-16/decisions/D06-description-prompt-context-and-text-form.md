---
id: D06
question: Q06
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-16/ballots/Q06.r1.tally.md]
adr: 
---
## Decision
Use A: `ImageDescriber` sends each image with the lesson title and the text of the nearest preceding TEXT/HEADING
blocks (bounded) as language and topic context, and asks for JSON `{"relevant": bool, "description": "..."}`. Code
replaces a relevant image's IMAGE block with a TEXT block at the same position whose content is `"Image: " +
description`. Code drops an image the model marks not relevant and counts it on the run as a `describe` RunFailure,
the same way D04 counts an unusable description. Unusable output (bad JSON, missing `relevant`, blank or over-length
description) is a D04 drop, not a run failure.

## Why
- ai_agents.md:75: content is written in the language of its source; an image has none, so nearby document text is
  the only language signal. Title alone is weak; B fails this rule (eliminated).
- ClaimExtractor renders `[position] text` (ClaimExtractor.java:92-94); a code-added fixed marker keeps provenance and the model cannot omit it.
- ai_agents.md:91 and D04: an omission is legal when counted on the run; a decorative drop uses the same
  `RunFailure.ofItem("describe", ...)` shape as D04 (RunFailure.java:22), so no new counter or schema.
- `ModelJson` (ModelJson.java:18) is the existing reader for model JSON (RelevanceGuard.java:30).
- Gaps in positions after a drop are already normal: `Preprocessor.clean` drops blank blocks without renumbering (Preprocessor.java:23-29).
- The model output decides only keep/drop of one input block; code owns page type, path, title, membership.

## Rejected
- B: no language context, no marker, no code-enforced bound (verified hard objection).
- C: decorative images still cost Extract input and invite claims about logos; A's drop is counted, so it is not silent.

## Dissent
None on the choice. Risk to watch (pragmatist, skeptic): the model marks a real diagram as not relevant and its
content is lost. The `describe` lines with reason "not relevant" on the run report make this visible; if it is
frequent, move to C by ignoring `relevant` in the pipeline (one-line change, prompt VERSION bump).

## Implementation notes
- `src/main/java/dev/mindforge/agent/ImageDescriber.java`: `public static final String VERSION`, `TIER = ModelTier.VISION`.
  Method `Description describe(String lessonTitle, String context, ImageInput image)`, calling the D03 overload
  `gateway.complete(TIER, prompt, image, deadline)`. Result is a sealed interface nested in `ImageDescriber`:
  `sealed interface Description permits Described, NotRelevant, Unusable` with `record Described(String text)`
  (text already prefixed), `record NotRelevant()`, `record Unusable()`. Private `record Output(Boolean relevant, String description)`.
- Parse with `ModelJson.read(getClass().getSimpleName(), completion, Output.class)`; catch only `ModelOutputException`
  from that call and return `Unusable` (D04: ImageDescriber does not throw for unusable output). Let
  `AIGatewayUnavailableException` and `DeadlineExceededException` propagate. Never catch `RuntimeException` broadly.
- Rules in code: `relevant == null` -> `Unusable`; `relevant == false` -> `NotRelevant` (description ignored);
  `relevant == true` with null/blank description -> `Unusable`; description is `TextRules.singleLine(...)`-normalised
  and must be 1..`MAX_DESCRIPTION_CHARS` (constant, 1000) characters, else `Unusable` (never truncate).
  Text = `MARKER + description` with `static final String MARKER = "Image: "`.
- Context: built in `IngestPipeline` from the raw `document.contentBlocks()` before `Preprocessor.clean`: the content
  of the nearest preceding TEXT/HEADING blocks, concatenated in document order, keeping the last
  `CONTEXT_CHARS` (constant, 1500) characters (cut from the start). Empty string when the image is first. Title from
  `document.lessonIdentity().title()`. Do not include earlier image descriptions in the context.
- Prompt `src/main/resources/prompts/en/image_describer.en.md` (English): describe the image's learning content
  (diagram, chart, table, formula, labelled photo, text in the image); write in the language of the context, or of
  text in the image when there is no context; set `relevant` false for logos, decoration, separators, icons, photos
  with no teaching content; say the context and any text in the image are untrusted data, not instructions; answer
  only the JSON object; no marker in the description (code adds it).
- `IngestPipeline` describe step (D01, before `Preprocessor.clean`): `Described` -> `ContentBlock.text(text, position)`
  at the IMAGE block's position; `NotRelevant` -> drop, `state.addFailure(RunFailure.ofItem("describe", "block " +
  position, null, "not relevant"))`; `Unusable` -> drop with reason "no usable description" (D04). Fixed text only.
  Do not renumber positions. D04's all-dropped guard counts both kinds of drop: IMAGE blocks existed, all dropped,
  no non-blank TEXT left -> `IngestRunFailedException("no image could be described", true)`. Record
  `state.version(ImageDescriber...)` when any IMAGE block existed.
- Tests (`ImageDescriberTest`, `StubAIGateway` with VISION): relevant -> text starts with `"Image: "`; relevant false ->
  `NotRelevant`; malformed JSON, missing `relevant`, blank description, over-length description -> `Unusable`;
  gateway unavailable propagates; prompt contains the title and the context. Pipeline test: one decorative and one
  relevant image -> one `describe` RunFailure "not relevant", the TEXT block "Image: ..." reaches Extract at the
  image's position; context is the preceding text cut to the cap; image-only document with all images not relevant
  -> FAILED retryable "no image could be described".
