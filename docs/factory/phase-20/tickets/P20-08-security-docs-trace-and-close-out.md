---
id: P20-08
title: Trace every web-security requirement to code and tests, document the new settings and ADRs, and close Phase 20
status: in_progress
plan_tasks: [20.1, 20.2, 20.3, 20.4]
decisions: [D11]
depends_on: [P20-01, P20-02, P20-03, P20-04, P20-05, P20-06, P20-07]
size: M
issue: 62
commit:
fix_rounds: 0
---
## Goal
`docs/standards/security/web-security.md` ends each section with one "Enforced by:" line naming the implementing
classes and the guarding tests, and gains the Slack downloader, auth rate limit, client address, sign-in timing and
no-default CORS text. `api.md`, `deployment.md` and `env.example` document the new settings and the OWASP check. ADRs
0038–0041 are linked from `docs/INDEX.md`. The plan's Phase 20 is `[x]` with "As built" notes, the roadmap and the
CLAUDE.md "Project State" paragraph move on, and the follow-ups are GitHub issues.

## Context
- Model: the Phase 19 close-out `docs/factory/phase-19/tickets/P19-08-slack-docs-and-close-out.md` (commit `f776810`):
  copy the shape of its "As built" notes, `deployment.md` sections and `env.example` blocks.
- As built: read the Notes of P20-01..P20-07 and the code; check every name against `src/` before you write it.
- Files and current state:
  - `docs/standards/security/web-security.md` sections: Server-Authoritative State, Password Hashing, JWT Token Storage,
    OAuth CSRF Protection, Untrusted Input Handling (names the Discord downloader, not the Slack one), API
    Authorization, CORS Configuration (example default `http://localhost:4200`).
  - `docs/standards/backend/api.md:24-25` § Rate Limit Headers (model-call limit only).
  - `docs/project/deployment.md` (env tables; Railway/Render sections; § One live instance) and `env.example`
    (Security block).
  - `docs/project/implementation-plan.md:1706-1731`: `## [ ] Phase 20 — Security Hardening`, tasks 20.1–20.4 (20.3 text
    says `/api/auth/...`), checklist of three items.
  - `docs/project/roadmap.md:13` ("Next" line) and `:129` (Phase 20 entry).
  - `docs/INDEX.md:46` ADR paragraph ends with 0033–0037 (Slack bot).
  - `CLAUDE.md`: edit **only** the "Project State" paragraph.
- **Do not write new ADRs.** ADRs 0038 (auth limit keyed by client address), 0039 (client address from Tomcat's
  RemoteIpValve with private-range proxies), 0040 (sign-in always runs one hash comparison), 0041 (CORS admits one
  origin from the environment, none by default) already exist in `docs/adr/`. Link them only.
- **Do not supersede or edit ADR 0008**, and do not change the Vercel text in `architecture.md:389` /
  `tech-stack.md:6`: superseding ADR 0008 stays open for the human. Report it as an open human item.
- Follow `docs/agents/issue-tracker.md` for GitHub issues (repo `marcin-jasinski/mindforge`). Write docs in ASD-STE100
  Simplified Technical English where the doc already uses it. Timeless text in standards: no dates, no phase numbers,
  no change log (`commenting.md`).

## Decisions to apply
**D11 — Decision:** Use A: record the trace inside `docs/standards/security/web-security.md`. Under each section, add
one short "Enforced by:" line that names the implementing class(es) and the test(s) that fail when the requirement
breaks. Use class and test names (with method name where one test method is the guard), not file paths. Where no test
enforces a requirement, name the class and write "no test" so the gap stays visible. Do not add a new doc and do not
change `docs/INDEX.md` for the trace.

**D11 — Implementation notes:**
- Sections: Server-Authoritative State, Password Hashing, JWT Token Storage, OAuth CSRF Protection, Untrusted Input
  Handling, API Authorization, CORS Configuration, plus any section Phase 20 adds (auth rate limit, client address,
  sign-in timing — ADRs 0038–0040).
- Format, one line at the end of each section: `Enforced by: \`ClassA\`, \`ClassB\`; tested by \`SomeTest\`, \`ApiFlowTest.assertNoSensitiveFields\`.`
- Verify every named class and test exists (grep `src/`) before writing it; no file paths, no dates, no phase numbers.
- Close checklist item 1 in the plan's "As built" note with one line that points to web-security.md.

**Docs parts of the other decisions (code landed in P20-01..P20-07):**
- D01/D03: `api.md` § Rate Limit Headers gains: sign-in and sign-up (`POST /api/v1/auth/login`, `/register`) share one
  allowance per client address, `mindforge.rate-limits.auth.*`, same `429`/`Retry-After`/`RATE_LIMITED`/`RateLimit-*`.
  `env.example` and `deployment.md`: `AUTH_RATE_LIMIT_REQUESTS` (default 10) and `AUTH_RATE_LIMIT_WINDOW` (default
  PT1M). Note in deployment that in-memory limits are correct only with one live instance.
- D01 dissent: open a follow-up issue "distributed per-account password guessing is not slowed by the per-address
  limit" (label `ready-for-human`).
- D02: `deployment.md` env table row for optional `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (comma-separated CIDR list
  or a Java regex; default Spring's private ranges); § Railway/Render note on the trust model (`X-Forwarded-*` is
  trusted only from a private-range peer); self-hosted caveat (a reverse proxy or docker userland proxy on a private
  address is trusted; set the override to its address). `env.example`: the variable, commented out.
- D04: `web-security.md` short "Account enumeration" note: sign-in takes the same time for unknown, OAuth-only and
  wrong-password cases; register's `409` is an accepted risk bounded by the auth rate limit (ADR 0038, ADR 0040).
- D05: `web-security.md` § CORS: example in the no-default form; text "blank admits no origin; set it only when the SPA
  is served from another origin; one exact origin; never `*`" (ADR 0041). `env.example` Security block:
  `CORS_ALLOWED_ORIGIN=` (blank) with a comment (only for a separately served SPA; one exact origin; never `*`).
  `deployment.md`: list the variable as optional.
- D06: `deployment.md` (or its security/ops section): how to run `mvn dependency-check:check`, first-run cost, where
  suppressions live (`dependency-check-suppressions.xml`, each with a reason); automated enforcement belongs to
  Phase 21. Open a follow-up issue for Phase 21: scheduled + `workflow_dispatch` dependency-check job using an
  `NVD_API_KEY` secret.
- D07: `env.example`: `NVD_API_KEY=` (empty) with a comment: optional, free from nvd.nist.gov, speeds up the
  dependency check; never commit a value. `deployment.md`: `NVD_API_KEY` optional (first run without it is long and
  may be throttled); OSS Index and the Node/RetireJS analyzers are off because the audit covers Maven dependencies
  only.
- Q13: `web-security.md` § Untrusted Input Handling gains a Slack paragraph next to the Discord one: files downloaded
  only from the fixed `files.slack.com` hosts, the bot token sent only to them, capped read (ADR 0035); the
  `OutboundHttpGuardTest` allowlist sentence names `SlackFiles`.
- Q08: the plan's 20.3 "As built" records the path correction `/api/auth/...` → `/api/v1/auth/...` (R08).

## Acceptance criteria
- [ ] AC1: Every `web-security.md` section ends with one "Enforced by:" line in the D11 format; every class and test
  named exists in `src/`; a requirement with no test says "no test".
- [ ] AC2: `web-security.md` has the Slack downloader paragraph, the auth rate limit and client address text, the
  "Account enumeration" note, and the no-default CORS example and text.
- [ ] AC3: `api.md` § Rate Limit Headers names the auth endpoints, their per-address key and their settings.
- [ ] AC4: `deployment.md` and `env.example` document `AUTH_RATE_LIMIT_REQUESTS`, `AUTH_RATE_LIMIT_WINDOW`,
  `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` (commented out in `env.example`), `CORS_ALLOWED_ORIGIN` (blank),
  `NVD_API_KEY` (blank), the proxy trust note and self-hosted caveat, and how to run `mvn dependency-check:check`.
- [ ] AC5: `docs/INDEX.md` ADR paragraph links 0038–0041 with one-phrase summaries ("0038–0041 record security
  hardening: …"); no other INDEX change for the trace.
- [ ] AC6: `implementation-plan.md` Phase 20 heading, tasks 20.1–20.4 and the three checklist items are `[x]`, each
  task with an "As built" note naming classes and tests (20.1 points to the web-security.md trace; 20.2 records the
  dependency-check result from P20-07's Notes; 20.3 records the `/api/v1/auth` path correction); follow-up issue
  numbers are in the notes.
- [ ] AC7: `roadmap.md` marks Phase 20 done and its "Next" line names Phase 21 (keep the Phase 14 manual Langfuse
  note); CLAUDE.md "Project State" says work resumes at Phase 21 (no other CLAUDE.md change).
- [ ] AC8: Two GitHub issues exist (Phase 21 scheduled dependency-check job; distributed per-account guessing), and the
  ADR 0008 supersession question is listed as an open human item in the ticket Notes (not decided, not edited).

## Test plan
- Docs only: no new tests. Verify every class and test name in the trace with `grep -rn` over `src/`.
- Full gate `mvn -B verify -DskipFrontend=true` stays green (no code change expected).

## Files likely touched
- `docs/standards/security/web-security.md`
- `docs/standards/backend/api.md`
- `docs/project/deployment.md`
- `env.example`
- `docs/project/implementation-plan.md`
- `docs/project/roadmap.md`
- `docs/INDEX.md`
- `CLAUDE.md` (only "Project State")

## Out of scope
- New ADRs; editing or superseding ADR 0008; the Vercel text in `architecture.md` / `tech-stack.md`.
- A separate trace document (D11 rejected it); CI or workflow changes (Phase 21).
- Any production code change.

## Notes
- Decided: the client address section's "Enforced by:" line names the setting `server.forward-headers-strategy: native` next to `AuthRateLimitInterceptor`, because Tomcat's `RemoteIpValve` is not a class in `src/` (AC1 needs every named class in `src/`).
- Decided: `web-security.md` gains three sections (Auth Rate Limit, Client Address, Account Enumeration) and one tenancy paragraph in API Authorization (kbId binding, foreign id `404`, dedup per knowledge base), because 20.1 names these requirements and D11 asks for a trace of each.
- Decided: `docs/INDEX.md` also moves the "where work resumes" line to Phase 21 and adds the new deployment topics to the Deployment entry, as the P19-08 close-out did; the trace itself adds nothing to INDEX.
- Decided: issue #63 (Phase 21 scheduled dependency-check job) has label `ready-for-human` next to `factory`, because it needs a human to set the `NVD_API_KEY` repository secret.
- Follow-up issues: #63 (Phase 21 scheduled + `workflow_dispatch` dependency-check job, `NVD_API_KEY` secret), #64 (distributed per-account password guessing, `ready-for-human`).
- Open human item (not decided, not edited): supersede ADR 0008 (Vercel). `architecture.md` and `tech-stack.md` keep their Vercel text until then.
- Trace check: every class and test method named in an "Enforced by:" line was matched against `src/` with grep (none missing). Fast check `mvn -q -o test -DskipFrontend=true -Djacoco.skip=true`: exit 0.

### Deferred findings
- correctness/C1 nit 0.7 docs/standards/security/web-security.md:434: The doc says "the fixed `files.slack.com` hosts" (plural), but `SlackFiles` admits exactly one host.
- correctness/C2 nit 0.6 docs/standards/security/web-security.md:430: The doc lists `DiscordAttachments` and `SlackFiles` among the "operator-configured endpoints" in the guard-test allowlist, but these download from fixed vendor hosts, not from operator configuration.
