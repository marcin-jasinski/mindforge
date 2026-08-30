---
id: T09
title: What Query does to pgvector and Neo4j
type: grilling
status: open
assignee:
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

## Answer
