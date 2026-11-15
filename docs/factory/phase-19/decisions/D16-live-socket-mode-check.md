---
id: D16
question: Q16
status: decided
method: judge
class: local
votes: { pragmatist: A }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q16.r1.tally.md]
adr: 
---
## Decision
Use A: tick "Bot connects via Socket Mode and responds to commands" on fake-driven tests. Prove that the config builds
and starts the Socket Mode app with the app-level token (fake client, no network) and that every command handler
responds (unit tests). Record a live smoke test against a real Slack workspace as a human follow-up: in the
implementation-plan.md Phase 19 "As built" notes, in deployment.md, and as a GitHub issue.

## Why
- Phase 18 used the same shape for Discord: implementation-plan.md:1632 and deployment.md:124.
- The Slack app, both tokens and the test workspace are human-owned; an unattended run cannot get them.
- No test may connect to Slack in CI, and secrets must never be committed (conventions.md:13).

## Rejected
- B: needs a human with Slack secrets during an unattended run; it cannot happen before the phase closes.

## Dissent
None (single ballot). Risk to watch: fake-driven tests do not prove the real Socket Mode handshake or the Java
WebSocket backend (ADR-0037) against Slack; the follow-up issue must name this so a human runs it before production use.

## Implementation notes
- Config test: with a fake/stub client factory, assert the Socket Mode app is built and started with the app-level token,
  and that the bot does not start when the tokens are blank. No network.
- Handler unit tests: each Slack command handler returns a response (Mockito, no Spring context).
- Never log or commit `SLACK_BOT_TOKEN` / `SLACK_APP_TOKEN`; use placeholder values in tests.
- deployment.md Slack section: add "A live test against real Slack is not automated: it needs a Slack app, a bot token,
  an app-level token and a test workspace."
- implementation-plan.md Phase 19 As built: "A live smoke test against real Slack is a human follow-up."
- Open a GitHub issue for the live smoke test (label `ready-for-human`).
