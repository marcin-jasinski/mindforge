---
id: T28
title: Minor findings from the spec review
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Minor** — each is a one-line decision or a doc correction. Answer them
item by item.

1. **Untrusted content in Phase 17.** The article fetcher auto-ingests third-party web content into a wiki with no
   approval step. ADR 0012 assumed sources were the learner's own. A fetched page carrying injected instructions can
   revise any Concept, bounded only by the claim cap.
   *Decide:* leave it disabled by default and say so, or restrict article runs (e.g. create-only, no supersessions).
2. **Cost before Phase 14.** Phase 6.8 records a summed `cost` (`docs/project/implementation-plan.md:670`), but
   `CompletionResult.costUsd` is not populated until Phase 14 (`:345`). *Decide:* state that `cost` stays NULL until
   then.
3. **Missing migration.** Phase 11 has no task for `interactions` and `interaction_turns`
   (`docs/project/architecture.md:142`).
4. **Preprocessor's status.** The model-services table lists `Preprocessor` with tier "none" (`architecture.md:72`),
   while `docs/standards/backend/ai_agents.md:25` says a deterministic step is plain code, not a model service.
   *Decide:* its package, and whether its `VERSION` is recorded in `step_versions`.
5. **Revision diffs.** They are computed on the server or in the SPA (`implementation-plan.md:796`, `:991`)?
   T11's "no new dependency" covers export only.
6. **Glossary.** `CONTEXT.md`'s **Ingest Run** says a run integrates a document or reverts one. It omits `LINT` runs.
7. **Admin role.** `listUnredacted()` is "admin-only" (`implementation-plan.md:929`, `:947`), but Phase 9's auth has no
   admin role. Define one or drop the method.
8. **Index sort order.** "Sorted by title" with `String.compareTo` places `Ł`, `Ś` and `Ż` after `z`.
   *Decide:* a `Collator` for the knowledge base's locale, or accept the order.
9. **T12's counts.** T12 says "22 of the 71 main files and 4 of the 13 test classes"
   (`docs/wayfinder/tickets/12-existing-code-fate.md:102`). The tree has 72 main Java files and 9 test classes (12 test
   Java files including `support/`). Correct the numbers.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option, item by item.

1. **Untrusted articles: disabled by default and restricted.** Article fetching stays disabled by default, via
   `ProcessingSettings.fetchExternalArticles`. When enabled, an `ARTICLE` document's run is create-only:
   - Resolve drops every claim whose final path is already a live page, and records it in `failures`.
   - Supersede does not run (`supersession_skipped = true`, reason `"article"`).

   An injected instruction can therefore only create new pages, and a revert removes them. Relevance guarding and every
   other check still apply. An article whose lesson id already exists is skipped (T18).
2. **Cost before Phase 14.** Phase 6 does not write `ingest_runs.cost`. It stays `NULL` until Phase 14 populates
   `CompletionResult.costUsd` and sums it. A `NULL` cost means "not measured", never zero.
3. **Missing migration.** Phase 11.1 gains `V4__create_interactions.sql`, with `interactions` and `interaction_turns`.
   Both tables have a `knowledge_base_id` FK with `ON DELETE CASCADE`. `interaction_turns.used_page_paths` is `TEXT[]`,
   because paths are stable identity.
4. **`Preprocessor` is plain code.** It moves to `dev.mindforge.application.ingest` and leaves the model-services table.
   It has no `VERSION` and no `step_versions` entry. A change in its behaviour is a code change, visible in git like any
   other deterministic step — which is what `ai_agents.md` already says.
5. **Diffs are computed in the SPA.** The API returns a revision and its prior revision (T25: `RunReportQuery.report`,
   `WikiStore.listRevisions`). The SPA diffs them with the `diff` npm package, added in Phase 12.1. The server gains no
   diff dependency and no diff format to version.
6. **Glossary.** **Ingest Run** is redefined as: "One execution that changes the wiki — integrating a document or
   conversation turn, reverting an earlier run, or a full Lint — and the record of what it changed. Runs queue per
   knowledge base; at most one is active."
7. **No admin role: `listUnredacted()` is dropped.** Nothing calls it. An admin role would add security surface with no
   requirement behind it. `InteractionStore` keeps `listForUser`.
8. **Index order: `java.text.Collator` for Polish.** Use `Locale.forLanguageTag("pl")`, the prompt locale, and break ties
   by path. That places `Ł`, `Ś` and `Ż` where a Polish reader expects them, uses the standard library, and keeps the
   output deterministic for a given JDK.
9. **T12's counts, corrected.** In T12 and in the map's T12 line, the counts become: *22 of the 72 main files and 4 of
   the 9 test classes (12 test files counting `support/`)*.

### Feeds

- **T29:**
  - 6.2 — `Preprocessor` package
  - 6.8 — `cost`
  - 11.1, 11.5
  - 12.1
  - 17.2
  - `architecture.md` — model-services table
  - `CONTEXT.md`
  - `IndexRenderer` sort order
  - T12 text
