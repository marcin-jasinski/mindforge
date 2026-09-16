---
kind: wayfinder:map
title: Code review of Phases 0-13
created: 2026-09-16
---

# Code review of Phases 0-13

## Destination

Every finding of the 2026-09-16 two-axis code review is closed: `mvn verify` is green on a
fresh checkout on every platform, CI runs it, the three documented-standard breaches are
either fixed or the standard is amended, and the security, performance and dead-code items
are resolved or explicitly accepted.

The map is done when every ticket below is `status: closed`.

## Notes

**What was reviewed.** The diff from the repository root commit `08f4542` to `HEAD`
(`6d9984f`) — 518 files, 42,390 insertions, i.e. the whole codebase, Phases 0 through 13.
Reviewed on three axes: **Standards** (conformance to `docs/standards/**` plus a Fowler
smell baseline), **Spec** (conformance to `docs/project/implementation-plan.md`), and
**Quality** (test effectiveness, security, performance, dead code).

**Headline.** The codebase is in good shape. The completion claim for Phases 0-13 holds:
no scope creep, no missing phase deliverable, ownership checks on all 12 controllers,
cross-tenant isolation proved by test, thorough migration indexing, and 91.1% instruction
coverage against a 70% gate. The vacuous-test hunt came up nearly empty.

**But the build is red on a fresh Windows checkout** and has been since the prompt files
landed. That is R01, and it blocks everything that wants a green suite.

**Measured facts** (this machine, Docker running, 2026-09-16):

| | |
|---|---|
| `mvn test` as committed | **263 tests, 5 failures** (all `IngestRunTest`) |
| `mvn test` with R01's fix applied | **263 tests, 0 failures** |
| jacoco instruction coverage | **91.1%** (gate: 70%) |
| Frontend tests | **0**, and no runner installed |
| CI | **none** |

**Do not re-litigate.** These were verified during the review and are correct; no ticket
should revisit them:

- Domain is JDK-only. `kbId` is first on all 10 tenant-scoped port methods.
- No model call happens inside a transaction. All 11 model services carry `VERSION` + `TIER`.
- All 12 `@RestController`s check ownership as their first statement, SSE and export included.
- No forbidden field (`reference_answer`, `grounding_context`, `raw_prompt`, `raw_completion`,
  `cost`) is reachable from `api/`. BCrypt is cost 12; JWT is HttpOnly/Secure/SameSite=Lax.
- Zip-slip is closed: `BundleConformanceValidator` runs before a byte is written.
- There is no N+1 risk: entities carry plain UUID columns, no `@OneToMany`/`@ManyToOne`.
- Angular has no dead components; all 11 page components are routed.

**Tracker convention.** As the OKF map: one markdown file per ticket in `review-tickets/`,
YAML frontmatter carrying `id`, `type`, `status`, `severity`, `assignee` and `blocked_by`.
A ticket is *claimed* by filling `assignee`; *closed* by setting `status: closed` and filling
its `## Resolution` section. The **frontier** is every ticket with `status: open`, empty
`assignee`, and every id in `blocked_by` closed:

```
grep -l 'status: open' docs/wayfinder/review-tickets/*.md
```

**Ids are `R__`** so they never collide with the OKF map's `T01`-`T29`.

## Suggested order

1. **R01** first and alone — it unbreaks the build, and every other ticket wants a green suite.
2. Then **R13**, **R05**, **R19** — one-line fixes with real user-facing impact.
3. Then **R02**, **R03**, **R04** — make the build honest and keep it that way.
4. Everything else in any order. **R07** and **R08** need a decision before code.

## Tickets

### Blocking

- [R01 — Prompt files check out as CRLF and break five tests](review-tickets/R01-crlf-prompt-files-break-the-build.md)
  — `critical`. No root `.gitattributes`; `core.autocrlf=true` gives CRLF prompts; the stub
  gateway stops matching and every page write fails. Root cause fully traced, fix verified.

### Build and test infrastructure

- [R02 — Integration tests run under surefire, so the build needs Docker](review-tickets/R02-no-failsafe-plugin.md) — `high`.
- [R03 — There is no CI](review-tickets/R03-no-continuous-integration.md) — `high`. Blocked by R01, R02.
- [R04 — The frontend has no tests and no test runner](review-tickets/R04-frontend-has-no-tests.md) — `high`.

### Spec

- [R05 — A no-op retitle counts as a succeeded page task](review-tickets/R05-no-op-retitle-counts-as-success.md) — `high`.
- [R06 — Phase 3b's checkbox and the stale spec audit](review-tickets/R06-stale-phase-status-and-audit.md) — `low`.

### Standards

- [R07 — The application layer imports slf4j against an exhaustive allow-list](review-tickets/R07-application-layer-imports-slf4j.md) — `medium`. Needs a decision.
- [R08 — The API is unversioned](review-tickets/R08-api-is-unversioned.md) — `medium`. Needs a decision.
- [R09 — No rate limiting and no rate-limit headers](review-tickets/R09-no-rate-limit-headers.md) — `medium`.
- [R10 — `failures` and `stepVersions` cross a domain port as untyped maps](review-tickets/R10-untyped-maps-cross-a-domain-port.md) — `medium`.
- [R11 — Duplicated run lifecycle and LintService's data clump](review-tickets/R11-duplicated-run-lifecycle.md) — `low`.
- [R12 — Endpoint literals are inline in eleven page components](review-tickets/R12-frontend-endpoint-literals.md) — `low`.

### Quality — correctness and tests

- [R13 — `ModelOutputException` is unmapped and untested](review-tickets/R13-model-output-exception-unmapped.md) — `high`.
- [R14 — Four tests assert nothing about production code](review-tickets/R14-tests-that-test-nothing.md) — `medium`.
- [R15 — Untested edge cases on the model and validation boundary](review-tickets/R15-untested-edge-cases.md) — `medium`.

### Quality — security

- [R16 — Error messages echo internals, and actuator sits at the root behind permitAll](review-tickets/R16-error-detail-and-actuator-exposure.md) — `medium`.

### Quality — performance

- [R17 — The whole index is rendered into every Query and every Lint](review-tickets/R17-unbounded-index-on-interactive-paths.md) — `high`.
- [R18 — Export materializes the whole bundle behind a streaming facade](review-tickets/R18-export-materializes-whole-bundle.md) — `medium`.
- [R19 — Two repository queries have no limit](review-tickets/R19-unbounded-queries.md) — `medium`.

### Quality — dead code

- [R20 — One dead enum and two inert dependencies](review-tickets/R20-dead-code-and-inert-dependencies.md) — `low`.
