---
id: D03
question: Q03
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q03.r1.tally.md]
adr: 
---
## Decision
Use A: add a nested `Auth` setting under `mindforge.rate-limits` (`mindforge.rate-limits.auth.requests`, default 10,
and `mindforge.rate-limits.auth.window`, default PT1M; env `AUTH_RATE_LIMIT_REQUESTS`, `AUTH_RATE_LIMIT_WINDOW`).
Login and register share one allowance: one bucket per client address that counts POSTs to both paths. Do not reuse
the model-call `mindforge.rate-limits.requests` / `.window` values.

## Why
- The model-call limit is a per-user cap of 30/min (AppProperties.java:208-215, api.md:25), shared with the Discord
  and Slack bots (deployment.md:104,152). It is too loose for guessing, and coupling it to auth would let a bot tune
  silently change auth protection.
- D01 fixes one interceptor keyed by address over both paths; one bucket fits it with no path-aware key.
- A nested `Auth` with `@Positive` / `@NotNull` copies the `RateLimits` pattern (AppProperties.java:209-215).
- 10/min per address keeps a household or classroom behind one NAT usable; the value is env-tunable.
- A to B is additive later (key = path + address, second setting pair). Class `local`: the security boundary
  (limiter and key) is already recorded in ADR 0038; this is a tunable default and a property shape.

## Rejected
- B: a second setting pair and a path-aware key for mass sign-up, a threat no standard names; a 5/hour register cap
  makes NAT lock-out worse and hard to diagnose (long Retry-After).
- C: verified hard objection — the 30/min model-call cap is too loose for guessing and couples auth to the bot limits.

## Dissent
None in the vote. Risk to watch (skeptic): register spam from one address uses up that address's login allowance;
and integration tests that share a cached Spring context share the same auth buckets, so unexpected 429s in ITs mean
the auth limit was not raised in that test's properties.

## Implementation notes
- `AppProperties.RateLimits`: add `@Valid private Auth auth = new Auth();` with getter/setter, and
  `public static class Auth { @Positive private int requests = 10; @NotNull private Duration window = Duration.ofMinutes(1); }`
  with getters/setters. Javadoc: "Per-client-address cap on sign-in and sign-up attempts (ADR 0038)."
- `application.yml` under `mindforge.rate-limits`: `auth: { requests: ${AUTH_RATE_LIMIT_REQUESTS:10}, window: ${AUTH_RATE_LIMIT_WINDOW:PT1M} }`.
- `RateLimitConfig.addInterceptors`: one `new AuthRateLimitInterceptor(limits.getAuth().getRequests(),
  limits.getAuth().getWindow())`, `.addPathPatterns("/api/v1/auth/login", "/api/v1/auth/register")`; one cache, key =
  client address only (not the path).
- 429 / `Retry-After` / `RateLimit-*` / `RATE_LIMITED` come from `RateLimitExceededException` and the existing handler.
- Docs: add both env vars with defaults to `env.example` and `docs/project/deployment.md`; extend `api.md` § Rate Limit
  Headers with the auth limit (per address, login + register share it).
- Tests: `ApiFlowTest` (and every IT that registers or signs in many times) sets
  `mindforge.rate-limits.auth.requests` high (for example 1000) in `@TestPropertySource`; the auth-limit IT sets it low
  (for example 3, PT3S). Unit: login and register POSTs from one address draw from the same bucket (limit+1 mixed
  calls throws); validation rejects `requests=0` and a null window.
