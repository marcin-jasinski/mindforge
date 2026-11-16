---
id: P20-01
title: Limit sign-in and sign-up attempts per client address with a Caffeine fixed window
status: ready
plan_tasks: [20.3, 20.4]
decisions: [D01, D03]
depends_on: []
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
`POST /api/v1/auth/login` and `POST /api/v1/auth/register` share one Caffeine fixed-window allowance per client
address (`request.getRemoteAddr()`), default 10 per minute, set by `mindforge.rate-limits.auth.*`. When the allowance
is spent, the request gets `429` with `Retry-After`, `RateLimit-*` headers and code `RATE_LIMITED`. Allowed requests
carry `RateLimit-Limit/Remaining/Reset`. This closes plan task 20.3 and the 20.4 case "rate limit triggers".

## Context
- The per-user model-call limiter from R09 exists and must not change behaviour:
  `src/main/java/dev/mindforge/api/config/RateLimitInterceptor.java` (Caffeine, fixed window, keyed by
  `CurrentUser.userId()`, POST only, skips anonymous requests), `api/config/RateLimitConfig.java` (registers it on the
  model-calling paths only), `api/config/RateLimitExceededException.java`, and
  `GlobalExceptionHandler.rateLimited` (`429`, `Retry-After`, `RateLimit-*`, code `RATE_LIMITED`, fixed message).
  The R09 resolution in `docs/wayfinder/code-review-map.md`: rejected requests count; header names as in `api.md`.
- Login and register are anonymous, so the R09 interceptor never applies to them.
- `api/controller/AuthController.java` is `@RequestMapping("/api/v1/auth")`. The plan's `/api/auth/...` paths predate
  R08's `/api/v1` versioning (Q08): the limiter covers the `/api/v1/auth/...` routes. No `/api/auth/**` route exists.
- Settings: `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java` `RateLimits`
  (`requests`, `window`; `@Positive` / `@NotNull`); `src/main/resources/application.yml` `mindforge.rate-limits`
  (line ~130).
- The 429 shape is answered by Q09: throw the existing `RateLimitExceededException`, reuse the handler; set
  `RateLimit-*` on allowed responses as the R09 interceptor does. No frontend change: the login page shows the server's
  error text.
- Client address behind the proxy (`server.forward-headers-strategy`) is P20-02. In this ticket `getRemoteAddr()` is
  used as is; in tests it is the loopback address.
- Integration tests that share a cached Spring context share the auth buckets. `ApiFlowTest` signs up many times
  (`signedUp()`), so it must set a high auth limit. `RateLimitTest` also registers.
- ADR 0038 (`docs/adr/0038-auth-rate-limit-is-keyed-by-client-address.md`) is already written; do not write a new ADR.
- Binding rules: CLAUDE.md (thin controllers, constructor injection, beans via `@Configuration`, no reactive types,
  logger named `log`); `docs/standards/backend/api.md` § Rate Limit Headers; `docs/standards/global/error-handling.md`;
  `docs/standards/global/minimal-implementation.md` (no Bucket4j or other new library; Caffeine is on the classpath).

## Decisions to apply
**D01 — Decision:** Use A: key the auth rate limiter by client address only. One `HandlerInterceptor` counts every
POST to `/api/v1/auth/login` and `/api/v1/auth/register` (rejected attempts count, R09 precedent) in a Caffeine fixed
window keyed by `request.getRemoteAddr()` (made correct behind the proxy by the Q02 decision). When the window is spent
it throws `RateLimitExceededException`, so the existing handler answers 429 + `Retry-After` + `RATE_LIMITED`. Do not
read the request body and do not key on the email.

**D01 — Implementation notes:**
- New `dev.mindforge.api.config.AuthRateLimitInterceptor implements HandlerInterceptor`, constructor
  `(int limit, Duration window)`, `Cache<String, Window>` with `expireAfterWrite(window)` and a `maximumSize` bound.
  Either copy the R09 window logic or extract a package-private `FixedWindowCounter<K>` shared by both; R09 behaviour
  must not change.
- `preHandle`: skip non-POST; key = `request.getRemoteAddr()`; count first, then throw
  `new RateLimitExceededException(limit, resetSeconds)` when `count > limit`; else set `RateLimit-Limit/Remaining/Reset`.
- Register in `RateLimitConfig.addInterceptors` with `.addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register")`.
  One bucket for the two paths (D03); settings under `mindforge.rate-limits.auth.*` in `AppProperties`.
- Never read or log the request body or password; log the address at debug at most — except the 429 log line from
  D02: on a 429 log the key at INFO, `log.info("auth rate limit hit for {}", key)`, so a global bucket behind a
  misconfigured proxy shows up.
- Tests: unit (plain Mockito/`MockHttpServletRequest`): limit+1 POSTs from one address throws with correct reset
  seconds; a second address is not affected; GET is not counted; window expiry resets. Integration (MockMvc or
  RANDOM_PORT, Testcontainers): login over the limit → 429 with `Retry-After` and code `RATE_LIMITED`; failed logins
  count; register over the limit → 429; R09 limiter still keyed by user.

**D03 — Decision:** Use A: add a nested `Auth` setting under `mindforge.rate-limits`
(`mindforge.rate-limits.auth.requests`, default 10, and `mindforge.rate-limits.auth.window`, default PT1M; env
`AUTH_RATE_LIMIT_REQUESTS`, `AUTH_RATE_LIMIT_WINDOW`). Login and register share one allowance: one bucket per client
address that counts POSTs to both paths. Do not reuse the model-call `mindforge.rate-limits.requests` / `.window`
values.

**D03 — Implementation notes:**
- `AppProperties.RateLimits`: add `@Valid private Auth auth = new Auth();` with getter/setter, and
  `public static class Auth { @Positive private int requests = 10; @NotNull private Duration window = Duration.ofMinutes(1); }`
  with getters/setters. Javadoc: "Per-client-address cap on sign-in and sign-up attempts (ADR 0038)."
- `application.yml` under `mindforge.rate-limits`: `auth: { requests: ${AUTH_RATE_LIMIT_REQUESTS:10}, window: ${AUTH_RATE_LIMIT_WINDOW:PT1M} }`.
- `RateLimitConfig.addInterceptors`: one `new AuthRateLimitInterceptor(limits.getAuth().getRequests(),
  limits.getAuth().getWindow())`, `.addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register")`; one cache, key =
  client address only (not the path).
- 429 / `Retry-After` / `RateLimit-*` / `RATE_LIMITED` come from `RateLimitExceededException` and the existing handler.
- Tests: `ApiFlowTest` (and every IT that registers or signs in many times) sets `mindforge.rate-limits.auth.requests`
  high (for example 1000) in `@TestPropertySource`; the auth-limit IT sets it low (for example 3, PT3S). Unit: login
  and register POSTs from one address draw from the same bucket (limit+1 mixed calls throws); validation rejects
  `requests=0` and a null window.
- (Docs — `env.example`, `deployment.md`, `api.md` — are P20-08.)

## Acceptance criteria
- [ ] AC1: With `mindforge.rate-limits.auth.requests=N`, the N+1th POST to `/api/v1/auth/login` from one address in a
  window gets `429`, a `Retry-After` header with the seconds to the window end, and an error body with code
  `RATE_LIMITED` and the fixed message of the existing handler.
- [ ] AC2: Failed sign-ins (wrong password) count toward the allowance; the request after the allowance is spent gets
  `429` even with correct credentials.
- [ ] AC3: Login and register share one bucket: N mixed POSTs to the two paths spend the allowance and the next POST to
  either path gets `429`.
- [ ] AC4: Allowed auth POSTs carry `RateLimit-Limit`, `RateLimit-Remaining` and `RateLimit-Reset`.
- [ ] AC5: A second client address is not affected by the first address's spent allowance (unit); GET requests
  (`/api/v1/auth/me`, `/providers`) are not counted; after the window expires, sign-in works again.
- [ ] AC6: The interceptor never reads the request body and never logs the email or password; a 429 logs the key at
  INFO.
- [ ] AC7: `mindforge.rate-limits.auth.requests` defaults to 10 and `.window` to PT1M, bound from
  `AUTH_RATE_LIMIT_REQUESTS` / `AUTH_RATE_LIMIT_WINDOW`; `requests=0` or a null window fails validation.
- [ ] AC8: The R09 model-call limiter is unchanged: `RateLimitTest` still passes and still limits per user.
- [ ] AC9: `ApiFlowTest` and `RateLimitTest` set a high auth limit; the full gate stays green (except the known
  baseline failure `ApiFlowTest.theLoginRouteIsTheSinglePageApp`).

## Test plan
- AC5, AC6 (key and window logic), AC3 (mixed paths, unit): unit, plain JUnit + `MockHttpServletRequest`, no Spring
  context — `src/test/java/dev/mindforge/unit/api/AuthRateLimitInterceptorTest.java` (new; match the package layout of
  existing unit tests; if a shared `FixedWindowCounter` is extracted, test it there too).
- AC7: unit binding/validation test in the style of
  `src/test/java/dev/mindforge/infrastructure/config/ProcessingSettingsBindingTest.java` (or extend the existing
  `AppProperties` validation test if there is one).
- AC1–AC5 (end to end): integration, `@SpringBootTest(RANDOM_PORT)` + `TestContainerBase`,
  `src/test/java/dev/mindforge/integration/api/AuthRateLimitTest.java` (new) with
  `mindforge.rate-limits.auth.requests=3`, `mindforge.rate-limits.auth.window=PT3S`.
- AC8, AC9: existing `RateLimitTest` and `ApiFlowTest`, with the added `@TestPropertySource` entry; full gate
  `mvn -B verify -DskipFrontend=true`.

## Files likely touched
- `src/main/java/dev/mindforge/api/config/AuthRateLimitInterceptor.java` (new)
- `src/main/java/dev/mindforge/api/config/FixedWindowCounter.java` (new, only if extracted)
- `src/main/java/dev/mindforge/api/config/RateLimitInterceptor.java` (only if the shared counter is extracted)
- `src/main/java/dev/mindforge/api/config/RateLimitConfig.java`
- `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java`
- `src/main/resources/application.yml`
- `src/test/java/dev/mindforge/unit/api/AuthRateLimitInterceptorTest.java` (new)
- `src/test/java/dev/mindforge/integration/api/AuthRateLimitTest.java` (new)
- `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java`, `RateLimitTest.java` (property only)

## Out of scope
- `server.forward-headers-strategy` and any `X-Forwarded-For` handling (P20-02). No code reads `X-Forwarded-For`.
- A per-email or per-account bucket; a separate register budget; limits on OAuth, `/logout`, `/providers`, `/me`.
- Changes to the R09 limiter's behaviour or settings, and to the Discord/Slack bot limiters.
- Docs (`env.example`, `deployment.md`, `api.md`, `web-security.md`) — P20-08. ADR 0038 exists.
- Any frontend change.

## Notes
