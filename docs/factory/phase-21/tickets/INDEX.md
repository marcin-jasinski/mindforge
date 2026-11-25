# Phase 21 tickets — execution order

| Order | Id | Title | Depends on | Size | Status |
|---|---|---|---|---|---|
| 1 | [P21-01](P21-01-spa-login-test-fixture.md) | Serve a test-classpath SPA shell so the login-route test passes without the Angular build | — | S | ready |
| 2 | [P21-02](P21-02-checkstyle-gate.md) | Enforce the project's stated code-style rules with Checkstyle in the validate phase | P21-01 | L | ready |
| 3 | [P21-03](P21-03-spotbugs-gate.md) | Fail verify on SpotBugs findings at Medium threshold and fix the real defects | P21-02 | L | ready |
| 4 | [P21-04](P21-04-archunit-layer-rules.md) | Add ArchUnit and enforce the hexagonal layer table as fitness functions | P21-02 | S | ready |
| 5 | [P21-05](P21-05-archunit-controller-agent-runworker-rules.md) | Enforce thin controllers, independent model services and RunWorker-only queue reads with ArchUnit | P21-04 | S | ready |
| 6 | [P21-06](P21-06-markdown-regex-guard.md) | Guard that only MarkdownStructure matches markdown heading or link syntax with a regex | P21-02 | M | ready |
| 7 | [P21-07](P21-07-ci-quality-gates-and-dependency-check.md) | Run the quality gates in CI, publish their reports, and add the scheduled dependency-check workflow | P21-03 | S | ready |
| 8 | [P21-08](P21-08-e2e-model-stub.md) | Answer the E2E stack's model calls from a WireMock OpenAI-compatible stub in a test-only compose override | P21-02 | M | ready |
| 9 | [P21-09](P21-09-playwright-harness-and-smoke.md) | Add the Playwright Test project in src/test/e2e with a sign-up smoke spec against the compose stack | P21-08 | M | ready |
| 10 | [P21-10](P21-10-e2e-journey-spec.md) | Drive the wiki journey in the browser from upload to a downloaded OKF bundle | P21-09 | M | ready |
| 11 | [P21-11](P21-11-ci-e2e-job.md) | Run the browser E2E journey in a CI job against the compose stack | P21-07, P21-10 | S | ready |
| 12 | [P21-12](P21-12-close-phase-21-docs.md) | Close Phase 21 in the plan, roadmap, index, deployment guide and project state | P21-01 … P21-11 | S | ready |

Coverage: 21.1 -> P21-01, 02, 03, 07, 11; 21.2 -> P21-08, 09, 10, 11; 21.3 -> P21-04, 05, 06; #63 -> P21-07;
docs -> P21-12. Decisions: D01 -> 02; D02 -> 03; D03 -> 02, 03, 07; D06 -> 08; D07 -> 09, 10, 11, 12; D08 -> 11;
D11 -> 05; D12 -> 06; D14 -> 01. Answered: Q04/Q05/Q13 -> 07; Q09/Q10 -> 04; Q15 -> 10.
Manual (human) checks, not tickets: set the `NVD_API_KEY` secret; make the CI check required in branch protection.
