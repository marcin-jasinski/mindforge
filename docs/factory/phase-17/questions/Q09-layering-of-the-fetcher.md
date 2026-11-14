---
id: Q09
status: decided
decision: D09
impact: medium
blocks: [W02, W04, W05]
---
## Question
How is the fetcher split across layers: which part is a domain port, which part is infrastructure, and which
application class turns fetched articles into queued documents?

## Context
- Plan 17.2 names `ArticleFetcher` without a package; 17.1 puts `EgressPolicy` in `dev.mindforge.infrastructure.security`.
- Application may import only domain, agent, Spring transaction API and SLF4J (CLAUDE.md layer table). Creating a
  Document with a run, the lesson rule and dedup live in `IngestionService` (application). HTTP and HTML parsing are
  infrastructure.
- Existing pattern: `UploadPolicy` (domain port) ← `UploadSanitizer` (infrastructure.security); `DocumentParser`
  (port) ← `ParserRegistry`.
- `docs/standards/global/minimal-implementation.md`: no speculative abstractions.

## Known options
- A: Domain port (e.g. `ArticleSource.fetch(URI) → Optional<FetchedArticle>`, a JDK record with title, final URL,
  blocks); infrastructure adapter does `EgressPolicy` + HTTP + HTML; an application `ArticleFetcher` extracts URLs from
  blocks, calls the port, and queues each article through `IngestionService`.
- B: `ArticleFetcher` entirely in infrastructure (extract, policy, fetch, convert) and it calls
  `IngestionService` directly; the trigger (Q01) is an infrastructure listener. Fewer types; URL extraction logic sits
  in infrastructure.
- C: A, but URL extraction is a pure domain function (unit-testable without HTTP).

## Constraints
- Dependencies point inward; domain is JDK only (`docs/standards/architecture/hexagonal.md`).
- Beans in `@Configuration` (`IngestionConfig` or a new config class).
- Every tenant-scoped port method takes `kbId` first (a fetch port is not tenant-scoped; the document insert is).
