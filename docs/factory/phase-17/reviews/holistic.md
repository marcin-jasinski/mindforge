---
lens: holistic
ticket: phase-17
verdict: approve
---
- id: H1
  severity: minor
  confidence: 0.85
  location: docs/standards/security/web-security.md:75
  claim: Three docs say HTTP-client code in src/main lives only in the article fetch adapter, but P17-06's guard test and P17-05's SecurityConfig change put a RestClient in api/config/SecurityConfig.java, which is exempt by exact path.
  evidence: web-security.md:75 says "HTTP-client code in `src/main` lives only in the article fetch adapter ... `OutboundHttpGuardTest` fails on any other HTTP-client code". architecture.md:73 says "It is the only place in `src/main` with HTTP-client code". ADR 0023 line 10 says "allows HTTP-client code in `src/main/java` only in the article fetch adapter". But OutboundHttpGuardTest.OPERATOR_ENDPOINT_FILES exempts "dev/mindforge/api/config/SecurityConfig.java", and SecurityConfig.authorizationCodeTokenClient() builds a `RestClient`. tech-stack.md (httpclient5 row) does name the SecurityConfig token client, so the docs disagree with each other. A reader who follows web-security.md does not know that an exact-path exemption exists for operator-configured endpoints.
  fix: In web-security.md:75 and architecture.md:73, add "except operator-configured endpoints allowed by exact path in the guard test (today the OAuth2 token client in `SecurityConfig`)". ADR 0023 can stay as written or get the same note.

- id: H2
  severity: minor
  confidence: 0.8
  location: docs/adr/0024-articles-fetched-after-upload-commit-from-a-document-event.md:9
  claim: ADR 0024 says each article is queued "through `IngestionService.ingest` as an `ARTICLE` upload", but P17-03 built a separate `IngestionService.ingestArticle`, which does not admit or parse the article and does not publish `DocumentAccepted`.
  evidence: IngestionService.java ingestArticle(...) calls the private accept(...) with UploadSource.ARTICLE and skips uploadPolicy.admit and parser.parse ("HTML is not an upload format"). ArticleFetcher.fetchOne calls ingestion.ingestArticle(...). architecture.md and implementation-plan.md correctly name `ingestArticle`, so the ADR is the one doc that disagrees. A future reader who follows the ADR would route articles through admit/parse, and that path rejects text/html.
  fix: In ADR 0024, change "through `IngestionService.ingest` as an `ARTICLE` upload" to "through `IngestionService.ingestArticle` (same lock, dedup and lesson rule as `ingest`; no admit or parse)".

- id: H3
  severity: minor
  confidence: 0.7
  location: docs/project/architecture.md:143
  claim: architecture.md and implementation-plan.md say CLI documents are scanned for article URLs, but P17-08 does not register the listener in the cli profile, so a CLI upload never fetches. UploadSource.CLI in ArticleFetcher.SCANNED_SOURCES is dead.
  evidence: architecture.md:143 says "only API, CLI and file-watcher documents", and implementation-plan.md:1542 says "an API, CLI or file-watcher document". ArticleFetchConfig puts articleFetchTriggers under @Profile("!cli"). The CLI process (PipelineCommandRunner, UploadSource.CLI) publishes DocumentAccepted in its own JVM, where no listener is registered, and the server never sees that event. So no code path calls fetchFor for a CLI document. Lines 151-152 of architecture.md and deployment.md "CLI" say the CLI never fetches. This contradicts line 143.
  fix: Change the docs to "API and file-watcher documents (the cli profile registers no listener)". Optionally remove UploadSource.CLI from SCANNED_SOURCES and update ArticleFetcherTest to match.
