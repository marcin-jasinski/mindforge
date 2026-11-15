---
id: P19-08
title: Record the Slack bot, its settings and its ADRs in the docs, open the live-check follow-up, and close Phase 19
status: ready
plan_tasks: [19.1, 19.2, 19.3]
decisions: [D16, D06]
depends_on: [P19-01, P19-02, P19-03, P19-04, P19-05, P19-06, P19-07]
size: S
commit:
fix_rounds: 0
issue: 52
---
## Goal
The docs describe the Slack bot as built: the plan's Phase 19 tasks and checklist are `[x]` with "As built" notes, the
roadmap, `docs/INDEX.md`, `deployment.md`, `env.example`, `architecture.md`, `tech-stack.md`, `hexagonal.md` and the
CLAUDE.md "Project State" paragraph reflect it, ADRs 0033–0037 are linked, and the live Socket Mode smoke test is a
human follow-up issue.

## Context
- Model: the Phase 18 close-out (commit `fe129f5`, "P18-10 Record the Discord bot, its settings and the account model
  in the docs, …"). Read `docs/project/implementation-plan.md` Phase 18 "As built" notes and `deployment.md`
  § "Discord bot" (lines ~85-125) and copy their shape.
- Files and current state:
  - `docs/project/implementation-plan.md:1642-1660`: `## [ ] Phase 19 — Slack Bot`, tasks 19.1–19.3, checklist.
  - `docs/project/roadmap.md`: Phase 19 status and the "Next" line (next is Phase 20).
  - `docs/INDEX.md:46`: the ADR paragraph ends with "0027–0030 and 0032 record the Discord bot (…)". Add 0033–0037
    for the Slack bot: 0033 identity keyed by home team and user id; 0034 fail-closed team allowlist on workspace and
    caller team; 0035 uploads are files sent to the bot in a DM; 0036 commands act on the caller's only knowledge base;
    0037 Socket Mode uses the Java-WebSocket backend. **The Socket Mode ADR is 0037** (it was renumbered from a
    duplicate 0033); fix any stale "0033" reference to the Socket Mode ADR you find in docs or code comments.
  - `docs/project/deployment.md`: add a "Slack bot" section next to "Discord bot".
  - `env.example:92-108` holds the Discord block; add a Slack block after it.
  - `docs/project/architecture.md`: line ~32 lists adapter packages `api`, `cli`, `discord`; line ~113 "Slack |
    Phase 19 | `/mf-ask`, `/mf-quiz`, file upload over the same services" — update to as built; add the command data
    flow, the trust boundary (Slack → bot over Socket Mode; file download from `files.slack.com`) and the external
    systems table row.
  - `docs/standards/architecture/hexagonal.md:15`: adapter package list — add `slack`.
  - `docs/project/tech-stack.md` "Key Dependencies Summary": add the Bolt row.
  - `CLAUDE.md`: edit **only** the "Project State" paragraph (work resumes at Phase 20). Do not edit the layer table;
    the report asks the human to add `discord` and `slack` to it.
- **Do not write new ADRs**: ADRs 0033–0037 already exist in `docs/adr/`. Link them only.
- As-built facts to record (check them against the code before writing): settings `SLACK_BOT_TOKEN`,
  `SLACK_APP_TOKEN` (both required together; secrets), `SLACK_ALLOWED_TEAM_IDS` (required with the token; fail closed;
  ADR 0034); commands `/mf-ask <question>` (one-turn Query), `/mf-quiz start`, `/mf-quiz answer <text>`; upload = one
  file sent to the bot in a DM, optional text `lesson_id=<value>` and `new_version` (ADR 0035); the only knowledge base
  rule (ADR 0036); ephemeral replies (ADR 0032); per-user rate limit on `mindforge.rate-limits.*` with its own counter;
  account `slack-<team>-<user>@users.invalid`, separate from Discord and web, cannot sign in to the web (ADR 0027,
  ADR 0033); no reminders for Slack users; Slack app setup: Socket Mode on, app-level token with
  `connections:write`, bot scopes `commands`, `chat:write`, `im:history`, `files:read`, event subscription
  `message.im`, App Home messages tab enabled, slash commands `/mf-ask` and `/mf-quiz`; Slack uploads are scanned for
  article links like other uploads.
- Follow `docs/agents/issue-tracker.md` for the GitHub issue (repo `marcin-jasinski/mindforge`, label
  `ready-for-human`). Write docs in ASD-STE100 Simplified Technical English where the doc already uses it.

## Decisions to apply
**D16 — Decision:** Use A: tick "Bot connects via Socket Mode and responds to commands" on fake-driven tests. Prove
that the config builds and starts the Socket Mode app with the app-level token (fake client, no network) and that every
command handler responds (unit tests). Record a live smoke test against a real Slack workspace as a human follow-up:
in the implementation-plan.md Phase 19 "As built" notes, in deployment.md, and as a GitHub issue.

**D16 — Implementation notes (docs part):**
- deployment.md Slack section: add "A live test against real Slack is not automated: it needs a Slack app, a bot token,
  an app-level token and a test workspace."
- implementation-plan.md Phase 19 As built: "A live smoke test against real Slack is a human follow-up."
- Open a GitHub issue for the live smoke test (label `ready-for-human`). The issue must say that fake-driven tests do
  not prove the real Socket Mode handshake or the Java-WebSocket backend (ADR 0037) against Slack.

**D06 — Implementation notes (docs part):**
- docs/project/tech-stack.md "Key Dependencies Summary": one row for `bolt` + `bolt-socket-mode` + `Java-WebSocket`
  (purpose, package `dev.mindforge.slack`, ADR 0037). docs/INDEX.md ADR paragraph: add 0037.

Also from D01: link ADR 0033 from `docs/INDEX.md`; from D03: link 0033–0037 in `docs/INDEX.md`.

## Acceptance criteria
- [ ] AC1: `implementation-plan.md` Phase 19 heading, tasks 19.1–19.3 and both checklist items are `[x]`, each task
  with an "As built" note that names the classes and tests, and the note "A live smoke test against real Slack is a
  human follow-up." with the issue number.
- [ ] AC2: `roadmap.md` marks Phase 19 done and its "Next" line names Phase 20; CLAUDE.md "Project State" says work
  resumes at Phase 20 (no other CLAUDE.md change).
- [ ] AC3: `docs/INDEX.md` ADR paragraph links 0033–0037 with one-phrase summaries; no doc names the Socket Mode ADR
  as 0033.
- [ ] AC4: `deployment.md` has a "Slack bot" section with the settings table (`SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN`,
  `SLACK_ALLOWED_TEAM_IDS`), the Slack app setup (Socket Mode, scopes, event, App Home), commands, upload grammar,
  account model, rate limit, ephemeral replies and the "live test is not automated" sentence; `env.example` has the
  matching Slack block with blank secrets and SECRET markers.
- [ ] AC5: `architecture.md` has the as-built Slack adapter row, the command data flow, the trust boundary and the
  external systems row; `hexagonal.md` and `architecture.md` adapter lists include `slack`; `tech-stack.md` has the Bolt
  row citing ADR 0037.
- [ ] AC6: A GitHub issue for the live Socket Mode smoke test exists with label `ready-for-human`, and its number is in
  the plan's As built note.
- [ ] AC7: No new ADR file is added; the gate `mvn -B verify -DskipFrontend=true` passes (known baseline failure
  `ApiFlowTest.theLoginRouteIsTheSinglePageApp` excepted).

## Test plan
- Docs only: review against the code (settings names in `application.yml`, class names under `dev.mindforge.slack`,
  test classes under `src/test/java/dev/mindforge/unit/slack/` and `integration/slack/`); `grep -rn "0033"` in `docs/`
  to confirm AC3; run the gate for AC7.

## Files likely touched
- `docs/project/implementation-plan.md`, `docs/project/roadmap.md`, `docs/INDEX.md`, `docs/project/deployment.md`,
  `env.example`, `docs/project/architecture.md`, `docs/project/tech-stack.md`,
  `docs/standards/architecture/hexagonal.md`, `CLAUDE.md` (Project State only), `CONTEXT.md` (only if P19-01 did not
  already add the Slack key shape and it defines external identity)

## Out of scope
- New ADRs; edits to ADRs 0033–0037 other than fixing a stale number.
- The CLAUDE.md layer table (human task; mention it in the run report).
- Code changes.

## Notes
