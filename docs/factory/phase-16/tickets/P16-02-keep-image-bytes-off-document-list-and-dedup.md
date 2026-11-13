---
id: P16-02
title: Keep content blocks off the document list and the dedup lookup, and out of every document response
status: done
plan_tasks: [16.1]
decisions: [D01]
depends_on: [P16-01]
size: M
issue: 12
commit: eaa9ec0
fix_rounds: 0
---
## Goal
Image bytes now live in `documents.content_blocks` (ADR 0020), so that column can be large. After this ticket the
document list (`GET` of a knowledge base's documents) and the upload dedup lookup do not load `content_blocks`; only
the paths that need the blocks (the ingest run's `findById`) load them. A test proves no image bytes appear in a
`DocumentResponse`.

## Context
- P16-01 added `ImageInput` and `ContentBlock.image` (IMAGE blocks persist with base64 bytes in the JSONB column).
- `src/main/java/dev/mindforge/domain/port/DocumentRepository.java`: `findByContentHash(UUID kbId, ContentHash)`
  (line 21) and `listByKnowledgeBase(UUID kbId)` (line 26) return full `Document`s.
- `src/main/java/dev/mindforge/infrastructure/persistence/adapter/DocumentRepositoryAdapter.java:46-72` maps whole
  `DocumentEntity` rows (`DocumentJpaRepository.findByKnowledgeBaseIdAndContentHashAndUploadSourceNot`,
  `findByKnowledgeBaseId`). `DocumentEntity.contentBlocks` is an eager `@JdbcTypeCode(SqlTypes.JSON)` jsonb column.
- Callers: `IngestionService.ingest` (line ~90) uses the dedup result only for `documentId()`;
  `IngestionService.documents(kbId)` (line ~154) feeds `DocumentController` (line 68) -> `DocumentResponse`, which
  already excludes `contentBlocks` (`api/dto/response/DocumentResponse.java`).
- `IngestPipeline.generateAndCommit` loads the run's document with `documents.findById(kbId, documentId)` and needs
  the blocks — this path must keep loading them.
- Rules: every tenant-scoped port method takes `kbId` first; domain is JDK only; queries follow
  `docs/standards/backend/queries.md` and `docs/standards/backend/models.md`. Image bytes never in any API response
  (`docs/standards/security/web-security.md`, ADR 0020 Consequences).

## Decisions to apply
**D01 — Decision:** describe images inside the ingest run, as its first step, before `Preprocessor.clean` and so
before `RelevanceGuard`. Parsers store IMAGE blocks with their bytes in `documents.content_blocks` (JSONB) at upload;
the `Document` stays immutable, and every run attempt (including retries) describes the images again, under the
background permit, with `ImageDescriber.VERSION` recorded in `step_versions`. Keep `content_blocks` out of the
document list read path and out of every API response and the export.

**D01 — Implementation notes applied here:**
- Read amplification: make `listByKnowledgeBase` and `findByContentHash` not load `content_blocks` (projection or
  lazy basic fetch), or document why deferred. The run's own document load keeps the blocks.
- Test: integration test that `DocumentResponse` JSON for a document with an image has no base64 bytes.
- Dissent risk (skeptic): `content_blocks` is an eager JSONB column and the document list and `findByContentHash`
  load it, so image bytes inflate every document-list GET and every upload's dedup check.

Implementer's choice of mechanism, with these limits:
- Preferred: a JPA/JPQL projection (or a native query selecting named columns) for the two read paths, mapped to a
  `Document` whose `contentBlocks` is an empty list, with the port Javadoc stating that these two methods return
  documents without content blocks; or narrow `findByContentHash` to return what the caller needs (e.g. the
  document id) if that is simpler. Keep `kbId` first. Update `IngestionServiceTest` mocks if a signature changes.
- Hibernate lazy basic attributes need bytecode enhancement; do not add a build plugin for this.
- If you decide to defer, write the reason in this ticket's Notes and still deliver the `DocumentResponse` test.

## Acceptance criteria
- [x] AC1: `listByKnowledgeBase(kbId)` does not select the `content_blocks` column (proved by an integration test that
  a document inserted with an IMAGE block comes back from the list with no content blocks, or by asserting the SQL).
- [x] AC2: The dedup lookup used by `IngestionService.ingest` does not select `content_blocks`; an identical upload is
  still answered with the existing document's id (existing tests keep passing).
- [x] AC3: `findById(kbId, documentId)` still returns the full blocks, including the IMAGE block's `ImageInput`.
- [x] AC4: For a document whose blocks include an IMAGE block, the JSON body of the document list and the single
  document endpoint contains neither the base64 of the image bytes nor a `contentBlocks`/`image` field.
- [x] AC5: Port Javadoc states what each read method loads.

## Test plan
- Integration (Testcontainers): extend
  `src/test/java/dev/mindforge/integration/persistence/DocumentRepositoryAdapterTest.java` for AC1–AC3.
- Integration (API): a test in `src/test/java/dev/mindforge/integration/api/ApiFlowTest.java` (or a new sibling in that
  folder, following its setup) that stores a document with an IMAGE block for an owned knowledge base and asserts the
  list/detail JSON has no base64 of the bytes (AC4). Note: `ApiFlowTest.theLoginRouteIsTheSinglePageApp` fails on the
  baseline; do not try to fix it here.
- Unit: adjust `src/test/java/dev/mindforge/unit/application/IngestionServiceTest.java` if the dedup signature changes.

## Files likely touched
- `src/main/java/dev/mindforge/domain/port/DocumentRepository.java`
- `src/main/java/dev/mindforge/infrastructure/persistence/adapter/DocumentRepositoryAdapter.java`
- `src/main/java/dev/mindforge/infrastructure/persistence/jpa/DocumentJpaRepository.java`
- `src/main/java/dev/mindforge/infrastructure/persistence/mapper/` (document mapper, if a projection is mapped)
- `src/main/java/dev/mindforge/application/service/IngestionService.java` (only if a port signature changes)
- `src/test/java/dev/mindforge/integration/persistence/DocumentRepositoryAdapterTest.java`
- `src/test/java/dev/mindforge/integration/api/` (API JSON test)
- `src/test/java/dev/mindforge/unit/application/IngestionServiceTest.java`

## Out of scope
- A separate `document_images` table, a description cache by image hash, or any migration (D01 rejected option D).
- Changing what `findById` loads; changing `DocumentResponse` fields.
- Export checks (P16-08 covers the export with a real image run).

## Notes
- Decided: both read paths use one JPQL constructor projection (`DocumentWithoutBlocks`, all columns but `content_blocks`) mapped by a default method on `DocumentEntityMapper` to a `Document` with empty blocks, because it keeps the port signatures (no `IngestionServiceTest` change) and follows the existing `SELECT new` pattern; `original_content` stays selected so no other field of the returned `Document` changes.
- Tester r1 AC map: AC1 -> DocumentRepositoryAdapterTest.theListAndTheDedupLookup_leaveOutTheBlocks_whileFindByIdKeepsTheImage; AC2 -> same test + findByContentHash_skipsAConversationTurn; AC3 -> same test (findById keeps IMAGE); AC4 -> ApiFlowTest.aDocumentWithAnImage_isListedAndShownWithoutItsBytesOrBlocks; AC5 -> port Javadoc in DocumentRepository (inspected).
