---
id: R12
title: Endpoint literals are inline in eleven page components
type: refactor
status: closed
severity: low
assignee:
blocked_by: []
---

## Problem

`docs/standards/frontend/angular-patterns.md` models feature services sitting over a shared
`ApiService`, with components consuming signals rather than composing URLs. Instead, endpoint
path literals are built inline in the page components — for example
`frontend/src/app/pages/graph/graph.ts:27`, `page-index.ts:64` and `page-index.ts:70`, and the
documents page.

`ApiService` exists and is the only place `HttpClient` is injected (verified — that part of the
standard holds). The issue is that the *paths* live in 11 different components.

## Why it matters

Low on its own, but it is the multiplier on R08. If the API gains a `/api/v1` prefix, the change
is 11 component edits instead of one service edit — classic **Shotgun Surgery**. The same applies
to any future path rename.

It also means a component knows the server's URL shape, which is the coupling feature services
are meant to absorb.

## Fix

Introduce one feature service per resource area — roughly `WikiApi`, `RunApi`, `StudyApi`,
`QueryApi` — each owning its endpoint paths and exposing typed methods over `ApiService`.
Components inject the feature service and never see a path.

Keep it proportionate: four small services, not one per component, and no base-class hierarchy.
The generated client in `frontend/src/app/core/models/api.generated.ts` already provides the
types; these services only own paths and shapes.

If R08 lands first, do that prefix change **inside** these new services — that is the whole point
of the ticket.

## Acceptance criteria

- [x] No endpoint path literal remains in a component under `frontend/src/app/pages/`.
- [x] Each feature service is injected with `inject()` and exposes signals or observables, per
      `angular-patterns.md`.
- [x] A hypothetical `/api/v1` prefix would be a change in one place per resource area.

## Resolution

Added four root-provided feature services in `frontend/src/app/core/services/`, each over `ApiService` and owning its paths: `WikiApi` (knowledge bases, documents, index, search, pages, revisions, graph, health, export URL), `RunApi` (runs, run report, revert, supersessions, document retry, lint, progress SSE URL), `StudyApi` (flashcards, reviews, quiz sessions) and `QueryApi` (query sessions, messages, edits). All pages and `ProgressService` now inject them; no `/knowledge-bases/...` literal remains under `pages/`, and the hard-coded `/api/v1/...` export link in `kb-shell.ts` now comes from `WikiApi.exportUrl`. The `/api/v1` prefix stays in `ApiService` (the single place), landed by R08. `auth.service.ts` already was a feature service and is unchanged. Specs for `RunReportView` and `ProgressService` now stub `RunApi`. Frontend tests (17) and build pass.
