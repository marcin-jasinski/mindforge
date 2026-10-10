---
id: R16
title: Error messages echo internals, and actuator sits at the root behind permitAll
type: security
status: closed
severity: medium
assignee:
blocked_by: []
---

## Problem

Security came out of this review clean. Verified correct, and **not** to be re-litigated: all 12
controllers check ownership as their first statement (SSE and export included), cross-tenant 403s
are proved across 11 paths by `ApiFlowTest.java:316`, BCrypt is cost 12, JWT is
HttpOnly/Secure/SameSite=Lax and never in a body, no forbidden field is reachable from `api/`,
zip-slip is closed by `BundleConformanceValidator` running before a byte is written, there are no
concatenated queries, and `env.example` holds only placeholders.

Two items remain, both about exposure rather than access control.

### 1. Framework error messages are returned verbatim

`src/main/java/dev/mindforge/api/config/GlobalExceptionHandler.java:123-126`:

```java
if (e instanceof org.springframework.web.ErrorResponse framework) {
    HttpStatusCode status = framework.getStatusCode();
    return ResponseEntity.status(status).body(ErrorResponse.of(String.valueOf(status.value()), "REQUEST_ERROR",
        e.getMessage()));
}
```

`e.getMessage()` goes straight into the response. For `HttpMessageNotReadableException` that
message includes Jackson's parse location and a snippet of the offending payload, often naming
internal DTO types. So a malformed request body is echoed back with internal type names attached.

The deliberate design elsewhere is the opposite — `unexpected(...)` returns a flat "Something went
wrong" precisely to avoid this.

**Fix:** map the common framework exceptions to fixed, safe messages. `HttpMessageNotReadableException`
-> `"Malformed request body"`. `HttpMediaTypeNotSupportedException` -> `"Unsupported media type"`.
`MissingServletRequestParameterException` can safely name the missing parameter. Log the full
message server-side at `WARN`; return the safe one. Keep `MethodArgumentNotValidException`'s
field-level detail as it is — that is deliberate and useful (`:108-113`), and names only fields
the client sent.

### 2. Actuator is at the root behind `permitAll`

`src/main/resources/application.yml:66` sets:

```yaml
management.endpoints.web.base-path: /
```

and `src/main/java/dev/mindforge/infrastructure/security/SecurityConfig.java:37` ends with
`.anyRequest().permitAll()`.

Today only `health` is in `exposure.include`, so nothing sensitive is served, and both
`compose.yml` and `railway.json` depend on that health path — this is **not** a live
vulnerability.

The problem is the failure mode. Because the base path is `/` and the catch-all is `permitAll`,
adding any endpoint to `exposure.include` — `env`, `configprops`, `beans`, `heapdump` — publishes
it unauthenticated, at the root, with no other change and no visible signal in the security
config. `env` and `configprops` would expose `JWT_SECRET` and the OpenRouter API key.

**Fix:** pick one, in order of preference:

1. Move actuator back to `/actuator` and update the two healthcheck references. Cleanest; the
   root base-path buys nothing.
2. Keep `/`, but replace `.anyRequest().permitAll()` with an explicit
   `.requestMatchers("/health").permitAll().anyRequest().authenticated()` so anything added later
   is denied by default.

Either way, add a comment at `application.yml:66` stating that expanding `exposure.include`
requires a matching security rule.

## Why it matters

Item 1 is low-impact information disclosure. Item 2 is a latent configuration trap — the kind
where a future one-line change to add a metrics endpoint quietly publishes secrets.

## Acceptance criteria

- [ ] Framework error responses carry fixed, safe messages; full detail is logged, not returned.
- [ ] A test posts malformed JSON and asserts the response body contains no type name and no
      payload echo.
- [ ] Actuator is either relocated or protected by an explicit authenticated-by-default rule.
- [ ] Container and platform healthchecks still pass (`compose.yml`, `railway.json`).

## Resolution

1. `GlobalExceptionHandler`: framework errors (`ErrorResponse` exceptions) now return fixed text
   (`Unsupported media type`, `Missing request parameter: <name>`, else the status reason phrase) and log the
   full message at WARN. `HttpMessageNotReadableException` is not a Spring `ErrorResponse`, so the catch-all
   was answering malformed JSON with a 500; it now has its own handler returning 400 `Malformed request body`.
2. Option 1 taken: actuator base path is `/actuator`; `compose.yml`, `railway.json` and `deployment.md` updated.
   `SecurityConfig` also permits only `/actuator/health/**` and requires authentication for the rest of
   `/actuator/**`, so a future `exposure.include` addition is denied by default (the root `permitAll` stays for the
   SPA). Comment at the base-path line states the rule.
3. Tests in `ApiFlowTest`: malformed JSON gets a fixed message with no payload echo or type name;
   `/actuator/health` is 200 and `/actuator/env` is 401/403. `mvn verify` passes.
