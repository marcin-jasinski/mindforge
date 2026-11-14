# Phase 17 — Article Fetcher: tickets

Execution order. P17-01, P17-02, P17-04 and P17-09 have no dependencies and can run in parallel. P17-03 follows
P17-02; P17-05 follows P17-01 and P17-04.

| Id | Title | Depends on | Size | Status |
|---|---|---|---|---|
| [P17-01](P17-01-egress-policy.md) | Add the EgressPolicy that admits only http/https URLs on default ports whose every address is public unicast | — | M | ready |
| [P17-02](P17-02-document-accepted-event.md) | Publish DocumentAccepted in the upload transaction when an upload inserts a new document | — | S | ready |
| [P17-04](P17-04-html-article-converter.md) | Convert fetched HTML into a title and plain-text HEADING/TEXT/CODE blocks with a jsoup converter private to the fetcher | — | M | ready |
| [P17-09](P17-09-article-run-is-create-only-test.md) | Prove an ARTICLE run writes nothing to a live page and skips Supersede with reason "article" | — | S | ready |
| [P17-03](P17-03-ingestion-service-accepts-articles.md) | Add IngestionService.ingestArticle that queues a fetched article as an ARTICLE document through the upload transaction | P17-02 | M | ready |
| [P17-05](P17-05-http-article-source.md) | Fetch one article URL through the ArticleSource port with HttpClient 5 pinned to EgressPolicy-checked addresses | P17-01, P17-04 | L | ready |
| [P17-06](P17-06-outbound-http-guard-test.md) | Pin outbound HTTP client code to the article fetch adapter with a source-scan guard test | P17-05 | S | ready |
| [P17-07](P17-07-article-fetcher-service.md) | Add the ArticleFetcher that extracts a document's article URLs, fetches each and queues it as an ARTICLE document, behind fetchExternalArticles | P17-03, P17-05 | L | ready |
| [P17-08](P17-08-fetch-articles-after-upload-commit.md) | Fetch a new document's articles after the upload commits, on the run executor, when mindforge.articles.fetch-external-articles is on | P17-02, P17-07 | M | ready |
| [P17-10](P17-10-close-phase-17-docs.md) | Record the article fetch flow, the egress scope and the new settings in the docs, and close Phase 17 | P17-01..P17-09 | S | ready |

## Coverage

| Plan task / item | Tickets |
|---|---|
| 17.1 `EgressPolicy` | P17-01, P17-05 (every hop, DNS pin), P17-06 |
| 17.2 `ArticleFetcher` (extract, validate, fetch, HTML → text, ARTICLE document + QUEUED run, lesson skip) | P17-02, P17-03, P17-04, P17-05, P17-07, P17-08 |
| 17.2 create-only / no Supersede (already built) | P17-09 (test only) |
| 17.2 switch, off by default | P17-07 (component + off-path test), P17-08 (binding) |
| 17.3 tests (`EgressPolicyTest`, mock-server test with policy violation, ARTICLE run test) | P17-01, P17-05, P17-09 (plus tests in every ticket) |
| Checklist: all outbound HTTP (content/user URLs) through `EgressPolicy` | P17-05, P17-06, P17-10 |
| Checklist: disabled by default | P17-07, P17-08 |
| Close-out docs | P17-10 |

| Decision | Applied by |
|---|---|
| D01 | P17-02, P17-08, P17-10 |
| D02 | P17-01 |
| D03 | P17-06, P17-10 |
| D04 | P17-05 (and IP-literal checks in P17-01) |
| D05 | P17-04 |
| D06 | P17-05 (limits, content types), P17-07 (URL extraction, cap) |
| D07 | P17-03 (title chain in P17-04) |
| D08 | P17-07 (reason enum in P17-05) |
| D09 | P17-05 (port, adapter), P17-07 (application service) |
| D13 | P17-01 (seam), P17-05 (mock-server tests) |
