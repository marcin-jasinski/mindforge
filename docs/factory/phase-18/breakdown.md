# Phase 18 — Discord Bot: draft breakdown

Plan tasks: 18.1 (`ExternalIdentityRepository`, `UploadSource.DISCORD`), 18.2 (`DiscordBot`: JDA `ApplicationRunner`,
`/ask`, `/quiz start`, `/quiz answer`, `/upload`, guild/role allowlist, auto-provisioning identity resolution), 18.3
(daily SR reminder DMs), 18.4 (tests: first contact auto-provisions; non-allowed guilds rejected; user A cannot reach
user B's sessions), plus the completion checklist (CL1: commands delegate to the same application services as the web;
CL2: reminder DMs sent to users with due cards) and close-out docs. Tests ship with the item that adds the behaviour
(18.4 is spread over W02, W04, W07). `UploadSource.DISCORD` lands with its producer in W08 (phase-15 D06).

The bot is split so that tests need no JDA: a thin JDA listener (`DiscordBot`) translates slash-command events into
calls on a plain command handler with JDA-free inputs, and replies/DMs go through a small sender seam. Unit tests drive
the handler and the reminder job with Mockito; no test connects to Discord.

| Id | Intent | Plan tasks | Size | Depends on | Questions |
|---|---|---|---|---|---|
| W01 | `ExternalIdentityRepository` port + platform enum (`DISCORD`) in domain; `V8` migration `external_identities`; entity, JPA repository, MapStruct mapper, adapter; Testcontainers adapter test (find, create-and-link in one transaction, duplicate key, cascade on user delete, list per platform) | 18.1 | M | — | Q01, Q02, Q13 |
| W02 | Identity resolution application service: resolve a Discord user to a MindForge user, provisioning account (and default knowledge base if Q03 says so) on first contact, race-tolerant; unit test "first contact auto-provisions" and "second contact reuses the account" | 18.1, 18.2, 18.4 | M | W01 | Q01, Q02, Q03 |
| W03 | *(only if Q02 picks B or C)* Account linking: link-code issue and redeem (application service, storage, API endpoint or `/link` command, SPA control), `link(...)` implemented; tests incl. wrong/expired/reused code | 18.1 | M | W01, W02, W04 | Q02 |
| W04 | Bot skeleton: JDA dependency (`tech-stack.md`), `AppProperties.Discord` (token, allowed guild ids, role ids, reminder settings) with startup validation, `DiscordConfig` (`@Profile("!cli")`, built only with a token, JDA shut down on close), `DiscordBot` `ApplicationRunner` that logs in and registers the slash commands, the allowlist gate before identity resolution; unit tests: non-allowed guild rejected, missing role rejected, DM handling, no provisioning on rejection; binding test (no token → no bot beans) | 18.2, 18.4 | M | W02 | Q04, Q08, Q09 |
| W05 | Knowledge-base context for commands per Q03 (e.g. `kb` option with autocomplete over owned knowledge bases, or a stored current knowledge base), always followed by `KnowledgeBaseService.requireOwner`; tests incl. another user's knowledge base refused | 18.2 | S | W04 | Q03 |
| W06 | `/ask`: deferred reply, `QueryService.start/ask` per Q06, answer with cited page paths, split at Discord's message limit, fixed error texts; tests that the handler calls `QueryService` with the resolved user and knowledge base and that another user's interaction is refused | 18.2, CL1 | S | W05 | Q06, Q11, Q14 |
| W07 | `/quiz start` and `/quiz answer` over `QuizService` (`begin`/`isPreparing` or `start`, `next`, `answer`), session addressing per Q05, only `NextQuestion`/`Graded` text in replies; tests: full flow with a mocked `QuizService`, and user A answering into B's session is refused through `QuizService`'s ownership check | 18.2, 18.4, CL1 | M | W05 | Q05, Q11, Q14 |
| W08 | `/upload`: `UploadSource.DISCORD` (+ `ArticleFetcher.SCANNED_SOURCES` per Q12), attachment size check against `mindforge.upload.max-size` before download, download per Q07 (guard test updated, ADR if Q07 says so), `IngestionService.ingest` with `lesson_id`/`new_version`, exception → fixed reply; tests: queued reply, lesson collision, too large, refused MIME, download path cannot reach a non-Discord/non-public URL | 18.2, CL1 | M | W05 | Q07, Q11, Q12, Q14, Q15 |
| W09 | *(only if Q11 picks A or B)* Per-user rate limit for the model-spending bot commands, fixed "try again" reply; unit test that the limit rejects without calling a service | 18.2 | S | W04 | Q11 |
| W10 | SR reminder job: `@Scheduled` per Q10 settings, list Discord-linked identities, count due cards per owned knowledge base with `FlashcardService.due` (no generation), DM through the sender seam, skip zero, log and continue on a failed DM; tests: DM sent for due cards, none for zero, a failed DM does not stop the rest, no model call | 18.3, CL2 | M | W01, W04 | Q03, Q10 |
| W11 | Docs: plan 18.x and checklist `[x]` with "As built", roadmap, `deployment.md` (`DISCORD_BOT_TOKEN`, allowlists, reminder schedule, `env.example`), `architecture.md` (driving adapter, identity resolution, upload/egress note), `tech-stack.md` (JDA), `hexagonal.md` adapter row, ADRs for Q01/Q02 (account model) and Q07 (attachment egress) if decided so, `docs/INDEX.md` if descriptions change, CLAUDE.md "Project State"; report asks the human to add `dev.mindforge.discord` to CLAUDE.md's layer table | close-out | S | W01–W10 | Q01, Q02, Q07, Q09 |

Notes:
- W01 can start at once; W02 waits for Q01/Q02/Q03; W04 can start in parallel with W02 once Q04 is decided (it needs
  only the W02 service interface).
- W06, W07 and W08 are independent of each other after W05 and can run in parallel.
- If Q02 picks A (no linking), W03 is dropped and `link(...)` is left out of the port; the plan's "As built" says so.
- If Q11 picks C, W09 is dropped and the gap is recorded for Phase 20.
- JDA pulls OkHttp and Jackson 2; W04 checks the dependency tree against Spring Boot 4.1 (Jackson 3) and that
  `OutboundHttpGuardTest` still passes.
