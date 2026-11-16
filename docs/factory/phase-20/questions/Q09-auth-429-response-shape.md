---
id: Q09
status: answered
impact: low
blocks: [W02]
answered_by: docs/standards/backend/api.md#rate-limit-headers; src/main/java/dev/mindforge/api/config/GlobalExceptionHandler.java (rateLimited)
---
## Question
What does a rate-limited login or register answer: status, headers and body?

## Context
- Plan 20.3: "429 with `Retry-After`".
- `api.md` § Rate Limit Headers: an exhausted allowance is `429` with `Retry-After` and code `RATE_LIMITED`; limited
  endpoints return `RateLimit-Limit`, `RateLimit-Remaining`, `RateLimit-Reset`.
- `GlobalExceptionHandler.rateLimited(RateLimitExceededException)` already answers exactly that, with a fixed message.
- The SPA login page shows the server's error text (`pages/login/login.ts`), so no frontend change is needed.

## Known options
- A: Throw the existing `RateLimitExceededException` from the auth limiter; reuse the handler; set `RateLimit-*` on
  allowed auth responses as the existing interceptor does.

## Constraints
- `api.md` § Rate Limit Headers; `error-handling.md` (central handling, fixed messages). Answer: A; `api.md` gains one
  sentence naming the auth endpoints and their key.
