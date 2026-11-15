---
id: Q15
status: answered
impact: low
blocks: [W07]
answered_by: docs/factory/phase-18/decisions/D12-discord-uploads-and-article-fetch.md#Decision
---
## Question
Is a document uploaded through Slack scanned for article links like an API, CLI or Discord upload?

## Context
`ArticleFetcher.SCANNED_SOURCES = {API, CLI, FILE_WATCHER, DISCORD}`. D12: a bot upload is a user upload; excluding a
channel is an inconsistency with no security gain, because every fetched URL goes through `EgressPolicy` and the
feature is off by default.

## Known options
- A (settled): add `UploadSource.SLACK` to `SCANNED_SOURCES` with a unit test in `ArticleFetcherTest`.

## Constraints
`ARTICLE` and `CONVERSATION` stay out; D12's risk note: if non-allowlisted users could upload, revisit with Q02.
