---
id: T09
title: What Query does to pgvector and Neo4j
type: grilling
status: closed
assignee: claude
blocked_by: [T02, T06]
---

## Question

MindForge's retrieval discipline is graph first → lexical second → vector last. Phase 7 builds
Neo4j; Phase 11 builds pgvector semantic search and multi-turn RAG chat.

The LLM Wiki claim is that at moderate scale (~100 sources, hundreds of pages) `index.md` plus
targeted reads *is* the retrieval system, and embedding infrastructure is unnecessary. The demo
ships no vector store at all — it greps.

Decide:

- **Does Query replace Phase 11's conversational RAG?** Query reads curated pages rather than
  raw chunks, which is cheaper and better-grounded. Multi-turn, `TokenBudget` and
  grounding-context redaction still apply either way.
- **Does pgvector survive?** Migrations V1–V7 already installed it (feeds T12). If Query is
  index-driven, embeddings may be dead weight — or the fallback when a bundle outgrows its index.
  Name the scale at which you would add it back rather than deciding forever.
- **Does Neo4j survive, and as a projection of what?** Cross-links are already a graph. Neo4j
  would project page links instead of a per-document `conceptMap` — which is a smaller,
  better-defined job than Phase 7 currently describes. Or Cytoscape reads links straight from
  the bundle and Neo4j goes.
- **Is `ConceptMapperAgent` dead?** Almost certainly — cross-linking is Ingest's job now.
  Confirm and record it, because Phase 6 lists it as one of seven agents.
- **Does Query file its answers back into the wiki?** The spec argues yes and the demo does it with a
  `y/n` gate. T03 removed gating, so if Query writes, it writes automatically — meaning it needs a run
  record and revert like any other write, and Query stops being a pure read path. Weigh that here.
- **Citations.** Query answers cite pages; pages cite sources via OKF `# Citations`. Decide
  whether a Query answer can cite through to the original upload.

**Inherited from T04.** `ConceptMapperAgent` is **dead** — confirmed, not still open. Cross-linking is what
Ingest is now, and the graph is `PageLink` rows.

The bigger inheritance: retrieval is no longer only the read path. T04's Resolve step matches claims
against existing pages to decide create-or-revise, which puts Phase 7/11 retrieval **on the ingest critical
path**. Retrieval quality now determines whether the wiki compounds or accretes duplicates, and retrieval
latency is inside every upload's completion time. Weigh the graph-first / lexical / vector ordering against
that load, not only against Query.

**Inherited from T05.** Page bodies are Postgres `TEXT`, so full-text search over `wiki_pages` is available to
Resolve and Query's lexical tier without a new store, and `page_links (knowledge_base_id, target_slug)` is
indexed for a graph tier.

**Inherited from T06.** The retrieval surface is one projected root index — `* [title](/path.md) - description`
per page, sectioned by type — rendered by the same code that writes export's `index.md` and the Write step's
linkable index. Pages gained a one-sentence `description` for exactly this. Links store `target_path` (T06
renamed `slug` → `path`, e.g. `concepts/mitoza`). An exact path match is the same page; **synonyms that
slugify differently are this ticket's problem**, on the ingest critical path via Resolve. If Query files
answers back, this ticket adds their page type and directory — T06 left none for them.

**Inherited from T07.** There is no outbox table. A Neo4j projection, if it survives, is fed by
`@TransactionalEventListener(AFTER_COMMIT)` on `IngestRunCompleted` and rebuilt from `page_links`, and must
tolerate a missed event. `DomainEvent.GraphProjectionUpdated` is this ticket's to keep or delete. If Query files
answers back, that is a page-writing run: it takes the `knowledge_bases.active_run_id` lease and queues behind
any running ingest.

**Inherited from T08.** Study no longer needs a graph store. Weak-page targeting is SQL over `study_events` and
1-hop `page_links`, and Phase 10's `RetrievalPort.findWeakConcepts()` and "Graph RAG question targeting" are
gone. Neo4j must justify itself on Query and the graph view alone. A natural-language study scope ("quiz me on
the Krebs cycle") waits for Query to resolve a topic to pages.

## Answer

All decisions taken on the recommended option under the user's standing instruction to proceed with
recommendations.

**The rendered index is the retrieval system. Neo4j and pgvector both go. Query replaces Phase 11's RAG chat, is a
pure read path, and never files answers back.** Retrieval is the LLM Wiki's claim taken at its word — hand the model
the catalog, let it pick pages, load them — with a lexical prefilter held in reserve for the day a knowledge base
outgrows its index budget.

```
Query turn:   index + question + prior turns ──SMALL──▶ chosen paths ──code──▶ bodies (+1-hop links) within TokenBudget
                                                                                 ──LARGE──▶ answer citing page paths
Ingest:       blocks + index ──Extract (LARGE)──▶ claims, each with a proposed target path or "new: <title>"
                                   ──Resolve (code)──▶ verified PageWriteTasks
```

### The eight decisions

**1. Retrieval is the index handed to the model, not a graph-first / lexical / vector cascade.**

The projected root index (T06: `* [title](/path.md) - description`, one line per live page) is what the model reads
to decide which pages matter. It is rendered fresh per call, so it cannot be stale (T02 decision 6).

- **Why not lexical-first.** MindForge is Polish by default, and Postgres ships no Polish text-search dictionary:
  `to_tsvector('polish', …)` does not exist without installing ispell files. A `simple` configuration cannot equate
  *mitozy* with *mitoza*, and no lexical tier equates *podział mitotyczny* with *Mitoza*. On T04's own criterion
  every such miss is a **duplicate page**, the compounding failure mode. A model reading titles and descriptions
  handles inflection and synonyms natively.
- **Cost.** About 30 tokens a line, so a 300-page knowledge base is ~9K tokens of index — SMALL-tier cheap per Query
  turn, and marginal inside an Extract call that already carries the whole document.

**This refines T04's Resolve step without adding an LLM call.** Extract already sees the document; it now also sees
the index, and returns each claim with a **proposed target path** (an existing page) or `new: <title>`. Resolve stays
code, as T04 ruled: it verifies every proposed path exists (a hallucinated path becomes a create, never a write to a
page that is not there), derives paths for new titles (T06), collapses two claims proposing the same new path into
one task, and applies T06's exact-path rule — a "new" title whose path already exists is a revision. Matching
judgment moves into a call that was already being made; no navigation, no iteration. It is T04's own rejected
alternative, retrieval-as-a-second-call, avoided rather than adopted.

**Named ceiling — the lexical prefilter.** When a knowledge base's rendered index exceeds **20K tokens** (roughly
600 pages), code prefilters the index before it goes into a prompt:

- `pg_trgm` similarity on title and description, which tolerates Polish inflection far better than a stemmer-less
  full-text search;
- plus `simple` full-text search on bodies, against the question or the document's headings;
- producing a top-N slice.

Build it when a knowledge base crosses the line, not before. Nothing that exists today is near it.

**2. pgvector goes.** V6's extension and V7's `content_embeddings` are dropped (T12). Nothing reads embeddings: the
index covers matching below the ceiling, and trigram covers the prefilter above it. The re-add condition is named,
not "forever": **when a knowledge base is past the prefilter ceiling and measurement shows the trigram slice
missing pages that the full index would have chosen** — duplicate pages traced to prefilter misses, or Query answers
missing a page that exists. ADR 0005 is superseded.

**3. Neo4j goes. The graph is `page_links`, in Postgres.**

Every job Phase 7 gave Neo4j is gone or is SQL now:

- **Weak-concept detection** is SQL over `study_events` (T08).
- **Concept neighbourhoods** are 1-hop `page_links` joins.
- **The concept map** is `ConceptMapperAgent`'s output, and that agent is dead (T04).
- **The graph view** is one query: live pages plus `page_links` for a knowledge base.

ADR 0006 chose Neo4j for multi-hop Cypher over `WITH RECURSIVE`. That trade-off does not bite at hundreds of pages and
1–2 hops, and no hot path needs deeper.

Deleted with it: a container, `testcontainers-neo4j`, Phase 7 entirely, `GraphIndexer`, `RetrievalPort` in its Phase 7
shape, `DomainEvent.GraphProjectionUpdated`, Phase 8.2's `GraphIndexingListener`, and Phase 15.2's backfill CLI.
`architecture.md`'s "Neo4j — optional, degrades gracefully" row goes too. ADR 0006 is superseded.

**Re-add condition:** a traversal deeper than two hops on a user-facing path, where a recursive CTE measurably misses
its latency budget.

**4. Query replaces Phase 11's conversational RAG. Two single-shot calls per turn.**

1. **Select** (SMALL): the index, the question and the prior turns in, a list of paths out.
2. **Load** (code): verify the paths are live, load bodies with superseded sections marked (T02), and add 1-hop link
   neighbours while `TokenBudget` allows.
3. **Answer** (LARGE): cite pages by path.

`AIGateway`, `DeadlineProfile.INTERACTIVE` and `TokenBudget` carry over exactly as Phase 11 planned them.
`Interaction` / `InteractionTurn` stay, with `usedConcepts` renamed `usedPagePaths` and redacted from `listForUser` as
before. `WeakConcept` is deleted (T08's weak page replaces it). `SearchService` becomes **page search** for the SPA's
page browser, and it is the same trigram/full-text code as the prefilter — one lexical function, two callers.
`ConceptMapperAgent`: **dead**, re-confirmed.

**5. Query does not file answers back.** A filing Query would be a page-writing run. It would need the lease, and so
queue a chat reply behind a multi-minute ingest (T07); a run record and revert; a new page type and directory (T06);
and a Resolve pass over its own output.

The valuable case is already covered by T03's channel at zero new machinery. The user says *"save that to the
wiki"*; that turn becomes a `CONVERSATION` document carrying the answer; Ingest integrates it into the pages it
belongs in, with provenance *"because you saved it on 2026-09-10"*. The answer does not become a page of type *Query
Answer* — which is the right outcome for a compounding wiki, not merely the cheap one. Query stays a pure read path:
no lease, no run, no write.

**6. Citations stop at pages.** A Query answer cites the pages it was grounded on, rendered as SPA links. Each page
shows its sources one click away (T06's projected Citations, from `page_sources`). The answer does **not** claim a
specific upload supports a specific sentence: prose is opaque (T02) and there is no per-sentence provenance (T07), so
citing through to an upload would be provenance the system does not have. The honest chain is answer → page →
sources.

**7. No cache in front of the wiki.** Rendering the index is one indexed query over a few hundred rows, and bodies are
primary-key reads. Caffeine stays exactly where Phase 10.2 already put it — quiz sessions. Add a cache keyed on
`(kbId, max(updated_at))` if index rendering ever shows up in a profile. This closes the map's Caffeine fog.

**8. The CLI and bot phases shift only at their edges.** Phase 15.1 (pipeline CLI) and 15.3 (quiz CLI) stand; 15.2
(Neo4j backfill) is deleted. Phases 18 and 19 replace `/search` and `/mf-search` with `/ask` (and `/mf-ask`) backed by
Query. Uploads and quizzes are unchanged, and every command still delegates to the same application services as the
web UI. This closes the map's CLI/Discord/Slack fog.

### Feeds

- **T10** — orphans (no inbound `page_links`), dangling links and duplicate titles are SQL. A Lint that needs judgment
  gets the same rendered index as Extract and Query, and is bounded by the same 20K-token ceiling.
- **T11** — nothing: export was never going to read Neo4j or embeddings.
- **T12** — **delete**: V6, V7, `ContentEmbeddingEntity` and anything embedding- or Neo4j-shaped in code, the Neo4j
  and pgvector dependencies in `pom.xml`, the Neo4j Testcontainers module, the Compose service and
  `GraphProjectionUpdated`. **Change**: `Interaction` / `InteractionTurn` are planned rather than built, so no code
  change. **New**: `pg_trgm` only when the prefilter ceiling is crossed — not in the baseline.
- **T13** — Phase 7 deleted; Phase 11 re-cut to **Query**; 15.2 deleted; 18/19 `/search` → `/ask`; 8.2 deleted. ADRs
  0005 and 0006 are marked superseded by a new retrieval ADR. `CLAUDE.md`'s and `hexagonal.md`'s "graph first →
  lexical second → vector last" is replaced by "the rendered index first; a lexical prefilter past 20K tokens; no
  vector store". `tech-stack.md` and `architecture.md` lose Neo4j and pgvector. Extract's prompt gains the index and
  per-claim target paths (the T04 refinement above).
