---
id: D02
question: Q02
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q02.r1.tally.md]
adr:
---
## Decision
Use A. The `cli` profile does not start the embedded web server. Add `src/main/resources/application-cli.yml` with `spring.main.web-application-type: none`. Put `@ConditionalOnWebApplication(type = SERVLET)` on the web-only beans so that they do not load in the non-web CLI context. Do not add `@Profile` annotations, and do not change `MindForgeApplication.main`.

## Why
- The plan requires `java -jar mindforge.jar --spring.profiles.active=cli` (implementation-plan.md:1447,1455). A profile yml is the minimum mechanism that obeys this contract.
- With B, the CLI binds a port (application.yml:68 `${PORT:8080}`, or a random port) and serves the full authenticated API from a batch process. This is attack surface that has no purpose.
- With C, the boot logic goes into `main()` and duplicates Spring's profile handling.
- `@ConditionalOnWebApplication` names the real condition (no servlet context), so the classes do not need to know the `cli` profile name. Two of the three ballots preferred it.
- Spring web security does not make an `HttpSecurity` bean in a non-web context. Thus `SecurityConfig` (`@EnableWebSecurity`, SecurityConfig.java:25) must be gated, or the CLI context fails to start.
- Class `local`: this is configuration-only. It does not change auth behavior on any served surface (the CLI serves no HTTP), the data model or a dependency.

## Rejected
- B: starts the HTTP server and security stack in a short-lived process, which exposes the API (verified hard objection).
- C: hides the boot mode in `main()` and goes around the required profile entry point (verified hard objection).

## Dissent
None. All three chose A. The risk the skeptic identified: component-scanned controllers or advice can depend on beans that the gate removes. Then the CLI context fails at startup. The CLI context-load test must catch this at build time.

## Implementation notes
- New `src/main/resources/application-cli.yml`: `spring.main.web-application-type: none`. Add other keys only if needed (for example, `spring.main.banner-mode: off`).
- Add `@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)` to these classes in `dev.mindforge.api.config`: `SecurityConfig`, `SpaConfig`, `RateLimitConfig`, `ApiDocsConfig`, `JwtFilter`, `RateLimitInterceptor`, `GlobalExceptionHandler`, `OAuthLoginSuccessHandler` (if it is a bean).
- Controllers (`dev.mindforge.api.controller`): do not annotate them first. Add the same condition only if the CLI context-load test fails because of them. If many classes need it, add one condition per package-level config. Do not annotate class by class.
- Do not change `SseProgressNotifier`. It works as a plain bean without a server.
- Test: add an integration test (Testcontainers PostgreSQL, `StubAIGateway`) that boots the context with `@ActiveProfiles("cli")`. The test must assert that the context is not a `WebApplicationContext`, that there is no `SecurityFilterChain` bean, and that the application beans (for example, the ingest application service) are present. Make sure the CLI `CommandLineRunner` does not run during this test. For example, the runner can do nothing when its required args are absent.
- Keep the existing web context tests green. No change is permitted to the default-profile behavior.
