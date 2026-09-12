# Model Service Standards

Every LLM call in MindForge is made by a **model service**: a stateless, concrete `@Service`-style class in `dev.mindforge.agent`, wired in `@Configuration`, with a concrete method signature. There is no `Agent` interface, no `AgentContext`, no registry and no capability descriptor (ADR 0013).

## Model Services

```java
package dev.mindforge.agent;

public class ClaimExtractor {

    public static final String VERSION = "1";

    private final AIGateway gateway;
    private final PromptLoader prompts;

    public ClaimExtractor(AIGateway gateway, PromptLoader prompts) { ... }

    public ExtractResult extract(List<ContentBlock> chunk, String renderedIndex, List<PlannedPage> plannedPages) { ... }

    public List<EditItem> extractEdit(String instruction, String quotedAnswer, String renderedIndex) { ... }
}
```

- Inputs and outputs are typed domain values (`ExtractResult`, `EditItem`, `PageDraft`, `List<LinkInsertion>`, `List<SupersessionProposal>`), never a shared mutable context.
- Model services never call each other; the owning application service sequences them.
- A deterministic step (e.g. `Preprocessor`, Resolve) is plain code in `dev.mindforge.application`, not a model service with a prompt. It has no `VERSION` and is not recorded in `step_versions`.

## Version Management

Each model service declares `public static final String VERSION`. Increment it **only** when the service's logic or one of its prompts changes — never for style fixes. A service with two prompts (`ClaimExtractor`: documents and edits) has one `VERSION` covering both.

`VERSION` and the resolved model id of every call are recorded on the run in `ingest_runs.step_versions`. That is how "which prompt wrote this page?" is answered: join the page revision to its run.

## Model Selection

Request models by role, never by provider string:

```java
// CORRECT
CompletionResult r = gateway.complete(ModelTier.LARGE, prompt, DeadlineProfile.BACKGROUND);
CompletionResult r = gateway.complete(ModelTier.SMALL, prompt, DeadlineProfile.INTERACTIVE);

// NEVER
gateway.complete("openai/gpt-4o", prompt);  // ❌ hardcoded provider
gateway.complete("large", prompt);          // ❌ string literal, use ModelTier enum
```

Every call is single-shot. There is no multi-turn tool loop inside a call. A deadline applies per call, never per run. Calls made by an `INGEST` or `LINT` run take a permit from the global background semaphore first.

## LLM Gateway

All LLM calls flow through `AIGateway`. Never instantiate or call a provider SDK directly:

```java
// CORRECT
CompletionResult r = gateway.complete(ModelTier.LARGE, prompt, DeadlineProfile.BACKGROUND);

// NEVER
OpenAiApi openAi = new OpenAiApi(apiKey);
openAi.chatCompletionEntity(request);  // ❌
```

`AIGateway` exposes chat completion only — there are no embeddings (ADR 0016).

## Prompt Files

Prompt files follow the pattern `{name}.{locale}.md`. Polish (`pl`) is the default locale. Every template must have at least a `.pl` baseline:

```
src/main/resources/prompts/pl/claim_extractor.pl.md   ✓
src/main/resources/prompts/en/claim_extractor.en.md   ✓ (when added)
src/main/resources/prompts/claim_extractor.md         ❌ (locale-neutral)
```

## Every Rule Stated in the Prompt Is Enforced in Code

A prompt *teaches* a rule so the model does not have to learn it by trial; code *enforces* it so a model that ignores the prompt cannot corrupt the wiki.

- **The model proposes content; code owns identity, membership and deletion.** Page type, path, title, sources and supersession are assigned by code — never parsed out of model prose. `PageDraft` carries a description and a body, and no title.
- **Typed outputs, verified.**
  - A claim revises only a live Concept or a page planned earlier in the run; any other target becomes a create from the claim's title. Claims are grouped into one task per path.
  - `Delete` and `Retitle` exist only in edit output and apply only to a live Concept.
  - A draft is rejected if its body carries frontmatter, a `# Citations` section, a level-1 heading repeating the title, a page link that is not `/(concepts|sources)/<id>.md(#<anchor>)?`, an image or a reference definition — and, for a document ingest revising a Concept, if it drops an existing section anchor.
  - Titles and descriptions pass `TextRules`: single line, 1–200 and 1–300 characters.
  - A `LinkInsertion` is applied only to an eligible span, only to a live or successfully drafted target, and only if the text is unchanged once links are stripped.
  - A supersession proposal is kept only if it names a section the detector was shown and a Concept this run wrote.
- **One parser.** Headings, sections, anchors and links are read only through `MarkdownStructure`.
- **Never trust a model's report of what it did.** Pages written are counted from the rows the transaction inserted.
- **Fail loudly, never truncate.** Over the claim cap for one Extract call, or the page-task cap for one run, the run fails; it does not silently process the first N. Where an input must be bounded and omission is acceptable (Supersede candidates, a writer's source blocks), the omission is counted on the run.

```java
// NEVER
PageType type = PageType.of(parseFrontmatter(completion).get("type"));  // ❌ model-asserted metadata
```

## Lesson Identity

Resolve `lessonId` via the five-step deterministic algorithm:
1. Markdown frontmatter `lesson_id` — must already match the identifier grammar; never rewritten
2. Markdown frontmatter `title` (slugified)
3. PDF metadata `Title` (slugified)
4. Filename (without extension, slugified)
5. **REJECT** — throw `LessonIdentityException`; never fall back to `"unknown"`

An upload may override the id with an explicit `lessonId`, validated the same way.

The grammar is `Identifier`'s, shared with page names and section anchors: `[a-z0-9]+(-[a-z0-9]+)*`, 1–80 characters. `index` and `log` are reserved everywhere; `default` and `conversation` are reserved for lesson ids. A derived id that lands on a reserved word gains `-lesson`; an explicit one is rejected.

`Identifier.slugify`: NFC; letters that NFD-decompose to ASCII keep their base; `ł→l`, `ø→o`, `ß→ss`, `đ→d`, `æ→ae`, `œ→oe`, `þ→th`; Greek letters by their Polish names; any other letter or digit is dropped **and** the result gains an 8-hex SHA-256 suffix (`x-<hex>` if nothing is left); lowercase, `-` separators, capped at 80 with the suffix kept.

A resolved lesson id that already has a document in the knowledge base is a new version only when the upload says `newVersion`; otherwise it is rejected with 409.

```java
// NEVER
String lessonId = metadata.getOrDefault("lesson_id", "unknown");  // ❌
```
