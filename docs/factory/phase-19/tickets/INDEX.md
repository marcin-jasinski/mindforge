# Phase 19 — Slack Bot: tickets

Execution order. P19-01 and P19-02 can run in parallel; P19-06 can run in parallel with P19-03..P19-05;
P19-05 and P19-07 can run in parallel after their dependencies.

| Id | Title | Depends on | Size | Status |
|---|---|---|---|---|
| [P19-01](P19-01-slack-platform-and-identity-key.md) | Add the Slack external platform and its home-team-and-user identity key | — | S | done |
| [P19-02](P19-02-slack-socket-mode-bot-lifecycle.md) | Add Bolt with the Java-WebSocket Socket Mode backend and start the Slack bot only when both tokens are set | — | M | ready |
| [P19-03](P19-03-slack-command-gate.md) | Gate every Slack slash command through the team allowlist, the rate limit and identity resolution | P19-01, P19-02 | L | ready |
| [P19-04](P19-04-slack-ask-command.md) | Answer /mf-ask as a one-turn Query on the caller's only knowledge base | P19-03 | M | ready |
| [P19-05](P19-05-slack-quiz-command.md) | Run a Quiz Session through /mf-quiz start and /mf-quiz answer | P19-04 | M | ready |
| [P19-06](P19-06-slack-file-downloader.md) | Download a Slack file only from files.slack.com with the bot token and a capped read | P19-02 | S | ready |
| [P19-07](P19-07-slack-dm-upload-handler.md) | Ingest a file sent to the bot in a direct message as a Slack upload | P19-04, P19-06 | L | ready |
| [P19-08](P19-08-slack-docs-and-close-out.md) | Record the Slack bot, its settings and its ADRs in the docs, open the live-check follow-up, and close Phase 19 | P19-01..P19-07 | S | ready |

## Coverage

- Plan tasks: 19.1 → P19-07; 19.2 → P19-01..P19-07; 19.3 (allowlist, identity, ownership) → P19-01, P19-03, P19-04,
  P19-05, P19-07; close-out → P19-08.
- Decisions: D01 → P19-01 (adapter rule carried in P19-03, P19-07); D02 → P19-03 (event rule carried in P19-07);
  D03 → P19-04; D04 → P19-07; D05 → P19-05; D06 → P19-02, P19-08; D07 → P19-03 (rate limiter), P19-05 (quiz
  sessions); D16 → P19-02, P19-08.
- Breakdown W03 dropped (D07 chose copy, no shared package). Breakdown W04 split into P19-03 (gate) and the
  knowledge-base part of P19-04; W07 split into P19-06 (downloader) and P19-07 (handler).
