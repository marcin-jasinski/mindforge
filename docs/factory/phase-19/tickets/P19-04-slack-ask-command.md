---
id: P19-04
title: Answer /mf-ask as a one-turn Query on the caller's only knowledge base
status: done
plan_tasks: [19.2, 19.3]
decisions: [D03]
depends_on: [P19-03]
size: M
commit: 804be1b
fix_rounds: 0
issue: 48
---
## Goal
`/mf-ask <question>` runs a one-turn Query on the caller's only knowledge base and replies (ephemeral) with the answer
and its cited page paths. `SlackKnowledgeBases` picks that knowledge base: exactly one owned → its id after
`requireOwner`; zero, several, or a failed ownership check → one fixed reply and no work.

## Context
- P19-03 built the gate (`SlackCommandHandler`), the `SlackCommand` interface, the `SlackCall` record and the
  ephemeral `SlackReply` seam; a `SlackCommand` bean is registered on Bolt by its name in `SlackConfig`.
- Mirror, do not import: `src/main/java/dev/mindforge/discord/AskCommand.java` (one-turn: `QueryService.start(kbId,
  userId)` then `ask(kbId, userId, interactionId, question)`; `MODEL_UNAVAILABLE = "The model is not available now. Try
  again later."` for `AIGatewayUnavailableException | DeadlineExceededException | ModelOutputException`;
  `NotOwnerException | NotFoundException` → fixed text; answer + "\n\nSources:" + "- path" lines;
  `MAX_QUESTION_LENGTH = 4000`), `DiscordKnowledgeBases.java` (`ownedKnowledgeBase`), and their tests.
- Q11 (Phase 18 D06): each `/mf-ask` calls `QueryService.start` then `ask` once; the adapter keeps no interaction
  state; refusals map to fixed texts.
- Slash commands carry one free-text argument; the whole text is the question. Empty text or text longer than 4000
  characters → a fixed usage reply, no Query.
- Security: every entry point verifies ownership (`docs/standards/security/web-security.md` § API Authorization); never
  return `grounding_context`, `raw_prompt`, `raw_completion`, `cost`. Every tenant-scoped port call takes `kbId` first.
  The Query runs after the ack, on the gate's virtual thread; no transaction in the adapter.
- ADR 0036 (`docs/adr/0036-slack-commands-act-on-the-callers-only-knowledge-base.md`) is already written; ADR 0030
  is Discord-scoped. Do not write a new ADR.

## Decisions to apply
**D03 — Decision:** Use E: a Slack command names no knowledge base. After identity resolution, call
`KnowledgeBaseService.list(userId)`; with exactly one, take its `kbId`, call `requireOwner(kbId, userId)`, and do the
work with that `kbId`. With zero or more than one, send one fixed ephemeral reply and do no work. No modal, no name
parsing, no stored selection. The Slack selection (Block Kit select, value = id) is designed with account linking
(#43). Record this in ADR 0036 (done).

**D03 — Implementation notes:**
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

## Acceptance criteria
- [x] AC1: `SlackKnowledgeBases` returns the id of the caller's only knowledge base after `requireOwner(id, userId)`;
  with zero or two knowledge bases, or when `requireOwner` throws `NotFoundException` or `NotOwnerException`, it
  returns empty and sends exactly one fixed reply; with two, `requireOwner` is not called.
- [x] AC2: `/mf-ask <question>` calls `QueryService.start(kbId, userId)` then `ask(kbId, userId, interactionId,
  question)` once with the resolved user and knowledge base, and replies with the answer followed by the cited page
  paths (no "Sources" block when there are none).
- [x] AC3: Two `/mf-ask` calls start two interactions; the command keeps no interaction state.
- [x] AC4: A model failure (`AIGatewayUnavailableException`, `DeadlineExceededException`, `ModelOutputException`)
  replies "The model is not available now. Try again later."; `NotOwnerException`/`NotFoundException` reply with the
  fixed knowledge-base text; no exception message is sent.
- [x] AC5: Empty question text, or text over 4000 characters, gets a fixed usage reply and no Query call.
- [x] AC6: `SlackConfig` registers the `/mf-ask` command and the `SlackKnowledgeBases` bean; `SlackConfigTest` still
  shows no Slack bean without tokens or in `cli`.
- [x] AC7: No reply contains grounding context or any field listed as never-returned in CLAUDE.md.

## Test plan
- AC1: unit `src/test/java/dev/mindforge/unit/slack/SlackKnowledgeBasesTest.java` (Mockito `KnowledgeBaseService`).
- AC2–AC5, AC7: unit `src/test/java/dev/mindforge/unit/slack/SlackAskCommandTest.java` (Mockito `QueryService`, the
  recording reply double from P19-03, `make*` factories for `QueryService.Answer`).
- AC6: integration-tagged `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`.

## Files likely touched
- `src/main/java/dev/mindforge/slack/SlackKnowledgeBases.java` (new)
- `src/main/java/dev/mindforge/slack/SlackAskCommand.java` (new)
- `src/main/java/dev/mindforge/slack/SlackConfig.java`
- `src/test/java/dev/mindforge/unit/slack/SlackKnowledgeBasesTest.java` (new)
- `src/test/java/dev/mindforge/unit/slack/SlackAskCommandTest.java` (new)
- `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`

## Out of scope
- A knowledge base option, name parsing, a stored selection, a modal or Block Kit select (deferred to #43).
- Multi-turn Query continuity.
- `/mf-search` (replaced by `/mf-ask` in v3.0).

## Notes
- Decided: `/mf-ask` strips the text and checks it (blank or over 4000 characters -> "Usage: /mf-ask <question> (at most 4000 characters).") before the knowledge-base lookup, because a bad question then costs no database call.
- Decided: a `NotOwnerException`/`NotFoundException` from the Query replies with the same `SlackKnowledgeBases` fixed text, because the ticket names one fixed knowledge-base text.
- Decided: the P19-03 routing test in `SlackConfigTest` now expects two routes (the fake command and `/mf-ask`), because `/mf-ask` is now always registered.
- Tester r1 AC map: AC1 SlackKnowledgeBasesTest; AC2 SlackAskCommandTest.theOnlyKnowledgeBase_*, anAnswerWithoutCitedPaths_*; AC3 twoAsks_startTwoInteractions; AC4 aModelFailure_*, anOwnershipFailureFromTheQuery_*; AC5 anEmptyOrTooLongQuestion_*, aQuestionOfExactly4000Characters_isAsked; AC6 SlackConfigTest (bothTokensRegisterTheAskCommand*, no-bean tests); AC7 failure tests assert fixed text only (no message leak).
