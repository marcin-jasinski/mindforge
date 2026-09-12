---
id: T11
title: How a bundle gets exported
type: grilling
status: closed
assignee: claude
blocked_by: [T05, T06]
---

## Question

Export is the agreed justification for adopting OKF rather than inventing a page schema: a user
can download their knowledge base, open it in Obsidian, or hand it to another agent. If the
export is not actually conformant, OKF is doing no work.

Decide:

- **What comes out?** A zip, or an initialized git repo with history. Git is the recommended OKF
  distribution form and gives the user the version history the spec assumes — but MindForge does
  not run git today and the map rules git out as a storage *backend* (not as an export format).
- **Are raw sources included?** The LLM Wiki architecture has three layers; the wiki is layer
  two. A bundle without its sources is not reproducible, and one with them may be large and may
  re-export copyrighted uploads.
- **Link rewriting on the way out.** If pages are Postgres rows (T05), stored links must be
  converted to the bundle-relative form so they resolve in Obsidian. Demo ADRs 0015/0017/0021.
- **`index.md` and `log.md` generation** if they are synthesized rather than stored.
- **Conformance check before handing the file over.** OKF §9 is three rules; validating them on
  export is cheap and turns "OKF-conformant" from a claim into a test.
- **Does anything have to be stripped?** `reference_answer` and grounding context must never
  reach a client (`docs/standards/security/web-security.md`), and an export is a client. If T08
  put quiz state in page bodies, this is where it bites.
- **Is export a synchronous download or a background job?**

**Inherited from T05.** Pages are Postgres rows and the bundle is never materialized, so export reads rows
and renders — there is no directory to zip. The link-rewriting bullet is settled: bodies are stored in the
canonical bundle-relative form and the store never rewrites them, so export emits bodies **verbatim**.
Deleted pages are simply absent (no live row). `source_docs:` is `DISTINCT document_id` over `page_sources`.

**Inherited from T06** (amends the line above: there is no `source_docs:` key). Layout is `concepts/` and
`sources/` plus a root `index.md` and `log.md`; no per-directory indexes. Frontmatter is exactly `type`,
`title`, `description`, `timestamp`. Export appends a projected `# Citations` section built from
`page_sources` (Source Summary links; dated labels for conversation sources) and renders supersession markers
inline — so "verbatim" means the stored prose is untouched, not that nothing is rendered around it. `index.md`
comes from the shared index renderer with `okf_version: "0.1"`; `log.md` entry shapes are sketched in T06
decision 7 and are this ticket's to finish. No quiz state, SM-2 data, reference answers or run ids in any file.

**Inherited from T07.** `log.md` projects from `ingest_runs`: `INGEST` runs (rendered **Edit** when the
document's `upload_source` is `CONVERSATION`) and `REVERT` runs, `COMPLETED` only; created and revised counts
are derived from `page_revisions`; `FAILED` runs are omitted. A lesson can have several `Document` versions, all
citing through the same `sources/<lesson-id>` page, so projected Citations are distinct by path. Export reads
committed rows and does not need the ingest lease.

**Inherited from T08.** The stripping bullet is settled by construction. Study data — flashcards, SM-2 state,
quiz sessions, reference answers, grounding excerpts, study events — lives only in its own tables and never in
page bodies or frontmatter, so export has nothing to strip. Do not add a redaction step. Export simply never
reads those tables.

**Inherited from T09–T10.** Export never touches Neo4j or embeddings, because neither exists. `log.md` gains
`* **Lint**: N links added.` for `COMPLETED` `LINT` runs that inserted at least one link. The link check inside ingest is
part of the ingest's own counts. Lint findings and suggestions never reach export.

## Answer

All decisions taken on the recommended option under the user's standing instruction to proceed with
recommendations.

**A synchronous zip download of the wiki layer only, rendered from one consistent snapshot, validated against OKF
§9 before a byte is sent.** No git, no raw sources, no redaction step, no background job, and no new dependency.

```
GET /api/knowledge-bases/{kbId}/export     → 200 application/zip
Content-Disposition: attachment; filename="<kb-name-slug>-okf.zip"

<kb-name-slug>/
├── index.md                  # shared index renderer (T06) + okf_version
├── log.md                    # rendered from ingest_runs (T06, T07, T10)
├── concepts/<name>.md        # frontmatter + body verbatim + supersession notes + projected # Citations
└── sources/<lesson-id>.md
```

### The seven decisions

**1. A zip, not a git repository.** `java.util.zip.ZipOutputStream` is standard library. A git export would mean adding
JGit and synthesizing a commit per run from `page_revisions` — tempting, since the history exists, but it is a
dependency plus a history-rewriting exporter for a user who has not asked to diff their study notes in a terminal.
OKF §3 allows either form, and `log.md` already carries the run-by-run history in readable form.
**Re-add condition:** a user asks for history outside the app, at which point per-run commits from
`page_revisions` is the design.

**2. Raw sources are excluded.** Four reasons, each sufficient:

- **MindForge does not keep them.** `documents.original_content` holds extracted text, and PDF/DOCX bytes are stored
  nowhere (`StoragePort` was never built, T05). Including sources would mean adding binary storage just to re-export it.
- **Size.** Uploads may be 50 MB each (Phase 4.1).
- **Copyright.** A bundle meant to be handed to another person or agent would redistribute third-party material the
  user uploaded for private study.
- **Privacy.** Conversation-turn sources are the user's chat.

Reproducibility is not a goal: import is out of scope. The wiki layer says where it came from through each page's
projected `# Citations`.

**3. Pages render as frontmatter + stored body verbatim + two projections.**

- **Frontmatter** — `type`, `title`, `description`, `timestamp` (T06). Emitted and escaped with SnakeYAML, which
  Spring Boot already ships, so a title containing `: ` or quotes cannot break the YAML.
- **Body** — the stored prose, byte for byte. Links are already canonical bundle-relative paths (T05), so nothing is
  rewritten.
- **Supersession notes** — for each live supersession of a section, a blockquote inserted directly under that heading:
  `> Superseded by [Mejoza](/concepts/mejoza.md).` **Amends T02 decision 8**, which spoke of prefixing the heading:
  the heading text stays untouched, so its anchor (`slugify(heading)`, T06) stays stable for every link into it.
- **`# Citations`** — numbered, projected from `page_sources` (T06):
  - on a `Concept` page, each distinct contributing lesson as `[n] [Lesson title](/sources/<lesson-id>.md)`, and each
    conversation source as `[n] Conversation, 2026-08-30`;
  - on a `Source Summary` page, its own documents as plain text: `[n] biologia-3.pdf, uploaded 2026-09-10`. There is
    nothing to link to (decision 2), and a self-link helps nobody.

**Dangling links ship as they are.** OKF §5.3 requires consumers to tolerate them as not-yet-written knowledge, and
Obsidian renders them as unresolved notes. **Fragments** are MindForge heading slugs; a consumer that anchors by
heading text (Obsidian does) opens the right page at its top. Accepted — rewriting fragments per consumer is the
per-store rewriting T05 removed.

**4. `log.md` vocabulary, final.** Newest date first, `## YYYY-MM-DD` in UTC, one line per `COMPLETED` run that changed
something:

```markdown
# Update Log

## 2026-09-10
* **Ingest**: [Biologia — lekcja 3](/sources/biologia-lekcja-3.md) — 2 created, 5 revised, 1 claim superseded.
* **Edit**: conversation — 1 revised.
* **Lint**: 12 links added.
* **Revert**: undid the ingest of 2026-09-09 ([Biologia — lekcja 2](/sources/biologia-lekcja-2.md)) — 3 pages restored.
```

`FAILED` runs and zero-change runs are omitted. A reverted run keeps its own line — the log is history. Counts are
derived from `page_revisions` (T07). A link to a Source Summary a later revert deleted simply dangles.

**5. Conformance is checked on the rendered files, before sending, and a failure is a server error.** A
`BundleConformanceValidator` (infrastructure, pure function over `Map<path, content>`) re-parses what the renderers
produced — not the rows — and checks:

1. OKF §9 rule 1 — every non-reserved `.md` has parseable YAML frontmatter;
2. §9 rule 2 — every frontmatter has a non-empty `type`;
3. §9 rule 3 — `index.md` has no frontmatter besides root `okf_version` and only `# ` sections with `* [title](url)`
   entries; `log.md` has only `## YYYY-MM-DD` date headings;
4. MindForge's own rules — every path matches `(concepts|sources)/[a-z0-9]+(-[a-z0-9]+)*.md` with no reserved final
   segment (T06).

A violation is a bug in MindForge, never the user's fault. The export returns 500, logs the violations, and **never
ships a non-conformant bundle**. The same validator is the oracle in the exporter's tests, which is what turns
"OKF-conformant" from a claim into a test.

**6. Nothing is stripped, because nothing sensitive is ever read.** T08 put reference answers, grounding, SM-2 state
and study events in their own tables. The exporter reads `wiki_pages`, `page_sources`, `page_supersessions`,
`ingest_runs`, `page_revisions` (counts only) and `documents` (lesson title, filename, upload date only), and nothing
else. There is **no redaction step**: a filter is only as good as the list it remembers, whereas an exporter that never
queries the study tables cannot leak them. `cost`, `step_versions`, `failures` and `findings` on `ingest_runs` are never
selected. The endpoint checks that the caller owns `kbId`, as every controller method must, and every read goes through
`kbId`-first ports (T05).

**7. Synchronous and streamed, from one read-only snapshot.** A few hundred pages of a few kilobytes each renders in well
under a second, so a background job — which would need job state, a place to park the file and a way to notify — buys
nothing. The exporter reads everything in one `@Transactional(readOnly = true, isolation = REPEATABLE_READ)` so a
concurrent ingest commit cannot yield a torn bundle (an index listing a page the zip lacks). It does not take the ingest
lease (T07). Files are rendered in memory, validated, then streamed via `StreamingResponseBody` on a virtual thread.

**Ceiling:** in-memory rendering is fine to tens of megabytes of prose. Past that, validate while streaming, and move to a
background job only if requests start hitting timeouts.

### Feeds

- **T12** — no new dependency: `java.util.zip` and the SnakeYAML already on Spring Boot's classpath. No export code
  exists today.
- **T13** — Export is a new, small phase: `BundleExporter` (the index, log, page and citation renderers shared with the
  in-app index), `BundleConformanceValidator`, the endpoint, and an export button in the SPA. Supersession notes render
  as a blockquote under the heading, in the SPA as well as in export, for the same anchor-stability reason.
