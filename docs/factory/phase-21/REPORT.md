# Factory run report — Phase 21 — End-to-End Testing and CI/CD

- **Run:** phase-21 · started 2026-11-25T09:36+01:00 · finished 2026-11-25T14:56+01:00 (last review)
- **Outcome:** all 12 tickets done, 0 parked. Not pushed and not merged yet. The close steps will push `factory/phase-21` and merge it into `develop` after this report is committed. Until then, no merge SHA exists.
- **Branch:** `factory/phase-21` · base 76c75fafa98fb39c9ec4598f8028143dd29061cb

## Audit first

1. **No CI run exists yet.** The branch is not pushed. The gate `mvn -B verify -DskipFrontend=true` is green, and the E2E journey passed in container runs on the implementer's machine. The first GitHub Actions run (both the `linux` and `e2e` jobs) is a human check. The plan checklist item "CI passes on a clean checkout" says this.
2. **Judge-decided question:** D14 (test fixture `src/test/resources/static/index.html`) was decided by the judge alone, with one ballot. D07 (Playwright Test in `src/test/e2e/`) went to a round-2 debate and all three voters changed their vote.
3. **ADR written:** ADR 0042 (E2E model answers come from a fake OpenAI-compatible server), from D06.
4. **Human actions in GitHub:** set the `NVD_API_KEY` secret; make the CI check a required status check in branch protection. Today nothing blocks a PR below the JaCoCo 70% gate.
5. **Deferred minor findings:** see below. The holistic finding H1 is a wrong comment in `ci.yml`.

## Tickets

Commit hashes are from `git log` of the branch. The `commit:` fields in the ticket files and the status column in `tickets/INDEX.md` agree with them.

| Ticket | Title | Status | Commit | Fix rounds | Notes |
|---|---|---|---|---|---|
| P21-01 | Serve a test-classpath SPA shell so the login-route test passes without the Angular build | done | b7dfe36 | 0 | Fixed the baseline failure `ApiFlowTest.theLoginRouteIsTheSinglePageApp` |
| P21-02 | Enforce code-style rules with Checkstyle in the validate phase | done | cbdfa32 | 0 | 124 violations fixed in 66 files |
| P21-03 | Fail verify on SpotBugs findings at Medium threshold | done | 93430bf | 0 | 2 null dereferences fixed; `DM_EXIT` excluded narrowly |
| P21-04 | ArchUnit and the hexagonal layer rules | done | ab04617 | 0 | |
| P21-05 | ArchUnit rules: thin controllers, independent model services, RunWorker-only queue reads | done | 25a6bb8 | 0 | 2 minor findings deferred |
| P21-06 | Guard that only MarkdownStructure matches markdown syntax with a regex | done | 4c98fee | 1 | Major finding: link check missed `](`; fixed |
| P21-07 | Quality gates in CI, reports, scheduled dependency check | done | 309f41f | 0 | 3 minor findings deferred |
| P21-08 | WireMock OpenAI-compatible model stub in `compose.e2e.yml` | done | 56e37ce | 0 | Dockerfile `COPY config` fix (image build was broken by P21-02) |
| P21-09 | Playwright Test project and sign-up smoke spec | done | 291f7f1 | 0 | 1 minor finding deferred |
| P21-10 | Browser journey: upload to downloaded OKF bundle | done | 643b52a | 1 | Test-guard blocker: smoke spec lost 2 assertions; restored |
| P21-11 | CI job for the browser E2E | done | 624ac6d | 0 | 1 minor finding deferred |
| P21-12 | Close Phase 21 in the docs | done | e107f9c | 1 | Major finding: CI checklist item ticked without a CI run; fixed |

## Decisions

| Id | Question | Method | Class | Decision (one line) | Dissent / risk to watch |
|---|---|---|---|---|---|
| D01 | Q01 Checkstyle ruleset | unanimous | local | Project ruleset encoding only the stated rules; fail in `validate`; wrap long lines; no baseline | none |
| D02 | Q02 SpotBugs threshold | unanimous | local | Medium/Max on `verify`; exclude 3 noisy patterns; FindSecBugs deferred | none |
| D03 | Q03 Gates in Maven lifecycle | unanimous | local | Checkstyle on `validate`, SpotBugs on `verify`; one CI `mvn -B verify` step | none |
| D06 | Q06 E2E stub provider | unanimous | adr | WireMock container as OpenAI-compatible provider in `compose.e2e.yml` | Mappings can drift from the prompts; `ModelStubMappingsTest` guards this |
| D07 | Q07 E2E runner and location | debate | local | Playwright Test (TypeScript) in `src/test/e2e/`, outside Maven | Round 1 was C,C,C; all three moved to A in round 2 |
| D08 | Q08 Where E2E runs | unanimous | local | Separate `e2e` CI job after `linux`, on every push and PR | none |
| D11 | Q11 Controller repository definition | unanimous | local | A `@RestController` must not depend on `domain.port..` or `infrastructure.persistence..` | Third-party types out of scope |
| D12 | Q12 Markdown regex offenders | unanimous | local | Source-scan guard test; `BundleConformanceValidator` allowed by exact path | Heading check can miss some regex shapes (deferred C2 of P21-06) |
| D14 | Q14 Baseline SPA login test | judge | local | Test fixture `static/index.html` with `<app-root>` | Single ballot; judge decided. The holistic review found the D14 rationale says CI builds the SPA, which is not true for the `linux` job |

Method counts: unanimous 7 · majority 0 · debate 1 · judge 1. Blockers: 0. Questions answered from the docs without a panel: Q04, Q05, Q09, Q10, Q13, Q15.

## Decision outcomes

| Decision | Method | Vote changes | Models (panel / judge) | Tickets applying it (status, fix rounds) | Findings / rulings citing it |
|---|---|---|---|---|---|
| D01 | unanimous | none | opus / opus | P21-02 (done, 0) | P21-02.r1.spec-security (approve) |
| D02 | unanimous | none | sonnet / opus | P21-03 (done, 0) | P21-03.r1.spec-security (approve) |
| D03 | unanimous | none | sonnet / opus | P21-02 (done, 0), P21-07 (done, 0) | P21-02.r1.spec-security (approve) |
| D06 | unanimous | none | opus / opus | P21-08 (done, 0) | none |
| D07 | debate | pragmatist, architect, skeptic: C to A (all toward the final decision) | opus / opus | P21-09 (done, 0), P21-10 (done, 1), P21-11 (done, 0), P21-12 (done, 1) | none |
| D08 | unanimous | none | sonnet / opus | P21-11 (done, 0) | none |
| D11 | unanimous | none | sonnet / opus | P21-05 (done, 0) | none |
| D12 | unanimous | none | sonnet / opus | P21-06 (done, 1) | P21-06.r1.correctness (1 major on the link check, fixed; 1 minor deferred), P21-06.r1.spec-security |
| D14 | judge | none | sonnet / opus | P21-01 (done, 0) | P21-01.r1.spec-security; holistic H1 (minor: comment says CI builds the frontend) |

## Panel statistics

Decisions per method: unanimous 7, majority 0, debate 1, judge 1. Round-2 debates 1 (Q07). Vote changes 3 (all toward the final decision, 0 away). Ballots discounted for false evidence: 3, all in `Q07.r1` (pragmatist, architect, skeptic; all opus). Their option C conflicted with the already decided D08, so the judge did not count them. Disputed findings: none. There is no `rulings/` directory (valid 0, invalid 0, defer 0).

## ADRs written

- ADR 0042 — E2E model answers come from a fake OpenAI-compatible server (`docs/adr/0042-e2e-model-answers-come-from-a-fake-openai-compatible-server.md`), from D06. It is listed in `docs/INDEX.md`.

## Deferred findings

Minor findings that were not fixed.

- Holistic H1 (minor, confidence 0.85) — `.github/workflows/ci.yml:24`: the comment on the `linux` job's `mvn -B verify` step says the step builds the frontend. It does not: `pom.xml` sets `skipFrontend` to `true` by default and `ci.yml` does not change it. The real SPA bundle is built only in the `e2e` job through the Dockerfile. The SPA routing test passes in the `linux` job only because of the P21-01 fixture. Fix: remove "frontend build," from the comment (the `windows` job comment at `ci.yml:55` has the same claim from before this phase), or add `-DskipFrontend=false` to the `linux` step. `deployment.md` is already correct.
- P21-05 C1/S1 — `ArchitectureTest.java:86`: the run-queue rule matches only calls with owner `IngestRunRepository`; a call through `IngestRunRepositoryAdapter` is not caught.
- P21-06 C2 — `MarkdownRegexGuardTest.java:35`: the heading check misses some ATX heading regexes (for example `"(?m)^\\s*#+ (.*)$"`).
- P21-07 C1, C2 — `dependency-check.yml:20,18`: the NVD cache is not saved when the check fails; `cache: maven` also stores the NVD data under the key of the `ci.yml` jobs. S1 — no `permissions:` block in `dependency-check.yml:8`.
- P21-09 S1 — ticket P21-09 line 80: the AC4 run record uses `npx playwright test --trace off`, not the AC command.
- P21-10 C1 — `journey.spec.ts:22`: the fixture path `fixtures/biology.md` resolves against the working directory, so the spec runs only from `src/test/e2e/`.
- P21-11 S1 — `ci.yml:60`: the `e2e` job runs branch code with the default `GITHUB_TOKEN` scope; no `permissions:` block.
- P21-07 note — the JaCoCo `report` goal is not bound, so the CI `jacoco` artifact is not produced.
- FindSecBugs plugin: deferred by D02.
- P21-12 C2 (nit) — `deployment.md:37`: the `linux` row omits `-- --no-watch`. The P21-12 C1 and S2 findings were fixed in the fix round.

## Manual checks still open

- Push the branch and confirm the first green `linux` and `e2e` jobs on GitHub Actions.
- Set the `NVD_API_KEY` repository secret; confirm the first weekly or manual `Dependency check` run.
- Make the `linux` CI check a required status check in branch protection, so a PR below the JaCoCo 70% gate is blocked.
- Confirm the JaCoCo 70% gate fails a build below 70% (configured, not shown to fail).
- Phase 14: the manual Langfuse trace check (from an earlier phase).

## Doc updates by the scribe

P21-12 already updated the plan, the roadmap, `docs/INDEX.md`, `docs/project/deployment.md` and the `CLAUDE.md` Project State. The scribe checked them against the tickets and found them correct. The scribe added one section to `docs/INDEX.md`: "Factory Run Records", which lists `docs/factory/phase-15/` to `phase-21/` (no earlier run was listed there). Nothing was committed.

## Statistics

- Tickets: 12 done, 0 parked. Fix rounds: 3 in total (P21-06, P21-10, P21-12: 1 each). Disputed findings: 0.
- Decisions per method: unanimous 7, debate 1, judge 1.
- Agent calls (hook state): 102 in total, forced continuations 3, stalled 0.
  - planner@opus 1; ticket-writer@opus 1; scribe@sonnet 1
  - solver@opus 12; solver@sonnet 14; judge@opus 9
  - implementer@default 12; fixer@default 3
  - tester@sonnet 13
  - reviewer@opus 26; reviewer@sonnet 10
