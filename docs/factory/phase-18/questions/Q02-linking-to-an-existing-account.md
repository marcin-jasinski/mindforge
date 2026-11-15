---
id: Q02
status: decided
decision: D02
impact: high
blocks: [W01, W02, W03]
---
## Question
Does this phase let a Discord user link to an existing MindForge web account (the port's `link(...)`), and if so, how
does the server verify that the Discord user owns that account?

## Context
Plan 18.1 lists `ExternalIdentityRepository.link(...)` next to `createUserAndLink(...)`, but no task, command or test
calls `link`; the plan's commands are only `/ask`, `/quiz start`, `/quiz answer`, `/upload`.
`docs/standards/global/minimal-implementation.md` ("Build what you need", "No future stubs") forbids a method with no
caller. Without linking, a person who already uses the web app gets a second, empty account in Discord (no knowledge
bases, Q03) and cannot study their existing wiki from Discord — which is the vision's "ambient learning" point
(`docs/project/vision.md:64`). Linking by an unverified claim (`/link email:x`) would let anyone take over any account:
an account-takeover risk.

## Known options
- A: No linking in Phase 18. Implement `findUserId` and `createUserAndLink` only; drop `link` from the port until a
  caller exists; record it in the plan's "As built". Discord users live in their own accounts.
- B: One-time link code issued by the web app: an authenticated `POST /api/v1/users/me/link-codes` returns a short-lived
  random code (stored hashed, single use, TTL minutes); `/link code:<code>` in Discord calls `link(DISCORD, discordId,
  userId)`. Needs an API endpoint, a small table or a Caffeine store (single instance), and a frontend button (extra
  frontend gate). Handles an already-provisioned Discord account (re-point or refuse).
- C: Code issued by the bot, entered in the web app (`/link` DMs a code; the signed-in user enters it on a profile page).
  Same verification strength as B, inverse flow.

## Constraints
- web-security.md § API Authorization: every REST method verifies ownership; a link code is a credential — never log
  it, store only a hash, single use, short TTL, rate limited.
- Never return a token in a response body to the SPA for storage — a link code is shown once to the user, not a session
  token; it must not authenticate anything except the link.
- If the Discord identity is already linked to an auto-provisioned account, the decision must say what happens to that
  account's data (move, refuse, or keep separate) — no silent data loss.
- Phase 19 reuses the same port.
