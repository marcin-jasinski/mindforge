# Phase 19 — Slack Bot: scope

## Goal

From `docs/project/implementation-plan.md` § "Phase 19 — Slack Bot": a Slack bot built on Slack Bolt for Java with
ask, quiz and upload handlers, workspace security and identity resolution. `/mf-ask` (Query) replaces the old
`/mf-search` (v3.0 change). Identity resolution reuses the same `ExternalIdentityRepository` as Discord.

## In scope

- 19.1 `UploadSource.SLACK`, added with its producer (phase-15 D06; the same pattern as `UploadSource.DISCORD`).
- 19.2 `SlackBot` in `dev.mindforge.slack`: Socket Mode; commands `/mf-ask`, `/mf-quiz`; a file upload handler; a
  workspace allowlist; identity through `ExternalIdentityRepository` (via the existing `ExternalIdentityService`).
- 19.3 Unit tests: allowlist enforcement, identity resolution, ownership check.
- The wiring the plan does not name, mirroring Phase 18: the Bolt and Socket Mode dependencies (`tech-stack.md`),
  `AppProperties.Slack` with fail-closed validation, a `@Configuration` that builds the bot only when configured and
  never in the `cli` profile, a per-user rate limit, ephemeral replies, a capped file download from a fixed Slack
  host list, `deployment.md` and `env.example` settings.
- Close-out docs (factory `docs_to_update`): implementation plan ("As built", `[x]`), roadmap, `docs/INDEX.md`,
  `docs/project/deployment.md`, CLAUDE.md "Project State"; also `docs/project/architecture.md` (adapter row, data flow,
  trust boundary, external systems table), `docs/project/tech-stack.md` (Bolt), `docs/standards/architecture/hexagonal.md`
  (adapter package list) and an ADR for each load-bearing decision (identity key, workspace allowlist, knowledge base
  selection, file upload path).

## Out of scope

- SR reminder DMs for Slack users: Phase 19 has no reminder task (Phase 18.3 is Discord only).
  `ReminderService` stays `ExternalPlatform.DISCORD` only.
- Account linking between platforms or to a web account (`ExternalIdentityRepository.link` is not built; issue #43).
- Rate limiting of auth endpoints and the security checklist pass (Phase 20); OWASP audit (20.2).
- Commands outside the plan (`/mf-lint`, `/mf-export`, flashcard review, knowledge base management in Slack).
- Frontend changes.
- Changes to the Discord adapter's behaviour (a pure refactor to share code is in scope only if Q07 says so).
- A live run against a real Slack workspace (needs tokens and a workspace only the human has; see Q16).

## Existing state (code is the evidence)

Nothing of Phase 19 exists: no `dev.mindforge.slack` package, no Bolt dependency in `pom.xml`, no `Slack` section in
`AppProperties`, no `ExternalPlatform.SLACK`, no `UploadSource.SLACK`. No stubs or TODOs. Phase 18 built everything
the Slack bot can reuse:

- **Identity:** `domain/port/ExternalIdentityRepository.java` (`findUserId`, `createUserAndLink`, `findByPlatform`;
  not tenant-scoped), `domain/model/ExternalPlatform.java` (only `DISCORD("Discord")`; the label names the provisioned
  knowledge base), `domain/model/ExternalAccounts.java` (synthetic email `<platform>-<externalId>@users.invalid`; the
  external id must match `[A-Za-z0-9]+`), `application/service/ExternalIdentityService.resolve(platform, externalId,
  displayName)` (resolve or provision user + link + one knowledge base named after the platform, race-tolerant).
  `V8__create_external_identities.sql`: `platform VARCHAR(50)` without CHECK, `external_id VARCHAR(64)`, PK
  `(platform, external_id)`. A new `ExternalPlatform` constant needs no migration.
- **Upload:** `UploadSource` ends with `DISCORD`; `ArticleFetcher.SCANNED_SOURCES = {API, CLI, FILE_WATCHER, DISCORD}`
  (`application/service/ArticleFetcher.java:43`). `IngestionService.ingest(DocumentUpload)` and `UploadPolicy` apply
  on every channel.
- **Discord adapter** (`src/main/java/dev/mindforge/discord/`, 18 files): `DiscordConfig` (`@Profile("!cli")`,
  `@Conditional(BotTokenSet)`), `DiscordBot` (lifecycle), `DiscordListener`, `DiscordCommandHandler` (gate: allowlist →
  rate limit → defer → resolve → command; fixed replies), `DiscordAllowlist`, `DiscordRateLimiter` (Caffeine, keyed by
  `long` snowflake), `DiscordKnowledgeBases` (`kb` option + autocomplete + `requireOwner`), `DiscordQuizSessions`
  (Caffeine, keyed by internal user id), `DiscordReply`/`JdaDiscordReply` (ephemeral, split at 2000 chars),
  `AskCommand`, `QuizCommand`, `UploadCommand`, `DiscordAttachments` (the only downloader; host list, capped read).
  Tests in `src/test/java/dev/mindforge/unit/discord/` and `integration/discord/`.
- **Egress guard:** `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` — markers
  include `okhttp3`, `.download(`, `.getProxy()`; `OPERATOR_ENDPOINT_FILES` allows `SecurityConfig.java` and
  `DiscordAttachments.java` by exact path. A Slack file download needs one more allowed file.
- **Dependencies:** `pom.xml` declares JDA before Spring AI so JDA's `okhttp-jvm` 5 precedes Spring AI's `okhttp` 4
  (both hold package `okhttp3`, ADR 0025). Slack's `slack-api-client` also uses OkHttp; the order must hold.
- **Accounts:** a bot-provisioned account cannot sign in to the web (ADR 0027) and there is no bot command that
  creates a knowledge base, so a Slack user will own exactly the one provisioned knowledge base until linking (#43).
- **Docs:** `architecture.md:113` lists "Slack | Phase 19 | `/mf-ask`, `/mf-quiz`, file upload over the same services".
  `hexagonal.md:15` and `architecture.md:32` list adapter packages `api`, `cli`, `discord`. CLAUDE.md's layer table
  lists only `api` and `cli` (the factory may edit only "Project State"; the human should add `discord` and `slack`).
- **Baseline:** `ApiFlowTest.theLoginRouteIsTheSinglePageApp` fails without the frontend build (`journal.md`); not a
  regression. The `fast` command runs Maven offline (`-o`): the new Bolt artifacts must be resolved once online.

## Plan/code disagreements and gaps

1. Plan "workspace allowlist" names no semantics. Slack has shared channels (Slack Connect), Enterprise Grid
   (`enterprise_id`), and slash commands in DMs that still carry a `team_id` — unlike Discord, where ADR 0028 rejects
   DMs because they have no guild (Q02).
2. "Identity via `ExternalIdentityRepository`": Slack recommends `team_id` + `user_id` as the user key, but
   `ExternalAccounts.syntheticEmail` admits only `[A-Za-z0-9]` ids (Q01).
3. Slack slash commands carry one free-text argument: no typed options, no attachments, no autocomplete. ADR 0030's
   `kb` option with autocomplete and Phase 18's `/upload` attachment option cannot be copied (Q03, Q04).
4. The plan names one `/mf-quiz` command, but a quiz needs start and answer (Q05).
5. Completion checklist "Bot connects via Socket Mode" can only be proved live with real tokens (Q16).

## Constraints that apply

From `CLAUDE.md` § Non-Negotiable Rules unless stated otherwise:

- Hexagonal: `dev.mindforge.slack` is a driving adapter; Bolt/Slack SDK types stay in it; no business logic there;
  logic such as resolve-or-provision stays in `ExternalIdentityService` (`docs/standards/architecture/hexagonal.md`).
  The domain stays JDK only (`ExternalPlatform.SLACK` is a plain constant).
- All beans via `@Configuration`, constructor injection, no static singletons; the bot's composition root is its own
  `@Configuration` (hexagonal.md § Composition Root).
- Virtual threads; never reactive types. Slack needs an ack within 3 s, so slow work runs on a virtual thread after the
  ack and answers through the response URL or Web API.
- Never call a model inside a database transaction; the bot opens none.
- Every entry point verifies resource ownership (`docs/standards/security/web-security.md` § API Authorization):
  `KnowledgeBaseService.requireOwner` before work; quiz and Query ownership through the services' own checks.
- Never return `reference_answer`, `grounding_context`, `raw_prompt`, `raw_completion`, `cost` — a Slack message is a
  client. Study data never enters a wiki page or an export.
- Account model: ADR 0027 (synthetic `slack-…@users.invalid` email, provisioning always inserts; "Phase 19 reuses the
  same rule with its own platform prefix"); ADR 0030 (provisioning creates one knowledge base named after the platform;
  a knowledge base reference is untrusted and checked with `requireOwner`; never a name).
- Allowlist: fail closed, never "empty = all" (ADR 0028 § Considered Options; ADR 0022;
  `docs/standards/global/validation.md`); the check runs before identity resolution and model spend.
- Reply visibility: ADR 0032 § Consequences — "Platform bots in Phase 19 (Slack) follow the same rule with the
  platform's equivalent of an ephemeral reply."
- File download: ADR 0029 § Consequences — "Platform files in Phase 19 (Slack) follow the same rule: the platform's
  client, a fixed host list, a capped read." Uploads then pass `UploadPolicy`/`UploadSanitizer` (same limits on every
  channel). A user-chosen URL still goes through `EgressPolicy` (ADR 0023). `OutboundHttpGuardTest` must stay green.
- Rate limit per bot user in the adapter (D11 phase-18; ADR 0019 § Consequences).
- Secrets (bot token, app token) only in environment variables, never logged (`docs/standards/global/conventions.md`).
- Minimal dependencies, documented in `tech-stack.md`; keep the OkHttp 5 ordering in `pom.xml` (ADR 0025 note).
- Minimal implementation: no unused methods, no speculative abstractions (`docs/standards/global/minimal-implementation.md`).
- Java: logger `log`; records for values; domain exceptions; fixed user-facing texts, never exception messages
  (`docs/standards/backend/java-conventions.md`, `docs/standards/global/error-handling.md`).
- Tests: unit tests without a Spring context, Mockito, AssertJ, `make*` factories; Testcontainers for integration
  (`docs/standards/testing/test-writing.md`). No test connects to Slack.
- CONTEXT.md vocabulary: Query, Quiz Session, Flashcard, Knowledge Base, Document, Lesson.
- New ADRs follow the format of the newest ADR (`docs/adr/0032-…`); contradicting an accepted ADR is a blocker.

## Definition of done

Plan completion checklist (Phase 19):

- [ ] Bot connects via Socket Mode and responds to commands. (Proved with fakes in tests; the live check is a human
  follow-up, see Q16.)
- [ ] Identity resolution reuses the same `ExternalIdentityRepository` as Discord.

Plus: tasks 19.1–19.3 are `[x]` with "As built" notes and the phase heading is `[x]`; the 19.3 tests exist (allowlist
enforcement, identity resolution, ownership check); the `docs_to_update` files and architecture, tech-stack and
hexagonal.md reflect the phase; ADRs exist for the load-bearing decisions; all quality gates pass
(`mvn -B verify -DskipFrontend=true`; frontend gate only if `frontend/**` changes; the known baseline failure
`ApiFlowTest.theLoginRouteIsTheSinglePageApp` is not a regression).
