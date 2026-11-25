---
id: Q06
status: decided
decision: D06
impact: high
blocks: [W09, W10, W11]
---
## Question
How does the E2E stack get deterministic model answers: a stub `AIGateway` behind a Spring profile in the shipped JAR,
a fake OpenAI-compatible HTTP server the real adapter calls, or a test-side Spring context with the existing
`StubAIGateway`?

## Context
Plan 21.2: "Against `docker compose up` with a stub-provider profile". No such profile exists: `StubAIGateway`
(`src/test/java/dev/mindforge/support/StubAIGateway.java`) is test code and is not in the JAR or the Docker image.
`src/main/resources` has `application.yml`, `application-dev.yml`, `application-cli.yml`. The provider is
OpenAI-compatible (`spring.ai.openai.base-url: ${OPENROUTER_BASE_URL}`), and the journey needs answers that pass code
validation for every step (relevance guard, page selection, page writing, supersession, link check, flashcards, quiz,
grading). `ApiFlowTest.givenAnswers()` and `support/Prompts.java` already route answers by prompt fragment.
The choice decides whether production code gains a fake model, whether the real `AIGatewayAdapter` path (retries,
tracing, permits) is exercised, and how the E2E is started.

## Known options
- A: A `stub` Spring profile in `src/main/` with a canned-answer `AIGateway` bean (routing by prompt fragment); compose
  gets a `compose.e2e.yml` override that sets `SPRING_PROFILES_ACTIVE=stub`. Ships a fake in the product JAR; needs a
  guard so it cannot be the default.
- B: A small fake OpenAI-compatible server (a test-side container or script, or WireMock with JSON mappings) that
  answers `/chat/completions` by prompt fragment; compose override points `OPENROUTER_BASE_URL` at it. No production
  code change; exercises the real adapter.
- C: No compose: a `@SpringBootTest(RANDOM_PORT)` + Testcontainers PostgreSQL in `src/test/java/.../e2e/` with
  `StubAIGateway` as a bean, the SPA served from the built JAR classpath, and Playwright driving the browser. Reuses
  `givenAnswers()`; departs from the plan's "docker compose up".

## Constraints
- CLAUDE.md § Model Services: all LLM calls through `AIGateway`; never instantiate a provider SDK directly.
- CLAUDE.md § Testing: use `StubAIGateway` for deterministic responses; never mock at `ChatClient` level.
- `docs/standards/global/minimal-implementation.md`: no future stubs / speculative code in `src/main`.
- `docs/standards/security/web-security.md`: a production deployment must never answer with fake model output by
  default.
- A new runtime profile or a new container is a design choice worth an ADR (format of `docs/adr/0041-*.md`).
