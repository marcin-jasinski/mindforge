# Phase 18 — Discord Bot: scope

## Goal

From `docs/project/implementation-plan.md` § "Phase 18 — Discord Bot": a Discord bot with ask, quiz and upload slash
commands; guild allowlists; identity resolution; spaced-repetition reminders by DM. `/ask` (Query) replaces the old
`/search` (v3.0 change; `docs/wayfinder/tickets/09-query-retrieval-neo4j.md` § 8). Every command delegates to the same
application services as the web UI.

## In scope

- 18.1 Domain additions: an `ExternalIdentityRepository` port with `findUserId(platform, externalId)`, `link(...)`,
  `createUserAndLink(...)`, its persistence adapter and Flyway migration; `UploadSource.DISCORD`.
- 18.2 `DiscordBot` in `dev.mindforge.discord`: JDA as a Spring-managed `ApplicationRunner`; slash commands `/ask`,
  `/quiz start`, `/quiz answer`, `/upload`; guild and role allowlist from `AppProperties.discord.*`; identity resolution
  that auto-provisions a MindForge user on first contact.
- 18.3 SR reminder job: `@Scheduled` daily; DM each Discord-linked user who has due flashcards.
- 18.4 Unit tests: first contact auto-provisions; a command from a guild not on the allowlist is rejected; user A cannot
  reach user B's sessions.
- The wiring the plan does not name: the JDA dependency, `AppProperties.Discord`, a `@Configuration` that builds the
  bot only when it is configured and not in the `cli` profile (Q08), `deployment.md` settings.
- Close-out docs (factory `docs_to_update`): implementation plan, roadmap, `docs/INDEX.md` if a description changes,
  `docs/project/deployment.md` (bot token, allowlists, reminder schedule), CLAUDE.md "Project State"; also
  `docs/project/architecture.md` (driving adapter, data flow), `docs/project/tech-stack.md` (JDA in "Key Dependencies
  Summary"), `docs/standards/architecture/hexagonal.md` (adapter package list), and an ADR for each load-bearing
  decision (identity/account model, attachment egress).

## Out of scope

- Slack bot, `UploadSource.SLACK`, Slack identity (Phase 19). The port must not be shaped for Slack before Slack
  exists (`minimal-implementation.md`), but its names must not be Discord-only (19 reuses it, plan 19 checklist).
- Rate limiting of auth endpoints and the security checklist pass (Phase 20); OWASP audit (20.2).
- Other commands (`/lint`, `/export`, flashcard review in Discord, conversation edits from Discord): the plan lists
  exactly four commands.
- Frontend changes, unless Q02 picks a web-issued link code.
- Phase 14's open manual Langfuse check.
- A live run against real Discord: it needs a bot token and a test guild that only the human has. The plan's
  checklist is proven with fakes (a command handler and a DM sender that tests can drive); a live smoke test is a
  human follow-up, not part of the definition of done.

## Existing state (code is the evidence)

- **Nothing of Phase 18 exists.** No `discord` package, no JDA in `pom.xml`, no `ExternalIdentity*` type, no
  `external_identities` table (migrations `V1`–`V7`), no `discord` section in `AppProperties`, no mention in
  `tech-stack.md` or `deployment.md`. No stubs or TODOs.
- `UploadSource` (`domain/model/UploadSource.java`): `API, CLI, FILE_WATCHER, CONVERSATION, ARTICLE`.
  `documents.upload_source` is `VARCHAR(50)` without CHECK, mapped with `valueOf` — a new constant needs no migration
  (phase-15 D06). D06: "Phases 18–19 (bots) should follow the same pattern: one constant per real channel, added with
  its producer."
- `ArticleFetcher.SCANNED_SOURCES = {API, CLI, FILE_WATCHER}` (`application/service/ArticleFetcher.java:43`) — a
  `DISCORD` document fetches no articles unless it is added (Q12).
- **Accounts:** `users.email VARCHAR(255) NOT NULL` with a unique index (`V1__baseline.sql:3-14`); `password_hash`
  nullable. `User` record (`domain/model/User.java`), `UserRepository` (not tenant-scoped: findById, findByEmail,
  insert, recordLogin, updateDisplayName). `AccountService` has `register`, `authenticate`, `signInWithProvider(email,
  …)` (creates on first sign-in, matches by email), `get`, `byEmail`, `rename`. A Discord user has no email the bot can
  read, so auto-provisioning cannot use any of these as they are (Q01).
- **Knowledge bases:** `KnowledgeBaseService` — `create`, `list(ownerId)`, `get`, `requireOwner(kbId, userId)`,
  `update`, `delete`. A new user owns no knowledge base, and no command in the plan names one (Q03).
- **Query:** `QueryService.start(kbId, userId)` → `Interaction`; `ask(kbId, userId, interactionId, question)` →
  `Answer(answer, citedPaths)`; `requireSession` throws `NotOwnerException` for another user's interaction.
- **Quiz:** `QuizService.begin(kbId, userId, scope)` (async generation) / `start(...)` (sync), `isPreparing`,
  `next(kbId, userId, sessionId)`, `answer(kbId, userId, sessionId, answer)` → `Graded`; `owned(...)` rejects another
  user's session. `QuizSessionStore` has no "current session of a user" lookup (Q05). The CLI quiz runner
  (`cli/QuizCommandRunner.java`) is a working non-web adapter over the same calls.
- **Flashcards:** `FlashcardService.due(kbId, scope)` returns due cards without generating any; `deck` generates.
  Study state is per knowledge base (`StudyProgressStore.dueCards(kbId, …)`).
- **Upload:** `IngestionService.ingest(DocumentUpload)`; `DocumentUpload(kbId, uploadedBy, source, filename, mimeType,
  content, lessonId, newVersion)`. `UploadSanitizer` enforces MIME and `mindforge.upload.max-size` "on every upload
  channel" (`AppProperties.Upload`). `DocumentController.upload` and `PipelineCommandRunner` are the two producers.
- **Egress:** `OutboundHttpGuardTest` allows HTTP-client markers (`java.net.http.HttpClient`, `RestClient`,
  `org.apache.hc.client5`, …) only in `infrastructure/web/` and `api/config/SecurityConfig.java`. JDA uses OkHttp,
  which is not a marker; downloading a Discord attachment is still outbound HTTP to a URL received from a user's
  message. ADR 0023 § Consequences: "bot links … that URL is untrusted and must go through `EgressPolicy`. That change
  needs a new decision." (Q07)
- **Rate limit:** `RateLimitInterceptor` (API adapter, Caffeine, per user, POST only, `mindforge.rate-limits.*`). The
  CLI is not limited (ADR 0019 § Consequences). A bot is public-facing (Q11).
- **Scheduling:** `@EnableScheduling` on `PipelineConfig`; `@Scheduled` used by `QuizSessionCleanup` and
  `RunWorkerTriggers` with `${…}` intervals.
- **Feature switch precedents:** Langfuse export is off unless both keys are set (`TracingConfig.LangfuseKeysSet`);
  article fetching off by default; `ArticleFetchConfig` registers its trigger with `@Profile("!cli")`.

## Plan/code disagreements

1. 18.2 "identity resolution auto-provisions on first contact" vs `users.email NOT NULL UNIQUE`: an auto-provisioned
   Discord user has no email (Q01).
2. 18.1 names `link(...)`, but no task, command or test in the plan calls it; `minimal-implementation.md` forbids a
   method without a caller (Q02).
3. The plan's commands name no knowledge base, but every application service is keyed by `kbId` and a new user owns
   none (Q03).
4. 18.2 "JDA as a Spring-managed `ApplicationRunner`" vs hexagonal.md and CLAUDE.md, which list only `dev.mindforge.api`
   and `dev.mindforge.cli` as adapter packages. `architecture.md` already lists "Discord / Slack | Phases 18–19" as a
   driving adapter. CLAUDE.md's layer table is outside the factory's allowed CLAUDE.md edit ("Project State" only) —
   the human should add `dev.mindforge.discord` to it (Q09).
5. `/upload` downloads a file from Discord's CDN — outbound HTTP to a user-supplied resource, which ADR 0023 says needs
   a new decision (Q07).

## Constraints that apply

From `CLAUDE.md` § Non-Negotiable Rules unless stated otherwise:

- Domain is JDK only: `ExternalIdentityRepository` and any platform type in `dev.mindforge.domain` import no framework
  (`docs/standards/architecture/hexagonal.md`).
- Adapters are thin: input validation + auth check (allowlist, identity, `requireOwner`) + delegate to an application
  service; no business logic in the bot (`docs/standards/backend/api.md`; hexagonal.md). Logic such as "resolve or
  provision" lives in an application service.
- All beans via `@Configuration`, constructor injection, no static singletons; one composition root per surface
  (hexagonal.md § Composition Root).
- Virtual threads; never introduce reactive types — use JDA's blocking/`complete()` or callback APIs, no Reactor.
- Every tenant-scoped port method takes `kbId` first. `ExternalIdentityRepository`, like `UserRepository`, is not
  tenant-scoped (accounts live above knowledge bases).
- Ownership: every entry point verifies resource ownership (`docs/standards/security/web-security.md` § API
  Authorization) — the bot calls `KnowledgeBaseService.requireOwner` before any work, as the CLI does (ADR 0019).
- Never return `reference_answer`, `grounding_context`, `raw_prompt`, `raw_completion`, `cost` to a client — a Discord
  message is a client. Only `NextQuestion` and `Graded` reach the user (as in `QuizCommandRunner`).
- Study data never enters a wiki page or an export (unchanged; the bot writes neither).
- Untrusted input: uploads go through `UploadPolicy`/`UploadSanitizer` (same size and MIME limits on every channel);
  outbound HTTP to a user- or content-derived URL goes through `EgressPolicy` (ADR 0022, ADR 0023, ADR 0025);
  `OutboundHttpGuardTest` must stay green. Allowlists over blocklists and fail closed
  (`docs/standards/global/validation.md`).
- Never call a model inside a database transaction; the bot opens none.
- Secrets in environment variables, never committed (`docs/standards/global/conventions.md`); the bot token is a
  secret and must never be logged.
- Minimal dependencies, documented in `tech-stack.md` (conventions.md; tech-stack.md "Key Dependencies Summary").
- Minimal implementation: no unused methods, no Slack-shaped abstractions now (`minimal-implementation.md`).
- New `UploadSource` constant only with its producer (phase-15 D06).
- Migrations: next is `V8__…`; small, focused, FK indexed, timestamps (`docs/standards/backend/migrations.md`,
  `docs/standards/backend/models.md`); persistence sub-packages `entity/ jpa/ mapper/ adapter/` with MapStruct mappers.
- Java: logger `log`; records for values and results; domain exceptions, never bare `RuntimeException`
  (`docs/standards/backend/java-conventions.md`, `docs/standards/global/error-handling.md`). User-facing messages are
  fixed text; never echo exception messages from a provider.
- Tests: unit tests without a Spring context, Mockito for ports, AssertJ, `make*` factories, `StubAIGateway` for model
  calls; Testcontainers for the persistence adapter (`docs/standards/testing/test-writing.md`). Integration tests need
  Docker (`.claude/factory.md`).
- New ADRs follow the newest file's format (`docs/adr/0026-…`); a decision that contradicts an accepted ADR is a
  blocker (`.claude/factory.md`).
- Use CONTEXT.md vocabulary: Query (not search/chat), Quiz Session, Flashcard, Study Scope, Knowledge Base, Document.

## Definition of done

Plan completion checklist (Phase 18):

- [ ] Commands delegate to the same application services as the web UI.
- [ ] SR reminder DMs sent to users with due cards.

Plus: tasks 18.1–18.4 are `[x]` in the plan with "As built" notes and the phase heading is `[x]`; the 18.4 tests exist
(first contact auto-provisions; a non-allowed guild is rejected; user A cannot reach user B's sessions); roadmap,
deployment, architecture, tech-stack, hexagonal.md, CLAUDE.md "Project State" and the other `docs_to_update` files
reflect the phase; ADRs written for decisions that set a trust boundary or the account model; all quality gates pass
(`mvn -B verify -DskipFrontend=true`; the frontend gate only if `frontend/**` changes). The known baseline failure
`ApiFlowTest.theLoginRouteIsTheSinglePageApp` (gate run without frontend, `journal.md`) is not a regression.
