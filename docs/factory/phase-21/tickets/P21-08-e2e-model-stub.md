---
id: P21-08
title: Answer the E2E stack's model calls from a WireMock OpenAI-compatible stub in a test-only compose override
status: done
plan_tasks: [21.2]
decisions: [D06]
depends_on: [P21-02]
size: M
issue: 73
commit: e9ab579
fix_rounds: 0
---
## Goal
`docker compose -f compose.yml -f compose.e2e.yml up -d --build --wait` starts postgres, the real app image and a
pinned WireMock `model-stub` that answers every model call of the journey (ingest, flashcards, quiz, grading) with
deterministic chat-completion JSON. An unknown prompt gets a 400 so the run fails fast. A unit test catches mapping
drift from the prompt resources. `src/main` and `compose.yml` do not change.

## Context
- ADR `docs/adr/0042-e2e-model-answers-come-from-a-fake-openai-compatible-server.md` (Accepted) records this decision.
- `src/main/resources/application.yml:49`: `spring.ai.openai.base-url: ${OPENROUTER_BASE_URL:...}`. The adapter
  `src/main/java/dev/mindforge/infrastructure/ai/AIGatewayAdapter.java` makes non-streaming calls (~99–101, 161); the
  openai-java SDK appends `/chat/completions` to the base URL (~21–22). `AiConfig.java:51`: a 4xx is not retried.
- `compose.yml`: services `postgres`, `app` (built from `Dockerfile`, port 8080, healthcheck on
  `/actuator/health`); `app.environment` has `JWT_SECRET`, `AUTH_SECURE_COOKIES`, `OPENROUTER_API_KEY` but no
  `OPENROUTER_BASE_URL`. `compose.override.yml` (dev secret, plain-HTTP cookies) is merged only by a plain
  `docker compose up`.
- Prompt headings (`src/main/resources/prompts/en/*.en.md` line 1; constants in
  `src/test/java/dev/mindforge/support/Prompts.java`): GUARD `# Task: document relevance check`, EXTRACT
  `# Task: claim extraction`, WRITE `# Task: wiki page writing`, LINKS `# Task: link insertion`, SUPERSEDE
  `# Task: detection of out-of-date sections`, REVIEW `# Task: wiki review`, FLASHCARDS `# Task: flashcard writing`,
  QUIZ `# Task: quiz questions`, GRADE `# Task: answer grading`, SELECT, ANSWER, EDIT, DESCRIBE.
- Canned answers to copy: `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java` `givenAnswers()` (ingest),
  and the flashcard/quiz answers at ~198–202. Grading answer: check the parser of the quiz evaluator service in
  `src/main/java/dev/mindforge/agent/` for the required JSON shape.
- Surefire excludes `**/e2e/**` and failsafe includes `**/e2e/**/*Test.java` (`pom.xml` ~316, ~332): the Java unit
  test must not sit in a package named `e2e`.
- Checkstyle (P21-02) runs in `validate`: the new test must pass it.

## Decisions to apply
**D06 — Decision:** the E2E stack gets deterministic model answers from a WireMock container that acts as an
OpenAI-compatible provider. Add a test-only compose override `compose.e2e.yml` with a `model-stub` service and point
the app's `OPENROUTER_BASE_URL` at it. Static JSON mappings answer `POST /v1/chat/completions` by the `# Task: ...`
prompt fragment. Do not change `src/main`: no `stub` Spring profile, no fake `AIGateway` in the JAR.

**Implementation notes:**
- `compose.e2e.yml` (repo root). Always use explicit files: `docker compose -f compose.yml -f compose.e2e.yml up -d
  --build --wait`. A plain `docker compose up` also merges `compose.override.yml`, and must never pick up the stub.
  - Service `model-stub`: `image: wiremock/wiremock:<pinned 3.x tag>`, volume
    `./src/test/e2e/model-stub:/home/wiremock:ro`, no published port.
  - `app.environment`: `OPENROUTER_BASE_URL: http://model-stub:8080/v1`, `OPENROUTER_API_KEY: e2e-not-a-key`,
    `AUTH_SECURE_COOKIES: "false"`, a throwaway `JWT_SECRET` of 32+ bytes; `app.depends_on: [model-stub]`.
    `compose.yml` does not pass `OPENROUTER_BASE_URL` today; add it only in the override.
- `src/test/e2e/model-stub/mappings/*.json`: one file per fragment the journey hits: GUARD, EXTRACT, WRITE, LINKS,
  SUPERSEDE, FLASHCARDS, QUIZ, GRADE (add SELECT and ANSWER only if the journey asks a Query). Each has
  `request: {method: POST, urlPath: /v1/chat/completions, bodyPatterns: [{contains: "# Task: ..."}]}` and
  `response: {status: 200, jsonBody: {id, object: "chat.completion", created, model: "e2e-stub",
  choices: [{index: 0, message: {role: assistant, content: "<answer JSON as a string>"}, finish_reason: stop}],
  usage: {prompt_tokens, completion_tokens, total_tokens}}}`. Ingest contents copy `ApiFlowTest.givenAnswers()`;
  flashcard, quiz and grading answers must pass the code validation of those services (check their parsers).
  Name the matching `Prompts` constant in each mapping (`metadata` field). If the ingest pipeline also calls another
  prompt (for example REVIEW), add a mapping for it in the same way.
- `zz-unmatched.json`: lowest priority (e.g. `priority: 10`), any `POST`, returns 400 with body
  "no e2e stub answer for this prompt". A 4xx is not retried, so the run fails fast.
- On failure, the CI job (P21-11) dumps `GET /__admin/requests/unmatched` next to the report.
- `src/test/java/dev/mindforge/unit/infrastructure/ai/ModelStubMappingsTest.java` (plain JUnit 5 + AssertJ, no
  Spring): every mapping's `contains` fragment occurs in some `src/main/resources/prompts/**` resource, and every
  mapping except the catch-all returns parseable chat-completion JSON (and its `content` parses as JSON).
- `docs/project/deployment.md`: state that `compose.e2e.yml` is test-only and give the exact up command.
- No change to `src/main` and no change to `compose.yml` defaults.

## Acceptance criteria
- [x] AC1: `compose.e2e.yml` exists as specified (pinned WireMock 3.x tag, read-only volume, no published port, the four `app.environment` values, `depends_on: [model-stub]`). `compose.yml`, `compose.override.yml` and `src/main/**` are unchanged.
- [x] AC2: `src/test/e2e/model-stub/mappings/` holds one mapping per journey prompt (at least GUARD, EXTRACT, WRITE, LINKS, SUPERSEDE, FLASHCARDS, QUIZ, GRADE), each with a `metadata` naming its `Prompts` constant, plus `zz-unmatched.json` (priority 10, 400, body "no e2e stub answer for this prompt").
- [x] AC3: The quiz answer's `referenceAnswer` is a distinct marker string (for example `E2E-SECRET-REFERENCE-ANSWER`) so the journey can assert it never appears in the DOM.
- [x] AC4: `ModelStubMappingsTest` passes; it fails if a mapping's fragment is not in any prompt resource (proved by a test on a `@TempDir` mapping, or by a hand-made bad mapping recorded in Notes), and it fails if a non-catch-all response is not valid chat-completion JSON.
- [x] AC5: Manual stack check (Docker is available on the factory host): with `docker compose -f compose.yml -f compose.e2e.yml up -d --build --wait`, a register + upload of a small markdown file through the API (curl with the cookie jar) reaches run status `COMPLETED`, and `docker compose -f compose.yml -f compose.e2e.yml exec model-stub wget -qO- http://localhost:8080/__admin/requests/unmatched` shows no unmatched request. Then `down -v`. Record the commands and result in Notes.
- [x] AC6: `docs/project/deployment.md` states `compose.e2e.yml` is test-only and gives the exact up and down commands.
- [x] AC7: `mvn -B verify -DskipFrontend=true` is green.

## Test plan
- Unit: `src/test/java/dev/mindforge/unit/infrastructure/ai/ModelStubMappingsTest.java` (AC2, AC4), reads files with `Path.of("src/test/e2e/model-stub/mappings")`, parses JSON with the Jackson already on the test classpath, AssertJ.
- Manual stack run (AC5); the browser specs come in P21-09/P21-10.

## Files likely touched
- `compose.e2e.yml` (new)
- `src/test/e2e/model-stub/mappings/*.json` (new)
- `src/test/java/dev/mindforge/unit/infrastructure/ai/ModelStubMappingsTest.java` (new)
- `docs/project/deployment.md`

## Out of scope
- Any `src/main` change, a `stub` profile, a fake `AIGateway` in the JAR, a hand-written fake server.
- Response templating or prompt-dependent answers.
- Playwright files (P21-09) and the CI job (P21-11).

## Notes
- Decided: pin `wiremock/wiremock:3.13.2` because it is the newest 3.x tag pulled on the factory host.
- Decided: add `COPY config ./config` to the `Dockerfile` backend stage because P21-02 put Checkstyle in `validate` and the image build failed with "Unable to find suppressions file config/checkstyle/suppressions.xml"; without it AC5 cannot build the image. No other Dockerfile change.
- Decided: the grading answer has English feedback ("Almost right.") and the quiz `referenceAnswer` is `E2E-SECRET-REFERENCE-ANSWER`; the other answers copy `ApiFlowTest`. REVIEW, SELECT and ANSWER have no mapping because ingest does not call REVIEW (`ApiFlowTest` would fail on the unmatched prompt) and the journey asks no Query.
- Decided: AC4 is proved by three `@TempDir` tests in `ModelStubMappingsTest` (unknown fragment, response without chat-completion fields, content that is not JSON).
- AC5 run (project `-p mf-e2e-p2108`): `docker compose -p mf-e2e-p2108 -f compose.yml -f compose.e2e.yml up -d --build --wait` -> all three services healthy. curl with a cookie jar: `POST /api/v1/auth/register` 201, `POST /api/v1/knowledge-bases`, multipart upload of `# Mitosis\n\nMitosis is cell division.\n` -> `latestRun.status` COMPLETED, index has `concepts/mitosis` and `sources/biology`. Also `POST flashcards/refresh` -> 1 card, `POST quiz-sessions` -> `next` gives "What does mitosis mean?", answer -> score 4. `exec model-stub wget -qO- http://localhost:8080/__admin/requests/unmatched` -> `"requests" : [ ]`; journal: 9 requests, all 200. An unknown prompt POST returns 400 from `zz-unmatched.json`. Then `down -v`.
- Tester r1 AC map: AC1 compose.e2e.yml (config parses); AC2 mappings + ModelStubMappingsTest; AC3 quiz.json marker; AC4 ModelStubMappingsTest 3 @TempDir tests; AC5 implementer hand-run; AC6 deployment.md; AC7 gate green.
