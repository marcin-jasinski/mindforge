# E2E model answers come from a fake OpenAI-compatible server

Status: Accepted (2026-11-25).

The browser E2E runs against the real Docker image started with
`docker compose -f compose.yml -f compose.e2e.yml up`. The test-only override `compose.e2e.yml` adds a `model-stub`
service, a pinned WireMock image that loads static JSON mappings from `src/test/e2e/model-stub/`, and sets the app's
`OPENROUTER_BASE_URL` to it. Each mapping answers `POST /v1/chat/completions` when the request body contains one
`# Task: ...` prompt heading, with an OpenAI chat-completion body whose content is the canned answer. A catch-all
mapping returns 400 so that an unknown prompt fails the run at once. The product has no fake model: `src/main` gains no
`stub` profile and no canned-answer `AIGateway`. The E2E therefore exercises the real `AIGatewayAdapter` path (retry,
circuit breaker, deadline, permits, tracing, cost).

## Considered Options

- **A `stub` Spring profile with a canned-answer `AIGateway` in `src/main`**: ships test code in the product JAR, needs
  a guard so that a deployment can never answer with fake output, and skips the real adapter.
- **`@SpringBootTest` with the test `StubAIGateway` and Playwright in the JVM, no compose**: tests the test classpath,
  not the image, and conflicts with the decision that the suite runs outside Maven (D08). `ApiFlowTest` already
  covers the stubbed-gateway path.
- **A hand-written fake server instead of WireMock**: more code to own. It stays the fallback if a journey step ever
  needs an answer that depends on the prompt.

## Consequences

- The mapping fragments duplicate the prompt headings. A unit test checks that every fragment still occurs in a prompt
  resource, so a renamed heading fails `mvn test` before the E2E runs.
- `compose.yml` and its defaults do not change. A plain `docker compose up` never starts the stub; the override must be
  named with `-f`.
- The E2E depends on the openai-java request path (`<base-url>/chat/completions`) and the non-streaming response
  shape. A move to streaming or tool calls in `AIGatewayAdapter` must update the mappings.
- Removing it is one compose file, one mapping directory and one unit test.

Decided in [D06](../factory/phase-21/decisions/D06-e2e-stub-provider.md) by the dark-factory run `phase-21`.
