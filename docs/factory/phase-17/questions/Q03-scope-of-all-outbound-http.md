---
id: Q03
status: decided
decision: D03
impact: high
blocks: [W01, W05, W07]
---
## Question
Does the checklist line "All outbound HTTP goes through `EgressPolicy`" cover only URLs taken from content (articles),
or also the operator-configured endpoints (model provider, Langfuse OTLP, OAuth2 providers)?

## Context
- Phase 17 checklist: "All outbound HTTP goes through `EgressPolicy`." Plan 17.1: the policy "rejects private IP
  ranges".
- Code: Spring AI calls `OPENROUTER_BASE_URL`; the dev profile sets `http://localhost:1234/v1` (LM Studio)
  (`src/main/resources/application-dev.yml:29`). OTLP export goes to `LANGFUSE_HOST`; OAuth2 login calls GitHub/Google.
  None of these is checked today, and they are built by Spring, not by MindForge code.
- Applying the private-range rule to the model endpoint breaks local development; wrapping Spring AI's and Spring
  Security's HTTP clients adds a large surface for no SSRF gain (the operator, not a user, sets these URLs).
- `web-security.md` § "Untrusted Input Handling" scopes the rule to untrusted URLs: "external URLs, and image URLs".

## Known options
- A: Scope = every URL that comes from content or a user (article URLs today). Operator-configured endpoints are
  trusted configuration and are out of scope; document this in `docs/project/architecture.md` and the checklist
  evidence. A test (e.g. an architecture test) proves no other class in `src/main` opens an HTTP connection to a
  content-derived URL.
- B: Every outbound call, including Spring AI, OTLP and OAuth, passes the policy, with an operator exemption list for
  configured endpoints (localhost allowed when configured).
- C: A now, and record B as later work for Phase 20 (security hardening).

## Constraints
- `web-security.md` § "Untrusted Input Handling": external URLs never fetched ad hoc.
- All LLM calls through `AIGateway` (unchanged either way).
- Local development with LM Studio on `localhost` must keep working (`application-dev.yml`; `docs/project/deployment.md`).
