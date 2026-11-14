---
id: D03
question: Q03
status: decided
method: unanimous
class: adr
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q03.r1.tally.md]
adr: docs/adr/0023-egress-policy-guards-untrusted-urls-only.md
---
## Decision
Use A: `EgressPolicy` guards every outbound HTTP request whose URL comes from content or a user (article URLs in
Phase 17). Operator-configured endpoints (model provider base URL `OPENROUTER_BASE_URL` / `spring.ai.openai.base-url`,
Langfuse OTLP `LANGFUSE_HOST`, OAuth2 provider URIs) are trusted configuration and are not checked. Record the scope
in the docs and pin it with a plain JUnit guard test that allows HTTP-client code only in the article fetch adapter.

## Why
- The governing standard scopes the rule to untrusted URLs: `web-security.md:73` ("external URLs, and image URLs are
  **untrusted**") and its SSRF anti-example `restTemplate.getForObject(userProvidedUrl, ...)` (`:82`).
- `architecture.md:276-277` frames `EgressPolicy` under "Untrusted input" too. The checklist line restates that rule.
- SSRF needs an attacker-controlled URL; no API endpoint sets an endpoint URL (grep of `api/` is empty).
- B fails the local-dev constraint: the private-range rule (`implementation-plan.md:1524`) rejects the dev model
  endpoint `localhost:1234` (`application-dev.yml:28`), and the needed exemption list re-creates A with more code.
- Class `adr`: this sets a security trust boundary that Phases 18-19 (bots) will rely on (scope.md:26 asks for it).

## Rejected
- B: breaks local development or needs an exemption list equal to A, plus wrappers around Spring AI, OTLP and OAuth2 clients; no SSRF gain.
- C: adds a speculative plan item; Phase 20.1 already re-verifies `web-security.md`, which carries this scope.

## Dissent
None. Risk to watch: a later feature (per-KB model settings, user webhooks, image URLs, bot-supplied links) makes an
endpoint URL user-controlled; that URL is then untrusted and must go through `EgressPolicy`. The guard test is the
tripwire for new HTTP code; it does not catch a config value that becomes user-settable.

## Implementation notes
- `docs/project/architecture.md` § "Security & Trust Model", "Untrusted input" bullet: add "Outbound HTTP to a URL
  taken from content or a user goes through `EgressPolicy`. Operator-configured endpoints (`OPENROUTER_BASE_URL`,
  `LANGFUSE_HOST`, OAuth2 provider URIs) are trusted configuration and are not checked (ADR 0023)."
- `docs/standards/security/web-security.md` § "Untrusted Input Handling": one matching sentence.
- `docs/project/implementation-plan.md:1541` (and `docs/factory/phase-17/scope.md:126`): reword to "All outbound HTTP
  to content- or user-derived URLs goes through `EgressPolicy` (operator-configured endpoints are trusted config; ADR 0023)."
- Guard test, no new dependency (no ArchUnit): `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java`,
  plain JUnit + AssertJ, walks `src/main/java`, fails naming the file if `java.net.http.HttpClient`, `HttpURLConnection`,
  `URL#openConnection`/`openStream`, `RestClient`, `RestTemplate`, `WebClient` or `Jsoup.connect` appears outside the
  single article HTTP adapter in `dev.mindforge.infrastructure`; asserts that adapter calls `EgressPolicy.check(`.
- Inside the fetcher, "goes through EgressPolicy" must hold for every hop: `HttpClient.Redirect.NEVER` and re-check
  each redirect target; check the resolved `InetAddress` (reject loopback, site-local, link-local, any-local, multicast,
  IPv6 ULA, IPv4-mapped IPv6) and connect to that address; allow only `http`/`https`. Cover each case in `EgressPolicyTest`
  / `ArticleFetcherTest` (redirect to a private address, hostname resolving to 127.0.0.1, `[::1]`, `[::ffff:10.0.0.1]`, `file://`, `data:`).
- Do not wrap Spring AI, OTLP or OAuth2 clients; the dev profile `localhost:1234` model endpoint keeps working.
