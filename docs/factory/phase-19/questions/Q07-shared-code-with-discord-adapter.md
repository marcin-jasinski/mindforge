---
id: Q07
status: decided
decision: D07
impact: medium
blocks: [W03, W04]
---
## Question
Does the Slack adapter copy the platform-neutral Discord helpers (rate limiter, current-quiz cache) or are they
extracted to a shared place both adapters use?

## Context
- `discord/DiscordRateLimiter.java` (Caffeine window keyed by `long` snowflake) and `discord/DiscordQuizSessions.java`
  (Caffeine keyed by internal user id, `ActiveQuiz(kbId, sessionId)`) contain no JDA type. The Slack bot needs the same
  behaviour; Slack ids are strings.
- D11 (phase-18) chose a copy of `RateLimitInterceptor`'s logic for the bot to keep tested web code unchanged.
- An adapter importing another adapter's package (`dev.mindforge.slack` → `dev.mindforge.discord`) couples two
  optional adapters that are switched on independently.

## Known options
- A: Copy into `dev.mindforge.slack` (`SlackRateLimiter` keyed by `String`, `SlackQuizSessions`); Discord unchanged.
- B: Extract a platform-neutral rate limiter and current-quiz cache into a shared adapter-support package (for example
  `dev.mindforge.chat`), generic in the key; Discord refactored to use them; tests moved.
- C: Slack imports the Discord classes directly (widening the rate limiter key).

## Constraints
- Minimal implementation and no speculative abstractions (`docs/standards/global/minimal-implementation.md`) — but
  two real callers exist now.
- Adapters are thin; no business logic (hexagonal.md). Discord behaviour and tests must not change if refactored.
- Code review map items are not re-opened (`.claude/factory.md`).
