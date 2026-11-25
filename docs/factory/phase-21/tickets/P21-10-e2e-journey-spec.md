---
id: P21-10
title: Drive the wiki journey in the browser from upload to a downloaded OKF bundle
status: ready
plan_tasks: [21.2]
decisions: [D07]
depends_on: [P21-09]
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
`src/test/e2e/specs/journey.spec.ts` runs, in plan 21.2 order, register -> upload -> run completes -> pages appear ->
open a page -> review flashcards -> take a quiz -> export bundle, against the compose stack with the WireMock model
stub, and passes. It asserts that no reference answer text is ever visible.

## Context
- P21-08: `compose.e2e.yml` + WireMock mappings in `src/test/e2e/model-stub/mappings/` (canned ingest, flashcard,
  quiz, grading answers; the quiz `referenceAnswer` is a distinct marker string — read it from the QUIZ mapping).
- P21-09: Playwright project in `src/test/e2e/` (config, tsconfig, `specs/smoke.spec.ts` with a register flow you can
  reuse as a helper inside `specs/` — keep it small, no framework).
- SPA (`frontend/src/app/`): routes `dashboard` (create knowledge base), `kb/:kbId` (documents; upload),
  `runs/:runId` (run report), `pages`, `pages/:directory/:name`, `study` (flashcards and quiz); Export button in
  `pages/kb/kb-shell.ts` (`wiki.exportUrl`). The export file name is `<slug>-okf.zip`.
- API-level reference for the same journey and its expectations: `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java`
  (upload -> COMPLETED, pages, flashcards, quiz session, export zip entries).
- `BundleExporter` runs `BundleConformanceValidator.violations` before it returns and answers 500 on any violation
  (`src/main/java/dev/mindforge/infrastructure/export/BundleExporter.java:94`), so a successful download is a
  validated bundle.
- CLAUDE.md § Security: `reference_answer` is never returned; study data never enters a page or export.

## Decisions to apply
**D07 — Decision:** write the browser E2E with Playwright Test (`@playwright/test`, TypeScript) as its own npm@11
project in `src/test/e2e/`, run against `docker compose -f compose.yml -f compose.e2e.yml up --wait`, outside Maven.

**Implementation notes (journey part):**
- Spec `src/test/e2e/specs/journey.spec.ts`. Journey in plan 21.2 order: register -> upload -> run completes -> pages
  appear -> open a page -> review flashcards -> take a quiz -> export bundle (validates). Use a unique e-mail per run.
  Assert that no reference-answer text is visible in the DOM (CLAUDE.md § Security). First assertion: an
  Angular-rendered element on the login page, not only `<app-root>`.
- No file in `src/test/e2e/` may match `*Test.java`. `mvn -B verify -DskipFrontend=true` stays green.

**Answered question Q15** (answered by plan 9b "As built" and checklist "Every exported bundle passes the conformance
validator"): the E2E clicks Export, receives the download, and checks it is a zip named `<slug>-okf.zip` whose entries
include `index.md`, `log.md` and the page files of the journey (as `ApiFlowTest` does at API level). Do not re-run a
validator on the downloaded files; the server-side validator is the oracle.

## Acceptance criteria
- [ ] AC1: The spec registers a unique user, creates a knowledge base, and uploads a small markdown fixture committed under `src/test/e2e/fixtures/` (content consistent with the stub answers, e.g. the `ApiFlowTest` biology notes).
- [ ] AC2: It waits (bounded timeout, no fixed sleeps) until the run shows completed in the UI, then sees the wiki pages from the stub answers in the pages list and opens one page, asserting its title and some body text.
- [ ] AC3: It reviews at least one flashcard (shows the front, reveals the back, records a grade) in the study screen.
- [ ] AC4: It takes a quiz: answers the question, submits, and sees the grading result; at no point does the DOM contain the quiz `referenceAnswer` marker (checked after the question is shown and after grading).
- [ ] AC5: It clicks Export, catches the download (`page.waitForEvent('download')`), asserts the suggested file name matches `<slug>-okf.zip`, and asserts the zip's entries include `index.md`, `log.md` and the journey's page files. Zip reading uses Node built-ins only or a helper in the spec — no new npm dependency unless unavoidable (if one is added, pin it exactly and record why in Notes and `tech-stack.md`).
- [ ] AC6: Against the running P21-08 stack, `npm test` in `src/test/e2e/` passes both specs; `npx tsc --noEmit` passes; WireMock shows no unmatched request after the run. Record the run in Notes.
- [ ] AC7: `mvn -B verify -DskipFrontend=true` is green.

## Test plan
- E2E (browser, Playwright Test): `src/test/e2e/specs/journey.spec.ts` proves AC1–AC6 against the compose stack.
- Factory gate (AC7).

## Files likely touched
- `src/test/e2e/specs/journey.spec.ts` (new)
- `src/test/e2e/fixtures/<notes>.md` (new)
- `src/test/e2e/model-stub/mappings/*.json` (only if a journey step needs an answer missing from P21-08; keep `ModelStubMappingsTest` green)

## Out of scope
- A second conformance validator on the client side.
- Query/chat, graph, Lint or revert flows.
- Any SPA or `src/main` change; if a step is impossible through the UI, stop and record it in Notes.

## Notes
