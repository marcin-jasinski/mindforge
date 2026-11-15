---
id: P19-03
title: Gate every Slack slash command through the team allowlist, the rate limit and identity resolution
status: done
plan_tasks: [19.2, 19.3]
decisions: [D02, D07]
depends_on: [P19-01, P19-02]
size: L
commit: 712abae
fix_rounds: 0
issue: 47
---
## Goal
A Slack slash command passes one gate: team allowlist (workspace and caller team) → per-user rate limit → ack →
resolve or provision the caller with `ExternalIdentityService.resolve(SLACK, slackExternalId(callerTeam, userId), …)`
on a virtual thread → the command. Replies go through one private (ephemeral) reply seam. Bolt routes every
registered `SlackCommand` by name; this ticket adds the gate and the seam, the commands come in P19-04 and P19-05.

## Context
- P19-01 added `ExternalPlatform.SLACK` and `ExternalAccounts.slackExternalId(teamId, userId)`. P19-02 added
  `AppProperties.Slack` (tokens), `SlackConfig` (`@Profile("!cli")`, both tokens), `SlackBot` and the Socket Mode app.
- Mirror the Discord gate, but do not import it: `src/main/java/dev/mindforge/discord/DiscordCommandHandler.java`
  (gate order, `NOT_AVAILABLE = "This bot is not available here."`, `FAILED = "Something went wrong. Try again later."`,
  rate-limit text `"Rate limit reached. Try again in N s."`, unknown command → `FAILED`), `DiscordCall.java` (a call
  record translated out of the platform SDK), `DiscordCommand.java`, `DiscordReply.java`, `DiscordAllowlist.java`,
  `DiscordRateLimiter.java`, `DiscordListener.java`; tests in `src/test/java/dev/mindforge/unit/discord/`
  (`DiscordCommandHandlerTest`, `RecordingDiscordReply`, `DiscordRateLimiterTest`, `DiscordAllowlistTest`).
- **D01 adapter rule (binding; D01 is applied in P19-01):** every `ExternalIdentityService.resolve(ExternalPlatform.SLACK,
  …)` call uses `ExternalAccounts.slackExternalId(callerTeamId, userId)`, after the allowlist check, with the same
  caller team id the allowlist checks. Never build the key by hand. Unit test: the same user id under two team ids
  resolves through two different external ids.
- **Q09 (ADR 0032 § Consequences):** every reply goes only to the caller; one reply helper in the Slack adapter sets it;
  no public option. For a slash command use the `response_url` reply with `response_type: ephemeral` (or the ack body,
  which is ephemeral by default). Replies never carry reference answers or grounding excerpts.
- **Q10 (Phase 18 D11, ADR 0019):** a per-user limit in the adapter with `mindforge.rate-limits.requests` / `.window`,
  its own counter keyed by the Slack user id, checked after the allowlist and before identity resolution; fixed reply
  "Rate limit reached. Try again in N s."; no new settings; no Bolt types in the limiter; tested with a fake clock.
- Slack needs an ack within 3 s: the allowlist and rate-limit rejections may be the ack text itself; slow work
  (identity resolution, the command) runs on a virtual thread after the ack (an executor built in `SlackConfig` with
  `Executors.newVirtualThreadPerTaskExecutor()`, closed on shutdown) and answers through `response_url`. No reactive
  types; the bot opens no transaction; never call a model inside one.
- Slack message length: split long text into ordered parts under a fixed limit (constant in the reply seam); record
  the chosen limit and `response_url`'s per-command message cap in Notes.
- Fixed user-facing texts only, never an exception message (`docs/standards/global/error-handling.md`); logger `log`;
  records for values. ADR 0034 is already written; do not write a new ADR.

## Decisions to apply
**D02 — Decision:** Use B: a required, fail-closed `mindforge.slack.allowed-team-ids`. Admit a slash command or event
only when both the payload workspace (`team_id` / event `team`) and the caller's own team (event `user_team`; for a
slash command `user_team` when present, else `team_id`) are listed. Missing, blank or unlisted -> reject. Ignore
`enterprise_id`. Admit DMs. The check runs first, before rate limit, identity resolution and provisioning; the reply
names no workspace.

**D02 — Implementation notes:**
- `dev.mindforge.slack.SlackAllowlist`: `public record SlackAllowlist(Set<String> teamIds)`, compact ctor
  `teamIds = Set.copyOf(teamIds)`, `public boolean admits(String workspaceTeamIdOrNull, String callerTeamIdOrNull)` —
  true only when both are non-blank and in `teamIds`. Javadoc cites ADR 0034.
- `AppProperties.Slack`: `Set<String> allowedTeamIds = Set.of()` (`mindforge.slack.allowed-team-ids`, env
  `SLACK_ALLOWED_TEAM_IDS`); `@AssertTrue(message = "allowed-team-ids must not be empty when bot-token is set")`, same
  shape as `AppProperties.Discord`.
- Slash commands (`/mf-ask`, `/mf-quiz`): workspace = `team_id`; caller = `user_team` if present, else `team_id`.
  File/message events: workspace = event `team` (or envelope `team_id`); caller = event `user_team`; null -> reject
  (applied to the upload event in P19-07). No block-action handlers in Phase 19 (D05).
- Gate order per handler: allowlist -> rate limit -> ack/defer -> identity resolution/provisioning. Rejection reply is a
  fixed ephemeral text naming no workspace; log at INFO with a reason code, never team ids in the reply.
- Tests `SlackAllowlistTest` (plain JUnit, `make*` helpers, AssertJ): listed/listed admits; listed workspace + foreign
  caller team rejected; null and blank caller team rejected; unlisted workspace rejected; DM (same team both ids)
  admitted; `AppProperties.Slack` validation fails with token set and empty list, passes without token.

**D07 — Decision (rate limiter part; the quiz sessions part is P19-05):** Use A: copy the two helpers into the Slack
adapter as `SlackRateLimiter` (keyed by the Slack user id `String`) and `SlackQuizSessions` (keyed by internal user id
`UUID`), with copied unit tests. Do not change any Discord class or test. Do not import `dev.mindforge.discord` from
`dev.mindforge.slack`.

**D07 — Implementation notes (rate limiter):**
- `dev.mindforge.slack.SlackRateLimiter`: constructor `SlackRateLimiter(int limit, Duration window, Clock clock)`;
  `private record Window(long startMillis, int count)`; `Cache<String, Window>` with `expireAfterWrite(window)`;
  `OptionalLong tryAcquire(String slackUserId)` — same logic as DiscordRateLimiter.java:31-41 (retry seconds rounded
  up, minimum 1). Javadoc: say it copies the RateLimitInterceptor / DiscordRateLimiter window logic.
- Build the bean in the Slack `@Configuration` from the existing rate-limit properties, as DiscordConfig does; no new
  settings.
- Tests: `src/test/java/dev/mindforge/unit/slack/SlackRateLimiterTest`, copied from the Discord test, with a
  fixed/mutable `Clock`. Cover: under limit allowed; over limit returns retry seconds >= 1; window reset after the
  window passes; separate users have separate counters.
- No import of `dev.mindforge.discord` anywhere under `dev.mindforge.slack` (main or test).

## Acceptance criteria
- [x] AC1: `SlackAllowlist.admits(workspace, caller)` is true only when both ids are non-blank and listed; a DM (same
  team in both) is admitted; a foreign caller team, a null or blank caller team, or an unlisted workspace is rejected.
- [x] AC2: `AppProperties.Slack.allowedTeamIds` binds from `mindforge.slack.allowed-team-ids`
  (`${SLACK_ALLOWED_TEAM_IDS:}` in `application.yml`); validation fails with "allowed-team-ids must not be empty when
  bot-token is set" when the bot token is set and the list is empty; passes without a token.
- [x] AC3: `SlackRateLimiter` allows up to the limit per Slack user id per window, then returns retry seconds >= 1
  (rounded up); a new window resets; two users have separate counters.
- [x] AC4: `SlackCommandHandler` rejects a non-admitted call with the fixed text "This bot is not available here." and
  then calls neither the rate limiter nor `ExternalIdentityService` (nothing is provisioned); the log line has a reason
  code and the reply names no team.
- [x] AC5: Over the rate limit, the reply is "Rate limit reached. Try again in N s." and `ExternalIdentityService` is
  not called.
- [x] AC6: An admitted call is acknowledged before identity resolution; identity resolution calls
  `resolve(ExternalPlatform.SLACK, ExternalAccounts.slackExternalId(callerTeam, userId), displayName)` with the caller
  team the allowlist checked (`user_team` when present, else `team_id`); then the matching `SlackCommand` runs with the
  resolved internal user id. First contact provisions (through the real service contract).
- [x] AC7: The same Slack user id from two allowed caller teams resolves with two different external ids.
- [x] AC8: An unknown command name, or an unexpected exception from resolution or a command, gets the fixed text
  "Something went wrong. Try again later." and no exception message reaches Slack.
- [x] AC9: Every reply goes through the one `SlackReply` seam and is ephemeral (`response_type: ephemeral` or the
  ack body); long text is split into ordered parts under the fixed limit.
- [x] AC10: `SlackConfig` builds `SlackAllowlist`, `SlackRateLimiter`, `SlackCommandHandler` and registers each
  `SlackCommand` bean on the Bolt `App` by its slash-command name; slow work runs on a virtual-thread executor closed
  on shutdown. Without tokens or in `cli`, none of these beans exists (extend `SlackConfigTest`).
- [x] AC11: No class under `dev.mindforge.slack` (main or test) imports `dev.mindforge.discord`; no Bolt type is used
  in `SlackAllowlist`, `SlackRateLimiter` or the call record.

## Test plan
- AC1, AC2: unit `src/test/java/dev/mindforge/unit/slack/SlackAllowlistTest.java` (plain JUnit, `make*` helpers,
  AssertJ) plus the properties validation test from P19-02.
- AC3: unit `src/test/java/dev/mindforge/unit/slack/SlackRateLimiterTest.java` (mutable `Clock`).
- AC4–AC8: unit `src/test/java/dev/mindforge/unit/slack/SlackCommandHandlerTest.java` (Mockito
  `ExternalIdentityService`, a fake `SlackCommand`, a recording `SlackReply` test double, a direct executor).
- AC9: unit test of the reply seam (`src/test/java/dev/mindforge/unit/slack/`), with a fake response sender — no
  network.
- AC10: integration-tagged `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`.
- AC11: a test that scans `src/main/java/dev/mindforge/slack` and `src/test/java/dev/mindforge/unit/slack` for
  `dev.mindforge.discord`, or review.

## Files likely touched
- `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java`
- `src/main/resources/application.yml`
- `src/main/java/dev/mindforge/slack/SlackAllowlist.java` (new)
- `src/main/java/dev/mindforge/slack/SlackRateLimiter.java` (new)
- `src/main/java/dev/mindforge/slack/SlackCall.java` (new; Bolt-free call record)
- `src/main/java/dev/mindforge/slack/SlackCommand.java` (new)
- `src/main/java/dev/mindforge/slack/SlackCommandHandler.java` (new)
- `src/main/java/dev/mindforge/slack/SlackReply.java` (new) and its Bolt implementation (new)
- `src/main/java/dev/mindforge/slack/SlackConfig.java`, `SlackBot.java`
- `src/test/java/dev/mindforge/unit/slack/*` (new), `src/test/java/dev/mindforge/integration/slack/SlackConfigTest.java`

## Out of scope
- `/mf-ask`, `/mf-quiz`, the upload event handler and `SlackKnowledgeBases` (P19-04, P19-05, P19-07).
- Block actions, modals, `view_submission` handlers (none in Phase 19).
- `enterprise_id` handling, `users.info` lookups, a channel allowlist.
- A shared rate-limiter package or any change to Discord code or tests (D07).

## Notes
- Decided: reply part limit `BoltSlackReply.MAX_MESSAGE_LENGTH = 4000` chars (Slack's recommended maximum for a message `text`; the hard truncation is 40,000) because it keeps parts readable and leaves room under the `response_url` cap of 5 messages within 30 minutes per command (about 20,000 chars after the ack); the seam does not enforce the 5-message cap.
- Decided: a text sent before the ack becomes the ephemeral ack body (first part; any further parts go to `response_url`), and after `ack()` every part is posted to `response_url` with `response_type: ephemeral`, because this keeps all rejections inside the 3 s ack and gives one seam for both paths.
- Decided: `user_team` is read from the Socket Mode JSON body with Gson (Bolt's `SlashCommandPayload` has no such field); absent or JSON null -> `team_id`; present but not a string -> null, which the allowlist rejects, because a malformed caller team must fail closed.
- Decided: the command executor is a `defaultCandidate = false` bean named `slackCommandExecutor` (`destroyMethod = "close"`) because a plain `ExecutorService` bean would add a second candidate for the existing `Executor runExecutor` injection points.
- Decided: the rejection log line is `reason=team_not_allowed command=<name>` with no team ids, and a failed `response_url` post is logged (class or status only) and dropped, because no one else can be told.
- Decided: `SlackCommand.name()` returns the slash-command name with its slash (`/mf-ask`), the form Slack sends in `command` and Bolt routes on.
- Tester r1 AC map: AC1 SlackAllowlistTest; AC2 SlackPropertiesTest (allowedTeamIds_bind, tokensWithAnEmptyTeamList_fail, noTokenAndNoTeamList_passes); AC3 SlackRateLimiterTest; AC4 SlackCommandHandlerTest (rejection tests); AC5 aCallOverTheRateLimit...; AC6 anAdmittedCall...; AC7 theSameSlackUserFromTwoAllowedTeams...; AC8 unknownCommand/exception tests; AC9 BoltSlackReplyTest; AC10 SlackConfigTest; AC11 SlackAdapterBoundaryTest.
