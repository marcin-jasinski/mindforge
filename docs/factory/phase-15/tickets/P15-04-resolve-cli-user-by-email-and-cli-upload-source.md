---
id: P15-04
title: Resolve a user by email and add the CLI upload source
status: done
plan_tasks: [15.1, 15.2]
decisions: [D04, D06]
depends_on: []
size: S
issue: 5
commit: fc979b4
fix_rounds: 0
---
## Goal
`AccountService.byEmail(String email)` returns the user for an email (normalised as at sign-in) without recording a
login, and throws `NotFoundException("User")` for an unknown email. `UploadSource.CLI` exists, and a document with that
source saves, loads and deduplicates exactly like an `API` document.

## Context
- `src/main/java/dev/mindforge/application/service/AccountService.java`: `register`, `authenticate`,
  `signInWithProvider` (all call `users.recordLogin`), `get(UUID)` (throws `NotFoundException("User")`),
  `private static String normalise(String email)` (`trim().toLowerCase(Locale.ROOT)`). Wired in
  `infrastructure/config/AccountConfig.java`.
- `src/main/java/dev/mindforge/domain/model/UploadSource.java`: `API, FILE_WATCHER, CONVERSATION, ARTICLE`, with a
  Javadoc on channel behaviour. `upload_source` is `VARCHAR(50)` without a CHECK constraint (`V1__baseline.sql`), and
  the mapper uses `UploadSource.valueOf` (`infrastructure/persistence/.../DocumentEntityMapper.java`), so no migration.
- Only `CONVERSATION` and `ARTICLE` change behaviour (`IngestPipeline`, `IngestionService`).
- ADR `docs/adr/0019-cli-acts-as-a-named-user-without-password.md` (accepted): the CLI takes `--user=<email>`, resolves
  it through an application service, then checks ownership; no password.
- Tests: `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java`,
  `src/test/java/dev/mindforge/integration/persistence/DocumentRepositoryAdapterTest.java`,
  `src/test/java/dev/mindforge/integration/application/IngestRunTest.java` (dedup is tested there or in
  `ConcurrentUploadTest`).

## Decisions to apply
**D04 — Decision:** Both CLI runners take a required `--user=<email>`. An application-service method resolves the email
to a `User` (normalised the same way as sign-in; unknown email gives `NotFoundException("User")`), then the runner calls
`KnowledgeBaseService.requireOwner(kbId, user.userId())` before any work. That user is `DocumentUpload.uploadedBy`, the
quiz session `userId` and the owner of the recorded quiz scores. No password: the database credentials are the trust
boundary.

**D04 — Implementation notes (part applied here):**
- Application: `AccountService.byEmail(String email): User` — `users.findByEmail(normalise(email))
  .orElseThrow(() -> new NotFoundException("User"))`. Do not call `recordLogin`. The runners never touch
  `UserRepository`.
- `byEmail` test: unknown email throws `NotFoundException`, `recordLogin` never called; mixed-case and padded email
  resolves.

**D06 — Decision:** Add a `CLI` constant to `dev.mindforge.domain.model.UploadSource` and record it on every document
the CLI upload command ingests. Do not use or remove `FILE_WATCHER` in this phase.

**D06 — Implementation notes:**
- `UploadSource.java`: add `CLI` (place after `API`); update the Javadoc if it lists channels.
- Tests: persistence round-trip (Testcontainers) that a `CLI` document saves and loads; a duplicate CLI upload of the
  same content in one KB is deduplicated as for `API`. (The runner passing `UploadSource.CLI` is tested in P15-05.)
- Risk to watch: future code must not branch on `== UploadSource.API` to mean "a normal upload".

## Acceptance criteria
- [x] AC1: `AccountService.byEmail("  Ann@Example.COM ")` returns the user stored as `ann@example.com`.
- [x] AC2: `byEmail` of an unknown email throws `NotFoundException` for `User`.
- [x] AC3: `byEmail` never calls `UserRepository.recordLogin`.
- [x] AC4: `UploadSource.CLI` exists directly after `API`; no Flyway migration is added.
- [x] AC5: A `Document` with `UploadSource.CLI` saved through the document repository adapter loads back with
      `UploadSource.CLI`.
- [x] AC6: Two `IngestionService.ingest` calls with `UploadSource.CLI` and identical content in one KB return the same
      document id and queue only one run.

## Test plan
- AC1–AC3: unit, `unit/application/AccountServiceTest.java` (Mockito `UserRepository`, `verify(..., never())`).
- AC4: compile + review.
- AC5: integration, `integration/persistence/DocumentRepositoryAdapterTest.java`.
- AC6: integration, `integration/application/IngestRunTest.java` (or the existing dedup test class), `StubAIGateway`.

## Files likely touched
- `src/main/java/dev/mindforge/application/service/AccountService.java`
- `src/main/java/dev/mindforge/domain/model/UploadSource.java`
- `src/test/java/dev/mindforge/unit/application/AccountServiceTest.java`
- `src/test/java/dev/mindforge/integration/persistence/DocumentRepositoryAdapterTest.java`
- `src/test/java/dev/mindforge/integration/application/IngestRunTest.java`

## Out of scope
- A password prompt or `authenticate` call from the CLI; a fallback to the KB owner when `--user` is absent.
- Using or removing `UploadSource.FILE_WATCHER`; exposing the source in API DTOs.
- Any runner code (P15-05+).

## Notes
- Decided: the `UploadSource` Javadoc is not changed, because it names only the channels that change behaviour and `CLI` behaves as `API`.
- AC1: AccountServiceTest.shouldResolveAMixedCaseAndPaddedEmailWithoutRecordingALogin
- AC2: AccountServiceTest.shouldRefuseAnUnknownEmailWithoutRecordingALogin
- AC3: both tests above (`verify(users, never()).recordLogin`)
- AC4: UploadSource diff reviewed (CLI after API); no migration in the change
- AC5: DocumentRepositoryAdapterTest.aCliDocument_loadsBackWithItsUploadSource
- AC6: IngestRunTest.aSecondCliUploadOfTheSameContentIsDeduplicatedAsAnApiUploadIs
