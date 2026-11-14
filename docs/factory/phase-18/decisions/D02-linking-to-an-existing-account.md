---
id: D02
question: Q02
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q02.r1.tally.md]
adr:
---
## Decision
Use A: Phase 18 does not link a Discord user to an existing web account. Build `ExternalIdentityRepository` with
`findUserId`, `createUserAndLink` and the reminder job's listing method (per Q13/Q10) only. Do not add `link(...)` to
the port, the adapter or any command. Record the omission in plan 18.1 "As built" and open a follow-up GitHub issue for
a verified linking flow. A Discord user has a separate, auto-provisioned account (D01, ADR 0027).

## Why
- No plan task, command or test calls `link` (implementation-plan.md:1577-1589); minimal-implementation.md "Build What You
  Need" and "No Future Stubs" (lines 3, 12) forbid a method with no caller; Q13 already says "`link` only if Q02 gives it a caller".
- Every safe linking flow adds a new credential or trust boundary; its brute-force control (rate limiting) is Phase 20
  (scope.md:32), and the plan lists exactly four commands (scope.md:34).
- The data policy for an already-provisioned bot account (move, refuse, keep) has no safe default and needs its own design.
- A is additive to reverse: the `(platform, external_id)` key lets a later phase add `link` without migrating rows.
- Class `local`: removes an unused method; no layer, schema, API contract, dependency or security surface changes.
  ADR 0027 already records linking as a future path (0027:29).

## Rejected
- B (web-issued link code): sound, but a new credential, endpoint and frontend gate with no plan task; rate limiting is Phase 20.
- C (bot-issued code entered on the web): same cost as B, inverse flow, plus a frontend page out of scope.
- D-oauth (Connect Discord via OAuth2): strongest proof, but out of scope (scope.md:35) and widens the web trust boundary.
- D-cli (operator CLI link): new command with no plan task; ownership verified only out of band.
- E (`/link email:x`): account takeover; hard objection.

## Dissent
None. Risk to watch: an existing web user gets a second, empty account in Discord, so "ambient learning" on an existing
wiki (vision.md:64) is not met until linking ships. State this gap visibly in the docs and the issue.

## Implementation notes
- `dev.mindforge.domain.port.ExternalIdentityRepository`: `Optional<UUID> findUserId(ExternalPlatform platform, String externalId)`;
  `User createUserAndLink(ExternalPlatform platform, String externalId, User user)`; the reminder listing method from Q13/Q10. No `link`.
- Plan 18.1 "As built": "`link(...)` not built: no Phase 18 command calls it (minimal-implementation.md). A Discord user has a
  separate account (ADR 0027). Linking to a web account needs a verified flow and a data policy for the provisioned account; see issue #<n>."
- Open a GitHub issue (label `needs-triage`): "Link a chat-platform identity to an existing web account". Record: verified
  ownership on both sides (prefer platform OAuth or a hashed, single-use, short-TTL, rate-limited code), never by email claim;
  a rule for a provisioned account that owns data (refuse or explicit move, never silent loss); shared by Discord and Slack (Phase 19).
- `docs/project/deployment.md` (user notes): a Discord user gets a separate account (`discord-<id>@users.invalid`) that cannot
  see a web account's knowledge bases.
- `docs/project/roadmap.md`: list "account linking (bot <-> web)" as an open follow-up.
- No new tests beyond 18.4; no test may reference `link`.
