---
id: D01
question: Q01
status: decided
method: majority
class: adr
votes: { pragmatist: A, architect: A, skeptic: B }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q01.r1.tally.md]
adr: docs/adr/0038-auth-rate-limit-is-keyed-by-client-address.md
---
## Decision
Use A: key the auth rate limiter by client address only. One `HandlerInterceptor` counts every POST to
`/api/v1/auth/login` and `/api/v1/auth/register` (rejected attempts count, R09 precedent) in a Caffeine fixed window
keyed by `request.getRemoteAddr()` (made correct behind the proxy by the Q02 decision). When the window is spent it
throws `RateLimitExceededException`, so the existing handler answers 429 + `Retry-After` + `RATE_LIMITED`. Do not read
the request body and do not key on the email.

## Why
- Plan 20.3 names no key; a per-address key meets "limiter on login and register; 429 with Retry-After" in full and
  makes 20.4 "Rate limit triggers" testable.
- It reuses the R09 window logic (`RateLimitInterceptor.java:20-49`) with a `String` key and the existing 429 path
  (`GlobalExceptionHandler.java:100-104`); no body caching or `RequestBodyAdvice` exists today (grep), and none is added.
- It covers credential stuffing and mass registration from one address. No standard asks for per-account throttling
  (`web-security.md` requires BCrypt ≥ 12 only, line 28), and BCrypt cost slows distributed guessing.
- A per-email bucket counting all attempts lets anyone lock a victim out of password sign-in; A has no such defect.
- Adding a per-email bucket later is additive (skeptic agrees A→B is additive).

## Rejected
- B: adds a second mechanism (body read before the controller), exposes `AccountService.normalise`, and adds a victim
  lock-out vector, for a threat no project standard names.
- C: no address limit on login, so credential stuffing across many accounts from one address is not slowed.

## Dissent
Skeptic (B, 0.7): A does not slow a distributed guesser against one account, and A depends fully on Q02 — a wrong
client address makes A one global bucket (all sign-ins locked) or no limit (forged headers). Watch: the Q02 decision
must give a trustworthy address; record distributed per-account guessing as a follow-up.

## Implementation notes
- New `dev.mindforge.api.config.AuthRateLimitInterceptor implements HandlerInterceptor`, constructor
  `(int limit, Duration window)`, `Cache<String, Window>` with `expireAfterWrite(window)` and a `maximumSize` bound.
  Either copy the R09 window logic or extract a package-private `FixedWindowCounter<K>` shared by both; R09 behaviour
  must not change.
- `preHandle`: skip non-POST; key = `request.getRemoteAddr()`; count first, then throw
  `new RateLimitExceededException(limit, resetSeconds)` when `count > limit`; else set `RateLimit-Limit/Remaining/Reset`.
- Register in `RateLimitConfig.addInterceptors` with `.addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register")`
  (the controller is at `/api/v1/auth`, `AuthController.java:34`). One bucket or two for the two paths and the budget
  per Q03; settings under `mindforge.rate-limits.auth.*` in `AppProperties`.
- Never read or log the request body or password; log the address at debug at most.
- Tests: unit (plain Mockito/`MockHttpServletRequest`): limit+1 POSTs from one address throws with correct reset
  seconds; a second address is not affected; GET is not counted; window expiry resets. Integration (MockMvc,
  Testcontainers): login over the limit → 429 with `Retry-After` and code `RATE_LIMITED`; failed logins count; register
  over the limit → 429; R09 limiter still keyed by user.
