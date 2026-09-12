---
id: T25
title: The read surface of WikiStore and the other kbId-scoped ports
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Gap** — the port lists the writes and almost none of the reads.

Phase 5.3 (`docs/project/implementation-plan.md:566`) gives `WikiStore`:
- `findByPath`, `findById`, `listPages`;
- `savePage`, `deletePage`, `reinsertPage`;
- `addSources`, `addSupersessions`, `deleteSourcesByRun`, `deleteSupersessionsByRun`;
- `listRevisions`, `danglingLinks`.

Later phases need reads it does not have:

| Consumer | Needs |
|---|---|
| Export (9b.1) | sources per page joined to document title, filename and date; live supersessions per page; revision counts per run |
| Health (7.1) | wrong-directory links, orphan Concepts, duplicate titles, dangling supersessions |
| Run report (9.7) | revisions written by a run, each with its prior revision; supersessions by run |
| Revert (5.6) | pages touched by a run; highest revision per page |
| Lesson scope (10.1) | pages whose `page_sources` join to documents of a lesson |
| Query and graph (11.2, 9.7) | outbound and inbound links per page; all live pages plus links for a knowledge base |
| `LogRenderer` (5.5) | completed runs with derived created, revised and superseded counts |

Separately, only `WikiStore` is required to take `kbId` first. `DocumentRepository.findById(UUID)`
(`src/main/java/dev/mindforge/domain/port/DocumentRepository.java:16`) and the planned `IngestRunRepository` are not,
even though export, run reports and revert reach documents and runs on behalf of a knowledge base.

Decide:

- **The read methods,** named per consumer.
- **Where cross-table reads live.**
  - *Recommended:* dedicated read ports (e.g. `RunReportQuery`, `WikiHealthQuery`), keeping `WikiStore` page-shaped.
  - *Alternative:* all on `WikiStore`.
- **kbId-first for other ports.** *Recommended:* the rule extends to `DocumentRepository` and `IngestRunRepository`.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option.

- **`WikiStore` stays page-shaped.**
- **Cross-table reads move to three query ports**, each named for its consumer.
- **`kbId` comes first on every tenant-scoped port.** The only exceptions are two system methods used by the sweep.

Every method below takes `UUID kbId` as its first argument; the signatures leave it out for brevity.

### `WikiStore` — pages, their rows and their links

| Method | Consumer |
|---|---|
| `findByPath(path)`, `findById(pageId)`, `findByPaths(paths)` | Resolve, Write, Query load, revert |
| `listIndex()` → `IndexEntry(path, title, description, type)` | `IndexRenderer` |
| `listBodies(type)` | full Lint, export |
| `pageIdsForLesson(lessonId)` → live Concepts whose `page_sources` join to documents of the lesson | Lesson scope (10.1) |
| `savePage(PageWrite, runId)` → page row, revision, re-derived links; returns the revision | commit 1, Lint, revert |
| `deletePage(pageId, runId)` → tombstone + row delete | edit deletions, revert |
| `reinsertPage(PageRevision from, runId)` | revert |
| `addSources(runId, sources)`, `addSupersessions(runId, rows)` | commits |
| `deleteSources(runId, pageIds)`, `deleteSupersessionsBySuperseding(runId, pageIds)`, `deleteSupersession(supersessionId)` → the removed row | revert (T16) |
| `tipRevisions(pageIds)` → highest revision per page; `revisionsByRun(runId)`; `findRevision(pageId, revision)`; `listRevisions(pageId)` | revert tip check, run report, page history |
| `outboundLinks(pageIds)`, `inboundLinks(paths)`, `graph()` → live pages + links | Supersede candidates, Query neighbours, graph view |
| `liveSupersessionsOf(pageIds)` → rows with superseding path and title, joined to live pages | writer input (T21), Query, SPA page view, study skip |

### Query ports — cross-table and read-only, returning no entity types

**`RunReportQuery`** — consumers: the run report (9.7), the run list, `LogRenderer` (5.5).

- `listRuns(page)` → run summaries: status, kind, document title and derived counts.
- `report(runId)` → each revision written with its prior revision, the run's supersessions, `failures` and size deltas.
- `logEntries()` → completed runs with:
  - their derived counts and `supersession_count`;
  - the lesson title and path;
  - for a revert, the reverted run's kind, date and lesson.

**`WikiHealthQuery`** — consumer: the health view (7.1).

- `danglingLinks()`, `wrongDirectoryLinks()`, `orphanConcepts()`, `duplicateConceptTitles()`.
- `supersessionsToCheck()` → live supersessions, each with the superseded body and whether the superseding page is live.
  The anchor itself is checked in Java (T26).

**`BundleQuery`** — consumer: export (9b.1).

- `citations()` → per page, its distinct sources with lesson id, lesson title, filename, `upload_source` and
  `created_at`.
- `supersessionNotes()` → per superseded page and anchor, the superseding path and title.

One port per consumer keeps each read's columns explicit. `BundleQuery` exposes no study table and no `cost`,
`step_versions`, `failures` or `findings` column, which makes T11 decision 6 a type. `WikiStore` keeps only the methods a
page writer needs.

### `kbId` comes first on every tenant-scoped port

This covers `DocumentRepository`, `IngestRunRepository`, the query ports above, `StudyProgressStore`, `QuizSessionStore`
and `InteractionStore` (for example `listForUser(kbId, userId)`).

```java
// DocumentRepository — 3b fixes findByContentHash and findById; Phases 4 and 6 add the rest
Optional<Document>  findById(kbId, documentId);
Optional<Document>  findByContentHash(kbId, hash);           // excludes CONVERSATION
boolean             lessonExists(kbId, lessonId);
Document            insert(kbId, document);
List<Document>      listByKnowledgeBase(kbId);

// IngestRunRepository — T17's lifecycle
IngestRun           enqueue(kbId, run);                       // QUEUED
boolean             claim(kbId, runId);                       // lease + QUEUED→RUNNING, one transaction
Optional<IngestRun> oldestQueued(kbId);
void                markWritten(kbId, runId, failures);       // fenced
void                complete(kbId, runId, supersessionCount, supersessionSkipped, failures);  // fenced + release
void                fail(kbId, runId, reason, retryable, failures);                           // fenced + release
Optional<IngestRun> findById(kbId, runId);
Optional<IngestRun> latestForDocument(kbId, documentId);
boolean             hasQueuedOrActive(kbId, RunKind kind);
// system — for the sweep only; each returned row carries its kbId
List<IngestRun>     findUnfinished();                         // RUNNING | WRITTEN
List<UUID>          knowledgeBasesWithQueuedRuns();
```

The two system methods are the named exception. They serve the sweep, which acts for no user. Only `RunWorker` may call
them, and an ArchUnit rule in 21.3 enforces that.

### Feeds

- **T29**:
  - 3b.3 — scope `findById`.
  - 5.3 — rewrite with these ports.
  - 7.1, 9.7 and 9b.1 — reference them.
  - `hexagonal.md` — extend the tenancy rule.
  - `CLAUDE.md` — re-word the rule to cover every tenant-scoped port.
