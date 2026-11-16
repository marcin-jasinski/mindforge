---
id: P20-04
title: Register a CORS source that admits one origin from CORS_ALLOWED_ORIGIN and none by default
status: ready
plan_tasks: [20.1]
decisions: [D05]
depends_on: []
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
`SecurityConfig` registers a `CorsConfigurationSource` and calls `.cors(...)`. With `CORS_ALLOWED_ORIGIN` blank (the
default) no cross-origin caller is admitted and responses are the same as today. With it set, exactly that one origin
is admitted with credentials. A value with `*` or a comma fails startup.

## Context
- `docs/standards/security/web-security.md` § CORS Configuration and ADR 0008 (accepted) require a CORS configuration
  with origins from an environment variable, never `*`. No `CorsConfigurationSource` bean exists today.
- `src/main/java/dev/mindforge/api/config/SecurityConfig.java`: CSRF is off for `/api/**` (line ~40); the session cookie
  is `SameSite=Lax` (`JwtService`); `401` on `/api/**` without a valid cookie.
- `src/main/resources/application.yml` `mindforge.security` (line ~101: `jwt-secret`, `jwt-expiry-seconds`,
  `secure-cookies`).
- The app ships as one container with the SPA embedded; dev uses the Angular proxy (`frontend/proxy.conf.json`).
- ADR 0041 (`docs/adr/0041-cors-admits-one-origin-from-the-environment-and-none-by-default.md`) is already written; do
  not write a new ADR. Do **not** supersede ADR 0008 and do not edit it: that stays open for the human.
- Binding rules: CLAUDE.md (beans via `@Configuration`, constructor injection); unit tests without a Spring context.

## Decisions to apply
**D05 — Decision:** Use B: add a `CorsConfigurationSource` bean whose one allowed origin comes from
`CORS_ALLOWED_ORIGIN`, with no default. When the variable is blank, the source holds no configuration, so no origin is
admitted and behaviour is the same as today. When it is set, admit exactly that origin, with credentials. Never accept
`*`. Wire `.cors(...)` in `SecurityConfig`, document the variable, and change the default in `web-security.md` § CORS
from `http://localhost:4200` to "none".

**D05 — Implementation notes:**
- `application.yml`, under `mindforge.security`: `cors-allowed-origin: ${CORS_ALLOWED_ORIGIN:}` with a one-line
  comment (blank = no cross-origin caller; ADR 0041).
- `SecurityConfig`: add
  `@Bean CorsConfigurationSource corsConfigurationSource(@Value("${mindforge.security.cors-allowed-origin:}") String allowedOrigin)`.
  Trim the value. Blank -> return an empty `UrlBasedCorsConfigurationSource` (register NO configuration; do not
  register a config with an empty origin list, because that makes the CORS processor reject same-origin requests
  whose `Origin` it misreads behind a proxy with 403). Value containing `*` or a comma -> throw
  `IllegalStateException` at startup (fail fast; one origin only). Otherwise a `CorsConfiguration` with
  `setAllowedOrigins(List.of(origin))`, methods GET, POST, PUT, PATCH, DELETE, OPTIONS, allowed headers `*`,
  `setAllowCredentials(true)`, registered for `/**`. Add `.cors(Customizer.withDefaults())` to the filter chain.
- Tests: unit test of the bean (plain JUnit, no Spring context): blank -> `getCorsConfiguration(request)` is null;
  set -> allowed origins equal the value and credentials are true; `*` -> `IllegalStateException`. Integration test
  in `src/test/java/dev/mindforge/integration/api/` (MockMvc or RANDOM_PORT): preflight `OPTIONS /api/v1/auth/login`
  with `Origin: http://evil.example` gets no `Access-Control-Allow-Origin` when unset; with the property set to an
  origin, a preflight from that origin gets that exact `Access-Control-Allow-Origin` and
  `Access-Control-Allow-Credentials: true`, and a preflight from another origin does not.
- (`env.example`, `deployment.md` and the `web-security.md` § CORS text are P20-08.)

## Acceptance criteria
- [ ] AC1: `application.yml` has `mindforge.security.cors-allowed-origin: ${CORS_ALLOWED_ORIGIN:}` with a comment.
- [ ] AC2: Blank value: the source returns `null` for every request; a preflight `OPTIONS /api/v1/auth/login` with
  `Origin: http://evil.example` gets no `Access-Control-Allow-Origin` header.
- [ ] AC3: Set to `https://app.example`: a preflight from `https://app.example` gets
  `Access-Control-Allow-Origin: https://app.example` and `Access-Control-Allow-Credentials: true`; a preflight from
  another origin gets no `Access-Control-Allow-Origin`.
- [ ] AC4: A value containing `*` or a comma throws `IllegalStateException` when the bean is created.
- [ ] AC5: Same-origin flows are unchanged: `ApiFlowTest` stays green with the variable unset.

## Test plan
- AC2 (source), AC3 (source), AC4: unit, plain JUnit, call the bean method directly with `MockHttpServletRequest` —
  `src/test/java/dev/mindforge/unit/api/CorsConfigurationTest.java` (new; match existing unit package layout).
- AC2, AC3 (HTTP): integration, `src/test/java/dev/mindforge/integration/api/CorsTest.java` (new), Testcontainers;
  two contexts (unset, and set via `@TestPropertySource`).
- AC1, AC5: review; full gate `mvn -B verify -DskipFrontend=true`.

## Files likely touched
- `src/main/java/dev/mindforge/api/config/SecurityConfig.java`
- `src/main/resources/application.yml`
- `src/test/java/dev/mindforge/unit/api/CorsConfigurationTest.java` (new)
- `src/test/java/dev/mindforge/integration/api/CorsTest.java` (new)

## Out of scope
- Superseding or editing ADR 0008; changing `architecture.md` / `tech-stack.md` Vercel text (open for the human).
- More than one origin, wildcard patterns, a `localhost:4200` default.
- Docs (`env.example`, `deployment.md`, `web-security.md`) — P20-08. ADR 0041 exists.
- Any frontend change.

## Notes
