---
id: P17-03
title: Add IngestionService.ingestArticle that queues a fetched article as an ARTICLE document through the upload transaction
status: done
plan_tasks: [17.2]
decisions: [D07]
depends_on: [P17-02]
size: M
issue: 26
commit:
fix_rounds: 0
---
## Goal
`IngestionService.ingestArticle(kbId, userId, finalUrl, title, text, blocks)` inserts a fetched article as an
`ARTICLE` `Document` with a `QUEUED` run, under the same knowledge-base lock, dedup, lesson rule and `distinctTitle`
as an upload. It never parses or admits bytes, never makes a new version, and never publishes `DocumentAccepted`.

## Context
- `src/main/java/dev/mindforge/application/service/IngestionService.java`: `ingest(DocumentUpload)` runs
  `uploadPolicy.admit` and `parser.parse` (~lines 82-83), then one transaction (~lines 86-115): lock knowledge base;
  identical hash -> existing id; lesson exists and not `newVersion` -> `LessonAlreadyExistsException`; new version of
  an unknown lesson -> `UnknownLessonException`; `distinctTitle` when the title is taken; insert `Document` +
  `enqueue` (QUEUED run + `IngestRunQueued`). P17-02 added `DocumentAccepted` on the insert branch of `ingest`.
- `src/main/java/dev/mindforge/domain/model/LessonIdentity.java`: `resolve(Map<String,String> metadata, String filename)`
  cleans and caps the title, falls back to the filename stem, throws `LessonIdentityException` when nothing resolves.
  `KEY_PDF_TITLE` is public (line 32); `KEY_FRONTMATTER_TITLE = "title"` is private (line 38).
- `documents.source_filename` is `VARCHAR(1000) NOT NULL` (`V1__baseline.sql`); `PageRenderer.documentLines` prints
  it in citations. Dedup is a unique index on (knowledge_base_id, content_hash) covering all sources.
- ADR 0026: HTML is not an upload format; no `text/html` parser exists or may be added, which is why articles need
  their own entry method.
- Lesson identity rules: `docs/standards/backend/ai_agents.md` § "Lesson Identity" (deterministic, never a
  placeholder). T18: an existing lesson is skipped, never a new version, never auto-suffixed ids.

## Decisions to apply
**D07 — Decision:** Take the lesson title from the converter's title (`<title>`, else `og:title`, else first `<h1>`;
resolved by the converter in P17-04) and pass it to `LessonIdentity.resolve` under the frontmatter `title` key; the
filename fallback is the last non-empty URL path segment (else the host). Store the final URL (after redirects), cut
to 1000 chars, in `sourceFilename`; `mimeType` is `text/html`; `uploadedBy` is the parent document's uploader;
`source` is `UploadSource.ARTICLE`; the content hash is `ContentHash.compute` over the extracted text in UTF-8. Insert
through a new `IngestionService` entry method that shares the existing transaction body (lock, dedup, lesson rule,
`distinctTitle`, enqueue). No migration.

**D07 — Implementation notes:**
- `IngestionService`: add `ingestArticle(UUID kbId, UUID userId, URI finalUrl, String title, String text,
  List<ContentBlock> blocks)` (`kbId` first). Extract the transaction body of `ingest()` into a private method that
  takes the resolved `LessonIdentity`, hash, filename, mimeType, text, blocks, source, uploader and `newVersion`;
  `ingest()` and `ingestArticle()` both call it. Do not call `uploadPolicy.admit` or `parser.parse` for articles.
  `newVersion` is always false for articles.
- Lesson identity: `LessonIdentity.resolve(title == null ? Map.of() : Map.of(KEY_FRONTMATTER_TITLE, title),
  lastPathSegment(url))`. Make `KEY_FRONTMATTER_TITLE` public with a Javadoc line (like `KEY_PDF_TITLE`) and use it;
  do not repeat the literal. Never pass a `lesson_id` key from HTML.
- `sourceFilename` = `finalUrl.toString()`, cut to 1000 chars (code-point safe). The stored filename and the fallback
  filename are different values: do not pass the full URL to `resolve`.
- Hash: `ContentHash.compute(text.getBytes(StandardCharsets.UTF_8))`.
- `uploadedBy` = the parent's uploader. Do not publish `DocumentAccepted` for ARTICLE inserts (no recursive fetch; D01).
- The caller (P17-07) catches `LessonAlreadyExistsException` and `LessonIdentityException` per article.

**Ticket-writer choice (record in Notes if changed):** the caller must log an identical article as DUPLICATE (D08),
so `ingestArticle` returns a small public record `IngestionService.ArticleAccepted(UUID documentId, boolean duplicate)`
— `duplicate` is true when the identical-content branch returned an existing document. The shared body already
distinguishes the branches (its `Accepted` result has a null run for an identical document).

## Acceptance criteria
- [x] AC1: `ingestArticle` inserts a `Document` with `uploadSource == ARTICLE`, `mimeType == "text/html"`,
  `sourceFilename == finalUrl.toString()`, `uploadedBy == userId`, `knowledgeBaseId == kbId`, the given text and
  blocks, and a hash over the UTF-8 text; it queues one `QUEUED` run and publishes `IngestRunQueued`; returns
  `duplicate == false`.
- [x] AC2: The lesson title comes from `title`; with `title == null` it comes from the last non-empty URL path
  segment's stem (`https://h.example/a/intro-to-cells.html` -> lesson from `intro-to-cells`), and with no path
  segment from the host.
- [x] AC3: When neither resolves (`LessonIdentity.resolve` throws), `LessonIdentityException` propagates and nothing
  is inserted.
- [x] AC4: Identical text already in the knowledge base -> returns that document's id with `duplicate == true`, no
  insert, no run.
- [x] AC5: Lesson id already present -> `LessonAlreadyExistsException`, no insert (never a new version).
- [x] AC6: A new lesson whose title is taken by another lesson gets `distinctTitle` (same as uploads).
- [x] AC7: A final URL longer than 1000 chars is stored cut to 1000 chars without splitting a surrogate pair.
- [x] AC8: `ingestArticle` publishes no `DocumentAccepted`; `ingest()` still publishes it on its insert branch, and all
  existing `ingest()` behaviour and tests are unchanged.
- [x] AC9: `uploadPolicy` and `parser` are never called by `ingestArticle`.
- [x] AC10: `LessonIdentity.KEY_FRONTMATTER_TITLE` is public; the literal `"title"` is not repeated in `IngestionService`.

## Test plan
- Unit (Mockito ports, inline `TransactionOperations`, AssertJ, `make*` factories such as `makeArticleBlocks()`):
  extend `src/test/java/dev/mindforge/unit/application/IngestionServiceTest.java` for AC1–AC9.
- AC10 by compilation/grep in review; AC2 also checked in a `LessonIdentityTest` only if one exists and needs it.

## Files likely touched
- `src/main/java/dev/mindforge/application/service/IngestionService.java`
- `src/main/java/dev/mindforge/domain/model/LessonIdentity.java`
- `src/test/java/dev/mindforge/unit/application/IngestionServiceTest.java`

## Out of scope
- Any migration or new column (`source_url`); any DTO change.
- The URL extraction and fetch loop (P17-07); HTML conversion (P17-04).
- A new-version path for articles; auto-suffixing lesson ids.

## Notes
- Decided: the shared body skips `DocumentAccepted` when `source == ARTICLE` instead of taking an extra flag, because the source already says the document is an article and D01 ties the rule to it.
- Decided: `sourceFilename` is cut to at most 1000 UTF-16 chars, one fewer when char 1000 would be a lone high surrogate, because that fits VARCHAR(1000) under any counting and never splits a pair.
- Decided: the host fallback goes through `LessonIdentity.resolve` as the filename, so its stem is used (`example.org` -> `example`), because the ticket forbids a second resolve path.
- Kept the ticket-writer choice: `ingestArticle` returns `IngestionService.ArticleAccepted(documentId, duplicate)`.
- Tester AC map (IngestionServiceTest): AC1 shouldQueueAnArticleAsAnArticleDocumentWithoutAnnouncingIt; AC2 shouldNameAnUntitledArticleAfterItsLastPathSegmentElseItsHost (+ title case in AC1 test); AC3 shouldRejectAnArticleWithNoTitleAndNoUsableUrlBeforeTouchingTheKnowledgeBase; AC4 shouldReturnTheExistingDocumentAsADuplicateForAnIdenticalArticle; AC5 shouldRejectAnArticleWhoseLessonAlreadyExistsInsteadOfMakingANewVersion; AC6 shouldGiveANewArticleLessonADistinctTitleWhenItsTitleIsTaken; AC7 shouldCutALongArticleUrl...SurrogatePair + shouldCutALongAsciiArticleUrl...; AC8 AC1 test (no DocumentAccepted) + existing ingest tests; AC9 verifyNoInteractions(policy, parser) in AC1 test; AC10 grep/compile.
