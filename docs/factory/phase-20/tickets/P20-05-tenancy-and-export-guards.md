---
id: P20-05
title: Guard the wiki store's knowledge-base binding and the exporter's freedom from study data with tests
status: ready
plan_tasks: [20.1]
decisions: []
depends_on: []
size: M
issue: 59
commit:
fix_rounds: 0
---
## Goal
The three requirements plan task 20.1 names are each proved by a test that fails when they break: every `WikiStore`
read with another knowledge base's id returns nothing; the export code reads no study table and no run internals;
dedup is per knowledge base (already tested). The remaining `web-security.md` sections are re-verified, and any gap
found is fixed or recorded in Notes.

## Context
- `src/main/java/dev/mindforge/domain/port/WikiStore.java`: every method takes `kbId` first. Read methods:
  `findByPath`, `findById`, `findByPaths`, `listIndex`, `listBodies`, `pageIdsForLesson`, `findSupersession`,
  `tipRevisions`, `revisionsByRun`, `findRevision`, `listRevisions`, `outboundLinks`, `inboundLinks`, `graph`,
  `liveSupersessionsOf`. Adapter: `infrastructure/persistence/adapter/WikiStoreAdapter.java` over the `WikiPage`,
  `PageRevision`, `PageLink`, `PageSource`, `PageSupersession` JPA repositories in `infrastructure/persistence/jpa/`.
  `src/test/java/dev/mindforge/integration/persistence/WikiStoreAdapterTest.java` tests only the link-row FK across
  knowledge bases; no test reads with another knowledge base's id.
- Export: `infrastructure/export/BundleExporter.java` reads `WikiStore`, `BundleQuery`
  (`infrastructure/persistence/adapter/BundleQueryAdapter.java` → `PageSourceJpaRepository`) and
  `RunReportQuery.logEntries` (`RunReportQueryAdapter` → `IngestRunJpaRepository.findLogEntries`, which selects kind,
  times, supersession count and document fields only — no `cost`, `step_versions`, `failures`, `findings`). No study
  repository is injected today. `src/test/java/dev/mindforge/integration/export/BundleExportTest.java`
  (`anExportOfEveryKindOfRowIsConformantEscapedAndCarriesNoRunInternals`) covers run internals; nothing guards against
  a future study read.
- Study persistence types (must never be reachable from export code): `FlashcardJpaRepository`,
  `QuizSessionJpaRepository`, `StudyEventJpaRepository`, `InteractionJpaRepository`, `InteractionTurnJpaRepository`,
  their entities, and the domain study/interaction ports (`grep` `domain/port` for the study and interaction stores).
- Dedup: `uq_documents_kb_content_hash ON documents (knowledge_base_id, content_hash)` (`V1__baseline.sql:45`);
  `DocumentRepositoryAdapterTest.sameHashInTwoKnowledgeBases_isNotADuplicateAcrossThem`. Done; record only.
- Source-scan guard style to copy: `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java`
  (scans `src/main/java` text for markers; plain JUnit). ArchUnit is Phase 21.3 — do not add it.
- Q13 (answered, ADR 0023): `EgressPolicy` is not widened. Re-verify that `OutboundHttpGuardTest` passes and its
  allowlist (`infrastructure.web`, `SecurityConfig.java`, `DiscordAttachments`, `slack/SlackFiles.java`) matches the
  standard; the Slack paragraph in `web-security.md` is P20-08.
- Do not re-litigate (`docs/wayfinder/code-review-map.md`): domain JDK-only, `kbId` first on tenant-scoped ports,
  controllers check ownership first, no forbidden field reachable from `api/`, BCrypt 12, cookie flags, zip-slip.
- Binding rules: CLAUDE.md § Testing (Testcontainers with real PostgreSQL, AssertJ, `make*` factories, unit tests
  without Spring); "Study data never enters a wiki page or an export".

## Decisions to apply
No factory decision binds this ticket. Answered questions that apply:
- **Q13 — Answer A:** no widening of `EgressPolicy`; 20.1 re-verifies that the guard test passes and its allowlist
  matches the standard.
- **CLAUDE.md:** "Study data never enters a wiki page or an export — the exporter never reads study tables";
  "Every tenant-scoped port method takes `kbId` first".

## Acceptance criteria
- [ ] AC1: An integration test writes pages, revisions, sources, links and a supersession into knowledge base A, then
  calls every `WikiStore` read method listed above with knowledge base B's id (and A's page ids, paths, run id,
  supersession id) and asserts each returns empty / absent / an empty graph.
- [ ] AC2: The same test (or a sibling) asserts the reads with A's id do return the rows, so AC1 is not vacuous.
- [ ] AC3: A source-scan unit test fails if any file under `dev/mindforge/infrastructure/export/`, or
  `BundleQueryAdapter.java` / `RunReportQueryAdapter.java`, references a study or interaction type (the JPA
  repositories and entities named above, and the domain study/interaction store ports).
- [ ] AC4: A source-scan (or query-text) assertion fails if `IngestRunJpaRepository.findLogEntries`' query selects
  `cost`, `step_versions`/`stepVersions`, `failures` or `findings`.
- [ ] AC5: `OutboundHttpGuardTest` and `DocumentRepositoryAdapterTest` pass unchanged; any gap found in the other
  `web-security.md` sections is either fixed here with a test, or — if it needs a decision — recorded in Notes and
  reported, not fixed.

## Test plan
- AC1, AC2: integration, Testcontainers, `src/test/java/dev/mindforge/integration/persistence/WikiStoreAdapterTest.java`
  (extend with a `make*`-built two-knowledge-base fixture).
- AC3, AC4: unit, plain JUnit source scan,
  `src/test/java/dev/mindforge/unit/infrastructure/export/ExportReadsNoStudyDataGuardTest.java` (new), in the style of
  `OutboundHttpGuardTest`.
- AC5: existing tests; full gate `mvn -B verify -DskipFrontend=true`.

## Files likely touched
- `src/test/java/dev/mindforge/integration/persistence/WikiStoreAdapterTest.java`
- `src/test/java/dev/mindforge/unit/infrastructure/export/ExportReadsNoStudyDataGuardTest.java` (new)
- Production code only if AC1 or the audit finds a real gap (record it in Notes).

## Out of scope
- Widening `EgressPolicy` or `OutboundHttpGuardTest`'s scope; ArchUnit rules (Phase 21.3).
- Reworking the items on the review map's "Do not re-litigate" list.
- HTTP-level cross-user tests (P20-06); docs and the `web-security.md` trace lines (P20-08).

## Notes
