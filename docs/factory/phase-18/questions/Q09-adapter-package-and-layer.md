---
id: Q09
status: answered
impact: low
blocks: [W04, W11]
answered_by: docs/project/implementation-plan.md#Phase 18 — Discord Bot (18.2 "`DiscordBot` (`dev.mindforge.discord`)"); docs/project/architecture.md#Driving Adapters ("Discord / Slack | Phases 18–19")
---
## Question
Where does the bot code live, and what may it import?

## Context
Plan 18.2 names `dev.mindforge.discord`. `docs/standards/architecture/hexagonal.md` and CLAUDE.md's layer table list
only `dev.mindforge.api` and `dev.mindforge.cli` as adapter packages; `architecture.md` § Driving Adapters already lists
"Discord / Slack | Phases 18–19 | `/ask`, `/quiz`, `/upload` over the same services".

## Known options
- Settled: `dev.mindforge.discord` is a driving adapter with the adapter rules (all layers, thin, no business logic),
  its own `@Configuration` as composition root (like `cli/CliConfig`). Domain additions (port, platform enum,
  `UploadSource.DISCORD`) go in `dev.mindforge.domain`; the persistence adapter in `infrastructure.persistence`; the
  identity "resolve or provision" logic in an application service. W11 adds `dev.mindforge.discord` to hexagonal.md's
  adapter row and to architecture.md. CLAUDE.md's layer table is outside the factory's allowed CLAUDE.md edit
  ("Project State" only, `.claude/factory.md` docs_to_update) — the report must ask the human to add it there.

## Constraints
- Dependencies point inward; domain JDK only.
- JDA types never leave `dev.mindforge.discord` (no JDA import in application or domain).
