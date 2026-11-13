---
id: P15-01
title: Boot the cli profile without the web stack and without the run sweep
status: ready
plan_tasks: [15.1, 15.2]
decisions: [D02, D01]
depends_on: []
size: M
issue: 2
commit:
fix_rounds: 0
---
## Goal
`java -jar mindforge.jar --spring.profiles.active=cli` starts a non-web Spring context: no embedded server, no
security filter chain, no API adapter beans. The application services and `RunWorker` exist, but nothing triggers the
worker: no startup sweep, no periodic sweep, no AFTER_COMMIT drain. A CLI process therefore never touches the runs of
a live server. No command runner exists yet (later tickets add them).

## Context
- Only `src/main/resources/application.yml` and `application-dev.yml` exist. No `@Profile` is used anywhere.
  `src/main/java/dev/mindforge/MindForgeApplication.java` is a plain `SpringApplication.run`; do not change it.
- Web-only configuration in `src/main/java/dev/mindforge/api/config/`: `SecurityConfig` (`@EnableWebSecurity`; needs
  `HttpSecurity`, which a non-web context does not provide, so it must be gated or the cli context fails),
  `SpaConfig`, `RateLimitConfig`, `ApiDocsConfig` (all `@Configuration`) and `GlobalExceptionHandler` (advice).
  `JwtFilter`, `RateLimitInterceptor` and `OAuthLoginSuccessHandler` are not components; they are created inside
  those configurations and disappear with them. Verify this before you annotate them.
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java` declares `RunWorker runWorker(...)` and
  `RunWorkerTriggers runWorkerTriggers(RunWorker, Executor)`. `RunWorkerTriggers`
  (`infrastructure/event/RunWorkerTriggers.java`) drains after the commit of `IngestRunQueued`, sweeps on
  `ApplicationReadyEvent`, sweeps every `mindforge.runs.sweep-interval` (PT1M) and calls `RunWorker.stop()` on
  `ContextClosedEvent`. `RunWorker.sweep` fails every RUNNING run not in this JVM's in-memory set
  (`docs/project/deployment.md#one-live-instance`, ADR-0015).
- Binding rules: hexagonal layering (`docs/standards/architecture/hexagonal.md`), beans wired in `@Configuration`,
  constructor injection, minimal implementation (`docs/standards/global/minimal-implementation.md`), testing standard
  (`docs/standards/testing/test-writing.md`).
- Integration tests extend `src/test/java/dev/mindforge/support/TestContainerBase.java` (PostgreSQL 15
  Testcontainer, `StubAiConfig`, sets `mindforge.runs.sweep-interval=PT1H`), are `@Tag("integration")`, and use
  `@SpringBootTest`. Existing tests such as `integration/application/IngestRunTest` use
  `webEnvironment = NONE`; they must stay green after the web beans become conditional.

## Decisions to apply
**D02 — Decision:** The `cli` profile does not start the embedded web server. Add
`src/main/resources/application-cli.yml` with `spring.main.web-application-type: none`. Put
`@ConditionalOnWebApplication(type = SERVLET)` on the web-only beans so that they do not load in the non-web CLI
context. Do not add `@Profile` annotations to them, and do not change `MindForgeApplication.main`.

**D02 — Implementation notes:**
- New `application-cli.yml`: `spring.main.web-application-type: none`. Add other keys only if needed (for example
  `spring.main.banner-mode: off`).
- Add `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)` to the web-only classes in
  `dev.mindforge.api.config`: `SecurityConfig`, `SpaConfig`, `RateLimitConfig`, `ApiDocsConfig`,
  `GlobalExceptionHandler` (and `JwtFilter`, `RateLimitInterceptor`, `OAuthLoginSuccessHandler` only if they are beans
  in their own right).
- Controllers (`dev.mindforge.api.controller`): do not annotate them first. Add the same condition only if the CLI
  context-load test fails because of them. If many classes need it, add one condition per package-level config, not
  class by class. (`RunController` is handled in P15-02.)
- Do not change `SseProgressNotifier` in this ticket; it works as a plain bean without a server.
- Test: an integration test (Testcontainers PostgreSQL, `StubAIGateway`) boots the context with
  `@ActiveProfiles("cli")` and asserts that the context is not a `WebApplicationContext`, that there is no
  `SecurityFilterChain` bean, and that the application beans (for example `IngestionService`) are present.
- Keep the existing web context tests green. No change to default-profile behaviour.

**D01 — Decision (part applied here):** Put `@Profile("!cli")` on the `runWorkerTriggers` bean method in
`PipelineConfig` so that a `cli` profile has `RunWorker` but no trigger (no startup sweep, no periodic sweep, no
AFTER_COMMIT drain). Keep `RunWorker` in all profiles; `@EnableScheduling` stays. Test: start a `cli`-profile context
with a RUNNING run row of "another process"; assert it is still RUNNING after the context is ready (no sweep).
(The runner's explicit `drain`/wait and `RunWorker.stop()` are P15-06.)

## Acceptance criteria
- [ ] AC1: `src/main/resources/application-cli.yml` exists and sets `spring.main.web-application-type: none`.
- [ ] AC2: In a context with profile `cli`, the context is not a `WebApplicationContext`, there is no
      `SecurityFilterChain` bean, and `IngestionService`, `QuizService`, `KnowledgeBaseService`, `AccountService` and
      `RunWorker` beans exist.
- [ ] AC3: In a context with profile `cli`, there is no `RunWorkerTriggers` bean.
- [ ] AC4: A RUNNING ingest run row inserted by the test (not in this JVM's active set) is still RUNNING after the
      `cli` context is ready and after a short sweep interval (the test sets `mindforge.runs.sweep-interval` to a few
      seconds or less and waits longer than it).
- [ ] AC5: In the default profile, `RunWorkerTriggers`, `SecurityFilterChain` and the API config beans still exist;
      all existing tests stay green (baseline failure `ApiFlowTest.theLoginRouteIsTheSinglePageApp` excepted).
- [ ] AC6: `MindForgeApplication` is unchanged; no `@Profile` annotation is added to `dev.mindforge.api` classes.

## Test plan
- AC1–AC4: integration test `src/test/java/dev/mindforge/integration/cli/CliProfileContextTest.java` (new),
  `@Tag("integration")`, `@SpringBootTest`, `@ActiveProfiles("cli")`, extends `TestContainerBase`. Override the sweep
  interval for this class only (a subclass `@DynamicPropertySource` or equivalent) for AC4; seed the KB, document and
  RUNNING run with `TestFixtures`/the repositories. AssertJ only.
- AC5: the existing integration suite (`integration/api/*`, `integration/application/*`) run by
  `mvn -B verify -DskipFrontend=true`.

## Files likely touched
- `src/main/resources/application-cli.yml` (new)
- `src/main/java/dev/mindforge/api/config/SecurityConfig.java`
- `src/main/java/dev/mindforge/api/config/SpaConfig.java`
- `src/main/java/dev/mindforge/api/config/RateLimitConfig.java`
- `src/main/java/dev/mindforge/api/config/ApiDocsConfig.java`
- `src/main/java/dev/mindforge/api/config/GlobalExceptionHandler.java`
- `src/main/java/dev/mindforge/infrastructure/config/PipelineConfig.java`
- `src/test/java/dev/mindforge/integration/cli/CliProfileContextTest.java` (new)

## Out of scope
- Any `CommandLineRunner`/`ApplicationRunner`, the `dev.mindforge.cli` package, `ConsoleProgressNotifier` (P15-02+).
- Changing `SseProgressNotifier`, `RunController` or the `ProgressNotifier` parameter types (P15-02).
- A heartbeat lease or any coordination between processes (out of phase scope).
- Changing `MindForgeApplication.main`.

## Notes
