---
id: P17-07
title: Add the ArticleFetcher that extracts a document's article URLs, fetches each and queues it as an ARTICLE document, behind fetchExternalArticles
status: done
plan_tasks: [17.2, 17.3]
decisions: [D06, D08]
depends_on: [P17-03, P17-05]
size: L
issue: 29
commit: b3b0d6f
fix_rounds: 0
---
## Goal
`ProcessingSettings` has `fetchExternalArticles` (false by default). The application service
`dev.mindforge.application.service.ArticleFetcher.fetchFor(kbId, documentId, uploadedBy)` does nothing when the
switch is off or the document is an `ARTICLE`/`CONVERSATION`. Otherwise it takes the first N distinct http(s) URLs
from the document's TEXT/HEADING blocks, fetches each through `ArticleSource` with no transaction open, and queues each
through `IngestionService.ingestArticle`. A failing URL is skipped and logged with a fixed reason; the others go on.

## Context
- `src/main/java/dev/mindforge/domain/model/ProcessingSettings.java`: record with components `chunkSizeTokens, ...,
  Map<String,Boolean> featureFlags, Map<ModelTier,String> modelTierMappings`; `defaults()`, `withModels(...)`; the
  `featureFlags` map is unused. The canonical constructor is also called in
  `src/test/java/dev/mindforge/unit/application/FlashcardServiceTest.java` (update it).
- P17-05 added `domain/port/ArticleSource` (`FetchedArticle fetch(URI)`), `domain/model/FetchedArticle`
  (`finalUrl, title, text, blocks`), `domain/model/ArticleFetchException` (`reason()`: POLICY, TIMEOUT, HTTP_STATUS,
  CONTENT_TYPE, TOO_LARGE, NO_TITLE, LESSON_EXISTS, DUPLICATE, REDIRECTS, UNREACHABLE, EMPTY) and
  `AppProperties.Articles.maxPerDocument` (default 10).
- P17-03 added `IngestionService.ingestArticle(kbId, userId, URI finalUrl, String title, String text,
  List<ContentBlock> blocks)` returning `IngestionService.ArticleAccepted(documentId, duplicate)`; it throws
  `LessonAlreadyExistsException` or `LessonIdentityException`.
- `DocumentRepository.findById(UUID kbId, UUID documentId)` returns `Optional<Document>` with blocks;
  `Document.uploadSource()`. `MarkdownParser` keeps link Markdown in block text; PDF/DOCX blocks hold visible text.
- Application layer may import only domain, agent, Spring transaction API and SLF4J (`hexagonal.md`). No
  `@Transactional` here: network I/O never runs inside a transaction.
- Q10/Q11/Q14 answers (settled): switch is global, off by default, a named record component; scan only uploads
  (`API`, `CLI`, `FILE_WATCHER`), never `ARTICLE` (no recursion) or `CONVERSATION`; existing lesson -> skip and log,
  identical content -> existing id, no new run.
- The bean wiring and the listener that calls `fetchFor` are P17-08.

## Decisions to apply
**D09 (application part) — operative text:** `ArticleFetcher` with `void fetchFor(UUID kbId, UUID documentId, UUID
uploadedBy)` (kbId first). Return early when `!fetchExternalArticles` or the source is ARTICLE/CONVERSATION. Load
blocks with `DocumentRepository.findById(kbId, documentId)`. Package-private
`static List<URI> extractUrls(List<ContentBlock>, int cap)`. No `@Transactional`. For each URL: call the port, then
`ingestion.ingestArticle(kbId, uploadedBy, a.finalUrl(), a.title(), a.text(), a.blocks())`; catch and log per URL.

**D06 — Decision:** Take `http`/`https` URLs from TEXT and HEADING blocks only (never CODE, IMAGE, AUDIO, VIDEO).
Normalise each URL by dropping the fragment, keep distinct URLs in document order, and fetch the first N (default 10).
Log the count of URLs over the cap with a fixed message.

**D06 — Implementation notes (this ticket's part):**
- Scan `ContentBlock` content of TEXT and HEADING blocks; match Markdown `[..](url)` and bare `https?://...`; trim
  trailing `.,;:!?)]"'`; keep only `http`/`https`; drop the fragment and keep the query; `LinkedHashSet` for order;
  `limit(N)`; `log.info("Article URLs over cap skipped: documentId={} skipped={}", ...)` when skipped > 0. A string
  that does not parse as a URI is ignored.
- Tests (plain JUnit, tiny limits): CODE-block URL skipped; duplicate and fragment-only variants deduped; document
  order kept; cap=2 with 3 URLs fetches 2 and logs skipped=1; trailing punctuation trimmed; `ftp:`/`mailto:` ignored.

**D08 — Decision:** When one article cannot be ingested, skip it, log it with a sanitised URL and a fixed reason code,
and continue with the next URL. Store nothing about the skip. The parent upload, its document and its run are not
affected. Do not add a metrics counter in this phase.

**D08 — Implementation notes:**
- Reasons come from `ArticleFetchException.Reason`. Map `LessonAlreadyExistsException` -> LESSON_EXISTS,
  `LessonIdentityException` -> NO_TITLE, `ArticleAccepted.duplicate() == true` -> DUPLICATE.
- The service catches per URL; one failure never stops the loop. Also catch an unexpected `RuntimeException` per URL
  and log it with a fixed reason text (e.g. `reason=UNEXPECTED`, exception class name only, never its message), so the
  loop never dies mid-list.
- Log: `log.warn("article skipped reason={} url={}", reason, safeUrl)` for POLICY, TIMEOUT, HTTP_STATUS, CONTENT_TYPE,
  TOO_LARGE, NO_TITLE (and REDIRECTS, UNREACHABLE, EMPTY); `log.info` for LESSON_EXISTS and DUPLICATE (include kbId).
  `safeUrl` = scheme + host + port + path; strip userinfo, query and fragment (package-private static helper).
- Tests: each reason is logged and skipped; the second URL is still ingested after the first fails; nothing is written
  for a skipped URL; the logged URL has no query or userinfo.

**Q10 (answered) — switch:** add `boolean fetchExternalArticles` as a named `ProcessingSettings` component; `defaults()`
and `withModels(...)` set it to false; add `ProcessingSettings withFetchExternalArticles(boolean)` returning a copy
(used by P17-08). Do not use the `featureFlags` map for it.

## Acceptance criteria
- [x] AC1: `ProcessingSettings.defaults().fetchExternalArticles()` and `withModels(...).fetchExternalArticles()` are
  false; `withFetchExternalArticles(true)` returns a copy with every other component equal.
- [x] AC2: With the switch off, `fetchFor` returns without calling `DocumentRepository`, `ArticleSource` or
  `IngestionService` (a document with URLs fetches nothing).
- [x] AC3: For a parent with source `ARTICLE` or `CONVERSATION`, nothing is fetched; for `API`, `CLI`, `FILE_WATCHER`
  the URLs are fetched. A missing document returns quietly (logged), fetching nothing.
- [x] AC4: `extractUrls` takes URLs from TEXT and HEADING blocks only; ignores CODE (and other types); handles Markdown
  links and bare URLs; trims trailing `.,;:!?)]"'`; drops fragments, keeps queries; dedups in document order; ignores
  `ftp:`, `mailto:` and unparsable strings.
- [x] AC5: With cap 2 and 3 distinct URLs, exactly the first 2 are fetched and the over-cap count (1) is logged.
- [x] AC6: Each fetched article is queued with `ingestArticle(kbId, uploadedBy, finalUrl, title, text, blocks)` from
  the `FetchedArticle`.
- [x] AC7: When URL 1 fails with any `ArticleFetchException` reason, `LessonAlreadyExistsException`,
  `LessonIdentityException` or an unexpected `RuntimeException`, URL 2 is still fetched and queued; nothing is
  ingested for URL 1.
- [x] AC8: Each skip is logged with its reason code and a URL with no userinfo, query or fragment; no log line or
  exception message contains remote body text.
- [x] AC9: `ArticleFetcher` imports nothing outside domain, SLF4J and the JDK, and has no `@Transactional`.

## Test plan
- Unit (plain Mockito for `DocumentRepository`, `ArticleSource`, `IngestionService`; AssertJ; `make*` factories such
  as `makeDocument(UploadSource, List<ContentBlock>)`, no `@BeforeEach` fixtures):
  `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java` (new) for AC2–AC8. Logged output: capture
  with a Logback `ListAppender` on the `ArticleFetcher` logger (Logback is on the test classpath via Spring Boot).
- Unit: `src/test/java/dev/mindforge/unit/domain/ProcessingSettingsTest.java` (new or extend) for AC1.
- AC9 by review. Update `FlashcardServiceTest` for the new component.

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/ProcessingSettings.java`
- `src/main/java/dev/mindforge/application/service/ArticleFetcher.java` (new)
- `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java` (new)
- `src/test/java/dev/mindforge/unit/domain/ProcessingSettingsTest.java` (new or extended)
- `src/test/java/dev/mindforge/unit/application/FlashcardServiceTest.java`

## Out of scope
- The `@Configuration` bean, the property binding and the `AFTER_COMMIT` listener (P17-08).
- Links inside fetched articles or conversation turns; DOCX hyperlink targets not present in block text.
- A metrics counter, a per-document note about skips, a per-KB or per-upload toggle, a UI switch.
- Any change to `Resolver` or `IngestPipeline`.

## Notes
- Decided: `extractUrls(List<ContentBlock>)` returns all distinct URLs and `fetchFor` applies the cap, because the over-cap log line needs the documentId and the full count, which a capped static helper cannot give.
- Decided: `ArticleFetcher` takes `ProcessingSettings` and `int maxPerDocument` in its constructor, because the application layer cannot import `AppProperties`; P17-08 passes `articles.maxPerDocument`.
- Decided: AC4 and the `safeUrl` rule are tested through `fetchFor` (captured `ArticleSource.fetch` calls and log lines), because the test package cannot call the package-private helpers.
- Decided: an unexpected exception is logged as `reason=UNEXPECTED url=... exception=<class name>` at WARN, with no throwable attached, so no remote text reaches the log.
- Tester r1 AC map: AC1 -> ProcessingSettingsTest (both tests); AC2 -> ArticleFetcherTest.fetchFor_withTheSwitchOff_touchesNothing; AC3 -> fetchFor_anArticleOrConversationParent_fetchesNothing, fetchFor_anUploadedParent_fetchesItsUrls, fetchFor_aMissingDocument_logsAndFetchesNothing; AC4 -> fetchFor_takesUrlsFromTextAndHeadingBlocksOnly, ..._dropsFragmentsKeepsQueriesAndDedupsInDocumentOrder, ..._trimsTrailingPunctuation, ..._ignoresOtherSchemesAndUnparsableUrls; AC5 -> fetchFor_overTheCap_...; AC6 -> fetchFor_queuesTheFetchedArticle; AC7 -> fetchFor_whenAFetchFails_..., fetchFor_whenAnIngestFails_...; AC8 -> assertSkipLogged in those tests + fetchFor_aDuplicateArticle_...; AC9 -> review (imports checked: domain, SLF4J, JDK only; no @Transactional).

### Deferred findings
- correctness/C1 minor 0.6 src/main/java/dev/mindforge/application/service/ArticleFetcher.java:40: A URL wrapped in Markdown emphasis keeps the closing markers, so the fetcher requests a wrong URL and the link is lost as an HTTP_STATUS skip.
- standards/S1 nit 0.6 src/main/java/dev/mindforge/domain/model/ProcessingSettings.java:7: The edited Javadoc line is far longer than the 120-column wrap used in the rest of the file.
