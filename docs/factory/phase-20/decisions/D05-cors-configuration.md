---
id: D05
question: Q05
status: decided
method: unanimous
class: adr
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q05.r1.tally.md]
adr: docs/adr/0041-cors-admits-one-origin-from-the-environment-and-none-by-default.md
---
## Decision
Use B: add a `CorsConfigurationSource` bean whose one allowed origin comes from `CORS_ALLOWED_ORIGIN`, with no
default. When the variable is blank, the source holds no configuration, so no origin is admitted and behaviour is the
same as today. When it is set, admit exactly that origin, with credentials. Never accept `*`. Wire `.cors(...)` in
`SecurityConfig`, document the variable, and change the default in `web-security.md` § CORS from
`http://localhost:4200` to "none".

## Why
- ADR 0008 (accepted) requires CORS configured with origins from an environment variable (docs/adr/0008, Consequences);
  B satisfies it. C contradicts it and is a human blocker (.claude/factory.md:70).
- The app ships as one container (deployment.md:3) and dev uses the Angular proxy (frontend/proxy.conf.json), so no
  caller needs a default origin.
- A default of `http://localhost:4200` is a credentialed rule in production: the session cookie is `SameSite=Lax`
  (JwtService.java:64), CSRF is off for `/api/**` (SecurityConfig.java:40), and localhost ports are the same site, so
  any page on that port could act as the user. B removes that exposure.
- B is one bean, one property and one `.cors` call; it is easy to remove if ADR 0008 is superseded later.

## Rejected
- A: the `localhost:4200` default admits a credentialed cross-origin caller that no workflow needs.
- C: supersedes accepted ADR 0008; only the human can make that call.

## Dissent
None. Risk to watch: ADR 0008 and `architecture.md`/`tech-stack.md` still describe a Vercel split that the
`SameSite=Lax` cookie cannot serve. Superseding ADR 0008 is open for the human (see ADR 0041 Consequences).

## Implementation notes
- `application.yml`, under `mindforge.security`: `cors-allowed-origin: ${CORS_ALLOWED_ORIGIN:}` with a one-line
  comment (blank = no cross-origin caller; ADR 0041).
- `SecurityConfig` (src/main/java/dev/mindforge/api/config/SecurityConfig.java): add
  `@Bean CorsConfigurationSource corsConfigurationSource(@Value("${mindforge.security.cors-allowed-origin:}") String allowedOrigin)`.
  Trim the value. Blank -> return an empty `UrlBasedCorsConfigurationSource` (register NO configuration; do not
  register a config with an empty origin list, because that makes the CORS processor reject same-origin requests
  whose `Origin` it misreads behind a proxy with 403). Value containing `*` or a comma -> throw
  `IllegalStateException` at startup (fail fast; one origin only). Otherwise a `CorsConfiguration` with
  `setAllowedOrigins(List.of(origin))`, methods GET, POST, PUT, PATCH, DELETE, OPTIONS, allowed headers `*`,
  `setAllowCredentials(true)`, registered for `/**`. Add `.cors(Customizer.withDefaults())` to the filter chain.
- `env.example`: in the Security block, `CORS_ALLOWED_ORIGIN=` (blank) with a comment: only for a separately served
  SPA; one exact origin; never `*`.
- `docs/standards/security/web-security.md` § CORS: update the example to the no-default form and the text to "blank
  admits no origin; set it only when the SPA is served from another origin" (ADR 0041). `deployment.md`: list the
  variable as optional.
- Tests: unit test of the bean (plain JUnit, no Spring context): blank -> `getCorsConfiguration(request)` is null;
  set -> allowed origins equal the value and credentials are true; `*` -> `IllegalStateException`. Integration test
  in `src/test/java/dev/mindforge/integration/api/` (MockMvc or RANDOM_PORT): preflight `OPTIONS /api/v1/auth/login`
  with `Origin: http://evil.example` gets no `Access-Control-Allow-Origin` when unset; with the property set to an
  origin, a preflight from that origin gets that exact `Access-Control-Allow-Origin` and
  `Access-Control-Allow-Credentials: true`, and a preflight from another origin does not.
