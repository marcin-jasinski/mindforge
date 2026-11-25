# Scope — Phase 21: End-to-End Testing and CI/CD

## Goal

From `docs/project/implementation-plan.md` § "Phase 21": a full E2E test suite that covers the core user journeys, a
GitHub Actions CI pipeline, and quality gates. The E2E journey follows the wiki; ArchUnit gains model-service rules.

## In scope

- 21.1 — `.github/workflows/ci.yml`: on push and PR, Checkstyle → SpotBugs → tests with Testcontainers → JaCoCo 70% gate.
- 21.2 — Playwright E2E smoke test against the running stack with a stub model provider: register → upload → run
  completes → pages appear → open a page → review flashcards → take a quiz → export bundle (the bundle validates).
- 21.3 — ArchUnit fitness functions:
  - no `dev.mindforge.domain` class depends on `infrastructure` or `api`;
  - no `@RestController` method accesses a repository directly;
  - no class in `dev.mindforge.agent` depends on another class in `dev.mindforge.agent`;
  - only `RunWorker` calls `IngestRunRepository.findUnfinished` and `knowledgeBasesWithQueuedRuns` (T25);
  - no class outside `dev.mindforge.domain.model.MarkdownStructure` matches heading or link syntax with a regex (T26).
- Follow-up given to Phase 21 by Phase 20: a scheduled and manual OWASP dependency-check job
  ([#63](https://github.com/marcin-jasinski/mindforge/issues/63); `docs/project/deployment.md` § "Dependency check";
  plan Phase 20 header note and 20.x "As built": "A scheduled job is Phase 21 work (#63)").
- Docs listed in `.claude/factory.md` `docs_to_update`: plan checklist, roadmap, `docs/INDEX.md`,
  `docs/project/deployment.md` (CI and quality gates, E2E run instructions), the CLAUDE.md "Project State" paragraph.

## Out of scope

- Deploy jobs (Vercel / Railway / Render on merge), named in `docs/project/tech-stack.md` § CI/CD. Phase 21 tasks
  name no deploy step.
- Frontend lint (ESLint) — `tech-stack.md` lists it, Phase 21 does not.
- Phase 14's open manual Langfuse check.
- New user-facing features. The SPA already has every screen the journey needs (see Existing state).
- Human-only actions (the factory cannot do them; record them in REPORT.md):
  - set the repository secret `NVD_API_KEY` for the dependency-check job (#63; the job works without it, slowly);
  - mark the CI check as a required status check in the GitHub branch protection of the integration and default
    branches, so that a failing JaCoCo gate actually blocks a merge (checklist item "JaCoCo gate blocks PRs below
    70%"). The factory can only make the check fail.

## Existing state (code is the evidence)

- **CI already exists**: `.github/workflows/ci.yml` (commits 3af10e3, 6ca2741, 166674c). Triggers `push` (all branches)
  and `pull_request`. Linux leg: `npm ci && npm test` in `frontend/`, then `mvn -B verify` (SPA built, unit +
  integration, JaCoCo check), uploads `target/site/jacoco/`. Windows leg: `mvn -B package` (no Docker). No Checkstyle,
  SpotBugs, E2E or dependency-check step.
- **JaCoCo gate already exists**: `pom.xml` `jacoco-maven-plugin` 0.8.13, `prepare-agent` + `check` with BUNDLE /
  INSTRUCTION / COVEREDRATIO minimum 0.70. Surefire runs `unit/`; failsafe runs `**/integration/**/*Test.java` and
  `**/e2e/**/*Test.java` (R02). Coverage is the combined result.
- **No Checkstyle, no SpotBugs** in `pom.xml`; no ruleset file in the repo. 384 main and 144 test Java files.
  `.editorconfig` sets LF, 4-space indent, trailing-whitespace trim.
- **No ArchUnit** dependency, no ArchUnit test. Source-scan guard tests exist and set the house pattern:
  `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` (ADR 0023),
  `unit/infrastructure/export/ExportReadsNoStudyDataGuardTest.java`, `unit/slack/SlackAdapterBoundaryTest.java`.
- **Current code against the 21.3 rules** (checked by grep):
  - domain imports only JDK and `dev.mindforge.domain` — clean. application imports only domain, agent, Spring tx,
    SLF4J — clean. agent imports only domain and `infrastructure.ai` — clean.
  - No agent class references another agent class — clean.
  - `findUnfinished` / `knowledgeBasesWithQueuedRuns` are called only in
    `src/main/java/dev/mindforge/application/service/RunWorker.java` (lines 106, 116) — clean.
  - **Regex rule (T26) has offenders**: `src/main/java/dev/mindforge/infrastructure/export/BundleConformanceValidator.java`
    lines 35–37: `INDEX_ENTRY` (contains `\\]\\(`) and `LOG_DATE` (`"## \\d{4}-..."`). See Q12.
  - **Controller rule**: `src/main/java/dev/mindforge/api/controller/AuthController.java` holds Spring Security's
    `ClientRegistrationRepository`. See Q11.
- **E2E**: no `src/test/e2e/`, no `src/test/java/.../e2e/` directory, no Playwright in `frontend/package.json`
  (vitest only). No stub-provider profile: `StubAIGateway` lives only in `src/test/java/dev/mindforge/support/`;
  `src/main/resources` has `application.yml`, `application-dev.yml`, `application-cli.yml`. The model provider is
  OpenAI-compatible (`spring.ai.openai.base-url: ${OPENROUTER_BASE_URL:...}`).
- `compose.yml` (postgres + app built from the Dockerfile, healthchecks) and `compose.override.yml` (dev secret,
  plain-HTTP cookies) exist. `Dockerfile` builds the SPA and the JAR.
- The API-level journey is already covered by `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java`
  (upload → run COMPLETED → index/pages → revert; flashcards refresh/review; quiz session; export zip). Its
  `givenAnswers()` and `src/test/java/dev/mindforge/support/Prompts.java` hold the prompt-fragment routing a stub needs.
- The SPA has every screen of the journey: `frontend/src/app/app.routes.ts` (`register`, `dashboard`, `kb/:kbId`
  documents, `pages`, `pages/:directory/:name`, `runs/:runId`, `study`), and the Export button in
  `frontend/src/app/pages/kb/kb-shell.ts` (`wiki.exportUrl`).
- Export already validates: `BundleExporter` calls `BundleConformanceValidator.violations` before it returns (plan 9b
  "As built"; `src/main/java/dev/mindforge/infrastructure/export/BundleExporter.java:94`).
- **Baseline failure**: `ApiFlowTest.theLoginRouteIsTheSinglePageApp` fails under the factory gate
  (`-DskipFrontend=true`) because `classpath:/static/index.html` does not exist without the SPA build. It passes in CI,
  which builds the SPA. See Q14.

## Plan / code disagreements

1. Plan 21.1 names one push branch that does not exist here: integration branch is `develop`, the default branch is
   the other protected branch (`.claude/factory.md` `git`). The existing workflow already triggers on every push.
   (Q04, answered.)
2. Plan 21.1 says "`mvn test` (Testcontainers)". In this build `mvn test` runs unit tests only; Testcontainers tests run
   under `mvn verify` (`docs/project/deployment.md` § Build, R02). (Q05, answered.)
3. Plan 21.2 path `src/test/e2e/` vs the testing standard `src/test/java/.../e2e/` with `@Tag("e2e")`
   (`docs/standards/testing/test-writing.md` § Test Discovery and Structure); failsafe already includes
   `**/e2e/**/*Test.java`. (Q07.)
4. Plan 21.2 says "a stub-provider profile"; none exists, and the only stub is test code. (Q06.)
5. Plan 21.3 T26 rule as written fails on `BundleConformanceValidator`. ArchUnit cannot read string literals, so the
   rule's mechanism is not plain ArchUnit. (Q12.)
6. ADR 0023 rejected ArchUnit "for one rule"; Phase 21 adds five rules and names ArchUnit. No conflict. (Q09, answered.)

## Constraints that apply

- `CLAUDE.md` § Architecture table and `docs/standards/architecture/hexagonal.md` table: domain JDK only; application
  imports only domain, agent, `org.springframework.transaction.*`, `org.slf4j.*`; agent imports domain +
  `infrastructure.ai.*`; adapters are `api`, `cli`, `discord`, `slack`.
- `CLAUDE.md` § Model Services: all LLM calls through `AIGateway`; request by `ModelTier`. A stub provider must sit
  behind `AIGateway` or behind the provider HTTP endpoint.
- `CLAUDE.md` § Testing: unit tests load no Spring context; use `StubAIGateway`, never mock `ChatClient`;
  `@Testcontainers` with real PostgreSQL; AssertJ; `make*` factories. ArchUnit tests are unit tests (no Spring context).
- `CLAUDE.md` § Security and `docs/standards/security/web-security.md`: no `reference_answer`, `grounding_context`,
  `raw_prompt`, `raw_completion`, `cost` in responses; JWT in HttpOnly cookies. Secrets (`NVD_API_KEY`) never committed
  (`docs/standards/global/conventions.md` § Environment Variables). A stub provider must not be on by default in a
  production deployment.
- `docs/standards/global/conventions.md`: `npm@11` is the only frontend package manager; minimal dependencies, and
  document why major ones are included.
- `docs/standards/global/minimal-implementation.md`: no future stubs, no speculative abstractions.
- `docs/standards/testing/test-writing.md`: `unit/`, `integration/` (`@Tag("integration")`), `e2e/` (`@Tag("e2e")`).
- `docs/project/deployment.md` § Build: `mvn verify` needs Docker; `-DskipITs` must pair with `-Djacoco.skip=true`.
- `.claude/factory.md`: the factory gate is `mvn -B verify -DskipFrontend=true` and must stay runnable on the factory
  host (no SPA build, Docker available). Any new verify-bound check also runs there.
- Issue #63: dependency-check job on schedule + `workflow_dispatch`, not on every push and not in `verify`;
  `NVD_API_KEY` from a secret; cache `~/.m2/repository/org/owasp/dependency-check-data`; a failure fails the job.
- ADR 0023 (`docs/adr/0023-egress-policy-guards-untrusted-urls-only.md`): `OutboundHttpGuardTest` stays.
- New ADRs follow the format of the newest file, `docs/adr/0041-*.md`.

## Definition of done

- [ ] CI passes on a clean checkout with real Testcontainers.
- [ ] JaCoCo gate blocks PRs below 70% (the factory delivers a failing check; making it required is a human action).
- [ ] E2E covers upload → wiki → study → export.
- [ ] ArchUnit enforces layer and model-service boundaries.
- [ ] Plan tasks 21.1, 21.2, 21.3 checked with "As built" notes; roadmap, INDEX, deployment.md and the CLAUDE.md
      Project State paragraph updated.
- [ ] All quality gates pass (`mvn -B verify -DskipFrontend=true`; the frontend gate when `frontend/**` changes).
