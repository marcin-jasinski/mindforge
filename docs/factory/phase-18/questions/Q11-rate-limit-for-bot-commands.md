---
id: Q11
status: decided
decision: D11
impact: medium
blocks: [W06, W07, W08, W09]
---
## Question
Are the bot's model-spending commands (`/ask`, `/quiz start`, `/quiz answer`, `/upload`) rate limited per user, and
with which limit?

## Context
The web limits model-calling POSTs per user in the API adapter (`api/config/RateLimitInterceptor.java`,
`mindforge.rate-limits.requests`/`window`, default 30 per minute; Caffeine, in-process). ADR 0019 § Consequences: the
CLI is not limited because the operator runs it. A bot is reachable by every member of an allowed guild (Q04) and each
first contact creates an account, so one member can spend model calls without bound. Phase 20.3 rate-limits only
`/api/auth/login` and `/register`.

## Known options
- A: Limit in the Discord adapter with the same per-user setting (`mindforge.rate-limits.*`), counted separately from
  the web (an in-adapter Caffeine window like `RateLimitInterceptor`); an exhausted allowance replies with a fixed
  "try again in N s" message.
- B: Extract the window counter into a shared, non-web class used by both the interceptor and the bot, so web and
  Discord share one allowance per user.
- C: No limit in Phase 18; rely on the guild/role allowlist; record the gap for Phase 20.

## Constraints
- Interceptor logic lives in `dev.mindforge.api`; the bot must not depend on servlet types.
- Minimal implementation: no generic limiter framework.
- A rejected command calls no application service.
