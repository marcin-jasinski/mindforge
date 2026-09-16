---
id: R04
title: The frontend has no tests and no test runner
type: chore
status: open
severity: high
assignee:
blocked_by: []
---

## Problem

Phase 12 delivered the Angular SPA — 26 TypeScript files, 11 routed page components, a graph
view, run reports with diffs and revert, health, study, chat and export. It has **zero tests**,
and no way to run one:

- `find frontend/src -name '*.spec.ts'` -> **0 files**
- `angular.json` has **no `test` target**
- `frontend/package.json` `devDependencies` contains no karma, no jasmine, no vitest — nothing
  that can execute a test
- ...yet `frontend/package.json:7` still ships `"test": "ng test"`, which cannot work

So `npm test` is a broken promise, and the entire client half of the product is unverified.

## Why it matters

`docs/project/roadmap.md` marks Phase 12 complete. The backend is at 91.1% instruction coverage;
the frontend is at zero, and this review could confirm only that the components exist and are
routed — not that any of them behaves correctly.

The highest-risk untested logic is not the templates. It is:

- **the revision diff computation**, which ticket T28 item 5 deliberately moved *into the SPA*
  using the `diff` npm package — so there is no server-side implementation to cross-check
  against, and a wrong diff misrepresents what a run changed right before someone decides
  whether to revert it;
- **`progress.service.ts`** — the SSE run-progress stream, including reconnection and terminal
  state;
- **`page-links.directive.ts` and `markdown.ts`** — wiki-link rendering, the one place client
  code reinterprets a wiki invariant that the backend owns;
- **the revert confirmation flow**, which triggers a destructive, irreversible server operation.

## Fix

1. Install a runner. Angular 21 with `@angular/build` supports **vitest**, which needs no
   browser and no karma stack. Add the devDependency and the `test` target to `angular.json`.
2. Write tests for the four items above **first**. They are mostly pure logic; most need no
   TestBed.
3. Only then consider component tests, and keep them shallow — assert on rendered output, not
   on Angular internals, per `docs/standards/testing/test-writing.md` ("test behavior not
   implementation").

Do **not** chase a coverage number here. Four meaningful tests on the logic above are worth more
than 60% coverage of template bindings.

## Acceptance criteria

- [ ] `npm test` in `frontend/` runs and passes.
- [ ] Diff computation, SSE progress, wiki-link rendering and the revert flow each have a test.
- [ ] The frontend test command runs in CI (coordinate with R03).

## Resolution

<!-- filled on close -->
