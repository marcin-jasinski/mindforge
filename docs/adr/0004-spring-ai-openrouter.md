# Spring AI with OpenRouter as the LLM gateway

MindForge uses Spring AI 1.x as its LLM integration layer, with OpenRouter
(`https://openrouter.ai/api/v1`) as the default provider in production and LM Studio
(`http://localhost:1234/v1`) for local development. Both are OpenAI-compatible endpoints,
so the same Spring AI client configuration applies to both; switching between them is a
single environment variable change (`SPRING_AI_OPENAI_BASE_URL`) with no code changes
required. Spring AI provides provider-agnostic abstractions for chat, structured output,
embeddings, and vector store integration.

## Considered Options

- **LangChain4j** — rejected: more verbose API; structured output is less ergonomic
  than Spring AI's `@StructuredOutput`; weaker integration with Spring's dependency
  injection and auto-configuration; smaller community in the Spring ecosystem.

- **Direct provider SDKs (OpenAI Java SDK, Anthropic SDK, etc.)** — rejected: locks
  the codebase to a single provider. Migrating between providers (e.g., GPT-4o to Claude)
  would require code changes across every agent. OpenRouter already aggregates all major
  providers behind a single API key.

- **LM Studio only (local-first)** — rejected: insufficient for production costs and
  quality; small local models cannot reliably perform synthesis, concept mapping, or
  quiz generation at acceptable quality. LM Studio is retained as a local development
  option when no API key is available.

## Consequences

- Three model tiers are defined — `SMALL` (fast/cheap: classification, guards),
  `LARGE` (synthesis, generation, quiz), `VISION` (image analysis) — and mapped to
  specific model identifiers in application configuration. Agents declare which tier
  they need; the AI gateway resolves the model name.
- Cost discipline rule: deterministic logic first → `SMALL` model → `LARGE` model last.
  Agents must not default to the large model for tasks that can be handled cheaply.
- Langfuse Cloud provides per-call observability (token counts, latency, cost) via
  Spring AI → Micrometer Tracing → OpenTelemetry export. Falls back to SLF4J logging
  when `LANGFUSE_PUBLIC_KEY` is absent, so Langfuse is never a hard runtime dependency.
- Adding a new LLM provider requires only a new Spring AI auto-configuration entry;
  the agent implementations do not change.
