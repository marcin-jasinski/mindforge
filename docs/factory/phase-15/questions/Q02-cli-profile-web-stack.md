---
id: Q02
status: decided
decision: D02
impact: medium
blocks: [W01]
---
## Question
Does the `cli` profile start the embedded web server and the web/security beans, and if not, how are web-only beans
kept out of the CLI context?

## Context
- Plan: "`CommandLineRunner` under `--spring.profiles.active=cli`"; checklist: "via `java -jar mindforge.jar
  --spring.profiles.active=cli`". Nothing says whether the HTTP server starts.
- If it starts, it binds `${PORT:8080}` — the same port as a live server on the host — and exposes the API from a
  short-lived process.
- With `spring.main.web-application-type: none`, `api/config/SecurityConfig` (`@EnableWebSecurity`, `HttpSecurity`
  bean) and the MVC configs (`SpaConfig`, `RateLimitConfig`, `ApiDocsConfig`) may fail or be meaningless. There is no
  `@Profile` anywhere in the codebase today.
- `SseProgressNotifier` imports `SseEmitter` but works as a plain object without a server.

## Known options
- A: `application-cli.yml` sets `spring.main.web-application-type: none`; web-only `@Configuration` classes get
  `@Profile("!cli")` (or a `@ConditionalOnWebApplication`).
- B: Keep the web stack; `application-cli.yml` sets `server.port: 0` (random) so it does not clash; runners exit the
  context when done.
- C: `MindForgeApplication.main` picks web type from the args before boot (no profile annotations).

## Constraints
- All beans wired via `@Configuration`, no static singletons (CLAUDE.md).
- Entry point must remain `java -jar mindforge.jar --spring.profiles.active=cli` (plan checklist).
- Minimal implementation (docs/standards/global/minimal-implementation.md).
