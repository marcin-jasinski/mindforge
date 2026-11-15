# Phase 19 — Slack Bot: draft breakdown

Plan tasks: 19.1 (`UploadSource.SLACK`), 19.2 (`SlackBot` in `dev.mindforge.slack`: Socket Mode, `/mf-ask`,
`/mf-quiz`, file upload handler, workspace allowlist, identity via `ExternalIdentityRepository`), 19.3 (tests:
allowlist enforcement, identity resolution, ownership check), completion checklist (CL1: bot connects via Socket Mode
and responds to commands; CL2: identity resolution reuses the same `ExternalIdentityRepository` as Discord), and
close-out docs. Tests ship with the item that adds the behaviour (19.3 is spread over W01, W04, W05, W06, W07).
`UploadSource.SLACK` lands with its producer in W07 (phase-15 D06).

Shape mirrors Phase 18: a thin Bolt layer translates Slack payloads into a Bolt-free call record; a gate
(allowlist → rate limit → ack → resolve identity → command) dispatches to command classes; replies go through one
private-reply seam. Unit tests drive the gate and commands with Mockito; no test connects to Slack.

| Id | Intent | Plan tasks | Size | Depends on | Questions |
|---|---|---|---|---|---|
| W01 | `ExternalPlatform.SLACK("Slack")` and the Slack external id format per Q01 (and the `ExternalAccounts` rule change if Q01 needs a separator); tests: synthetic email for a Slack id, `ExternalIdentityService` provisions a Slack user + "Slack" knowledge base, adapter integration test stores and finds a `SLACK` link next to a `DISCORD` one | 19.2, 19.3 (identity), CL2 | S | — | Q01, Q14 |
| W02 | Bolt + Socket Mode dependencies per Q06 (`pom.xml` order keeps `okhttp-jvm` 5 first; `tech-stack.md`), `AppProperties.Slack` (bot token, app token, `allowed-team-ids` and any Q02 fields, fail-closed `@AssertTrue`), `SlackConfig` (`@Profile("!cli")`, only with both tokens), `SlackBot` lifecycle (start Socket Mode app, stop on close); tests: properties validation, wiring without tokens or in `cli` → no Slack beans, lifecycle starts/stops a fake Socket Mode client | 19.2, CL1 | M | — | Q06, Q13, Q16 |
| W03 | *(only if Q07 picks B)* Extract the platform-neutral rate limiter and current-quiz cache into a shared adapter-support package; Discord switched over with unchanged behaviour; tests moved | 19.2 | S | — | Q07 |
| W04 | Gate: `SlackAllowlist` per Q02 (pure value), rate limiter (Q07/Q10), `SlackCommandHandler` (allowlist → rate limit → ack within 3 s → resolve identity with `ExternalIdentityService` on a virtual thread → command), private reply seam (Q09), knowledge base resolution per Q03 with `requireOwner`; tests: non-allowed workspace (and per Q02, external org / DM) rejected with a fixed reply and nothing provisioned, rate limit rejects before resolution, first contact provisions, a foreign or unknown knowledge base refused | 19.2, 19.3 (allowlist, identity, ownership), CL1 | L | W01, W02, W03? | Q01, Q02, Q03, Q07, Q09, Q10 |
| W05 | `/mf-ask`: one-turn Query (`QueryService.start` + `ask`), answer with cited page paths, split to Slack's limits, fixed error texts; tests: calls with the resolved user and knowledge base, two asks → two interactions, refusal → fixed text | 19.2, CL1 | S | W04 | Q03, Q11 |
| W06 | `/mf-quiz` per Q05 over `QuizService` (`start`, `next`, `answer`), current quiz per D05/Q12; only `NextQuestion`/`Graded` text in replies; tests: full flow, no active quiz, user A cannot answer into B's session through the real `QuizService` ownership check | 19.2, 19.3 (ownership), CL1 | M | W04 | Q03, Q05, Q12 |
| W07 | File upload handler per Q04: `UploadSource.SLACK` + `ArticleFetcher.SCANNED_SOURCES` (Q15), one Slack downloader (https, fixed `files.slack.com` host list, bearer token only to that host, declared-size check, capped read; Q08), `OutboundHttpGuardTest` allows that one file by exact path, `IngestionService.ingest` with `lesson_id`/`new_version`, fixed refusal texts; tests: queued reply, too large, wrong host refused before any request, refused MIME, lesson collision, guard test case for another Slack file | 19.1, 19.2, CL1 | M | W04 | Q03, Q04, Q08, Q15 |
| W08 | Docs: plan 19.x and checklist `[x]` with "As built", roadmap, `deployment.md` + `env.example` (`SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN`, allowlist, Slack app scopes/events for Socket Mode), `architecture.md` (adapter row, command data flow, trust boundary, external systems), `tech-stack.md`, `hexagonal.md` adapter list, `docs/INDEX.md`, CLAUDE.md "Project State", ADRs for Q01/Q02/Q03/Q04 as decided; live Socket Mode smoke test recorded as a human follow-up (Q16); report asks the human to add `discord` and `slack` to CLAUDE.md's layer table | close-out, CL1 | S | W01–W07 | Q01–Q04, Q16 |

Notes:
- W01 and W02 can start at once and in parallel; W03 (if any) too.
- W05, W06 and W07 are independent after W04 and can run in parallel.
- If Q03 picks a Block Kit modal (A/B) or Q04/Q05 pick modal or button flows, W04 also carries the Bolt
  `view_submission` / `block_actions` routing; W06/W07 grow to M/L.
- W02 adds new Maven artifacts: the offline `fast` command needs them resolved once online.
