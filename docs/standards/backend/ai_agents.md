# AI Agent Standards

## Agent Interface

All agents must implement the `Agent` interface:

```java
public interface Agent {
    String name();
    AgentCapability capability();
    AgentResult execute(AgentContext context);
}
```

Each agent class also declares version constants and a static `CAPABILITY`:

```java
public class SummarizerAgent implements Agent {
    public static final String VERSION = "1.0.0";
    public static final String PROMPT_VERSION = "v1";

    private static final AgentCapability CAPABILITY = AgentCapability.of(
        "summarizer",
        "Produces a structured lesson summary from raw document text",
        Set.of("raw_text"),
        "summary"
    );

    @Override public String name() { return CAPABILITY.name(); }
    @Override public AgentCapability capability() { return CAPABILITY; }
    @Override public AgentResult execute(AgentContext context) { ... }
}
```

## Version Management

Increment `VERSION` **only** when the agent's logic or prompt changes. Never bump for unrelated code style changes — this would invalidate all cached step checkpoints.

## Model Selection

Request models by role, never by provider string:

```java
// CORRECT
ChatResponse response = context.gateway().complete("large", messages);
ChatResponse response = context.gateway().complete("small", messages);
ChatResponse response = context.gateway().complete("vision", messages);

// NEVER
context.gateway().complete("openai/gpt-4o", messages);  // ❌ hardcoded provider
```

## LLM Gateway

All LLM calls flow through `AIGateway`. Never instantiate or call a provider SDK directly:

```java
// CORRECT
ChatResponse response = context.gateway().complete("large", messages);

// NEVER
OpenAiApi openAi = new OpenAiApi(apiKey);
openAi.chatCompletionEntity(request);  // ❌
```

## Prompt Files

Prompt files follow the pattern `{name}.{locale}.md`. Polish (`pl`) is the default locale. Every template must have at least a `.pl` baseline:

```
src/main/resources/prompts/pl/summarizer_system.pl.md   ✓
src/main/resources/prompts/en/summarizer_system.en.md   ✓ (when added)
src/main/resources/prompts/summarizer_system.md         ❌ (locale-neutral)
```

## Lesson Identity

Resolve `lessonId` via the five-step deterministic algorithm:
1. PDF frontmatter `lesson_id`
2. PDF frontmatter `title` (slugified)
3. PDF metadata `Title`
4. Filename (without extension)
5. **REJECT** — throw `LessonIdentityException`; never fall back to `"unknown"`

```java
// NEVER
String lessonId = metadata.getOrDefault("lesson_id", "unknown");  // ❌
```
