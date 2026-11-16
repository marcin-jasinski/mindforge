# CORS admits one origin from the environment, and none by default

Status: Accepted (2026-11-16).

`SecurityConfig` registers a `CorsConfigurationSource` and calls `.cors(...)`. The allowed origin comes from
`CORS_ALLOWED_ORIGIN` (`mindforge.security.cors-allowed-origin`) and has no default. Blank means the source holds no
configuration: no cross-origin caller is admitted, which is the right state for the shipped deployment, one container
with the SPA embedded, and for local development through the Angular dev-server proxy. Set, it admits exactly that one
origin, with credentials. A value with `*` or a comma fails startup. This keeps ADR 0008's rule that CORS is explicit
and its origin is an environment variable, and replaces the `http://localhost:4200` default in `web-security.md`.

## Considered Options

- **The `web-security.md` bean as written, default `http://localhost:4200`**: a credentialed rule in every deployment
  that does not set the variable. The session cookie is `SameSite=Lax` and CSRF is off for `/api/**`, and every port on
  localhost is the same site, so any page on that port could call the API as the signed-in user. No workflow needs it.
- **No CORS bean; supersede ADR 0008 for the single-container deployment**: the likely long-term state, but it
  overturns an accepted ADR, which the factory run may not do. Left to the human.

## Consequences

- No behaviour changes for the shipped deployment: with the variable blank, responses carry no CORS headers.
- A separately served SPA must set the variable to its exact origin. Such a split still does not work as ADR 0008
  describes, because a cross-site XHR does not send the `SameSite=Lax` cookie; that is a known gap, not fixed here.
- ADR 0008, `architecture.md` and `tech-stack.md` still describe a Vercel split. Superseding ADR 0008 with the
  single-container deployment is open for the human; this ADR does not do it.
- Removing the bean later is one method and one property.

Decided in [D05](../factory/phase-20/decisions/D05-cors-configuration.md) by the dark-factory run `phase-20`.
