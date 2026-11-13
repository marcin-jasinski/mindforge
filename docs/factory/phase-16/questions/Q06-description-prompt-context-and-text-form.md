---
id: Q06
status: decided
decision: D06
impact: medium
blocks: [W05]
---
## Question
What does `ImageDescriber` send with each image, and what exact text does the replacing TEXT block hold
(including what happens when the model judges the image to carry no learning content)?

## Context
- Plan 16.2: "replaces the block with a TEXT block holding the description". Nothing about context or form.
- `ai_agents.md` § Prompt Files: a prompt that makes content tells the model to detect the language and write in it;
  claims and pages are in the language of their source. An image has no language of its own; without some document
  text the description will default to English inside, e.g., a Polish lesson.
- `ai_agents.md`: every rule the prompt teaches is enforced in code; model output is read with `ModelJson` in other
  services (`RelevanceGuard`), with `ModelOutputException` on a bad shape.
- Extract (`ClaimExtractor`) renders blocks as `[position] text`; a description that does not say it describes an
  image could be read as the author's own prose. A marker such as "Image: …" or "[Figure] …" makes provenance clear.
- Decorative images (logos, separators) waste Extract input if described.

## Known options
- A: Prompt gets the lesson title and the text of the nearest preceding block(s) as language and topic context;
  output JSON `{ "relevant": bool, "description": "..." }`; code drops the block when `relevant` is false (counted
  on the run) and prefixes the kept text with a fixed marker (e.g. `Image: `).
- B: Prompt gets the lesson title only; plain-text output; the whole completion (stripped) becomes the block content,
  no marker, no drop.
- C: As A but no drop: decorative images get a one-line description.

## Constraints
- English prompt file `src/main/resources/prompts/en/image_describer.en.md` (`ai_agents.md`).
- Code enforces what the prompt teaches: e.g. non-blank description, a length bound, single-shot (`ai_agents.md`).
- Model output never decides page type, path, title or membership (`CLAUDE.md` Model Services).
