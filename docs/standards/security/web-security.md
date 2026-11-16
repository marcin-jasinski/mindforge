# Web Security Standards

## Server-Authoritative State

The server owns all grading, scoring, and session state. Client payloads must never contain security-critical state.

**Fields that must never appear in API responses to browsers:**
- `reference_answer`
- `grounding_context`
- `raw_prompt`
- `raw_completion`
- `cost`

```java
// NEVER in any API response
return Map.of("referenceAnswer", session.question(index).referenceAnswer());  // ❌

// CORRECT: return only evaluated results
return new EvaluationResponse(result.score(), result.feedback());
```

Redaction is enforced in `InteractionStore.listForUser()` — **not only in controllers** (defense-in-depth). The store never returns unredacted interaction data for user-facing queries.

**An export is a client too.** Reference answers and grounding excerpts exist only in `quiz_sessions`, and no study data (flashcards, SM-2 state, study events, quiz sessions) is ever written into a wiki page body or frontmatter. The bundle exporter never queries those tables, and it never selects `cost`, `step_versions`, `failures` or `findings` from `ingest_runs`. Keep sensitive data out of what gets rendered, rather than redacting it on the way out.

Enforced by: `InteractionStoreAdapter`, `BundleExporter`; tested by `ApiFlowTest.anUploadIngestsIntoPagesAndNoResponseCarriesASensitiveField`, `ApiFlowTest.aLearnerStudiesFlashcardsAndAQuizWithoutEverSeeingAReferenceAnswer`, `SecurityTest.aForeignRunIdOrPagePathUnderOnesOwnKnowledgeBaseIsNotFoundAndShowsNothingOfTheOwner`, `QueryAndEditTest.aUsersHistoryHoldsOnlyTheirQuestionsAndAnswers`, `ExportReadsNoStudyDataGuardTest`.

## Password Hashing

Passwords must be hashed with bcrypt at cost factor **≥ 12**. Plaintext passwords must never be stored, logged, or transmitted.

```java
// CORRECT (Spring Security BCryptPasswordEncoder)
PasswordEncoder encoder = new BCryptPasswordEncoder(12);
String hashed = encoder.encode(rawPassword);

// NEVER
userRepository.save(user.withPassword(rawPassword));  // ❌ plaintext
log.info("User {} registered with password {}", email, password);  // ❌ log leak
```

Enforced by: `BCryptPasswordHasher`, `AccountService`; tested by `AccountServiceTest.shouldRegisterWithALowercasedEmailAndAHashedPassword`; the cost of 12: no test.

## JWT Token Storage

JWT tokens must be stored in **HttpOnly, Secure, SameSite=Lax cookies**. Never return JWT in response body for the SPA to store in `localStorage` or `sessionStorage`.

The `Secure` flag is configurable via `AUTH_SECURE_COOKIES=false` for local HTTP development only.

```java
// CORRECT
ResponseCookie cookie = ResponseCookie.from("token", jwtToken)
    .httpOnly(true)
    .secure(secureFlag)
    .sameSite("Lax")
    .path("/")
    .build();
response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

// NEVER
return Map.of("token", jwtToken);  // ❌ SPA stores in localStorage
```

Enforced by: `JwtService`, `AuthController`, `SecurityConfig`; tested by `ApiFlowTest.registerSetsAnHttpOnlyCookieThatSignsInUntilLogout`, `SecurityTest.aSessionCookieWithAnAlteredSignatureIsRefused`, `SecurityTest.aWronglySignedExpiredUnsignedOrMalformedSessionCookieIsRefused`.

## OAuth CSRF Protection

OAuth `state` parameter must be validated on **every** OAuth callback to prevent CSRF attacks. Spring Security's OAuth2 client handles this automatically — never disable state validation.

```java
// CORRECT: handled by Spring Security OAuth2 client
// Ensure http.oauth2Login() is configured without disabling state validation

// NEVER: skip state validation or use a static state value
```

Enforced by: `SecurityConfig`; state validation: no test.

## Untrusted Input Handling

All uploaded filenames, external URLs, and image URLs are **untrusted**. Always use the security helpers — never write ad-hoc filesystem or HTTP handling.

Outbound HTTP to a URL taken from content or a user goes through `EgressPolicy`; operator-configured endpoints (`OPENROUTER_BASE_URL`, `LANGFUSE_HOST`, OAuth2 provider URIs) are trusted configuration and are not checked (ADR 0023). HTTP-client code in `src/main` lives only in the article fetch adapter (`infrastructure.web`), except operator-configured endpoints allowed by exact path in the guard test (the OAuth2 token client in `SecurityConfig`, the Discord downloader `DiscordAttachments` and the Slack downloader `SlackFiles`). The adapter checks every redirect hop and connects only to the addresses the policy checked (ADR 0025); `OutboundHttpGuardTest` fails on any other HTTP-client code.

A Discord `/upload` attachment is downloaded by JDA's own client only in `DiscordAttachments`, only from `cdn.discordapp.com` or `media.discordapp.net` over `https` on the default port, and read to at most `mindforge.upload.max-size` (ADR 0029).

A file sent to the Slack bot is downloaded only in `SlackFiles`, only from the fixed `files.slack.com` hosts over `https` on the default port, with no redirects. The bot token goes only to these hosts, and the read stops at `mindforge.upload.max-size` (ADR 0035, ADR 0029).

```java
// CORRECT
uploadPolicy.admit(userProvidedFilename, mimeType, sizeBytes);  // UploadSanitizer
URI target = egressPolicy.check(externalUrl);  // throws EgressDeniedException

// NEVER
new FileInputStream(userProvidedFilename);  // ❌ path traversal risk
restTemplate.getForObject(userProvidedUrl, ...);  // ❌ SSRF risk
```

Enforced by: `UploadSanitizer`, `EgressPolicy`, `HttpArticleSource`, `DiscordAttachments`, `SlackFiles`; tested by `UploadSanitizerTest`, `EgressPolicyTest`, `HttpArticleSourceTest`, `OutboundHttpGuardTest`, `DiscordAttachmentsTest`, `SlackFilesTest`.

## API Authorization

Every `@RestController` method must verify that the authenticated user owns the requested resource. Never trust resource IDs from the path or body without ownership check.

```java
// CORRECT
@GetMapping("/documents/{id}")
public DocumentResponse get(@PathVariable UUID id, @AuthenticationPrincipal UserPrincipal user) {
    Document doc = documentService.findById(id);
    if (!doc.getOwnerId().equals(user.userId())) throw new AccessDeniedException("Not owner");
    return DocumentResponse.from(doc);
}

// NEVER: return resource without ownership check
```

Every read of a tenant-scoped store binds the `kbId` of the knowledge base that the caller owns. A run id or a page path from another knowledge base is not found (`404`); it does not show that the other knowledge base has it. Document dedup is per knowledge base.

Enforced by: `KnowledgeBaseService`, `WikiStoreAdapter`, `RevertService`; tested by `KnowledgeBaseServiceTest.shouldRefuseAKnowledgeBaseToAnyoneButItsOwner`, `ApiFlowTest.aUserCannotReachAnotherUsersKnowledgeBase`, `SecurityTest.anotherUserIsForbiddenTheOwnersRunReportRevertPageRevisionsAndExport`, `SecurityTest.aForeignRunIdOrPagePathUnderOnesOwnKnowledgeBaseIsNotFoundAndShowsNothingOfTheOwner`, `RevertServiceTest.shouldReportARunTheKnowledgeBaseDoesNotHaveAsNotFound`, `WikiStoreAdapterTest.everyReadWithAnotherKnowledgeBasesIdReturnsNothing`, `DocumentRepositoryAdapterTest.sameHashInTwoKnowledgeBases_isNotADuplicateAcrossThem`.

## Auth Rate Limit

Sign-in and sign-up (`POST /api/v1/auth/login` and `POST /api/v1/auth/register`) share one allowance per client address: `mindforge.rate-limits.auth.requests` per `mindforge.rate-limits.auth.window` (ADR 0038). A failed attempt counts. An exhausted allowance is `429` with `Retry-After` and code `RATE_LIMITED`. The limiter never reads the request body, and it logs only the address. The counters are in memory, so the limit is correct only with one live instance.

The limit is per address, not per account: it does not slow a guesser who uses many addresses against one account.

Enforced by: `AuthRateLimitInterceptor`, `RateLimitConfig`, `AppProperties`; tested by `AuthRateLimitInterceptorTest`, `AuthRateLimitTest`, `AuthRateLimitPropertiesTest`.

## Client Address

The client address is `request.getRemoteAddr()`. Tomcat's remote IP valve sets it (`server.forward-headers-strategy: native`, ADR 0039). The valve uses `X-Forwarded-For` and `X-Forwarded-Proto` only when the direct peer is in `server.tomcat.remoteip.internal-proxies` (Spring's default: the private, loopback, link-local and CGNAT ranges), and it reads `X-Forwarded-For` from right to left. Application code never reads `X-Forwarded-For`.

Enforced by: the `server.forward-headers-strategy: native` setting, `AuthRateLimitInterceptor`; tested by `ClientAddressTest.aTrustedPeerIsLimitedAsTheRightmostForwardedAddress`, `ClientAddressTest.forwardedHttpsFromATrustedPeerMakesTheOAuthRedirectUriHttps`, `UntrustedProxyClientAddressTest.aForgedForwardedAddressFromAnUntrustedPeerDoesNotGiveANewBucket`.

## Account Enumeration

Password sign-in always runs one hash comparison (ADR 0040). An unknown email and an account with no password are compared against a hash made at startup; a wrong password is compared against the stored hash. All three cases take the same time and get the same `401` message. Sign-up answers `409` for an email that has an account: this is an accepted risk, bounded by the auth rate limit (ADR 0038).

Enforced by: `AccountService`; tested by `AccountServiceTest.shouldCompareAnUnknownEmailOnceAgainstTheHashMadeAtConstruction`, `AccountServiceTest.shouldCompareAnAccountWithoutAPasswordOnceAgainstTheHashMadeAtConstruction`, `AccountServiceTest.shouldCompareAWrongPasswordOnceAgainstTheStoredHash`, `AccountServiceTest.shouldRefuseAWrongPasswordAndAnAccountWithoutOneAlike`.

## CORS Configuration

Cross-origin requests are configured via a `CorsConfigurationSource` bean. The allowed origin comes from the
environment (`CORS_ALLOWED_ORIGIN`), never from code, and there is no default origin (ADR 0041).

```java
// CORRECT
@Bean
public CorsConfigurationSource corsConfigurationSource(
        @Value("${mindforge.security.cors-allowed-origin:}") String allowedOrigin) {
    String origin = allowedOrigin.trim();
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    if (origin.isEmpty()) {
        return source;  // blank: no cross-origin caller is admitted
    }
    if (origin.contains("*") || origin.contains(",")) {
        throw new IllegalStateException("one origin without '*' or ','");
    }
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(origin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    source.registerCorsConfiguration("/**", config);
    return source;
}

// NEVER
config.setAllowedOrigins(List.of("*"));  // ❌ wildcard
@Value("${CORS_ALLOWED_ORIGIN:http://localhost:4200}")  // ❌ a default origin
```

The Angular SPA and the Spring Boot API are served from the same origin (single JAR with embedded Angular build), so
they need no CORS. A blank `CORS_ALLOWED_ORIGIN` admits no origin. Set it only when the SPA is served from another
origin. It holds one exact origin, never a list and never `*`; the server does not start with `*` or a list.

Enforced by: `SecurityConfig`; tested by `CorsConfigurationTest`, `CorsTest`.
