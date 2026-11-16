# Phase 20 — Security Hardening: scope

## Goal

From `docs/project/implementation-plan.md` § "Phase 20 — Security Hardening": a systematic security checklist pass
against `docs/standards/security/web-security.md`, an OWASP dependency audit, and rate limiting (of the auth
endpoints).

## In scope

- 20.1 Verify each requirement in `web-security.md`, including the three the plan names: the exporter never reads
  study tables or run internals; every `WikiStore` query binds `kbId`; dedup is per knowledge base. Fix the gaps found
  (see "Gaps found by the planner" below), and record where each requirement is implemented (checklist item 1; Q11).
- 20.2 `dependency-check-maven` in `pom.xml`; fix or suppress every finding with `CVSS >= 7.0`;
  `mvn dependency-check:check` passes (Q06, Q07).
- 20.3 A Caffeine-backed limiter on `POST /api/v1/auth/login` and `POST /api/v1/auth/register`; `429` with
  `Retry-After` (Q01, Q02, Q03, Q08, Q09).
- 20.4 Security integration tests: the auth rate limit triggers; a tampered JWT gives `401`; cross-user access to
  pages, runs and export gives `403` (Q10).
- The sign-in timing leak that `AccountService.authenticate` marks for Phase 20 (Q04).
- Close-out docs (factory `docs_to_update`): implementation plan ("As built", `[x]`), roadmap, `docs/INDEX.md`,
  `docs/project/deployment.md` + `env.example` (new auth-limit and NVD settings, proxy note), CLAUDE.md "Project
  State"; also `docs/standards/backend/api.md` § Rate Limit Headers, `web-security.md` (Slack downloader, auth limit,
  trace), and an ADR for each load-bearing decision (auth limiter key and client address; CORS if Q05 changes ADR 0008).

## Out of scope

- CI changes and the GitHub Actions pipeline (Phase 21.1) — unless Q06 picks a CI job for the OWASP check.
- ArchUnit fitness functions (Phase 21.3). A guard test in the existing source-scan style is allowed if 20.1 needs it.
- A wider `EgressPolicy` scope (all outbound HTTP): ADR 0023 decided against it; 20.1 only re-verifies (Q13).
- npm / frontend dependency audit: the plan names only `dependency-check-maven` (Q12).
- Account enumeration through `register`'s `409`, email verification, account lockout, MFA, JWT revocation: not in
  `web-security.md` or the plan (Q04 decides only the sign-in timing fix).
- Rate limits on OAuth endpoints, `/logout`, `/providers`, `/me`: the plan names only login and register.
- Changes to the per-user model-call limiter (`RateLimitInterceptor`, R09) and the bot limiters.
- Things verified correct in `docs/wayfinder/code-review-map.md` § "Do not re-litigate" (domain JDK-only, `kbId` first
  on tenant-scoped ports, every controller checks ownership first, no forbidden field reachable from `api/`, BCrypt 12,
  cookie flags, zip-slip closed). 20.1 records them as traced; it does not rework them.

## Existing state (code is the evidence)

**Rate limiting (20.3).** A per-user limiter already exists from R09: `api/config/RateLimitInterceptor.java`
(Caffeine, fixed window, keyed by `CurrentUser.userId()`, POST only, skips anonymous requests),
`api/config/RateLimitConfig.java` (model-calling paths only), `api/config/RateLimitExceededException.java`,
`GlobalExceptionHandler.rateLimited` (`429`, `Retry-After`, `RateLimit-*`, code `RATE_LIMITED`),
`AppProperties.RateLimits` (`mindforge.rate-limits.requests=30`, `window=PT1M`; env `RATE_LIMIT_REQUESTS` /
`RATE_LIMIT_WINDOW`), `integration/api/RateLimitTest.java`. Login and register are anonymous, so the existing
interceptor never applies to them: the auth limit needs a key other than the user (Q01). No
`server.forward-headers-strategy` is set and nothing reads `X-Forwarded-For`, while production runs behind the
Railway/Render proxy (Q02). The SPA login page shows the server's error text (`pages/login/login.ts`,
`apiErrorMessage`); no frontend change is expected.

**Auth.** `api/controller/AuthController.java` maps `/api/v1/auth/{register,login,logout,providers,me}` (the plan's
`/api/auth/...` predates R08's versioning; Q08). `SecurityConfig` permits the four auth routes and answers `401` on
`/api/**` without a valid cookie. `JwtFilter` leaves a request anonymous when `JwtService.verify` fails (signature,
expiry, malformed), so a tampered token already gives `401`; no test proves it. `AccountService.authenticate` (line 42)
carries `// ponytail: an unknown email skips the hash comparison, so timing can reveal it; Phase 20 hardens sign-in`
(Q04). OAuth-only accounts (`passwordHash == null`) also skip the comparison.

**Checklist items the plan names.**
- Exporter: `infrastructure/export/BundleExporter.java` reads `WikiStore`, `BundleQuery` (`BundleQueryAdapter` →
  `PageSourceJpaRepository`, pages + documents) and `RunReportQuery.logEntries` (`IngestRunJpaRepository.findLogEntries`
  selects kind, times, supersession count and document fields only — no `cost`, `step_versions`, `failures`,
  `findings`). No study repository is injected. `BundleExportTest.anExportOfEveryKindOfRowIsConformantEscapedAndCarries
  NoRunInternals` covers run internals; nothing guards against a future study read (a gap to close with a test).
- `WikiStore`: every method of `domain/port/WikiStore.java` takes `kbId` first; every derived and `@Query` method in
  `WikiPage`, `PageRevision`, `PageLink`, `PageSource`, `PageSupersession` JPA repositories binds
  `knowledgeBaseId` on every joined entity. `WikiStoreAdapterTest` tests only the link-row FK across knowledge bases;
  no test reads with another knowledge base's id (a gap to close with a test).
- Dedup: `uq_documents_kb_content_hash ON documents (knowledge_base_id, content_hash)` (`V1__baseline.sql:45`);
  `DocumentRepositoryAdapterTest.sameHashInTwoKnowledgeBases_isNotADuplicateAcrossThem`. Done.

**Other `web-security.md` sections.** Forbidden fields: verified (review map); `ApiFlowTest.assertNoSensitiveFields`.
Redaction in `InteractionStore.listForUser` (`InteractionTurnJpaRepository` selects two columns). BCrypt 12
(`BCryptPasswordHasher`). Cookie flags and `AUTH_SECURE_COOKIES` (`JwtService`, `application.yml:104`). OAuth state:
Spring Security default, not disabled. Untrusted input: `UploadSanitizer`, `EgressPolicy`, `OutboundHttpGuardTest`
(allows `SecurityConfig.java`, `DiscordAttachments`, `SlackFiles.java`). `web-security.md` names the Discord downloader
but not the Slack one (ADR 0035, Phase 19) — a doc gap. CORS: **no `CorsConfigurationSource` bean exists**, while
`web-security.md` § CORS and ADR 0008 require one; `deployment.md` says one container, `architecture.md:389` and
`tech-stack.md:6` still say Vercel (Q05). Ownership: all 12 REST controllers call `requireOwner`/user-scoped services;
`ApiFlowTest.aUserCannotReachAnotherUsersKnowledgeBase` and others already assert `403` for KB, index, page, runs list,
progress, health, documents, graph, export, flashcards, quiz, chat.

**OWASP (20.2).** No `dependency-check-maven` in `pom.xml`; no suppression file; no `NVD_API_KEY` in the environment.
The NVD API is reachable from the factory host. `.github/workflows/ci.yml` runs `mvn -B verify` (linux) and
`mvn -B package` (windows). The factory `fast` command is offline (`-o`): a new plugin must be resolved online once.

## Gaps found by the planner (plan vs code)

1. Plan paths `/api/auth/login`, `/api/auth/register` vs code `/api/v1/auth/...` (Q08, answered).
2. The existing limiter is per user; auth endpoints are anonymous — a new key is needed (Q01), and behind a proxy
   `getRemoteAddr()` is the proxy (Q02).
3. CORS bean required by `web-security.md` and ADR 0008 is missing; deployment docs disagree with each other (Q05).
4. `web-security.md` does not name the Slack file downloader (ADR 0035).
5. No test proves tampered JWT → `401`, cross-KB `WikiStore` reads return nothing, or the exporter never touches study
   tables.
6. Code TODO for Phase 20: sign-in timing (Q04).

## Constraints that apply

- `CLAUDE.md` § Non-Negotiable Rules — Security: never return forbidden fields; JWT only in HttpOnly/Secure/SameSite=Lax
  cookies; BCrypt ≥ 12; every `@RestController` method verifies ownership; study data never enters a page or export.
- `CLAUDE.md` § API/Controllers: thin controllers (the limiter is an interceptor/filter, not controller logic);
  constructor injection; no reactive types. § Architecture: beans wired via `@Configuration`; tenant-scoped port
  methods take `kbId` first.
- `CLAUDE.md` § Testing and `docs/standards/testing/test-writing.md`: unit tests without Spring context; Testcontainers
  for integration; AssertJ; `make*` factories.
- `docs/standards/security/web-security.md` (all sections) — the checklist itself.
- `docs/standards/backend/api.md` § Rate Limit Headers: `429` with `Retry-After`, code `RATE_LIMITED`, `RateLimit-*`.
- `docs/standards/global/error-handling.md`: typed exceptions, central handling, fixed messages (R16).
- `docs/standards/global/validation.md`: allowlists over blocklists, server-side validation (trusted-proxy config).
- `docs/standards/global/minimal-implementation.md` and `conventions.md` § minimal dependencies: no Bucket4j or other
  library for the limiter (plan says Caffeine, already on the classpath); no speculative abstraction.
- `docs/project/tech-stack.md` + ADR 0007: Caffeine, in-process; `deployment.md` § One live instance (in-memory limits
  are correct only with one replica).
- ADR 0008 (CORS from an env var, never `*`), ADR 0023 (egress scope), ADR 0029/0035 (bot downloaders).
- `docs/wayfinder/code-review-map.md` § Do not re-litigate; R09 resolution (rejected requests count; header names).
- `.claude/factory.md`: new ADRs in the newest ADR format (`docs/adr/0037-*.md`); an ADR contradiction is a human
  blocker.

## Definition of done

- [ ] Every `web-security.md` requirement traced to implementing code.
- [ ] `mvn dependency-check:check` passes.
- [ ] Rate limiter active on auth endpoints.
- [ ] Plan tasks 20.1–20.4 done and marked `[x]` with "As built"; roadmap, INDEX, deployment, CLAUDE.md Project State
      updated.
- [ ] All quality gates pass (`mvn -B verify -DskipFrontend=true`; frontend gate if `frontend/**` changes). Known
      baseline failure `ApiFlowTest.theLoginRouteIsTheSinglePageApp` is not this run's concern.
