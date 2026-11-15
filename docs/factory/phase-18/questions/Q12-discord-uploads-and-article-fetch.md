---
id: Q12
status: decided
decision: D12
impact: low
blocks: [W08]
---
## Question
Does a document uploaded through Discord fetch the external articles it links to (when
`mindforge.articles.fetch-external-articles` is on), like API and CLI uploads?

## Context
`ArticleFetcher.SCANNED_SOURCES = EnumSet.of(API, CLI, FILE_WATCHER)`
(`src/main/java/dev/mindforge/application/service/ArticleFetcher.java:43`); `ARTICLE` and `CONVERSATION` fetch nothing
(no recursion). ADR 0024 decides the fetch runs after an upload commits from `DocumentAccepted`, which
`IngestionService.ingest` publishes for every newly inserted non-article document — so a `DISCORD` document publishes
the event, but the fetcher skips it unless the set changes. The set is an allowlist, so the default for a new
constant is "no fetch".

## Known options
- A: Add `DISCORD` to `SCANNED_SOURCES`: a Discord upload is a user upload like any other; still off by default.
- B: Leave it out: content arriving through a chat platform is fetched less eagerly; document the difference.

## Constraints
- Article fetching remains off by default (Phase 17 checklist).
- D06 risk note: prefer branching on special values; this set is an explicit allowlist, so the choice must be explicit
  and tested.
