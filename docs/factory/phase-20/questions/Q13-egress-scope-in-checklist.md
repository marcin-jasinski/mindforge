---
id: Q13
status: answered
impact: low
blocks: [W05, W08]
answered_by: docs/adr/0023-egress-policy-guards-untrusted-urls-only.md#considered-options; docs/adr/0035-slack-uploads-are-files-sent-to-the-bot-in-a-direct-message.md
---
## Question
Does the 20.1 checklist pass widen `EgressPolicy` to every outbound HTTP call, and how is the Slack file downloader
reflected in `web-security.md`?

## Context
- ADR 0023 (accepted): `EgressPolicy` guards only content- and user-derived URLs; operator endpoints are trusted; it
  rejected "every outbound call through the policy" and says "Phase 20.1 already re-verifies `web-security.md`".
- `OutboundHttpGuardTest` allows HTTP-client code only in `infrastructure.web`, `SecurityConfig.java`,
  `DiscordAttachments` and `slack/SlackFiles.java`.
- `web-security.md` § Untrusted Input Handling describes the Discord downloader (ADR 0029) but not the Slack one
  (ADR 0035, Phase 19) — a documentation gap.

## Known options
- A: No widening; 20.1 re-verifies that the guard test passes and its allowlist matches the standard, and adds a Slack
  paragraph (fixed `files.slack.com` hosts, bot token only to them, capped read) to `web-security.md`.

## Constraints
- ADR 0023, 0029, 0035. Answer: A.
