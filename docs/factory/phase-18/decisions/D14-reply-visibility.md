---
id: D14
question: Q14
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q14.r1.tally.md]
adr: docs/adr/0032-discord-replies-are-ephemeral.md
---
## Decision
Use A: every Discord reply is ephemeral. This includes Query answers, quiz questions and grades, upload results,
error and rejection replies, and deferred replies (the ephemeral flag is set on the defer, because
it cannot change after the first response). There is no `public` option. Replies never carry reference answers or
grounding excerpts.

## Why
- Knowledge bases are private and single-owner; ownership is checked on every request (web-security.md:87-89,
  architecture.md:298). A public reply shows the owner's wiki content and study data to every channel member.
- A message posted in a channel cannot be recalled from other members, logs or other bots; the leak is not reversible.
- D03 and D04 already use fixed ephemeral replies (D03:47, D04:49); one rule for all replies is simpler.
- Loss of channel history costs little: each `/ask` is a real Query interaction kept in the web history (D06:14-16).
- A per-command opt-in (C) can be added later with no schema change, so A closes no door.

## Rejected
- B: posts answers cut from a private knowledge base to the whole channel.
- C: a caller-controlled leak path and a second mode to test, with no stated requirement.

## Dissent
None. Risk to watch: users may ask for shared learning in a channel; if so, add an explicit per-use opt-in (C) as a
new decision, never a public default.

## Implementation notes
- One reply helper in the Discord adapter (`dev.mindforge.api` side or the Discord adapter package) owns the ephemeral
  flag: `deferReply(true)` / `reply(...).setEphemeral(true)`; handlers do not call JDA reply methods directly.
- Error, rejection (D04) and unknown-knowledge-base (D03) replies go through the same helper.
- Tests: for each command path (`/ask`, `/quiz start`, `/quiz answer`, `/upload`, and an error path) assert the
  defer/reply is ephemeral; assert no reply text contains the reference answer or grounding excerpts.
- Document "all replies are ephemeral; history lives in the web app" in the Discord docs.
