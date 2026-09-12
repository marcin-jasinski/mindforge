# Retrieval is the rendered index; Neo4j and pgvector are removed

The retrieval system is the wiki's rendered index — one line per live page carrying its path,
title and one-sentence description — handed to the model, which chooses the pages that matter.
Extract uses it to propose a target page per claim; Query uses it to select pages before
answering. The cross-link graph is `page_links` in PostgreSQL. This supersedes
[ADR 0005](0005-pgvector-no-dedicated-vector-db.md) and [ADR 0006](0006-neo4j-derived-projection.md).

Lexical-first retrieval fails MindForge specifically. It is Polish by default, and PostgreSQL
ships no Polish stemming dictionary; no lexical tier equates a synonym with a page title. In a
wiki that compounds, every retrieval miss is a **duplicate page**. A model reading titles and
descriptions handles inflection and synonyms, at ~30 tokens per page.

Named ceilings instead of "forever":

- **Lexical prefilter** — past 20K tokens of index (~600 pages), `pg_trgm` on title and description
  plus `simple` full-text search on bodies narrows the index before it enters a prompt.
- **Vector search** returns when a knowledge base is past that ceiling and measurement shows the
  prefilter missing pages the full index would have chosen.
- **A graph store** returns for a traversal deeper than two hops, on a user-facing path, that a
  recursive CTE measurably cannot serve in time.

## Consequences

- Phase 7 (Neo4j), `GraphIndexer`, the backfill CLI, `content_embeddings`, the `vector` extension
  and `AIGateway.embed` are deleted.
- Weak-page study targeting is SQL over `study_events` and 1-hop `page_links`.
- Query never files answers back into the wiki. "Save that" is a conversation edit.

Decided in [T09](../wayfinder/tickets/09-query-retrieval-neo4j.md).

## Amendments

2026-09-12, from the spec review ([T23](../wayfinder/tickets/23-extract-long-documents.md)): tokens are estimated as
characters ÷ 3 in one domain function. Crossing the 20K ceiling logs a warning and shows in the knowledge base's health
view; nothing truncates the index before the prefilter exists.
