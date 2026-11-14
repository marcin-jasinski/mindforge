# EgressPolicy guards URLs from content or users; operator-configured endpoints are trusted

Status: Accepted (2026-11-14).

`EgressPolicy` checks every outbound HTTP request whose URL comes from content or a user. In Phase 17 that is the
article URLs taken from a document's blocks. The check holds for every hop: redirects are followed by hand and each
target is checked again, and the resolved address is checked, not only the host name. Endpoints that the operator
sets in configuration (the model provider base URL, the Langfuse OTLP host, the OAuth2 provider URIs) are trusted and
are not checked. This is the scope of `web-security.md` § "Untrusted Input Handling", and of the Phase 17 line "All
outbound HTTP goes through `EgressPolicy`". A plain JUnit guard test allows HTTP-client code in `src/main/java` only
in the article fetch adapter.

## Considered Options

- **Every outbound call through the policy, with an operator exemption list**: the private-range rule rejects the dev
  profile's model endpoint (`localhost:1234`), so it needs an exemption list that re-allows exactly the configured
  endpoints. That is this decision again, plus wrappers around the HTTP clients that Spring AI, the OTLP exporter and
  Spring Security build. No SSRF gain: no user sets these URLs.
- **This scope now, and the wider check recorded as Phase 20 work**: a plan item with no threat behind it; Phase 20.1
  already re-verifies `web-security.md`.
- **An ArchUnit rule as the guard**: a new test dependency for one rule; a source scan does the same job.

## Consequences

- Local development with LM Studio on `localhost` keeps working; Spring AI, OTLP and OAuth2 clients are not wrapped.
- A new class that opens HTTP fails the guard test until it is the article adapter or goes through `EgressPolicy`.
- If a feature lets a user or a knowledge base set an endpoint URL (per-KB model settings, webhooks, bot links), that
  URL is untrusted and must go through `EgressPolicy`. That change needs a new decision.

Decided in [D03](../factory/phase-17/decisions/D03-scope-of-all-outbound-http.md) by the dark-factory run `phase-17`.
