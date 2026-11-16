---
id: Q05
status: decided
decision: D05
impact: medium
blocks: [W04, W08]
---
## Question
`web-security.md` § CORS and ADR 0008 require a `CorsConfigurationSource` bean with the origin from
`CORS_ALLOWED_ORIGIN`, but none exists and the app ships as one same-origin container: does Phase 20 add the bean, or
record that the single-origin deployment needs none?

## Context
- Code: no CORS configuration anywhere in `src/main`; `SecurityConfig` does not call `.cors(...)`. `env.example` has no
  `CORS_ALLOWED_ORIGIN`.
- `deployment.md` line 3: "MindForge ships as one container: the Spring Boot JAR with the Angular SPA embedded".
  Local SPA development uses the Angular dev-server proxy (`frontend/proxy.conf.json`), so it is same-origin too.
- `web-security.md` § CORS itself says CORS "is a development-only concern" in production, but still prescribes the
  bean with a `http://localhost:4200` default.
- ADR 0008 (accepted) chose a split deployment (Vercel SPA + Railway API) and says "CORS must be explicitly configured
  ... allowed origins list is an environment variable". `architecture.md:389` and `tech-stack.md:6,190` still describe
  Vercel; no `vercel.json` exists and the SPA uses relative `/api` URLs.
- A split deployment would also break the `SameSite=Lax` session cookie on cross-site XHR, so the Vercel design no
  longer works as written.
- Adding a credentialed CORS rule widens what a browser lets another origin do; with the default
  `http://localhost:4200` it admits any page served on that local port.

## Known options
- A: Add the `CorsConfigurationSource` bean exactly as `web-security.md` shows (env `CORS_ALLOWED_ORIGIN`, default
  `http://localhost:4200`, never `*`), wire `.cors()` in `SecurityConfig`; document the variable. Satisfies ADR 0008.
- B: As A, but with no default: no bean / no origin admitted unless `CORS_ALLOWED_ORIGIN` is set (fail closed).
- C: No CORS bean; amend `web-security.md`, `architecture.md`, `tech-stack.md` to the single-container deployment and
  supersede ADR 0008 with a new ADR. This contradicts an accepted ADR, so it needs the human (blocker path).

## Constraints
- `web-security.md` § CORS: origin from env var, never hardcoded, never `*`.
- ADR 0008 is accepted; a decision against it is a human blocker (`.claude/factory.md` house rules).
- `minimal-implementation.md`: build only what is called.
