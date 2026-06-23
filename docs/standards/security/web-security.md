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
return Map.of("referenceAnswer", artifact.getSummaryData().getReferenceAnswer());  // ❌

// CORRECT: return only evaluated results
return new EvaluationResponse(result.getScore(), result.getFeedback());
```

Redaction is enforced in `InteractionStore.listForUser()` — **not only in controllers** (defense-in-depth). The store never returns unredacted interaction data for user-facing queries.

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

## OAuth CSRF Protection

OAuth `state` parameter must be validated on **every** OAuth callback to prevent CSRF attacks. Spring Security's OAuth2 client handles this automatically — never disable state validation.

```java
// CORRECT: handled by Spring Security OAuth2 client
// Ensure http.oauth2Login() is configured without disabling state validation

// NEVER: skip state validation or use a static state value
```

## Untrusted Input Handling

All uploaded filenames, external URLs, and image URLs are **untrusted**. Always use the security helpers — never write ad-hoc filesystem or HTTP handling.

```java
// CORRECT
uploadSanitizer.sanitizeFilename(userProvidedFilename);
egressPolicy.check(externalUrl);

// NEVER
new FileInputStream(userProvidedFilename);  // ❌ path traversal risk
restTemplate.getForObject(userProvidedUrl, ...);  // ❌ SSRF risk
```

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

## CORS Configuration

Cross-origin requests are configured via a `CorsConfigurationSource` bean. The allowed
origin is injected from an environment variable, never hardcoded.

```java
// CORRECT
@Bean
CorsConfigurationSource corsConfigurationSource(
        @Value("${CORS_ALLOWED_ORIGIN:http://localhost:4200}") String allowedOrigin) {
    CorsConfiguration config = new CorsConfiguration();
    config.setAllowedOrigins(List.of(allowedOrigin));
    config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
    config.setAllowedHeaders(List.of("*"));
    config.setAllowCredentials(true);
    UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
    source.registerCorsConfiguration("/**", config);
    return source;
}

// NEVER
config.setAllowedOrigins(List.of("*"));  // ❌ wildcard in production
```

In production, the Angular SPA and the Spring Boot API are served from the same origin
(single JAR with embedded Angular build), so CORS is a development-only concern. The
`CORS_ALLOWED_ORIGIN` env var defaults to `http://localhost:4200` for local development
and must not be set to `*` in production.
