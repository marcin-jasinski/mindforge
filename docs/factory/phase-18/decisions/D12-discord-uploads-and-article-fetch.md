---
id: D12
question: Q12
status: decided
method: judge
class: local
votes: { pragmatist: A }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q12.r1.tally.md]
adr: 
---
## Decision
Use A: add `UploadSource.DISCORD` to `ArticleFetcher.SCANNED_SOURCES`, so a document uploaded through Discord fetches
the external articles it links to exactly like an API, CLI or file-watcher upload, and only when
`mindforge.articles.fetch-external-articles` is on (default `false`). `ARTICLE` and `CONVERSATION` stay out of the set.

## Why
- A Discord `/upload` is a user upload; it publishes `DocumentAccepted` like any non-article source (IngestionService.java:151-152), and ADR 0024 hangs the fetch on that event. Excluding one channel is an inconsistency with no stated gain.
- The feature stays off by default (application.yml:108), so A adds no default egress.
- Every fetched URL comes from document content and goes through `EgressPolicy` (ADR 0023) whatever the source; the Discord caller is already admitted by the fail-closed guild/role allowlist (ADR 0028), the same trust level as a registered API user.
- One constant plus tests; the allowlist stays explicit, as the D06 risk note asks.

## Rejected
- B: leaves a channel-specific gap that users and docs must explain, with no security gain because the fetch path is already guarded and off by default.

## Dissent
None (single ballot). Risk to watch: if a later change lets non-allowlisted Discord users (for example DMs) upload, revisit this set together with ADR 0028.

## Implementation notes
- `src/main/java/dev/mindforge/application/service/ArticleFetcher.java`: `EnumSet.of(UploadSource.API, UploadSource.CLI, UploadSource.FILE_WATCHER, UploadSource.DISCORD)`; `UploadSource.DISCORD` is added by the Discord upload work (W08).
- Unit tests in `ArticleFetcherTest` (plain Mockito): a `DISCORD` document with a link fetches it when the setting is on; a `DISCORD` document fetches nothing when the setting is off; `CONVERSATION` and `ARTICLE` documents fetch nothing.
- Docs: note in the article-fetch docs (Phase 17) that Discord uploads are scanned like API/CLI uploads.
