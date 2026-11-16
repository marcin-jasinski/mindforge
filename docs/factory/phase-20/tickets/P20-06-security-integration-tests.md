---
id: P20-06
title: Prove that a tampered session token is refused and that another user's pages, runs and export are forbidden
status: done
plan_tasks: [20.4]
decisions: [D14]
depends_on: [P20-01]
size: M
issue: 60
commit: ede4964
fix_rounds: 0
---
## Goal
A security integration test class proves plan task 20.4's remaining cases over real HTTP: a tampered, wrongly signed,
expired or `alg: none` session cookie gets `401`; another user's run report, revert, page revisions and export get
`403`; and a run or page id from another knowledge base asked for under the caller's own knowledge base gets `404`
with no data from the other knowledge base. (The 20.4 case "rate limit triggers" is P20-01's `AuthRateLimitTest`.)

## Context
- `src/main/java/dev/mindforge/api/config/JwtFilter.java` leaves a request anonymous when
  `infrastructure/security/JwtService.verify` fails (signature, expiry, malformed); `SecurityConfig` answers `401` on
  `/api/**` without a valid cookie. So a tampered token already gives `401`; no test proves it. `JwtService` uses JJWT
  (`io.jsonwebtoken.Jwts`, `Keys`) with an HMAC secret from `mindforge.security.jwt-secret`; the cookie is `token`.
- Routes: `api/controller/RunController.java` (`/api/v1/knowledge-bases/{kbId}`: `GET /runs`, `GET /runs/{runId}`,
  `POST /runs/{runId}/revert`, `DELETE /supersessions/{supersessionId}`, `GET /progress`);
  `WikiController.java` (`GET /index`, `GET /pages/{directory}/{name}`, `GET /pages/{directory}/{name}/revisions`,
  `GET /graph`); `ExportController.java` (`GET /api/v1/knowledge-bases/{kbId}/export`).
- `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java` already asserts `403` for KB, index, page, runs list,
  progress, health, documents, graph, delete, upload, lint (`aUserCannotReachAnotherUsersKnowledgeBase`) and export
  (`theOwnerDownloadsTheBundle...`). Reuse its patterns (`Client`, `signedUp()`, `createKnowledgeBase`, `upload`,
  `awaitRun`, `givenAnswers` with `StubAIGateway`, `assertNoSensitiveFields`); copy what you need into the new class
  or move shared helpers to `src/test/java/dev/mindforge/support/` if that stays small.
- After P20-01, sign-up is rate limited per address (default 10/min). This test signs up several users from loopback:
  set `mindforge.rate-limits.auth.requests=1000` in its `@TestPropertySource`.
- Known baseline failure `ApiFlowTest.theLoginRouteIsTheSinglePageApp` is not this ticket's concern.
- Binding rules: CLAUDE.md § Testing (Testcontainers, `StubAIGateway`, AssertJ, `make*` factories); review map "every
  controller checks ownership first" (do not re-litigate — this ticket only tests).

## Decisions to apply
No factory decision binds this ticket. The answered question that applies:
- **Q10 — Answer A:** a foreign knowledge base in the path → `403`; a foreign id under one's own knowledge base →
  `404` and no foreign data. That is the tenancy rule (tenant-scoped ports `findById(kbId, runId)`,
  `report(kbId, runId)` find nothing), not an ownership failure. New tests add the missing cases: run report by id and
  revert, page revisions, and the id-swap `404`.
- D14 (decisions/D14-revert-foreign-run-status.md): change RevertService.revert to throw NotFoundException("Run") after start() claims the lease when runs.findById(kbId, runId) is empty; keep 409 for an existing non-revertible run; add a RevertService unit test. This production change is in scope for this ticket.

## Acceptance criteria
- [x] AC1: A `/api/v1/...` request with a valid user's cookie whose signature is altered gets `401`.
- [x] AC2: A cookie signed with a different secret, an expired cookie (signed with the right secret), an `alg: none`
  token and a malformed value each get `401`.
- [x] AC3: User B gets `403` for user A's `GET /runs/{runId}`, `POST /runs/{runId}/revert`,
  `GET /pages/{directory}/{name}/revisions` and `GET /export`, using a real run id and page path from A.
- [x] AC4: User B, under B's own knowledge base, asking for A's run id (`GET /runs/{runId}`, `POST .../revert`) and A's
  page path's revisions gets `404`, and the response body contains no id, path or text from A.
- [x] AC5: No response in the class carries a forbidden field (`assertNoSensitiveFields` pattern).

## Test plan
- AC1–AC5: integration, `@SpringBootTest(webEnvironment = RANDOM_PORT)` + `TestContainerBase` + `StubAIGateway`,
  `src/test/java/dev/mindforge/integration/api/SecurityTest.java` (new). Build tampered tokens with JJWT in the test
  (or by editing the cookie string); no production change is expected. Full gate `mvn -B verify -DskipFrontend=true`.

## Files likely touched
- `src/test/java/dev/mindforge/integration/api/SecurityTest.java` (new)
- `src/test/java/dev/mindforge/support/` (only if shared HTTP helpers are extracted)

## Out of scope
- Production code changes; if a test finds a real gap, record it in Notes and report it.
- Re-testing the `403` cases `ApiFlowTest` already covers (except export, which 20.4 names).
- Rate-limit tests (P20-01, P20-02); CORS tests (P20-04).

## Notes
- Gap found (AC4): `POST /{ownKb}/runs/{foreignRunId}/revert` answers `409 REVERT_NOT_ALLOWED` with detail "Run <id> is not a completed ingest or Lint run", not `404`. `RevertService.revert` calls `start()` and then `isRevertible`, which treats a missing run like a non-revertible one. Report and revisions give `404` as Q10 expects. Needs a decision: fix in production or change AC4.
- Decided: the revisions `404` detail echoes the page path the caller sent ("Page concepts/mitosis"); the test asserts no foreign knowledge-base id or page text instead of path absence, because the echoed path is the caller's own input and is the same whether or not another knowledge base has that page.
- Gap fixed (D14): `RevertService.revert` now reads the run after `start()` claims the lease and throws `NotFoundException("Run")` (404) when the knowledge base has no such run; an existing non-revertible run still gets `409`. Unit test `RevertServiceTest.shouldReportARunTheKnowledgeBaseDoesNotHaveAsNotFound`; `RunController.revert` documents `404`.
