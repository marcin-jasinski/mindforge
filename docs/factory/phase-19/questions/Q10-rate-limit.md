---
id: Q10
status: answered
impact: low
blocks: [W04]
answered_by: docs/factory/phase-18/decisions/D11-rate-limit-for-bot-commands.md#Decision; docs/adr/0019-cli-acts-as-a-named-user-without-password.md#Consequences
---
## Question
Are Slack commands rate limited, with which settings, and where in the gate?

## Context
D11 placed a per-user limit in the bot adapter (the adapter that faces untrusted users, ADR 0019), using
`mindforge.rate-limits.requests` / `.window` with a counter of its own, keyed by the platform user id, checked after the
allowlist and before identity resolution, with the fixed reply "Rate limit reached. Try again in N s.".

## Known options
- A (settled): same for Slack, keyed by the Slack user id (string), its own counter; every model-spending command and
  each upload counts once. Whether the class is copied or shared is Q07.

## Constraints
No new settings; no servlet or Bolt types in the limiter; unit-tested with a fake clock.
