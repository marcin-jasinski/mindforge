# Factory run report — Phase 19 (Slack Bot)

- **Run:** phase-19 · started/finished: see journal.md
- **Outcome:** all 8 tickets done on the branch; not merged by this report (docs step only, no commit made)
- **Branch:** `factory/phase-19` · base 1ac485f

## Audit first

1. D16 (Q16) was a planner blocker re-run as a low-impact fast-path question; method `judge`, one ballot (pragmatist, sonnet). The completion item "Bot connects via Socket Mode and responds to commands" is ticked on fake-driven tests only. No live Slack handshake was proven (issue #53).
2. ADR numbers collided when parallel judges wrote them. The orchestrator renumbered the Socket Mode ADR to 0037 (0033 identity, 0034 allowlist, 0035 DM uploads, 0036 only knowledge base). Check that ADR file names, ADR cross-references and docs/INDEX.md agree.
3. Holistic review deferred findings H1 and H2 (see below): ADR 0033 disagrees with the code on the `user_team` fallback, and web-security.md does not list `SlackFiles`.
4. Baseline gate failure `ApiFlowTest.theLoginRouteIsTheSinglePageApp` exists before the run (frontend skipped). Failsafe fails the build, so the coverage check may not have run in the gate. Run the coverage gate by hand.
5. D01 and D07 were majority decisions (dissent: pragmatist on D01, architect on D07).

## Tickets

| Ticket | Title | Status | Commit | Fix rounds | Notes |
|---|---|---|---|---|---|
| P19-01 | Slack platform and home-team-and-user identity key | done | f446463 | 0 | D01 |
| P19-02 | Bolt, Java-WebSocket Socket Mode, start only with both tokens | done | 5dbc2b7 | 0 | D06, D16 |
| P19-03 | Command gate: allowlist, rate limit, identity | done | 712abae | 0 | D02, D07 |
| P19-04 | /mf-ask on the only knowledge base | done | 804be1b | 0 | D03 |
| P19-05 | /mf-quiz start and answer | done | 3339b7e | 0 | D05, D07 |
| P19-06 | Slack file downloader | done | 3c027aa | 0 | no decision |
| P19-07 | DM upload handler | done | 2bf02ec | 0 | D04 |
| P19-08 | Docs and close-out | done | f776810 | 0 | D16, D06 |

No ticket is parked.

## Decisions

| Id | Question | Method | Class | Decision (one line) | Dissent / risk to watch |
|---|---|---|---|---|---|
| D01 | Slack identity key | majority | adr | `external_id = <home team>-<user id>` | Pragmatist (B): hyphen rule must stay SLACK-only, built by one helper |
| D02 | Workspace allowlist | unanimous | adr | Fail-closed `allowed-team-ids`; workspace and caller team both listed | `team_id` fallback for slash commands if Slack lets external members run them |
| D03 | Knowledge base selection | unanimous | adr | Act on the caller's only knowledge base; refuse with 0 or several | #43 must add a selection when a second one exists |
| D04 | File upload path | unanimous | adr | One file sent to the bot in a DM | Reopen if DMs rejected or a modal is chosen |
| D05 | Quiz command shape | unanimous | local | `/mf-quiz start` and `/mf-quiz answer <text>`, ephemeral | Long answers; add a modal later |
| D06 | Socket Mode backend | unanimous | adr | Java-WebSocket backend, no Tyrus | Less field use with Bolt; reconnect trouble reopens it |
| D07 | Shared code with Discord | majority | local | Copy rate limiter and quiz sessions into the Slack adapter | Architect (B): window logic now exists three times |
| D16 | Live Socket Mode check | judge | local | Tick on fake-driven tests; live smoke test is a human follow-up | Fake tests do not prove the real handshake |

Method counts: unanimous 5 · majority 2 · debate 0 · judge 1. Blockers: 1 (Q16, re-run as fast path). Q08–Q15 were answered in the plan or scope without separate decision files.

## Decision outcomes

| Decision | Method | Vote changes | Models (panel / judge) | Tickets applying it (status, fix rounds) | Findings / rulings citing it |
|---|---|---|---|---|---|
| D01 | majority | none | opus x3 / opus | P19-01 done 0 | holistic H1 (ADR 0033 text vs code) |
| D02 | unanimous | none | opus x3 / opus | P19-03 done 0 | none found |
| D03 | unanimous | none | opus x3 / opus | P19-04 done 0 | none found |
| D04 | unanimous | none | opus x3 / opus | P19-07 done 0 | none found |
| D05 | unanimous | none | sonnet x3 / opus | P19-05 done 0 | none found |
| D06 | unanimous | none | sonnet x3 / opus | P19-02 done 0, P19-08 done 0 | none found |
| D07 | majority | none | sonnet x3 / opus | P19-03 done 0, P19-05 done 0 | none found |
| D16 | judge | none | sonnet x1 / opus | P19-02 done 0, P19-08 done 0 | none found |

## Panel statistics

Decisions per method: unanimous 5, majority 2, judge 1 · round-2 debates 0 · vote changes 0 · ballots discounted for false evidence 0 as a `counted: no` row. Two claims were noted in tallies but did not discount a ballot: in Q07 the architect's false claim "Discord has only DiscordConfigWiringTest" weakened B; in Q06 an unverified Tyrus/jakarta clash claim was treated as a soft risk. Disputed findings: no `rulings/` directory exists, so valid 0, invalid 0, defer 0.

## ADRs written

- 0033 Slack identity keyed by home team and user id (D01)
- 0034 Slack commands admitted when workspace and caller team are allowed (D02)
- 0035 Slack uploads are files sent to the bot in a direct message (D04)
- 0036 Slack commands act on the caller's only knowledge base (D03)
- 0037 Slack Socket Mode uses the Java-WebSocket backend (D06; renumbered by the orchestrator after a collision)

All are listed in docs/INDEX.md.

## Deferred findings

Holistic review (reviews/holistic.md), verdict approve:
- H1 (minor, 0.8): ADR 0033 line 6 says an event's home team is `user.team_id` with a payload `team_id` fallback. The code (`SlackMessageListener.toMessage`) and ADR 0034 use the event's `user_team` only, with no fallback. Fix: edit ADR 0033.
- H2 (minor, 0.75): docs/standards/security/web-security.md:75-77 does not list `DiscordAttachments` and `SlackFiles` in the HTTP-client exceptions. Fix: add both and one sentence on the Slack download rules.
- H3 (nit, 0.9): docs/project/architecture.md:80 lacks a comma after "`SecurityConfig`".

Per-ticket review files were not scanned for other minor findings beyond the holistic review.

## Manual checks still open

- Live smoke test against a real Slack workspace (Slack app, bot token, app-level token): issue #53, label ready-for-human. It must prove the Socket Mode handshake and the Java-WebSocket backend (ADR 0037).
- Add `discord` and `slack` to the layer table in CLAUDE.md (human follow-up).
- Run the coverage gate by hand (see Audit first, item 4).

## Statistics

Tickets: 8 done, 0 parked · fix rounds 0 in total · disputed findings 0 · decisions: unanimous 5, majority 2, judge 1.
Forced continuations: 4 (stalled 0). Agent calls: 67 in total.

| Role | Model | Calls |
|---|---|---|
| planner | opus | 1 |
| solver | opus | 12 |
| solver | sonnet | 10 |
| judge | opus | 8 |
| ticket-writer | opus | 1 |
| implementer | default | 8 |
| tester | sonnet | 5 |
| reviewer | sonnet | 6 |
| reviewer | opus | 15 |
| scribe | sonnet | 1 |
