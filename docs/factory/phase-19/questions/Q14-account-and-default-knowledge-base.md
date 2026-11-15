---
id: Q14
status: answered
impact: low
blocks: [W01]
answered_by: docs/adr/0027-bot-users-provisioned-with-a-reserved-invalid-email.md; docs/adr/0030-discord-commands-name-the-knowledge-base-and-provisioning-creates-a-default-one.md
---
## Question
What account does a Slack user get on first contact?

## Context
ADR 0027: "On first contact from a chat platform (Discord in Phase 18, Slack in Phase 19), MindForge creates a `users`
row with the synthetic address `<platform>-<externalId>@users.invalid` … Phase 19 reuses the same rule with its own
platform prefix." ADR 0030: provisioning creates one knowledge base named after the platform.
`ExternalIdentityService.resolve` already does both for any `ExternalPlatform`.

## Known options
- A (settled): add `ExternalPlatform.SLACK("Slack")`; call `ExternalIdentityService.resolve(SLACK, <id per Q01>,
  displayName)`. A person on Discord and Slack has two separate accounts (no linking; #43).

## Constraints
No new migration for the constant (`platform VARCHAR(50)`, no CHECK). The external id format is Q01.
