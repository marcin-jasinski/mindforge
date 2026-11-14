---
id: P17-10
title: Record the article fetch flow, the egress scope and the new settings in the docs, and close Phase 17
status: done
plan_tasks: [17.1, 17.2, 17.3]
decisions: [D03, D01]
depends_on: [P17-01, P17-02, P17-03, P17-04, P17-05, P17-06, P17-07, P17-08, P17-09]
size: S
issue: 31
commit: 26caab8
fix_rounds: 0
---
## Goal
The project docs describe Phase 17 as built: where and when articles are fetched, the egress trust boundary, the new
dependencies and settings, and the `cli` behaviour. Phase 17 tasks, checklist and heading are `[x]` in the plan, and
the roadmap and CLAUDE.md "Project State" point to the next phase.

## Context
- Built by P17-01..P17-09: `EgressPolicy` (`infrastructure.security`), `DomainEvent.DocumentAccepted`,
  `IngestionService.ingestArticle`, `HtmlArticleConverter` (jsoup), `ArticleSource` port + `HttpArticleSource`
  (httpclient5, `infrastructure.web`), `OutboundHttpGuardTest`, `ArticleFetcher` (application), `ArticleFetchTriggers`
  (`@Profile("!cli")`), settings `mindforge.articles.*`, the ARTICLE-run integration test. Read the Notes of each
  ticket for choices that changed during implementation and reflect them.
- ADRs 0022–0026 (written in this run, Accepted) already record the load-bearing decisions; do not write a new ADR
  unless a ticket's Notes record a new load-bearing choice.
- Factory `docs_to_update`: `docs/project/implementation-plan.md`, `docs/project/roadmap.md`, `docs/INDEX.md`,
  `docs/project/deployment.md`, `CLAUDE.md` (only "Project State"). Scope also names `docs/project/architecture.md`
  and `docs/project/tech-stack.md` (rows were added in P17-04/P17-05; check them).
- Reports and docs use ASD-STE100 Simplified Technical English; vocabulary from `CONTEXT.md`.

## Decisions to apply
**D03 — Implementation notes (docs part):**
- `docs/project/architecture.md` § "Security & Trust Model", "Untrusted input" bullet: add "Outbound HTTP to a URL
  taken from content or a user goes through `EgressPolicy`. Operator-configured endpoints (`OPENROUTER_BASE_URL`,
  `LANGFUSE_HOST`, OAuth2 provider URIs) are trusted configuration and are not checked (ADR 0023)."
- `docs/standards/security/web-security.md` § "Untrusted Input Handling": one matching sentence (and make the
  `egressPolicy.check(externalUrl)` example match the real API if it differs).
- `docs/project/implementation-plan.md` (Phase 17 checklist line "All outbound HTTP goes through `EgressPolicy`") and
  `docs/factory/phase-17/scope.md` (Definition of done): reword to "All outbound HTTP to content- or user-derived URLs
  goes through `EgressPolicy` (operator-configured endpoints are trusted config; ADR 0023)."

**D01 — Implementation notes (docs part):**
- Update `docs/standards/architecture/hexagonal.md` § Domain Events with `DocumentAccepted` and its best-effort
  consumer (`ArticleFetchTriggers` -> `ArticleFetcher` on `runExecutor`, after commit, lost on crash by design per
  ADR 0015/0024).
- Record the `cli` choice in `docs/project/deployment.md`: the `cli` profile does not fetch articles (the listener is
  not registered there), because the process exits after the parent run.

**D06 / Q10 — settings to document in `docs/project/deployment.md`:** `FETCH_EXTERNAL_ARTICLES` (default false;
global; turns on fetching of linked articles for new uploads), `ARTICLES_MAX_PER_DOCUMENT` (10),
`ARTICLES_MAX_BODY_SIZE` (2MB), `ARTICLES_CONNECT_TIMEOUT` (5s), `ARTICLES_REQUEST_TIMEOUT` (15s, total per URL
across up to 5 redirects). State that only http/https on ports 80/443 to public addresses are fetched (ADR 0022),
there is no host allowlist and no egress setting, and that skipped articles show only in logs (D08).

## Acceptance criteria
- [x] AC1: Plan Phase 17: tasks 17.1–17.3, both checklist lines (with the D03 wording) and the heading are `[x]`, each
  task with a short "As built" note naming the classes and ADRs 0022–0026.
- [x] AC2: `docs/project/architecture.md` describes the article data flow (upload commit -> `DocumentAccepted` ->
  `ArticleFetcher` -> `ArticleSource` -> `ingestArticle` -> own ARTICLE run, create-only, no Supersede) and has the
  D03 trust-model sentence; `web-security.md` has the matching sentence.
- [x] AC3: `docs/project/deployment.md` lists the five settings with defaults, the egress rules, and the `cli`
  behaviour.
- [x] AC4: `hexagonal.md` § Domain Events lists `DocumentAccepted` and its consumer.
- [x] AC5: `docs/project/tech-stack.md` has rows for jsoup (ADR 0026) and httpclient5 (ADR 0025).
- [x] AC6: `docs/project/roadmap.md` marks Phase 17 done and names the next phase; CLAUDE.md "Project State" (only that
  paragraph) says where work resumes; `docs/INDEX.md` is updated only where a listed description changed (and lists
  ADRs 0022–0026 if it lists ADRs).
- [x] AC7: No code changes; the gate still passes.

## Test plan
- Docs only; verify by review and by `mvn -B verify -DskipFrontend=true` (no regressions beyond the baseline
  `ApiFlowTest.theLoginRouteIsTheSinglePageApp`).

## Files likely touched
- `docs/project/implementation-plan.md`, `docs/project/roadmap.md`, `docs/project/architecture.md`,
  `docs/project/deployment.md`, `docs/project/tech-stack.md`, `docs/INDEX.md`, `CLAUDE.md`
- `docs/standards/security/web-security.md`, `docs/standards/architecture/hexagonal.md`
- `docs/factory/phase-17/scope.md`

## Out of scope
- Any code or test change; the factory run report (`REPORT.md`); `CONTEXT.md` unless a new domain term was added.
- Rewriting ADRs 0022–0026.

## Notes
- Decided: added the five `ARTICLES_*`/`FETCH_EXTERNAL_ARTICLES` variables to `env.example`, because `deployment.md` says every variable is listed there; no code changed.
- Decided: the roadmap "Next" line and CLAUDE.md "Project State" name Phase 14 (manual Langfuse check open), then Phase 18, because Phase 14 is still open and Phase 18 is the next unchecked phase in the plan.
- Decided: no new ADR, because no P17-01..P17-09 Note records a load-bearing choice outside ADRs 0022–0026 (the OAuth2 token client JDK pin in `SecurityConfig` applies ADR 0025 and is recorded in `tech-stack.md`).
