# Phase 18 — Discord Bot: tickets (execution order)

| id | title | depends_on | size | status |
|---|---|---|---|---|
| P18-01 | Store external identity links with an ExternalIdentityRepository port and the V8 external_identities table | — | M | done |
| P18-02 | Reserve the users.invalid email domain for bot accounts and refuse it on registration and provider sign-in | P18-01 | S | done |
| P18-03 | Resolve a chat-platform user to a MindForge user, provisioning the account and a default knowledge base on first contact | P18-01, P18-02 | M | done |
| P18-04 | Add the Discord settings, the fail-closed guild and role allowlist, the per-user command rate limit, and the JDA bot lifecycle | — | M | done |
| P18-05 | Gate every Discord slash command through the allowlist, rate limit and identity resolution, reply ephemerally, and resolve the kb option | P18-03, P18-04 | L | done |
| P18-06 | Answer /ask in Discord with a one-turn Query interaction over QueryService | P18-05 | S | done |
| P18-07 | Run a Quiz Session from Discord with /quiz start and /quiz answer over QuizService | P18-05 | M | done |
| P18-08 | Upload a Document from Discord with /upload, downloading the attachment only from Discord's CDN hosts | P18-05 | M | ready |
| P18-09 | Send a daily DM to each Discord-linked user who has due Flashcards | P18-01, P18-04 | M | ready |
| P18-10 | Record the Discord bot, its settings and the account model in the docs, open the linking follow-up, and close Phase 18 | P18-01..P18-09 | S | ready |

Parallel after their dependencies: P18-04 with P18-01..P18-03; P18-06, P18-07, P18-08 and P18-09 with each other.

## Coverage

- Plan tasks: 18.1 -> P18-01, P18-02, P18-03 (As built in P18-10); 18.2 -> P18-03..P18-08; 18.3 -> P18-09;
  18.4 -> P18-03 (first contact auto-provisions), P18-05 (non-allowed guild rejected), P18-07 (user A cannot reach
  user B's session). Checklist "commands delegate to the same services" -> P18-06..P18-08; "reminder DMs" -> P18-09.
- Decisions: D01 -> P18-02, P18-03; D02 -> P18-01, P18-10; D03 -> P18-03, P18-05; D04 -> P18-04 (gate order in
  P18-05); D05 -> P18-07; D06 -> P18-06; D07 -> P18-08; D10 -> P18-09; D11 -> P18-04 (gate order in P18-05);
  D12 -> P18-08, P18-10; D14 -> P18-05 (asserted per command in P18-06..P18-08).

## Breakdown items without a ticket

- W03 (account linking): dropped by D02 — `link(...)` is not built; the gap is recorded in P18-10 (plan "As built",
  roadmap, follow-up issue).
- W05 (knowledge-base context): folded into P18-03 (default knowledge base) and P18-05 (`kb` option, autocomplete,
  `requireOwner`) per D03.
- W09 (bot rate limit): folded into P18-04 (`DiscordRateLimiter`) and P18-05 (gate order) per D11.

## Resolved overlap between decisions

- Defer timing: D05 defers before identity resolution, D07 after ownership. P18-05 uses one rule for all commands:
  the ephemeral defer runs right after the allowlist and rate-limit checks pass, before identity resolution. Rejected
  commands still reply immediately and provision nothing. No security property of D04/D07/D11 changes.
