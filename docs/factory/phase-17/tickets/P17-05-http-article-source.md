---
id: P17-05
title: Fetch one article URL through the ArticleSource port with HttpClient 5 pinned to EgressPolicy-checked addresses
status: done
plan_tasks: [17.1, 17.2, 17.3]
decisions: [D04, D09]
depends_on: [P17-01, P17-04]
size: L
issue: 27
commit:
fix_rounds: 1
---
## Goal
A domain port `ArticleSource` fetches one URL and returns a `FetchedArticle`, or throws `ArticleFetchException` with a
fixed `Reason`. Its adapter `HttpArticleSource` uses Apache HttpClient 5 whose DNS lookups go through `EgressPolicy`,
follows at most 5 redirects in code with a policy check on every hop, enforces the connect/total timeouts, the
HTML-only content types and the body cap, and converts the body with `HtmlArticleConverter`. All other Spring HTTP
clients stay on the JDK transport. Proven against a JDK mock HTTP server (plan 17.3).

## Context
- P17-01 added `infrastructure/security/EgressPolicy` (`check(String) -> URI`, `resolve(String host) ->
  List<InetAddress>`, `EgressDeniedException` with `Reason`) and the test helper
  `src/test/java/dev/mindforge/infrastructure/security/TestEgressPolicies` (`admitting`, `strictAddresses`).
- P17-04 added package-private `infrastructure/web/HtmlArticleConverter` (`convert(html, baseUrl) -> ArticleText`
  with `title()`, `text()`, `blocks()`).
- Ports live in `src/main/java/dev/mindforge/domain/port/` (e.g. `UploadPolicy`, implemented by
  `infrastructure/security/UploadSanitizer`). Domain is JDK only.
- `AppProperties` (`infrastructure/config/AppProperties.java`, prefix `mindforge`, `@Validated`, nested JavaBean
  classes with getters/setters and `jakarta.validation` constraints such as `@Positive`, `DataSize` for sizes as in
  `Upload.maxSize`). `application.yml` maps each property to an env var as `${ENV_NAME:default}`.
- Outbound HTTP today: Spring AI (`OPENROUTER_BASE_URL`, dev `localhost:1234`), OTLP to Langfuse, OAuth2. With HC5 on
  the classpath Spring Boot would prefer it for `RestClient`; the property pin keeps them on the JDK client.
- `httpclient5` 5.6.1 is BOM-managed and already in `~/.m2`.
- ADRs 0022, 0023, 0025 (Accepted) bind this ticket. Error handling: `docs/standards/global/error-handling.md` (fixed
  failure text, never remote content in messages).

## Decisions to apply
**D04 — Decision:** the article fetcher uses Apache HttpClient 5 (`httpclient5`, version from the Spring Boot BOM)
with a custom `DnsResolver` that resolves the host through `EgressPolicy` and returns only checked public-unicast
addresses, so the client connects to exactly what was checked; TLS SNI and hostname verification stay on the name.
Turn off the client's redirect handling and follow 301/302/303/307/308 in code, resolve `Location` against the current
URI, run `EgressPolicy.check` on every hop, and stop after 5 hops with a fixed failure reason. Check IP-literal hosts
in `EgressPolicy.check`. Keep every other Spring HTTP client on the JDK transport with a property pin.

**D04 — Implementation notes:**
- `pom.xml`: `org.apache.httpcomponents.client5:httpclient5`, no version (Boot BOM), comment
  "SSRF: DnsResolver pins checked addresses (ADR 0025, Q04)". Add a line to `docs/project/tech-stack.md` "Key
  Dependencies Summary".
- `application.yml`: `spring.http.clients.imperative.factory: jdk` (the Boot 4 key; `spring.http.client.factory` is
  deprecated since 4.0.0). Do not expose the fetcher's client as a `ClientHttpRequestFactory` bean.
- `CheckedDnsResolver implements org.apache.hc.client5.http.DnsResolver` (package `dev.mindforge.infrastructure.web`,
  per D09): `resolve(String host)` -> `egressPolicy.resolve(host).toArray(InetAddress[]::new)`; override
  `resolve(String, int)` too so no path skips the check; `resolveCanonicalHostname(host)` returns `host` unchanged.
- Build one `CloseableHttpClient`: `PoolingHttpClientConnectionManagerBuilder.create().setDnsResolver(checkedDnsResolver)`,
  connect and response timeouts, `disableRedirectHandling()`, never `useSystemProperties()` (no system proxy).
- Fetcher adapter: loop max 5 hops; `next = current.resolve(location)`; `egressPolicy.check(next)` before each
  request; body size cap; fixed failure texts ("too many redirects", "address not allowed").
- Tests: name mapped to 127.0.0.1 by the injected resolver is refused through the real client; redirect to
  `127.0.0.2` / `169.254.169.254` refused; 6th hop fails; http->https-style redirect to a test-admitted host succeeds.

**D09 — Decision:** Add a domain port `ArticleSource` (not tenant-scoped) that fetches one URL and returns a JDK-only
`FetchedArticle`. Implement it in infrastructure with `EgressPolicy`, HttpClient5 (D04) and the private jsoup
converter (D05). The application `ArticleFetcher` (P17-07) calls it.

**D09 — Implementation notes (this ticket's part):**
- `dev.mindforge.domain.port.ArticleSource`: `FetchedArticle fetch(URI url);` — no `kbId` (not tenant-scoped).
- `dev.mindforge.domain.model.FetchedArticle(URI finalUrl, String title, String text, List<ContentBlock> blocks)`;
  title may be null; defensive copy of `blocks`.
- `dev.mindforge.domain.model.ArticleFetchException` (named domain exception, same style as `UploadRejectedException`)
  with nested `enum Reason`: POLICY, TIMEOUT, HTTP_STATUS, CONTENT_TYPE, TOO_LARGE, NO_TITLE, LESSON_EXISTS, DUPLICATE.
  `reason()` accessor. Fixed messages; never the remote body. The adapter maps `EgressDeniedException` to POLICY and
  never throws any other exception type for a per-URL failure.
- `dev.mindforge.infrastructure.web.HttpArticleSource implements ArticleSource` (EgressPolicy + HC5 client +
  package-private `HtmlArticleConverter`, same package). Bean in a new `@Configuration` `ArticleFetchConfig`.

**D06 (fetch limits part) — applied here:** accept only `text/html` and `application/xhtml+xml` on the final response
(case-insensitive, parameters ignored; missing Content-Type fails -> CONTENT_TYPE). Cap the body at 2 MB: read
through a bounded stream; reading byte cap+1 fails with TOO_LARGE, never truncates; a `Content-Length` above the cap
may fail early. 5 s connect timeout, 15 s total per URL covering all hops. Limits in `AppProperties`:
`mindforge.articles.max-per-document` (10, used by P17-07), `max-body-size` (2MB, `DataSize`), `connect-timeout` (5s),
`request-timeout` (15s), each `@Positive`/non-null, each mapped in `application.yml` to an env var
(`ARTICLES_MAX_PER_DOCUMENT`, `ARTICLES_MAX_BODY_SIZE`, `ARTICLES_CONNECT_TIMEOUT`, `ARTICLES_REQUEST_TIMEOUT`).
Document them in `deployment.md` in P17-10.

**D13 (test part) — applied here:** `HttpArticleSourceTest` uses JDK `com.sun.net.httpserver.HttpServer` on
`InetAddress.getLoopbackAddress()` port 0, stopped in `@AfterEach`; URLs use the `articles.test` name and the server
port; policies from `TestEgressPolicies`. Build the source through the same factory method the production
`@Configuration` uses, with short test timeouts. No Spring context. Cases: admitted fetch succeeds; policy violation
with `strictAddresses` -> POLICY and the server received zero requests; redirect to `http://169.254.169.254/` and to
`http://127.0.0.2:<port>/` refused with the server seeing only the first request (never use the admitted 127.0.0.1 as
the refused target); 6th hop fails; timeout; non-HTML content type; over-size body.

**Ticket-writer choices (record in Notes if changed):**
- Three `Reason` values beyond D08's list, because D09 forbids any other exception type and these failures fit none of
  D08's codes: `REDIRECTS` (more than 5 hops), `UNREACHABLE` (connection refused, TLS or other I/O failure that is not a
  timeout), `EMPTY` (the converter produced no text). A non-2xx, non-redirect status, or a redirect without `Location`,
  is HTTP_STATUS. `NO_TITLE`, `LESSON_EXISTS`, `DUPLICATE` are used by P17-07, not by the adapter.
- The guard test (P17-06) allows HC5 and jsoup imports only in `dev.mindforge.infrastructure.web`, so the client is
  built by a static factory in that package (e.g. `HttpArticleSource.create(EgressPolicy, Duration connectTimeout,
  Duration requestTimeout, long maxBodyBytes)`), and `ArticleFetchConfig` (in `infrastructure.config`) calls only that
  factory and `new EgressPolicy()`. No `CloseableHttpClient` bean; `HttpArticleSource` owns the client and closes it
  (implements `AutoCloseable`, so Spring closes it on shutdown).
- Total timeout: track one deadline per `fetch` across all hops; each request's response timeout is the time left;
  exceeding it gives TIMEOUT. The test may set the request timeout to e.g. 300 ms with a handler that sleeps 2 s.

## Acceptance criteria
- [x] AC1: `ArticleSource`, `FetchedArticle` and `ArticleFetchException` (with `Reason`) exist in the domain with JDK
  imports only; `FetchedArticle` copies `blocks`.
- [x] AC2: `pom.xml` has `httpclient5` without a version and with the ADR 0025 comment; `tech-stack.md` has the row;
  `application.yml` sets `spring.http.clients.imperative.factory: jdk`.
- [x] AC3: `AppProperties.Articles` binds `max-per-document`, `max-body-size`, `connect-timeout`, `request-timeout`
  with defaults 10 / 2MB / 5s / 15s and rejects non-positive values at startup.
- [x] AC4: An admitted fetch of an HTML page returns `FetchedArticle` with the final URL, converted title, text and
  blocks.
- [x] AC5: With `strictAddresses("articles.test", 127.0.0.1, port)` the fetch fails with POLICY and the mock server
  records zero requests (the refusal happens in DNS resolution through `CheckedDnsResolver`, via the real client).
- [x] AC6: A 302 to `http://169.254.169.254/` and a 302 to `http://127.0.0.2:<port>/` each fail with POLICY; the server
  saw only the first request.
- [x] AC7: A chain of 6 redirects fails with REDIRECTS; a chain of up to 5 to an admitted URL succeeds and
  `finalUrl` is the last URL; a relative `Location` is resolved against the current URI.
- [x] AC8: A response slower than the request timeout fails with TIMEOUT.
- [x] AC9: `application/pdf` and a missing Content-Type fail with CONTENT_TYPE; `text/html; charset=UTF-8` and
  `application/xhtml+xml` are accepted.
- [x] AC10: A body over the cap fails with TOO_LARGE both with and without `Content-Length`.
- [x] AC11: A 404/500 fails with HTTP_STATUS; a refused connection fails with UNREACHABLE; a page that converts to no
  text fails with EMPTY.
- [x] AC12: No `ArticleFetchException` message contains the response body or the URL query.
- [x] AC13: The application context still starts with Spring AI and OAuth2 clients on the JDK transport (existing
  integration tests pass in the gate).

## Test plan
- Unit (plain JUnit 5 + AssertJ, JDK `HttpServer`, no Spring):
  `src/test/java/dev/mindforge/infrastructure/web/HttpArticleSourceTest.java` (new) for AC4–AC12.
- Unit: `src/test/java/dev/mindforge/unit/domain/FetchedArticleTest.java` (new) for the defensive copy in AC1.
- AC3: a binder test with `org.springframework.boot.context.properties.bind.Binder` over a `MapConfigurationPropertySource`
  in `src/test/java/dev/mindforge/unit/infrastructure/config/` (new), or follow an existing properties test if one
  exists; no full Spring context.
- AC2 by review; AC13 by the full gate (`mvn -B verify -DskipFrontend=true`).

## Files likely touched
- `pom.xml`, `docs/project/tech-stack.md`
- `src/main/resources/application.yml`
- `src/main/java/dev/mindforge/domain/port/ArticleSource.java` (new)
- `src/main/java/dev/mindforge/domain/model/FetchedArticle.java` (new)
- `src/main/java/dev/mindforge/domain/model/ArticleFetchException.java` (new)
- `src/main/java/dev/mindforge/infrastructure/web/CheckedDnsResolver.java` (new)
- `src/main/java/dev/mindforge/infrastructure/web/HttpArticleSource.java` (new)
- `src/main/java/dev/mindforge/infrastructure/config/ArticleFetchConfig.java` (new)
- `src/main/java/dev/mindforge/infrastructure/config/AppProperties.java`
- `src/test/java/dev/mindforge/infrastructure/web/HttpArticleSourceTest.java` (new)
- `src/test/java/dev/mindforge/unit/domain/FetchedArticleTest.java` (new)
- `src/test/java/dev/mindforge/unit/infrastructure/config/ArticlePropertiesTest.java` (new)

## Out of scope
- URL extraction from documents, the per-document cap, queueing articles (P17-07); the flag and the listener (P17-08).
- The outbound-HTTP guard test (P17-06).
- Non-HTML article formats (PDF/DOCX from the web); a host allowlist; a per-document wall-clock deadline.
- Wrapping Spring AI, OTLP or OAuth2 clients.

## Notes
- Decided: kept the three extra `Reason` values (REDIRECTS, UNREACHABLE, EMPTY) as the ticket proposed; `ArticleFetchException` extends `RuntimeException` (as `DeadlineExceededException`) and carries no cause, because a cause message may hold the host or remote text.
- Decided: `max-body-size`, `connect-timeout` and `request-timeout` are checked positive by one `@AssertTrue isLimitsPositive()` on `AppProperties.Articles`, because `@Positive` does not apply to `DataSize` or `Duration`.
- Decided: a `Location` that is not a valid URI fails with POLICY, because `EgressPolicy.check` refuses an unparsable URL the same way.
- Decided: the client also disables automatic retries, cookies and auth caching, and each request is cancelled after its response is handled, because a retry would break the one-request/one-deadline rule, cookies would cross between fetches, and closing an unread body in HC5 drains it to the end.
- Decided: any 2xx is a success; the deadline is also checked after each 8 KB read of the body, so a slow trickle cannot outlive the total timeout by more than one read.
- Decided (fix r1): the deadline is enforced from outside the blocking call: a per-source daemon watchdog cancels the request at the deadline, `DeadlineConnectionOperator` caps each connect at the time left and tries no further address after it, and every timeout is rounded up to at least 1 ms (also closes deferred C3). Any IOException after the deadline maps to TIMEOUT.
- Decided (fix r1): response header lines are capped at 8192 characters and headers at 100; exceeding either fails with TOO_LARGE.
- Decided (fix r1): the OAuth2 authorization-code token client gets a JDK-backed `RestClient` in `SecurityConfig`, because Spring Security builds it with the plain `RestClient.builder()`, which ignores the Boot property pin and picks HC5. User-info and JWK calls use `RestTemplate` (JDK `HttpURLConnection`) and need no change.
- Tester r1 AC map: AC1 FetchedArticleTest; AC2 review; AC3 ArticlePropertiesTest; AC4 HttpArticleSourceTest.anAdmittedHtmlPage_*; AC5 aHostResolvingToLoopback_*; AC6 aRedirectToANonPublicAddress_*; AC7 aSixthRedirect_*, fiveRedirects_*; AC8 aResponseSlowerThanTheRequestTimeout_*; AC9 aNonHtmlContentType_*, aMissingContentType_*, anHtmlContentType_*; AC10 aBodyOverTheCapWithContentLength_*, aChunkedBodyOverTheCap_*; AC11 anErrorStatus_*, aRefusedConnection_*, aPageWithNoText_*; AC12 aFailureMessage_*; AC13 full gate (only baseline failure).

### Deferred findings
- correctness/C3 minor 0.6 src/main/java/dev/mindforge/infrastructure/web/HttpArticleSource.java:88: When less than 1 ms is left on the deadline at the start of a hop, the response timeout becomes 0 ms, which HC5 and the socket treat as "no timeout", so that hop can block with no limit.
