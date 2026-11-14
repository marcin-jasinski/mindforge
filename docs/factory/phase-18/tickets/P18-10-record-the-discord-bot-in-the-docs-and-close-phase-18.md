---
id: P18-10
title: Record the Discord bot, its settings and the account model in the docs, open the linking follow-up, and close Phase 18
status: ready
plan_tasks: [18.1, 18.2, 18.3, 18.4]
decisions: [D02, D12]
depends_on: [P18-01, P18-02, P18-03, P18-04, P18-05, P18-06, P18-07, P18-08, P18-09]
size: S
issue: 42
commit:
fix_rounds: 0
---
## Goal
The docs describe the Discord bot as built: plan tasks 18.1–18.4 and the Phase 18 heading are `[x]` with "As built"
notes, deployment lists every new setting, architecture and standards name the new adapter, and the account-linking gap
is visible with a follow-up issue.

## Context
- Factory `docs_to_update` (`.claude/factory.md`): `docs/project/implementation-plan.md`, `docs/project/roadmap.md`,
  `docs/INDEX.md` (only if a description changes), `docs/project/deployment.md`, `CLAUDE.md` (only the "Project State"
  paragraph). Scope also names `docs/project/architecture.md` (driving adapter `dev.mindforge.discord`, data flow:
  slash event -> allowlist -> rate limit -> identity resolution -> application service; reminders),
  `docs/project/tech-stack.md` ("Key Dependencies Summary": JDA, why, audio excluded, OkHttp/Jackson 2 note),
  `docs/standards/architecture/hexagonal.md` (adapter package list gains `dev.mindforge.discord`), `env.example`.
- ADRs for this phase are already written: 0027 (D01 account model), 0028 (D04 allowlist), 0029 (D07 attachment
  egress), 0030 (D03 knowledge-base option and default KB), 0032 (D14 ephemeral replies). Link them; do not rewrite them.
  `web-security.md` already names ADR 0029 (P18-08).
- Settings to document in `deployment.md` and `env.example`: `DISCORD_BOT_TOKEN` (secret; bot off when blank),
  `MINDFORGE_DISCORD_ALLOWED_GUILD_IDS` (required with the token; startup fails when empty),
  `MINDFORGE_DISCORD_ALLOWED_ROLE_IDS` (optional; empty = any member), `mindforge.discord.reminder-cron` /
  `reminder-zone` with their env names and defaults (`0 0 9 * * *`, `UTC`); the bot shares `RATE_LIMIT_REQUESTS` /
  window with the web (separate counter); the bot never runs in the `cli` profile; commands are guild-only and DMs are
  outbound only (reminders); all replies are ephemeral (history lives in the web app); each `/ask` is a one-turn Query
  interaction with no follow-up context (D06); a lost quiz pointer after restart needs `/quiz start` again (D05);
  a live smoke test against real Discord is a human follow-up (needs a token and a test guild).
- CLAUDE.md's layer table is outside the allowed CLAUDE.md edit: do not change it; the factory report asks the human to
  add `dev.mindforge.discord` there (answered question Q09).
- Use ASD-STE100 style where the docs already do; CONTEXT.md vocabulary.

## Decisions to apply
**D02 — Linking to an existing account.** Decision: Phase 18 does not link a Discord user to an existing web account;
`link(...)` is not built. Record the omission in plan 18.1 "As built" and open a follow-up GitHub issue for a verified
linking flow. A Discord user has a separate, auto-provisioned account (D01, ADR 0027).
Implementation notes:
- Plan 18.1 "As built": "`link(...)` not built: no Phase 18 command calls it (minimal-implementation.md). A Discord user
  has a separate account (ADR 0027). Linking to a web account needs a verified flow and a data policy for the
  provisioned account; see issue #<n>."
- Open a GitHub issue (label `needs-triage`): "Link a chat-platform identity to an existing web account". Record:
  verified ownership on both sides (prefer platform OAuth or a hashed, single-use, short-TTL, rate-limited code), never
  by email claim; a rule for a provisioned account that owns data (refuse or explicit move, never silent loss); shared
  by Discord and Slack (Phase 19). If the issue cannot be opened from this environment, write the issue text into this
  ticket's Notes and leave `#<n>` for the human.
- `docs/project/deployment.md` (user notes): a Discord user gets a separate account (`discord-<id>@users.invalid`) that
  cannot see a web account's knowledge bases.
- `docs/project/roadmap.md`: list "account linking (bot <-> web)" as an open follow-up.

**D12 — Discord uploads and article fetch.** Docs part: note in the article-fetch docs (Phase 17 sections in
`deployment.md`/`architecture.md`) that Discord uploads are scanned like API/CLI uploads, only when
`mindforge.articles.fetch-external-articles` is on.

## Acceptance criteria
- [ ] AC1: Plan tasks 18.1–18.4 and the Phase 18 heading are `[x]` with "As built" notes naming the real classes, the
  V8 migration, the decisions/ADRs, and the D02 omission text with the issue number (or placeholder).
- [ ] AC2: `roadmap.md` marks Phase 18 done, sets "Next" to Phase 19, and lists account linking as an open follow-up and
  the live Discord smoke test as a human follow-up.
- [ ] AC3: `deployment.md` and `env.example` list every setting above with defaults and the behaviour notes above,
  including the separate-account note; the token is marked secret.
- [ ] AC4: `architecture.md` describes the Discord driving adapter and its data flow; `tech-stack.md` lists JDA;
  `hexagonal.md` lists `dev.mindforge.discord` as an adapter package; the article-fetch docs mention Discord uploads.
- [ ] AC5: CLAUDE.md "Project State" says work resumes at Phase 19; no other CLAUDE.md line changes.
- [ ] AC6: `docs/INDEX.md` is updated only if a listed file's description changed; all links resolve.

## Test plan
- Docs only: review. The full gate (`mvn -B verify -DskipFrontend=true`) must stay green (no code change).

## Files likely touched
- `docs/project/implementation-plan.md`, `docs/project/roadmap.md`, `docs/project/deployment.md`, `env.example`
- `docs/project/architecture.md`, `docs/project/tech-stack.md`, `docs/standards/architecture/hexagonal.md`
- `docs/INDEX.md` (if needed), `CLAUDE.md` ("Project State" only)

## Out of scope
- CLAUDE.md's layer table (human edit). New ADRs (already written). Any code change.

## Notes
