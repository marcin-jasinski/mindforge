---
id: R08
title: The API is unversioned
type: decision
status: open
severity: medium
assignee:
blocked_by: []
---

## Problem

`docs/standards/backend/api.md` documents versioning as a standard, and its own worked example
is `/api/v1/quiz`. **Every route in the codebase is unversioned.** All 13 controllers:

| Controller | `@RequestMapping` |
|---|---|
| `AuthController.java:27` | `/api/auth` |
| `UserController.java:19` | `/api/users/me` |
| `KnowledgeBaseController.java:28` | `/api/knowledge-bases` |
| `DocumentController.java:33` | `/api/knowledge-bases/{kbId}/documents` |
| `FlashcardController.java:27` | `/api/knowledge-bases/{kbId}/flashcards` |
| `QueryController.java:33` | `/api/knowledge-bases/{kbId}/query-sessions` |
| `QuizController.java:31` | `/api/knowledge-bases/{kbId}/quiz-sessions` |
| `PageController`, `RunController`, `ExportController`, `GraphController`, `HealthController` | `/api/knowledge-bases/{kbId}/...` |

## The decision

This needs a decision before any code, because it is not free — it touches 13 controllers, the
generated OpenAPI client (`frontend/src/app/core/models/api.generated.ts`), every hard-coded
endpoint literal in the SPA (see R12), and `docs/standards/backend/openapi.md`'s generation
command.

### Option A — add `/api/v1` now (recommended)

Cheapest it will ever be: there are **no external consumers**. The SPA is the only client, it
ships in the same JAR, and the two deploy together. Doing it later, once a CLI (Phase 15),
a Discord bot (Phase 18) or a Slack bot (Phase 19) is in the field, means a real migration.

Implementation is mechanical: a `server.servlet.context-path` or a single constant prefix on
each `@RequestMapping`, then regenerate the TypeScript client. Prefer the explicit prefix on each
controller over a global context path, so the version is visible at the route it belongs to.

### Option B — record that MindForge does not version its API

Legitimate for a single-tenant app whose only client ships in the same artifact. But then
**amend `docs/standards/backend/api.md`** to say so and delete the `/api/v1/quiz` example, so
the next agent does not read the standard and "fix" the code to match it.

What is not acceptable is the current state: a documented standard that every route violates.

## Why it matters

Medium now, higher after Phase 15. Phases 15, 18 and 19 all add API clients that do *not* ship
in the same artifact as the server, and a version prefix is the cheap insurance against them
breaking on a server deploy.

## Acceptance criteria

- [ ] A decision is recorded here.
- [ ] If A: every route is under `/api/v1`, the OpenAPI client is regenerated, `ApiFlowTest`'s
      11 paths updated, and the SPA works end to end.
- [ ] If B: `api.md` no longer documents versioning as a rule this project follows.
- [ ] The standard and the code agree either way.

## Resolution

<!-- filled on close -->
