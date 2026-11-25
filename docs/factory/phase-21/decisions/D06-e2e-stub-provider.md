---
id: D06
question: Q06
status: decided
method: unanimous
class: adr
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q06.r1.tally.md]
adr: docs/adr/0042-e2e-model-answers-come-from-a-fake-openai-compatible-server.md
---
## Decision
Use B: the E2E stack gets deterministic model answers from a WireMock container that acts as an OpenAI-compatible
provider. Add a test-only compose override `compose.e2e.yml` with a `model-stub` service and point the app's
`OPENROUTER_BASE_URL` at it. Static JSON mappings answer `POST /v1/chat/completions` by the `# Task: ...` prompt
fragment. Do not change `src/main`: no `stub` Spring profile, no fake `AIGateway` in the JAR.

## Why
- The seam exists: `application.yml:49` reads `OPENROUTER_BASE_URL`; `web-security.md:83` treats it as trusted config.
- B runs the real image, Dockerfile, embedded SPA and the real `AIGatewayAdapter` (retry, circuit breaker, deadline,
  permits, tracing, `costUsd`). `ApiFlowTest` already covers the `StubAIGateway` path.
- The adapter makes plain non-streaming calls (`AIGatewayAdapter.java:99-101,161`), so one response shape is enough.
- Answers are static (`ApiFlowTest.java:447-453`), and the fragments in `Prompts.java` are plain headings, so WireMock
  `bodyPatterns: contains` routes them without templating.
- It fits plan 21.2 ("docker compose up") and D08 (compose-started stack, suite outside Maven).
- A ships a fake model in the product (`minimal-implementation.md:12-13`); C drops compose and conflicts with D08.

## Rejected
- A: puts a fake model in the product JAR, which needs a guard so it is never the default, and skips the real adapter.
- C: tests the test classpath, not the image; no compose; conflicts with D08 (suite outside Maven).
- Hand-written fake server (pragmatist D, architect B2, skeptic D): more code to own than declarative mappings; use
  it only if a journey step needs an answer that depends on the prompt.

## Dissent
None. Shared risk: mappings drift from the prompt resources. The run then fails and the E2E goes red without a precise
cause. The drift unit test and the dump of WireMock's unmatched requests below address this.

## Implementation notes
What D06 fixes: how model answers are produced, the compose override, the mapping files and their location, the
drift test, the ADR. What it leaves to D07: the browser runner (language, package.json or not), spec location and
the start/wait commands of the suite. D08 already fixes where and when it runs. D06 does not need any particular
runner: it works with any runner that drives `http://localhost:8080` after `docker compose ... up --wait`.
- `compose.e2e.yml` (repo root). Always use explicit files: `docker compose -f compose.yml -f compose.e2e.yml up -d
  --build --wait`. A plain `docker compose up` also merges `compose.override.yml`, and must never pick up the stub.
  - Service `model-stub`: `image: wiremock/wiremock:<pinned 3.x tag>`, volume
    `./src/test/e2e/model-stub:/home/wiremock:ro`, no published port.
  - `app.environment`: `OPENROUTER_BASE_URL: http://model-stub:8080/v1` (the openai-java SDK appends
    `/chat/completions`, `AIGatewayAdapter.java:21-22`), `OPENROUTER_API_KEY: e2e-not-a-key`,
    `AUTH_SECURE_COOKIES: "false"`, a throwaway `JWT_SECRET` of 32+ bytes; `app.depends_on: [model-stub]`.
    `compose.yml` does not pass `OPENROUTER_BASE_URL` today (compose.yml:24-34); add it only in the override.
- `src/test/e2e/model-stub/mappings/*.json`: one file per fragment the journey hits: GUARD, EXTRACT, WRITE, LINKS,
  SUPERSEDE, FLASHCARDS, QUIZ, GRADE (add SELECT and ANSWER only if the journey asks a Query). Each has
  `request: {method: POST, urlPath: /v1/chat/completions, bodyPatterns: [{contains: "# Task: ..."}]}` and
  `response: {status: 200, jsonBody: {id, object: "chat.completion", created, model: "e2e-stub",
  choices: [{index: 0, message: {role: assistant, content: "<answer JSON as a string>"}, finish_reason: stop}],
  usage: {prompt_tokens, completion_tokens, total_tokens}}}`. Ingest contents copy `ApiFlowTest.givenAnswers()`;
  flashcard, quiz and grading answers must pass the code validation of those services (check their parsers).
  Name the matching `Prompts` constant in each mapping (`metadata` field).
- `zz-unmatched.json`: lowest priority (e.g. `priority: 10`), any `POST`, returns 400 with body
  "no e2e stub answer for this prompt". A 4xx is not retried (`AiConfig.java:51`), so the run fails fast.
- On failure, the CI job (D08) dumps `GET /__admin/requests/unmatched` (through `docker compose exec` or
  `docker compose logs model-stub`) next to the report.
- `src/test/java/dev/mindforge/unit/infrastructure/ai/ModelStubMappingsTest.java` (plain JUnit 5 + AssertJ, no
  Spring): every mapping's `contains` fragment occurs in some `src/main/resources/prompts/**` resource, and every
  mapping except the catch-all returns parseable chat-completion JSON. Do not put it under a package named `e2e`:
  surefire excludes `**/e2e/**` (pom.xml:316) and failsafe would pick it up (pom.xml:332).
- `docs/project/deployment.md`: state that `compose.e2e.yml` is test-only and give the exact up command.
- No change to `src/main` and no change to `compose.yml` defaults.
