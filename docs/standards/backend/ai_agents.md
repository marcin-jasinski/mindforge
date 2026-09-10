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

    public ClaimSet extract(List<ContentBlock> blocks, String renderedIndex) { ... }
}
```

- Inputs and outputs are typed domain values (`ClaimSet`, `PageDraft`, `List<LinkInsertion>`), never a shared mutable context.
- Model services never call each other; the owning application service sequences them.
- A deterministic step (e.g. `Preprocessor`, Resolve) is plain code, not a model service with a prompt.

## Version Management

Each model service declares `public static final String VERSION`. Increment it **only** when the service's logic or prompt changes — never for style fixes.

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

Every call is single-shot. There is no multi-turn tool loop inside a call.

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

- **The model proposes content; code owns identity, membership and deletion.** Page type, path, sources and supersession are assigned by code — never parsed out of model prose.
- **Typed outputs, verified.** A proposed target path must exist before a claim revises it. A `LinkInsertion` is applied only if its phrase is present and the text is unchanged once links are stripped. A body containing frontmatter or a `# Citations` section is rejected.
- **Never trust a model's report of what it did.** Pages written are counted from the rows the transaction inserted.
- **Fail loudly, never truncate.** Over the claim cap, the run fails; it does not silently process the first N.

```java
// NEVER
PageType type = PageType.of(parseFrontmatter(completion).get("type"));  // ❌ model-asserted metadata
```

## Lesson Identity

Resolve `lessonId` via the five-step deterministic algorithm:
1. Markdown frontmatter `lesson_id`
2. Markdown frontmatter `title` (slugified)
3. PDF metadata `Title`
4. Filename (without extension)
5. **REJECT** — throw `LessonIdentityException`; never fall back to `"unknown"`

Slugification is the same transliterating function used for page paths (NFD, strip combining marks, `ł→l` and friends, `[a-z0-9-]`). `index`, `log` and `default` are reserved.

```java
// NEVER
String lessonId = metadata.getOrDefault("lesson_id", "unknown");  // ❌
```
