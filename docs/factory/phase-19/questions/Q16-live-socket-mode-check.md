---
id: Q16
status: decided
decision: D16
impact: low
blocks: [W08]
---
## Question
How is the completion item "Bot connects via Socket Mode and responds to commands" proved, given that a real connection
needs a Slack app, a bot token, an app-level token and a test workspace?

## Context
Only the human can create a Slack app and its tokens and own a test workspace (an account and secrets). Phase 18 met
the same limit and recorded "A live smoke test against real Discord is a human follow-up" (implementation-plan.md
Phase 18.4 As built; deployment.md). This does not block the code work items: the factory can prove that the config
builds and starts the Socket Mode app with the app token (fake client) and that every handler responds (unit tests).

## Known options
- A: Tick the item on fake-driven tests; record a live smoke test as a human follow-up (issue) — the Phase 18 shape.
- B: The human supplies tokens and a test workspace for a live check before the phase closes.

## Constraints
Tokens must never be committed or logged (`docs/standards/global/conventions.md`). No test may connect to Slack in CI.
