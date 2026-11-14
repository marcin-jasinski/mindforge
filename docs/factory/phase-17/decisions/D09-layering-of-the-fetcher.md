---
id: D09
question: Q09
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q09.r1.tally.md]
adr:
---
## Decision
Use A. Add a domain port `ArticleSource` (not tenant-scoped) that fetches one URL and returns a JDK-only
`FetchedArticle`. Implement it in infrastructure with `EgressPolicy`, HttpClient5 (D04) and the private jsoup
converter (D05). Put the application service `ArticleFetcher` in `dev.mindforge.application.service`: it extracts
the URLs from the parent's blocks (D06), calls the port for each URL outside a transaction, and queues each article
through `IngestionService` (D07). The infrastructure `AFTER_COMMIT` listener (D01) calls `ArticleFetcher`.

## Why
- D01 / ADR 0024 already decide that an application service extracts the URLs and queues the articles; A is that shape.
- The application layer cannot import HTTP or jsoup (CLAUDE.md layer table), so it needs a domain port.
- Same pattern as `UploadPolicy` <- `UploadSanitizer` (domain/port/UploadPolicy.java:6, infrastructure/security/UploadSanitizer.java:15).
- All SSRF and HTTP code stays in one adapter, so the D03 guard can pin outbound HTTP to one place.
- Unanimous (3/3, confidence 0.7-0.8). This adds a port in the existing pattern and does not change a layer
  boundary; ADR 0024 already records the layering. Class `local`.

## Rejected
- B: contradicts accepted D01 / ADR 0024; puts the ingest flow (URL rules, queueing) in an adapter.
- C: a domain function for logic that has one caller; a package-private method in `ArticleFetcher` is equally
  testable without HTTP (minimal-implementation.md).

## Dissent
None. Risk to watch: the port must report a reason for each failure (D08), so a bad URL is logged with a fixed code
and never stops the loop.

## Implementation notes
- Domain (`dev.mindforge.domain.port`): `public interface ArticleSource { FetchedArticle fetch(URI url); }`.
  Not tenant-scoped, so no `kbId`.
- Domain (`dev.mindforge.domain.model`): `record FetchedArticle(URI finalUrl, String title, String text,
  List<ContentBlock> blocks)` (title may be null; D07 resolves the fallback). Defensive copy of `blocks`.
- Judge refinement of the ballots' `Optional<FetchedArticle>`: an empty Optional loses D08's reason. The port throws
  `ArticleFetchException` instead. Put it in `dev.mindforge.domain.model` (the domain port declares it, and domain
  cannot import application), with the D08 `Reason` enum: POLICY, TIMEOUT, HTTP_STATUS, CONTENT_TYPE, TOO_LARGE,
  NO_TITLE, LESSON_EXISTS, DUPLICATE. Fixed messages; never the remote body. The adapter maps
  `EgressDeniedException` to POLICY and never throws any other exception type for a per-URL failure.
- Infrastructure: `dev.mindforge.infrastructure.web.HttpArticleSource implements ArticleSource` (EgressPolicy +
  CloseableHttpClient from D04 + package-private jsoup `HtmlArticleConverter` from D05, same package). Bean in a new
  `@Configuration` (e.g. `ArticleFetchConfig`) or `IngestionConfig`.
- Application: `ArticleFetcher` with `void fetchFor(UUID kbId, UUID documentId, UUID uploadedBy)` (kbId first). Return
  early when `!fetchExternalArticles` or the source is ARTICLE/CONVERSATION (D01). Load blocks with
  `DocumentRepository.findById(kbId, documentId)`. Package-private `static List<URI> extractUrls(List<ContentBlock>, int cap)`
  (TEXT/HEADING only, http/https, drop fragment, distinct in order, cap; D06). No `@Transactional`. For each URL: call
  the port, then `ingestion.ingestArticle(kbId, uploadedBy, a.finalUrl(), a.title(), a.text(), a.blocks())` (D07);
  catch and log per URL per D08.
- Listener: `infrastructure/event/ArticleFetchTriggers` (D01) calls `articleFetcher.fetchFor(...)` on `runExecutor`.
- Tests: `ArticleFetcherTest` (plain Mockito, stub `ArticleSource`): extraction rules and cap; flag off -> no fetch;
  ARTICLE parent -> no fetch; one failing URL does not stop the next; each reason logged. Plan 17.3's
  mock-HTTP-server test targets the adapter: `HttpArticleSourceTest` (policy violation, timeout, non-HTML, over-size,
  redirect). The architecture guard asserts that only `infrastructure.web` imports httpclient5 and jsoup.
