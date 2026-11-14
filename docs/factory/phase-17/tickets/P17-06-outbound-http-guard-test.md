---
id: P17-06
title: Pin outbound HTTP client code to the article fetch adapter with a source-scan guard test
status: done
plan_tasks: [17.1]
decisions: [D03]
depends_on: [P17-05]
size: S
issue: 28
commit:
fix_rounds: 0
---
## Goal
A plain JUnit guard test fails, naming the file, when HTTP-client or HTML-fetch code appears in `src/main/java`
outside `dev.mindforge.infrastructure.web`, and when that adapter stops calling `EgressPolicy.check(`. This is the
tripwire for the checklist line "All outbound HTTP (to content- or user-derived URLs) goes through `EgressPolicy`".

## Context
- P17-05 put the only article HTTP code in `src/main/java/dev/mindforge/infrastructure/web/` (`HttpArticleSource`,
  `CheckedDnsResolver`, `HtmlArticleConverter`); `infrastructure/config/ArticleFetchConfig` only calls the
  `HttpArticleSource` factory and `new EgressPolicy()`.
- Operator-configured endpoints (Spring AI to `OPENROUTER_BASE_URL`, OTLP to `LANGFUSE_HOST`, OAuth2 providers) are
  trusted configuration and are not wrapped (ADR 0023, Accepted). Check before you write the marker list that no
  existing `src/main` file already uses a marker (e.g. `RestClient` in a tracing or OAuth2 config); if one does, the
  test must allow that file by exact path with a comment naming it as an operator-configured endpoint per ADR 0023 —
  do not widen the allowance to a package.
- No ArchUnit on the classpath; do not add it.
- Existing unit test folder: `src/test/java/dev/mindforge/unit/infrastructure/security/`.

## Decisions to apply
**D03 — Decision:** `EgressPolicy` guards every outbound HTTP request whose URL comes from content or a user (article
URLs in Phase 17). Operator-configured endpoints (model provider base URL `OPENROUTER_BASE_URL` /
`spring.ai.openai.base-url`, Langfuse OTLP `LANGFUSE_HOST`, OAuth2 provider URIs) are trusted configuration and are not
checked. Record the scope in the docs (P17-10) and pin it with a plain JUnit guard test that allows HTTP-client code
only in the article fetch adapter.

**D03 — Implementation notes (this ticket's part):**
- Guard test, no new dependency: `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java`,
  plain JUnit + AssertJ, walks `src/main/java`, fails naming the file if `java.net.http.HttpClient`,
  `HttpURLConnection`, `URL#openConnection`/`openStream` (`.openConnection(`, `.openStream(`), `RestClient`,
  `RestTemplate`, `WebClient` or `Jsoup.connect` appears outside the single article HTTP adapter; asserts that adapter
  calls `EgressPolicy.check(` (i.e. `HttpArticleSource.java` contains `.check(` on its `EgressPolicy` field).
- From D04: `org.apache.hc.client5` counts as an HTTP-client marker allowed only in the article fetch adapter.
- From D09: only `dev.mindforge.infrastructure.web` imports httpclient5 and jsoup (`org.jsoup`).
- Do not wrap Spring AI, OTLP or OAuth2 clients.

## Acceptance criteria
- [x] AC1: `OutboundHttpGuardTest` passes on the current tree.
- [x] AC2: It reports every `src/main/java` file outside `dev/mindforge/infrastructure/web/` that contains any of:
  `java.net.http.HttpClient`, `HttpURLConnection`, `.openConnection(`, `.openStream(`, `RestClient`, `RestTemplate`,
  `WebClient`, `Jsoup.connect`, `org.apache.hc.client5`, `org.jsoup` — the failure message lists the file paths.
- [x] AC3: It fails when `HttpArticleSource.java` contains no call to `EgressPolicy` `check(`.
- [x] AC4: Its own logic is proven: a helper method that scans a given directory is exercised against a temporary
  directory (`@TempDir`) holding one offending file outside `web/`, and reports that file.
- [x] AC5: No new test dependency; no Spring context.

## Test plan
- Unit (plain JUnit 5 + AssertJ, `java.nio.file.Files.walk`):
  `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` (new) for AC1–AC5. Resolve
  `src/main/java` from the Maven working directory (`Path.of("src/main/java")`), as surefire runs from the module root.

## Files likely touched
- `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` (new)

## Out of scope
- Docs for the egress scope (`architecture.md`, `web-security.md`, plan checklist wording) — P17-10.
- Any change to main code, unless the scan finds a real offender; then stop and record it in Notes.

## Notes
- Decided: allow `dev/mindforge/api/config/SecurityConfig.java` by exact path (its `RestClient` calls the operator-configured OAuth2 token endpoint, trusted per ADR 0023); no other file outside `infrastructure/web/` uses a marker, so no main code changed.
- Decided: the AC3 check finds the `EgressPolicy` field name by regex and requires `<field>.check(` in `HttpArticleSource.java`, because a bare `.check(` match would also accept the private `check(` helper.
- Tester AC map: AC1 shouldFindHttpClientCodeOnlyInTheArticleFetchAdapter; AC2 same + shouldReportAFileWithAMarkerOutsideTheAdapter...; AC3 shouldHaveTheArticleSourceCallEgressPolicyCheck + shouldNotAcceptAnArticleSource...; AC4 @TempDir test; AC5 plain JUnit, no deps.

### Deferred findings
- spec-security/S1 minor 0.4 src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java:28: The JDK HTTP client marker matches only the fully qualified name, so a wildcard import lets new JDK HTTP client code outside the adapter pass the tripwire.
