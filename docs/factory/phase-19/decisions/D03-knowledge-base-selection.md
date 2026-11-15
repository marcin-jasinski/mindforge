---
id: D03
question: Q03
status: decided
method: unanimous
class: adr
votes: { pragmatist: E, architect: E, skeptic: E }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-19/ballots/Q03.r1.tally.md]
adr: docs/adr/0036-slack-commands-act-on-the-callers-only-knowledge-base.md
---
## Decision
Use E: a Slack command names no knowledge base. After identity resolution, call `KnowledgeBaseService.list(userId)`;
with exactly one, take its `kbId`, call `requireOwner(kbId, userId)`, and do the work with that `kbId`. With zero or
more than one, send one fixed ephemeral reply and do no work. No modal, no name parsing, no stored selection. The
Slack selection (Block Kit select, value = id) is designed with account linking (#43). Record this in ADR 0036.

## Why
- Unanimous among three counted ballots; every cited fact checked (tally).
- A Slack user owns exactly one knowledge base in Phase 19: provisioning inserts one (ExternalIdentityService.java:46-55),
  bot accounts cannot sign in to the web (ADR 0027:10), the CLI creates none (only `requireOwner`,
  PipelineCommandRunner.java:134, QuizCommandRunner.java:95), scope.md:65.
- ADR 0030's objection to the implicit default (wrong knowledge base or lock-out with several) does not apply: E refuses
  with several and never picks. ADR 0030 is Discord-scoped and lists an "only knowledge base" default as compatible.
- The id discipline holds: the id comes from an owner-filtered list and still passes `requireOwner`; `kbId` first on
  every port call.
- A and B add a `view_submission` path, `trigger_id` timing and an extra step for uploads, for a list of one
  (minimal-implementation.md).

## Rejected
- A: a modal round trip and submission handler on every command to select from one item; no upload `trigger_id`.
- B: carries all of A's code for a branch unreachable before #43.
- C: names are not unique per owner (ADR 0030); free-text split can route work to the wrong knowledge base.
- D: by name has C's defect; by stored id is the stale pointer ADR 0030 rejected.

## Dissent
None. Risk to watch: an operator action or #43 that gives a Slack user a second knowledge base locks that user out of
Slack commands until a selection exists; #43 must add it.

## Implementation notes
- `dev.mindforge.slack.SlackKnowledgeBases` with `Optional<UUID> resolveKnowledgeBase(UUID userId)` (or with a reply
  sink, to match the Slack reply helper): `knowledgeBases.list(userId)`; `size() != 1` -> empty and one fixed reply
  (e.g. "This command needs exactly one knowledge base. Selecting a knowledge base is not available in Slack yet.");
  else `knowledgeBases.requireOwner(id, userId)`, catching `NotFoundException | NotOwnerException` -> empty and the
  same fixed reply. Never look up by name; never store a selection. Mirror `DiscordKnowledgeBases.ownedKnowledgeBase`.
- Order in the handler: allowlist -> rate limit -> ack -> `ExternalIdentityService.resolve(SLACK, ...)` -> resolve the
  knowledge base -> work. Used by `/mf-ask`, `/mf-quiz start` and the upload handler. `/mf-quiz answer` uses the
  knowledge base stored with the quiz session.
- Command text carries no knowledge-base token.
- Unit tests (plain Mockito, `src/test/java/dev/mindforge/unit/slack/SlackKnowledgeBasesTest.java`): one owned -> its
  id and `requireOwner` called; zero -> refusal, no work; two -> refusal, no `requireOwner`, no work; `requireOwner`
  throws `NotFoundException` (deleted between list and check) -> refusal.
- ADR number is 0036. The orchestrator renumbered the Socket Mode backend ADR to 0037; link 0033–0037 in
  `docs/INDEX.md`.
