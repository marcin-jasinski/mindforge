---
id: R09
title: No rate limiting and no rate-limit headers
type: feature
status: open
severity: medium
assignee:
blocked_by: []
---

## Problem

`docs/standards/backend/api.md` documents rate limit headers as a standard. The codebase has
neither the headers nor any rate limiting: `grep -rn "RateLimit" src/main/java` returns **zero
hits**.

## Why it matters

More than the missing header. Several endpoints spend money on every call:

- `POST /api/knowledge-bases/{kbId}/query-sessions/...` — two LLM calls per turn
  (`QueryService`), each carrying the **entire** rendered wiki index (see R17).
- `POST .../quiz-sessions` — one generation call per session (`QuizService`).
- `POST .../documents` — starts an ingest run: a relevance call, N extract calls, one LLM call
  **per page** in the fan-out, a link check and a supersession call.
- `POST .../lint` — two sequential LLM calls per chunk over the whole wiki (`LintService`).

An authenticated user can loop any of these. There is no per-user cap, no concurrency cap beyond
the per-knowledge-base ingest lease, and — until Phase 14 — no cost measurement either, since
`CompletionResult.costUsd` is populated as `0` and `ingest_runs.cost` stays NULL by design
(ticket T28 item 2). So the first signal of abuse is the provider bill.

The ingest lease serialises page-writing runs *per knowledge base*, which bounds concurrency but
not total spend, and does not touch Query, Quiz or Lint at all.

## Fix

Two parts; the first matters more.

### 1. A real limit on the LLM-spending endpoints

Keep it boring and in-process — Caffeine is already a dependency and there is exactly one live
instance (`docs/project/deployment.md` states the one-live-instance ceiling, so a distributed
limiter would be speculative). A per-user token bucket in a `OncePerRequestFilter` or a small
`HandlerInterceptor`, applied to the four endpoint groups above, is enough.

Limits should be configurable in `application.yml` under `mindforge.rate-limits`, so they can be
tuned without a deploy.

### 2. The headers

On limited endpoints return `RateLimit-Limit`, `RateLimit-Remaining` and `RateLimit-Reset`, and
`429 Too Many Requests` with a `Retry-After` when exhausted. Map it in
`GlobalExceptionHandler` alongside the existing codes so the response shape stays
`{error, code, detail}`.

The SPA should surface a 429 as a "slow down" message rather than the generic error toast.

### Scope note

If a limiter is judged premature, that is a defensible call for a single-user deployment — but
then amend `api.md` as R08 does for versioning, rather than leaving a documented standard
unimplemented. Do not leave the mismatch.

## Acceptance criteria

- [ ] The four LLM-spending endpoint groups are rate limited per user.
- [ ] Limits are configurable without a rebuild.
- [ ] `RateLimit-*` headers are present, and exhaustion returns 429 with `Retry-After`.
- [ ] An integration test proves the limit fires and resets.
- [ ] The SPA handles 429 distinctly.

## Resolution

<!-- filled on close -->
