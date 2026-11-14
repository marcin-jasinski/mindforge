---
id: Q03
status: decided
decision: D03
impact: high
blocks: [W02, W05, W06, W07, W08, W10]
---
## Question
Which knowledge base does a Discord command (`/ask`, `/quiz start`, `/upload`) act on, and how does an auto-provisioned
user, who owns none, get one?

## Context
Every application service is keyed by `kbId` (`QueryService.start(kbId, userId)`, `QuizService.begin(kbId, userId,
scope)`, `DocumentUpload.knowledgeBaseId`, `FlashcardService.due(kbId, scope)`), and CLAUDE.md requires `kbId` first on
every tenant-scoped port. The plan's command list names no knowledge base. The web passes `kbId` in the path; the CLI
takes `--kb=<id>` (ADR 0019). A UUID is unusable to type in Discord. A user may own several knowledge bases
(`KnowledgeBaseService.list(ownerId)`). A freshly provisioned account (Q01) owns none, so `/ask` and `/quiz` have
nothing to work on until something creates one; there is no `/kb create` in the plan.

## Known options
- A: A required `kb` option on each command with Discord autocomplete over the caller's knowledge bases (name shown,
  id as value); the bot checks `requireOwner` on the chosen id. Plus: provisioning creates one default knowledge base
  (e.g. "Discord"), so `/upload` works for a new user.
- B: A per-user "current knowledge base": `/kb use name:<…>` stores the choice (new column on the identity row, or a
  small table); commands use it; provisioning creates a default one and selects it.
- C: One knowledge base per user implicitly: the user's only (or oldest) knowledge base; provisioning creates it; a user
  with several gets an error or the oldest. Least UI, surprising for multi-KB users.
- D: A guild maps to a knowledge base by configuration — wrong under single-owner tenancy (members would write into the
  configured owner's wiki); listed to be rejected.

## Constraints
- Ownership is checked before any work (`KnowledgeBaseService.requireOwner`, web-security.md § API Authorization).
- Knowledge bases are single-owner; there is no sharing or membership model (ADR 0019 § Consequences).
- The reminder job (W10) must know which knowledge bases' due cards to count — all owned, or only the selected one.
- No speculative state: a stored selection needs a migration and a caller.
