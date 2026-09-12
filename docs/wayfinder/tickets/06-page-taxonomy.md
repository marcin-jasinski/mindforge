---
id: T06
title: The page taxonomy and bundle layout
type: grilling
status: closed
assignee: claude
blocked_by: [T02]
---

## Question

OKF requires a non-empty `type:` and deliberately refuses to define a taxonomy — that is the
producer's job. The demo pushes it into a per-wiki conventions doc (`AGENTS.md`) that the wiki
owner writes, precisely so it is *not* baked into the agent.

MindForge is a product, not an agent you point at your own repo. Decide:

- **What `type:` values does MindForge emit?** For a learning domain the candidates are things
  like `Concept`, `Source Summary`, `Topic`, `Comparison`, `Question`. Fixed set or open?
- **What directories does a bundle have?** OKF's structure is domain-independent; something has
  to choose. `concepts/`, `sources/`, `topics/`?
- **Is the taxonomy fixed by MindForge or per-`KnowledgeBase`?** A user learning organic
  chemistry and a user learning Spanish grammar want different page kinds. But a fixed taxonomy
  is what lets the quiz generator (T08) and Query (T09) know what they are looking at.
  If per-KnowledgeBase, this graduates the prompt-layer fog: something must carry the
  taxonomy into the Ingest prompt.
- **Title and slug rules**, and therefore Concept IDs — OKF's Concept ID is the file path minus
  `.md`, so slug policy is an identity decision, not cosmetics.
- **What goes in `index.md`** and whether it is generated or maintained. It is MindForge's
  retrieval index if T09 goes that way, which makes its shape load-bearing.
- **What goes in `log.md`** and whether users ever see it.
- **Frontmatter beyond the OKF minimum.** OKF lets producers add keys; consumers must preserve
  unknowns. Provenance, quiz-generation state and SM-2 anchors are candidates — but frontmatter
  a *foreign* consumer cannot act on is arguably database state that leaked into the format.


**Inherited from T02.** The slug is page identity *and* the stored form of every inbound link (link
targets live as slugs inside page bodies), so a rename is an N-body rewrite — slug policy carries a
real price tag, not a cosmetic one. `index` and `log` must be reserved names. `PageType` is a
normalising value object; this ticket produces its constants and decides fixed-vs-per-`KnowledgeBase`.
Supersession anchors to a **heading slug**, so heading-slug rules belong here too.

**Inherited from T04.** Slug and taxonomy policy is now a **retrieval** input, not only an identity
decision. T04's Resolve step matches extracted claims against existing pages to decide create-or-revise,
so a taxonomy that makes pages hard to match produces duplicate pages on every ingest — the compounding
failure mode. Judge candidate taxonomies by how well Resolve can hit them, not only by how well they
export.

**Inherited from T05.** Slugs are unique per bundle — `UNIQUE (knowledge_base_id, slug)` — not per
directory. The canonical link form `[Title](/dir/slug.md)` still carries a directory, so decide whether the
directory is identity or decoration: if it is derived from `type`, a type change leaves a stale path in
every inbound body. Revisions snapshot title and type but not slug, so a rename is not revertible by run
revert — another reason renames need a rule here.

## Answer

All decisions taken on the recommended option under the user's standing instruction to proceed with
recommendations.

**Two page types, fixed by MindForge and assigned by code. A page's path is its identity, is derived once
from its title, and never changes.** The model never picks a type, never picks a path, and never writes
metadata; it writes a title, a one-sentence description and prose.

```
bundle/
├── index.md                 # projected: okf_version + one section per type
├── log.md                   # projected from ingest runs
├── concepts/<name>.md       # type: Concept         — one page per idea the KB teaches
└── sources/<lesson-id>.md   # type: Source Summary  — one page per uploaded document
```

```java
record WikiPage(UUID pageId, UUID knowledgeBaseId, String path, String title, String description,
                PageType type, String markdownBody, int revision, Instant createdAt, Instant updatedAt) {}
record PageLink(UUID pageId, String targetPath, String fragment) {}
// path: "concepts/mitoza" — T02/T05's `slug` is renamed; column `path`, `page_links.target_path`
// PageRevision additionally snapshots `description`
```

### The eight decisions

**1. Fixed taxonomy, not per-`KnowledgeBase`: `Concept` and `Source Summary`.**

- **`Source Summary`** — exactly one per uploaded `Document`, created by the pipeline for that document. It is
  where the old `SummaryData` went (T02). A conversation turn (T03) is not a source of knowledge and gets no
  Source Summary page.
- **`Concept`** — every other page Ingest writes: a term, process, rule, person, event, theorem, grammatical
  construction. "Concept" in a learning platform means *a thing the knowledge base teaches*, which is broad
  enough for organic chemistry and Spanish grammar alike.

**Type is assigned by code, never by the model.** Resolve (code, T04) creates Concepts from claims; the pipeline
creates the document's Source Summary. That takes classification out of the LLM entirely — no taxonomy in the
prompt, no misclassified pages, no "is *Cell Division* a Topic or a Concept?" ambiguity for Resolve to trip
over. It is the decisive argument against a per-`KnowledgeBase` taxonomy, which would reintroduce a model
classification call on every page, a conventions layer to carry it into the prompt, a settings surface to edit
it, and a T08/T09 that can no longer know what it is reading. The demo pushed taxonomy into `AGENTS.md`
because it was an agent you point at your own repo; MindForge is a product, and its users are learning a
subject, not designing a schema.

Judged by T04's criterion — how well Resolve can hit it — one knowledge kind is the best possible taxonomy: a
claim about mitosis has exactly one place to land. Hierarchy (*Cell Division* → *Mitosis*, *Meiosis*) is
expressed by links, which is what OKF §5.3 says links are for; a hub is a Concept with many outbound links.

`PageType` stays a normalising value object that tolerates unknown values on read (OKF §4.1), with two
constants. **A new type is added only by the ticket that adds a producer for it** — if T09 files Query answers
back, T09 adds their type and directory.

Rejected: `Topic`, `Comparison`, `Question`, `Entity`. Each is a second place a claim could land, i.e. a
duplicate-page generator for Resolve, and none has a producer yet.

**2. The path is the page's identity and equals the OKF Concept ID.** `WikiPage.path` is the bundle-relative
path without `.md` — `concepts/mitoza`, `sources/biologia-lekcja-3`. It replaces T02/T05's `slug`, renamed
because "slug" conventionally means one segment, and because "Concept ID" collides with the `Concept` type in
MindForge's own language. `UNIQUE (knowledge_base_id, path)` is T05's constraint unchanged in meaning.

- **The directory is part of identity, not decoration**, and is fixed by type (`Concept` → `concepts/`,
  `Source Summary` → `sources/`). So a concept and a source summary with the same name never collide.
- **Type is immutable after creation.** With the directory inside the path, T05's stale-path worry cannot
  arise: nothing ever moves a page between directories.
- **Body links use the full path**, `[Mitoza](/concepts/mitoza.md#faza-anafazy)`, parsed into `target_path` and
  `fragment`. The Write step already receives the linkable index (T04), so the model copies exact paths. A
  link with the wrong directory is a dangling link — reported by T10, never "corrected" by guessing, which is
  the dead-link-laundering failure of demo ADR 0021.

**3. Paths are derived from the title by code, once, at creation — and never change.**

- **Concept**: `concepts/` + `slugify(title)`. **Source Summary**: `sources/` + the document's `lessonId`, which is
  already unique per `KnowledgeBase` (`idx_documents_kb_lesson`) and already code-assigned.
- **`slugify`**: Unicode NFD, strip combining marks, map the letters that do not decompose (`ł→l`, `ø→o`,
  `ß→ss`, `đ→d`), lowercase with `Locale.ROOT`, collapse `[^a-z0-9]+` to `-`, trim hyphens, cap at 80. Hyphens
  only. `mitoza-komorkowa`, not `mitoza-kom-rkowa` — which is what `LessonIdentity.slugify` produces today for
  "Mitoza komórkowa", so it must share this function (T12).
- **Reserved**: a final segment of `index` or `log` is rejected (OKF §3.1 reserves them at every level).
- **No rename operation exists.** A revision may change a page's `title` freely — titles are snapshotted
  (T05) — but the path stays where creation put it. T02's accepted cost, "a rename is an N-body rewrite", is
  therefore never paid, because a rename is never performed. A page whose title drifted far from its path is
  cosmetic; a page whose path moved is N broken bodies.
- **An exact path match is the same page.** If Resolve would create `concepts/komorka` and that path exists,
  it revises instead. Homonyms inside one knowledge base (a *cell* in biology and in a spreadsheet) merge into
  one page whose prose disambiguates — accepted; one KB is one subject far more often than not. Synonyms that
  slugify differently are Resolve's retrieval problem (T09), not a slug rule.

**4. Titles and descriptions are model output, in the knowledge base's language; everything else is protocol
in English.**

- `title` names **the thing, not the document** (demo `AGENTS.md`'s hardest-won rule); a Source Summary's title
  is the document's `lessonTitle`. Two live pages must not share a title — a Source Summary about *Mitoza*
  is titled for its document.
- **New field: `description`** — one sentence, a typed output of the Write step alongside the body, snapshotted
  in `PageRevision`. It is what makes the rendered index usable as the retrieval surface Resolve and Query read
  (T02 decision 6, T09); an index of bare titles is a much weaker match target. It is authored content, not
  metadata, so it does not breach T02 decision 4 — code still owns type, path, sources and supersession.
- `type` values, directory names, frontmatter keys and the headings of `index.md`/`log.md` are English and
  fixed: they are what OKF consumers route on.
- **Polish pages are OKF-conformant.** Conformance (§9) is frontmatter shape, a non-empty `type`, and reserved
  file structure — not language. Transliterated ASCII paths keep links portable to any filesystem. This settles
  the conformance half of the prompt-layer fog.

**5. Headings: top-level `#` sections, anchored by the same `slugify`.** A body opens at its first section and
never repeats the title as a heading (the demo's scar: the title renders twice). A heading's anchor is
`slugify(heading text)` — used by link fragments and by `PageSupersession.sectionAnchor` (T02). On duplicate
headings in one page, the first match wins; numbering suffixes are machinery for a case prompts avoid.
**No required section names**: section conventions are prompt content, not taxonomy, and belong to the prompt
layer.

**6. `index.md` — one root index, projected, identical in-app and in export.**

```markdown
---
okf_version: "0.1"
---
# Concepts

* [Mitoza](/concepts/mitoza.md) - Podział jądra komórkowego dający dwie identyczne komórki potomne.

# Sources

* [Biologia — lekcja 3](/sources/biologia-lekcja-3.md) - …
```

One section per type, entries sorted by title, each `* [title](/path.md) - description` per OKF §6. The root
frontmatter carries only `okf_version` (§11 permits it there alone). **No per-directory `index.md`** — two
directories do not need progressive disclosure. The *same renderer* produces the export file (T11) and the
linkable index handed to the Write step (T04) and Query (T09), so the model and the user's download can never
disagree about what the bundle contains. Deleted pages are absent because their row is (T05).

**7. `log.md` — projected from ingest runs, newest first, export only.** Date-grouped per OKF §7, one line per
run that changed something:

```markdown
# Update Log

## 2026-09-10
* **Ingest**: [Biologia — lekcja 3](/sources/biologia-lekcja-3.md) — 2 created, 5 revised, 1 claim superseded.
* **Edit**: conversation — 1 revised.
* **Revert**: ingest of 2026-09-09 — 3 pages restored.
```

Failed runs (zero pages, T04) changed nothing and are not logged; partial runs are, with their counts; a
reverted run keeps its line and gains a **Revert** line — the log is history, not current state. **Users do not
see `log.md` in the app**: the SPA shows run reports from the same rows (SPA fog). The exact entry vocabulary
beyond these three leading words is T11's to polish, and depends on T07's run record and T10's Lint runs.

**8. Frontmatter: the OKF recommended keys only — no producer extensions. Citations are projected.**

```yaml
---
type: Concept
title: Mitoza
description: Podział jądra komórkowego dający dwie identyczne komórki potomne.
timestamp: 2026-09-10T14:30:00Z
---
```

- **`# Citations` is projected from `page_sources` at export**, appended after the body, each entry linking the
  contributing document's Source Summary page (conversation-sourced contributions render as a dated label).
  The Write prompt forbids the model from writing a Citations section. This is T02 decision 4 applied to
  provenance: truth in rows, rendered on the way out — and it uses the OKF §8 convention a foreign consumer
  already understands, rather than a producer key none does.
- **Amends T02**, which named a `source_docs:` frontmatter key: dropped, because it would duplicate the
  projected Citations section in a form nobody reads.
- **No `supersedes` / `superseded_by` keys.** The superseded-section marker renders inline at the heading, with a
  link to the superseding page (T02 decision 8) — the place a reader actually needs it.
- **Never in frontmatter or body**: revision numbers, quiz state, SM-2 scheduling, reference answers, run ids.
  That is database state leaking into a portable format, and in the reference-answer case a security breach
  (T08, T11).
- No `tags` (nothing produces them) and no `resource` (a learning concept describes an idea, not an asset).

### Feeds

- **T07** — a re-upload of the same `lessonId` lands on the same `sources/<lesson-id>` path, so whether it
  *revises* that Source Summary or is skipped is T07's call; the path identity supports either.
- **T08** — only `Concept` pages are study material; a Source Summary is a digest of one upload, not knowledge
  in its own right. There is no `Topic` type to scope by, so scope is a page, a link neighbourhood, a source's
  contributed pages, or the whole bundle. Flashcards and quizzes are **not** wiki pages and get no type.
- **T09** — the rendered root index (title + description per page) is the retrieval surface; synonym matching
  across different slugs is retrieval's job. If Query files answers back, T09 adds their type and directory.
- **T10** — findings now include: links whose `target_path` has the wrong directory (dangling, never guessed),
  and titles duplicated across live pages. Lint cannot rename, because nothing can.
- **T11** — renders frontmatter (four keys), the projected `# Citations`, inline supersession markers,
  `index.md` and `log.md` as above; conformance check is §9's three rules plus "every path segment is
  non-reserved".
- **T12** — `LessonIdentity.slugify` must adopt the transliterating `slugify` (it mangles Polish today) and
  reject `log` as well as `index`. `WikiPage` gains `path` (renamed from `slug`) and `description`.
- **Prompt-layer fog** — narrowed: no per-`KnowledgeBase` conventions page (the taxonomy is fixed and assigned by
  code), and Polish pages are OKF-conformant. What remains is prompt versioning's home and section conventions.
