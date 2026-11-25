# Breakdown — Phase 21 (draft)

Plan tasks: 21.1 (CI workflow), 21.2 (Playwright E2E), 21.3 (ArchUnit). Follow-up #63 (dependency-check job) is
assigned to Phase 21 by Phase 20.

| Id | Intent | Plan tasks | Size | Depends on | Questions |
|---|---|---|---|---|---|
| W01 | Add `archunit-junit5` (test scope) and a unit `ArchitectureTest` with the full layer table: domain JDK-only, application and agent allowlists, nothing inward depends on an adapter package (`api`, `cli`, `discord`, `slack`). | 21.3 | S | — | Q09, Q10 |
| W02 | ArchUnit rules: no `@RestController` depends on a repository (per Q11 definition), and no `dev.mindforge.agent` class depends on another `dev.mindforge.agent` class. | 21.3 | S | W01 | Q11 |
| W03 | ArchUnit rule (T25): only `RunWorker` calls `IngestRunRepository.findUnfinished` and `knowledgeBasesWithQueuedRuns`. | 21.3 | S | W01 | — |
| W04 | T26 guard: no heading or link regex outside `MarkdownStructure` (source scan or reflective `Pattern` field check), and resolve the `BundleConformanceValidator` offenders per Q12. | 21.3 | M | W01 | Q12 |
| W05 | Add `maven-checkstyle-plugin` with the chosen ruleset and binding; fix or suppress existing findings so the gate is green. | 21.1 | M–L | — | Q01, Q03 |
| W06 | Add `spotbugs-maven-plugin` with the chosen threshold, binding and exclude filter; fix real findings. | 21.1 | M–L | — | Q02, Q03 |
| W07 | Update `.github/workflows/ci.yml`: Checkstyle → SpotBugs → `mvn -B verify` (Testcontainers) → JaCoCo 70% check, keep push/PR triggers and the Windows leg, publish reports. | 21.1 | S | W05, W06 | Q03, Q04, Q05 |
| W08 | Add the scheduled + `workflow_dispatch` OWASP dependency-check workflow with the `NVD_API_KEY` secret and an NVD data cache (#63). | #63 (21.1 area) | S | — | Q13 |
| W09 | Provide the deterministic stub model provider for the E2E stack (per Q06: stub profile, fake OpenAI-compatible server, or test-side `StubAIGateway` context), with canned answers that pass every ingest, study and quiz step; ADR if it is a new runtime profile or container. | 21.2 | M | — | Q06 |
| W10 | E2E harness: Playwright install and config at the chosen location, the stack start (compose override or test context), and one smoke spec that signs up and sees the dashboard. | 21.2 | M | W09 | Q06, Q07, Q08 |
| W11 | The journey spec: register → upload → run completes → pages appear → open a page → review flashcards → take a quiz → export downloads a valid `<slug>-okf.zip`. Asserts no reference answer is shown. | 21.2 | M | W10 | Q15 |
| W12 | CI `e2e` job that builds the stack and runs the journey (per Q08), uploading the Playwright report and traces on failure. | 21.1, 21.2 | S | W07, W11 | Q07, Q08 |
| W13 | Make `ApiFlowTest.theLoginRouteIsTheSinglePageApp` pass under the factory gate without the SPA build (per Q14). | (gate hygiene; checklist "CI passes on a clean checkout") | S | — | Q14 |
| W14 | Close the phase: plan 21.1–21.3 and checklist with "As built" notes, roadmap, `docs/INDEX.md`, `deployment.md` (quality gates, E2E run instructions, dependency-check job), `test-writing.md` e2e path if it changed, CLAUDE.md Project State; list the human actions (NVD secret, required status check). | 21.1, 21.2, 21.3 | S | W01–W13 | — |

Coverage check: 21.1 → W05, W06, W07, W12; 21.2 → W09, W10, W11, W12; 21.3 → W01, W02, W03, W04; #63 → W08;
docs → W14.

Parallel lanes: {W01 → W02, W03, W04}, {W05, W06 → W07}, {W08}, {W09 → W10 → W11 → W12}, {W13}.
