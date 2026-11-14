---
id: P18-06
title: Answer /ask in Discord with a one-turn Query interaction over QueryService
status: done
plan_tasks: [18.2]
decisions: [D06]
depends_on: [P18-05]
size: S
issue: 38
commit: 83cf3af
fix_rounds: 0
---
## Goal
`/ask kb:<knowledge base> question:<text>` starts a new Query interaction in the caller's knowledge base, asks the
question once, and replies ephemerally with the answer and its cited page paths. It uses the same `QueryService` as
the web (completion checklist "Commands delegate to the same application services as the web UI").

## Context
- After P18-05: `DiscordCommand` plug-in interface, `DiscordCommandHandler` gate (allowlist -> rate limit -> defer ->
  identity), `DiscordReply` seam (ephemeral, splits at 2000 chars), the shared `kb` option factory and
  `resolveKnowledgeBase(kbOption, userId)` -> `Optional<UUID>` (empty -> reply "Unknown knowledge base.").
- `src/main/java/dev/mindforge/application/service/QueryService.java`: `Interaction start(UUID kbId, UUID userId)` (:75),
  `Answer ask(UUID kbId, UUID userId, UUID interactionId, String question)` (:79), `record Answer(String answer,
  List<String> citedPaths)`; `requireSession` throws `NotOwnerException` for another user's interaction.
- Security: never return `grounding_context`, `raw_prompt`, `raw_completion`, `cost`; only `Answer.answer()` and
  `citedPaths()` reach the reply. Use CONTEXT.md vocabulary (Query, not search/chat). `/ask` replaces the old `/search`.
- Possible exceptions from `ask` (model unavailable, deadline, model output): map to fixed texts; never echo provider messages.
- Reply visibility (D14, applied in P18-05): every reply is ephemeral; assert it for this path.

## Decisions to apply
**D06 — Ask interaction continuity.** Decision: each `/ask` starts a new Query interaction. The handler calls
`QueryService.start(kbId, userId)`, then `QueryService.ask(kbId, userId, interaction.id(), question)` once, and replies
with the answer and its cited paths. The Discord adapter keeps no interaction state.
Implementation notes:
- `var interaction = queryService.start(kbId, userId); var answer = queryService.ask(kbId, userId, interaction.id(), question);`
  with `kbId` and `userId` from identity and knowledge-base resolution.
- Tests: the handler calls `start` then `ask` with the resolved user and knowledge base and the returned interaction id;
  two consecutive `/ask` calls create two interactions (`start` called twice); `NotOwnerException` from `ask` maps to a
  fixed error reply.
- Document that each `/ask` is a one-turn interaction with no follow-up context (P18-10).

## Acceptance criteria
- [x] AC1: `/ask` is registered with a required autocompleted `kb` option and a required `question` string option.
- [x] AC2: For an owned knowledge base, the command calls `QueryService.start(kbId, userId)` then
  `ask(kbId, userId, interaction.id(), question)` and sends one reply with the answer text followed by the cited page
  paths (a "Sources:" list; none when empty).
- [x] AC3: Two `/ask` calls call `start` twice; the adapter holds no interaction state.
- [x] AC4: A non-UUID or not-owned `kb` replies "Unknown knowledge base." and `QueryService` is never called.
- [x] AC5: `NotOwnerException`/`NotFoundException` from `QueryService` and model-side failures map to fixed replies
  without the exception message.
- [x] AC6: The reply path is ephemeral (deferred ephemerally through the seam) and the text contains only the answer
  and cited paths.

## Test plan
- Unit (plain Mockito, no Spring context): `src/test/java/dev/mindforge/unit/discord/AskCommandTest.java` (new) with a
  mocked `QueryService`, a mocked/real `KnowledgeBaseService` and a recording fake `DiscordReply`; covers AC2–AC6.
- AC1: assert the command definition's options in the same test class.

## Files likely touched
- `src/main/java/dev/mindforge/discord/AskCommand.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordConfig.java`
- `src/test/java/dev/mindforge/unit/discord/AskCommandTest.java` (new)

## Out of scope
- Follow-up context across `/ask` calls, a `new` option, channel- or thread-keyed interactions.
- Conversation edits from Discord.

## Notes
Decided: the `question` option has max length 4000 because the web's `QuestionRequest` uses `@Size(max = 4000)`.
Decided: `NotOwnerException`/`NotFoundException` from `QueryService` reply "Unknown knowledge base." (the D03 text) because to the caller both mean the knowledge base is not theirs or is gone.
Decided: `AIGatewayUnavailableException`, `DeadlineExceededException` and `ModelOutputException` share one reply "The model is not available now. Try again later." because the web maps all three to one MODEL_UNAVAILABLE code; other failures fall to the gate's generic reply.
Decided: the one-turn note is in the `AskCommand` Javadoc; the Discord user docs are P18-10's.
Tester r1 AC map (AskCommandTest): AC1 definition_hasARequiredAutocompletedKbOptionAndARequiredQuestion; AC2 anOwnedKnowledgeBase_startsAnInteractionAsksOnceAndRepliesWithAnswerAndSources + anAnswerWithoutCitedPaths_hasNoSourcesList; AC3 twoAsks_startTwoInteractions; AC4 aKbValueThatIsNotAUuid_*, anotherUsersKnowledgeBase_*; AC5 anOwnershipFailureFromTheQuery_*, aModelFailure_*; AC6 throughTheGate_theReplyIsDeferredThroughTheEphemeralSeamThenCarriesOnlyTheAnswerAndPaths.
