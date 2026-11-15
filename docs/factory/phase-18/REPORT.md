# Factory run report — Phase 18 (Discord Bot)

- **Run:** phase-18 · started 2026-11-14T20:44Z · finished: 2026-11-15T10:31:35Z
- **Outcome:** merged into `develop` (1ac485f). All 10 tickets are done. The integration gate was green except the baseline failure ApiFlowTest.theLoginRouteIsTheSinglePageApp.
- **Branch:** `factory/phase-18` · base ac2851dfb2183a90b238b72accab71aa94fed52e

## Audit first

1. Two decisions were made by the `judge` method with one ballot only. Check them first:
   - D06: each `/ask` starts a new Query interaction. The Discord adapter keeps no state between questions.
   - D12: `UploadSource.DISCORD` is added to `ArticleFetcher.SCANNED_SOURCES`. A Discord upload can fetch linked articles when `mindforge.articles.fetch-external-articles` is on (default `false`).
2. A Discord user has a separate account (ADR 0027). It has no link to a web account, and it cannot sign in to the web. Account linking was not built (D02). Follow-up issue: #43. The ephemeral replies (ADR 0032) are the only copy of an answer until #43 is done.
3. Add `dev.mindforge.discord` to the layer table in `CLAUDE.md` (decision Q09). This run did not change that table, because only the "Project State" paragraph may change.
4. Do a live smoke test of the bot with a real Discord token and a test guild. No automated test reaches real Discord.
5. Five ADRs were written: 0027, 0028, 0029, 0030 and 0032. There is no ADR 0031. The number is skipped.

## Tickets

| Ticket | Title | Status | Commit | Fix rounds | Notes |
|---|---|---|---|---|---|
| P18-01 | External identity links, `ExternalIdentityRepository`, V8 table | done | 547afed | 0 | |
| P18-02 | Reserved `users.invalid` email domain | done | 6529963 | 0 | |
| P18-03 | Resolve or provision an external identity | done | d5f4ef5 | 0 | |
| P18-04 | Discord settings, allowlist, rate limit, JDA lifecycle | done | ae61629 | 0 | |
| P18-05 | Command gate, ephemeral replies, `kb` option | done | a92a162 | 1 | One blocker finding was ruled invalid |
| P18-06 | `/ask` over `QueryService` | done | 83cf3af | 0 | |
| P18-07 | `/quiz start` and `/quiz answer` over `QuizService` | done | b744a71 | 0 | |
| P18-08 | `/upload` from Discord CDN hosts only | done | c5afdc9 | 1 | |
| P18-09 | Daily DM reminders for due Flashcards | done | 46a8ebb | 0 | |
| P18-10 | Docs and close of Phase 18 | done | fe129f5 | 0 | |

Whole-run review fixes are in commit 288d993 (holistic findings H1 and H2). H3 (no ADR 0031) was not fixed.

## Decisions

| Id | Question | Method | Class | Decision (one line) | Dissent / risk to watch |
|---|---|---|---|---|---|
| D01 | Account for a Discord user | unanimous | adr | Provision a `discord-<id>@users.invalid` user on first contact | |
| D02 | Link to an existing account | unanimous | local | Do not build linking; open a follow-up issue | Web history is unreachable (#43) |
| D03 | Knowledge base a command acts on | majority | adr | Required `kb` option with autocomplete; `requireOwner`; default knowledge base | skeptic chose E |
| D04 | Guild and role allowlist | unanimous | adr | Fail closed: guild list required when the token is set; role list optional | |
| D05 | Address of a Discord quiz session | unanimous | local | Caffeine cache keyed by `userId`, value `(kbId, sessionId)` | |
| D06 | `/ask` continuity | judge | local | Each `/ask` starts a new Query interaction | One ballot only |
| D07 | Attachment download and egress | majority | adr | JDA client; `https`; fixed CDN host list; declared size checked | skeptic chose C |
| D10 | Reminder job details | unanimous | local | Cron `0 0 9 * * *` in UTC; one DM per user | |
| D11 | Rate limit for bot commands | unanimous | local | Per-user Caffeine window; same settings as the API | |
| D12 | Discord uploads and article fetch | judge | local | Add `UploadSource.DISCORD` to `SCANNED_SOURCES` | One ballot only; fetch off by default |
| D14 | Reply visibility | unanimous | adr | Every reply is ephemeral | |

Method counts: unanimous 7 · majority 2 · debate 0 · judge 2. Blockers: 0.

## Decision outcomes

| Decision | Method | Vote changes | Models (panel / judge) | Tickets applying it (status, fix rounds) | Findings / rulings citing it |
|---|---|---|---|---|---|
| D01 | unanimous | none | opus x3 / opus | P18-02 (done, 0), P18-03 (done, 0) | none |
| D02 | unanimous | none | opus x3 / opus | P18-01 (done, 0), P18-10 (done, 0) | P18-01 and P18-10 spec-security (checked, no defect); holistic H1 (fixed in 288d993) |
| D03 | majority | none | opus x3 / opus | P18-03 (done, 0), P18-05 (done, 1) | P18-05 spec-security (checked, no defect) |
| D04 | unanimous | none | opus x3 / opus | P18-04 (done, 0) | P18-04 spec-security (checked, no defect) |
| D05 | unanimous | none | sonnet x3 / opus | P18-07 (done, 0) | none |
| D06 | judge | none | sonnet (one ballot) / opus | P18-06 (done, 0) | none |
| D07 | majority | none | opus x3 / opus | P18-08 (done, 1) | none |
| D10 | unanimous | none | sonnet x3 / opus | P18-09 (done, 0) | none |
| D11 | unanimous | none | sonnet x3 / opus | P18-04 (done, 0), P18-05 (done, 1) | none |
| D12 | judge | none | sonnet (one ballot) / opus | P18-08 (done, 1), P18-10 (done, 0) | P18-10 correctness (minor): deployment.md wrongly says a CLI upload is scanned (not fixed) |
| D14 | unanimous | none | sonnet x3 / opus | P18-05 (done, 1) | P18-05 spec-security (checked); holistic H1 (ADR 0032 updated) |

## Panel statistics

Decisions per method: unanimous 7, majority 2, judge 2, debate 0. Round-2 debates: 0. Vote changes: 0. Ballots discounted for false evidence: 0 (no `counted: no` row in any tally). Disputed findings: valid 0, invalid 1 (P18-05 correctness C1: JDA always enables the GUILDS intent, and the proposed fix did not compile), defer 0.

Four questions (Q08, Q09, Q13, Q15) did not go to the panel and have no decision file.

## ADRs written

- 0027 Bot users provisioned with a reserved invalid email (D01)
- 0028 Discord commands admitted by a fail-closed guild and role allowlist (D04)
- 0029 Discord attachments downloaded by the platform client from a fixed CDN host list (D07)
- 0030 Discord commands name the knowledge base, and provisioning creates a default one (D03)
- 0032 Discord replies are ephemeral (D14)

There is no ADR 0031. `docs/INDEX.md` lists "0027–0030 and 0032".

## Deferred findings

Minor findings not fixed:

- P18-01: `ExternalIdentityRepositoryAdapter.java:54` reports every `DataIntegrityViolationException` as a conflict, not only unique violations. (A related major, confidence 0.6, was deferred by the triage rules: `createUserAndLink` merges when given an existing user id. P18-03 always sends a new random id, so this path does not occur today.)
- P18-03: `ExternalIdentityService.java:68` cuts the display name to 255 chars after `singleLine`, so it can end in a space.
- P18-04: `DiscordBot.java:26` calls `jda.awaitReady()` with no limit in an `ApplicationRunner`; a slow gateway delays `ApplicationReadyEvent`.
- P18-04: `DiscordConfigWiringTest.java` is a no-Spring unit test outside the `unit/` tree.
- P18-05: `DiscordCommandHandler.java:63` replies on an unacknowledged interaction when `defer()` fails.
- P18-05: `DiscordListener.java:38` lets a `kb` autocomplete exception escape.
- P18-07: `QuizCommand.java:117` reports a `NotFoundException` from `next(...)` as "No active quiz" after a recorded answer.
- P18-08: `DiscordAttachments.java:50` does not cancel the download future on interrupt.
- P18-08: a null attachment content type is refused; no fallback to the file extension.
- P18-10: `deployment.md:85` wrongly lists the CLI as scanned for article URLs.
- Holistic H3 (nit): the ADR numbering gap at 0031.

## Manual checks still open

- Live smoke test of the Discord bot with a real token and test guild: `/ask`, `/quiz start`, `/quiz answer`, `/upload`, the reminder DM, and rejection of a non-allowed guild.
- Add `dev.mindforge.discord` to the layer table in `CLAUDE.md` (Q09).
- Issue #43 (account linking) stays open.
- Phase 14 still has its manual Langfuse check open (not from this run).

## Statistics

Tickets: 10 done, 0 parked. Total fix rounds: 2 (P18-05, P18-08), plus one whole-run fix commit. Disputed findings: 1, ruled invalid. Decisions: 11 (unanimous 7, majority 2, judge 2).

Agent calls (hook state at scribe time), 84 in total: planner@opus 1, solver@opus 14, solver@sonnet 13, judge@opus 12, ticket-writer@opus 1, implementer@default 10, tester@sonnet 6, reviewer@opus 15, reviewer@sonnet 8, fixer@default 3, scribe@sonnet 1. Forced continuations: 2. Stalled: 0.
