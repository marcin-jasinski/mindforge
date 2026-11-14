---
id: Q14
status: decided
decision: D14
impact: medium
blocks: [W06, W07, W08]
---
## Question
Are the bot's replies (Query answers, quiz questions and grades, upload results, errors) ephemeral — visible only to the
caller — or posted in the channel?

## Context
Slash-command replies can be ephemeral (only the invoking user sees them) or public. Answers are cut from the user's
own wiki, and quiz grades are study data; a public reply shows them to every channel member. Plan 18.4: "user A cannot
reach user B's sessions" — public replies do not let A act on B's session, but they expose B's content. Ephemeral
replies cannot be seen later in channel history.

## Known options
- A: All replies ephemeral.
- B: Query answers public (shared learning), quiz and upload ephemeral.
- C: A per-command `public:true` option, ephemeral by default.

## Constraints
- Knowledge bases are private, single-owner (web-security.md § API Authorization; architecture.md § Security & Trust
  Model "Tenancy is structural").
- Never send reference answers or grounding excerpts, either way.
