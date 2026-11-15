---
id: Q05
status: decided
decision: D05
impact: medium
blocks: [W06]
---
## Question
How does the single plan command `/mf-quiz` start a quiz and take answers?

## Context
- Plan 19.2 names `/mf-quiz` only. Discord has `/quiz start kb:<…>` and `/quiz answer text:<…>`
  (`discord/QuizCommand.java`), with the current quiz kept per internal user id in `DiscordQuizSessions` (D05).
- Slack slash commands have one free-text argument, so subcommands are parsed from text; Block Kit buttons and modals
  give typed input but need interaction handlers (`block_actions`, `view_submission`).
- The answer may be long free text; Slack slash command text is a single line in the composer.

## Known options
- A: Text subcommands: `/mf-quiz start` (knowledge base per Q03) and `/mf-quiz answer <text>`; replies ephemeral.
- B: `/mf-quiz` starts (knowledge base per Q03); each question is an ephemeral message with an "Answer" button that
  opens a modal with a multi-line input; the submission is graded and the next question is sent.
- C: `/mf-quiz` with no text starts, `/mf-quiz <text>` answers the current quiz.

## Constraints
- Ownership through `QuizService.next/answer` (`owned(...)`); user A cannot reach user B's session (plan 19.3).
- Only `NextQuestion` and `Graded` text reach the user; never `reference_answer` or `grounding_context` (CLAUDE.md).
- Replies ephemeral (ADR 0032 § Consequences); session pointer per D05 (Q12).
