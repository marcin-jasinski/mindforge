---
id: T02
title: What a wiki page is in the domain model
type: grilling
status: closed
assignee: claude
blocked_by: []
---

## Question

The root decision. Today MindForge's knowledge lives in `DocumentArtifact` — one record per
document, holding `SummaryData`, `FlashcardData` and `ConceptMapData` as JSONB, fingerprinted
per step. The wiki model says knowledge lives in *pages* that many documents contribute to and
that later documents revise.

What is a page in `dev.mindforge.domain`?

Things to settle:

- **Is a page markdown-first or record-first?** Either a page is a `String` body plus parsed
  frontmatter (OKF is the native representation, and structure is a parsing concern), or a page
  is a typed domain record that *renders to* OKF markdown (structure is native and markdown is
  a projection). This choice propagates into T05, T12 and T13.
- **What is the OKF `type:` field in domain terms** — an enum, a free string, or a value object?
  OKF says consumers must tolerate unknown types.
- **What survives of the current domain records?** `DocumentArtifact`, `SummaryData`,
  `ConceptMapData`, `FlashcardData`, `StepCheckpoint`, `StepFingerprint`, `ContentBlock`,
  `BlockType`, `Hashes`. Some of these are pipeline plumbing that outlives the change; some are
  the old knowledge model and should go.
- **What is the relationship between `Document` and pages?** A document is no longer the owner
  of its outputs — it is a *source* that contributed to N pages. Is provenance tracked
  (page ← which sources), and if so, does it live in frontmatter, in a join table, or both?
  OKF's `# Citations` convention is the format-level answer; the domain-level one is separate.
- **What is a bundle?** `KnowledgeBase` is the agreed boundary — does it become a domain
  aggregate that owns pages, or stay a thin owner record with pages addressed by bundle id?
- **`index.md` and `log.md`** are reserved OKF filenames, not concepts. Are they domain objects,
  derived views, or storage artifacts?

Constraint: `dev.mindforge.domain` imports JDK only. Whatever a page is, it parses and renders
without a framework, or the parsing lives in infrastructure.

## Answer

A page is a **typed record with an opaque prose body**. Identity, relationships and metadata are
typed fields; the prose the model writes is one `String`. The seam is drawn where the LLM's
authorship ends.

```java
// dev.mindforge.domain.model
record WikiPage(UUID pageId, UUID knowledgeBaseId, String slug, String title,
                PageType type, String markdownBody,   // prose only, no frontmatter
                int revision, Instant createdAt, Instant updatedAt) {}

record PageType(String value) {}                      // non-empty, normalised
record PageLink(UUID pageId, String targetSlug, String fragment) {}
record PageSource(UUID pageId, UUID documentId) {}
record PageRevision(UUID pageId, int revision, String markdownBody, Instant createdAt) {}
record PageSupersession(UUID supersededPageId, String sectionAnchor,
                        UUID supersedingPageId, Instant createdAt) {}

// dev.mindforge.domain.port
interface WikiStore { /* every method takes kbId first */ }
```

### The nine decisions

**1. Neither markdown-first nor record-first — a seam between them.** Identity and relationships
are typed; prose is opaque. Full record-first would model paragraphs, which is where it stops
earning: the LLM writes prose and we would parse it into sections and back on every write. Pure
markdown-first makes every cross-link a regex problem forever, which is the failure T01 §L spent
four ADRs on.

**2. The body is the truth for links; `PageLink` rows are a derived index.** The body stores one
convention — canonical bundle-relative `[Title](/concepts/slug.md)` — and links are parsed out
into rows on write. Resolution happens at **read** time as a left join on `(kbId, targetSlug)`;
nothing is stored resolved and nothing is ever promoted into the text.

The demo's expensive bug (ADR 0021) was not storing links as text, it was *baking resolution into
storage at write time*. Because we never bake, all three of T01's promised wins hold: promote-only-
if-the-target-exists is the join itself, `refresh_stored_links` is unnecessary (a forward reference
goes live the moment its target row appears), and the dangling report is the rows with no match.
`targetSlug` and `fragment` are separate fields, so ADR 0021 §3's anchored-link bug
(`page.md#section`) is unrepresentable.

The rows earn their place independently of links: backlinks, T10's dangling query, and T09's
Cytoscape/Neo4j edge set all read them instead of scanning bodies.

Accepted cost: a slug rename is an N-body rewrite. Findable via the rows, but it makes slug policy
(T06) an identity decision with a real price tag.

**3. `PageType` is a normalising value object.** OKF mandates non-empty and requires consumers to
tolerate unknown types, so a closed `enum` is wrong (it would also foreclose T06's
per-`KnowledgeBase` question, which is still open). A bare `String` carries no invariant and makes
`concept` / `Concept` / `Concpet` three types. The record's compact constructor enforces non-empty
and normalises case, so the invariant lives in one place instead of being re-checked in Ingest,
Lint, Export and the parser. `public static final PageType` constants carry whatever taxonomy T06
settles on.

**4. The body is prose only; frontmatter is projected from typed fields at export.** Every key that
matters (`type`, `title`, `source_docs`, `supersedes` / `superseded_by`) is already a typed field;
storing it in the body too guarantees drift, and the drifting copy would be the one the model wrote.
The demo needed `clean_frontmatter` as a choke point for exactly this.

This is T01 §24 generalised: **the model does not get to assert page metadata in prose it authored.**
The Ingest transaction knows the type, the sources and the supersession; the model contributes prose.

Provenance therefore falls out as a `PageSource` join, with `source_docs:` rendered from it on export
— the queryable form T07's retraction story needs, joining to `Document` as a flat verbatim string
never could.

Note the deliberate inversion against decision 2: **links are truth in the body because the model
writes them mid-sentence; metadata is truth in rows because code assigns it.**

**5. `KnowledgeBase` stays a thin owner record; it does not become an aggregate owning pages.**
The aggregate buys one invariant — slug uniqueness — that `UNIQUE (knowledge_base_id, slug)`
enforces better, under concurrency, without loading a bundle to touch one page. Pages carry
`knowledgeBaseId` and are reached through `WikiStore`.

Security payoff: **every `WikiStore` method takes `kbId` as its first argument**, so the ownership
check `CLAUDE.md` mandates on every controller method has a structural place to sit rather than
being a discipline each query has to remember. `KnowledgeBase` gains `pageCount` beside
`documentCount` and is otherwise unchanged.

**6. `index.md` and `log.md` are projections, not rows.** The index is rendered from the page table;
the log is rendered from ingest-run records. The export adapter emits both as files, and T06's slug
policy reserves both names so no `WikiPage` can claim them.

T01 §7 predicted this: once the index is a query, `ensure_index_entries` evaporates — and with it the
anti-clobber guard, the "dropping a link to a page that exists is rejected" rule, and the whole class
of bug where the catalog disagrees with the wiki. Same for the log: MindForge already has pipeline
checkpoints and an outbox, and a second model-writable audit trail is precisely the divergence
T01 §24 warns about.

Bonus for T09: an index rendered fresh per Operation and handed to the model as a tool result is
structurally incapable of being stale, which is most of what the demo's mechanical guards bought.

Cost accepted: a user hand-editing `index.md` in an exported bundle and re-importing is ignored.
Import is out of scope.

**7. A page is mutable, with `PageRevision` beside it — not the head of a revision chain.** Reads
dominate: Query, the SPA, link resolution and export all want the current body, and the chain form
taxes every one of them to make rollback elegant. `revision` on the page doubles as the cache
invalidation key T08 asked for.

T03 (gating) and T05 (storage) are untouched by this; both branches of T03 need somewhere to put a
body that is not live yet or not live any more, so naming the revision is safe either way. Retention
policy (keep all, keep N) belongs to T05, where the storage cost is visible.

**8. Supersession is a relation only — the superseded page's prose is never mutated.** The demo
prefixes `[SUPERSEDED] ` onto the corrected heading by string substitution; we store
`PageSupersession` and render the marker at read and export time by matching `sectionAnchor`.

This turns the riskiest write in the system — editing a page the user has already read and trusted,
the one write the demo had to human-gate — into a row insert, which is far easier to gate, roll back,
or get wrong harmlessly. It is also queryable, which T08 needs directly: a flashcard cut from a
superseded claim teaches something false, and skipping those must be a join, not a scan for a magic
prefix. One row answers both directions, so the demo's two flat scalars collapse — they existed
because flat YAML cannot join.

`sectionAnchor` is the heading's **slug**, not its text. If a heading later vanishes the supersession
dangles: reported, not deleted — the same shape and the same handling as a dangling link.

**No `status` field on `WikiPage`.** The demo kept supersession orthogonal to `status` so one field
would not mean different things depending on which Operation wrote it last. With Verify out of scope
there is no second axis, so the field has nothing left to hold.

**9. `DocumentArtifact` dies outright; it does not become the ingest record.**

- **Dies**: `DocumentArtifact`, `SummaryData`, `ConceptMapData`. A summary becomes a page of type
  `Source Summary`; a concept map becomes the cross-link graph (T09); the artifact itself *is* the
  one-document-owns-its-outputs assumption this map inverts.
- **Survives**: `Document`, `ContentBlock`, `BlockType`, `ContentHash`, `Hashes`, `LessonIdentity`.
  Parsing a document into ordered blocks is untouched — and T01 §20 makes document order
  load-bearing, so `ContentBlock` matters more, not less.
- **Not this ticket's**: `StepCheckpoint` / `StepFingerprint` go to T07. `FlashcardData`'s shape goes
  to T08; only its home changes here, as it stops being a field on an artifact.

The record of what one run did — which pages it touched, whether it wrote anything, what it cost — is
an **ingest run**, a new entity whose shape T04 and T07 decide. It belongs to a run, not a document
(a re-ingest is a second run over the same document). This is what makes T01 §24 enforceable: a run
that produced no page must fail the step, and what was written is counted from the rows the
transaction inserted, never from the model's own report. Renaming a record whose every field died is
how a table comes to half-mean the old thing for two years.

### Feeds

- **T04** — the ingest-run entity is its to shape; the read-only-source / writable-pages split is a
  type boundary here, not a path check.
- **T05** — `WikiStore`'s surface speaks pages, `kbId` first; `PageRevision` retention is its call.
- **T06** — slug policy is now an identity decision with a rename cost, and must reserve `index` and
  `log`. `PageType` constants are its output.
- **T07** — `PageSource` is the retraction handle; the run record is the fingerprint replacement.
- **T08** — skipping superseded claims is a `PageSupersession` join; `revision` is the cache key.
- **T09** — `PageLink` rows are the edge set; the rendered index is the retrieval surface.
- **T11** — export renders frontmatter, index and log, and emits the body verbatim.
- **T12** — decision 9's three-way split is the start of its inventory table.
