---
id: Q03
status: decided
decision: D03
impact: medium
blocks: [W02]
---
## Question
What allowance and settings does the auth limiter use: how many attempts per window, configured under which
properties, and do login and register share one allowance or have one each?

## Context
- The existing model-call limit is `mindforge.rate-limits.requests=30` per `window=PT1M` (`AppProperties.RateLimits`,
  env `RATE_LIMIT_REQUESTS`, `RATE_LIMIT_WINDOW`), one shared bucket across endpoints (R09 resolution). Discord and
  Slack bots share that setting.
- 30/min is far too loose for password guessing; a strict value may lock out a household behind one NAT.
- Integration tests need to lower the values (`RateLimitTest` sets them with `@TestPropertySource`); `ApiFlowTest`
  registers many users from one address (localhost) in one context, so a low default could break unrelated tests
  unless the test sets a high value.
- Retry-After: the existing fixed-window handler sends seconds until the window resets.

## Known options
- A: New `mindforge.rate-limits.auth.requests` / `.window` (env `AUTH_RATE_LIMIT_REQUESTS`, `AUTH_RATE_LIMIT_WINDOW`),
  default 10 per PT1M, one bucket shared by login and register.
- B: As A but separate allowances: login (e.g. 10/min) and register (e.g. 5/hour).
- C: Reuse `mindforge.rate-limits` values for auth (no new setting).

## Constraints
- `AppProperties` with `@Validated` constraints (`@Positive`, `@NotNull`), as `RateLimits` does.
- `429` with `Retry-After`, `RateLimit-*` headers and `RATE_LIMITED` (`api.md` § Rate Limit Headers, R09).
- New settings documented in `deployment.md` and `env.example`.
