# Ingest is a typed pipeline with page fan-out, and the Agent abstraction is deleted

Ingest runs as a fixed sequence of single-shot calls:

```
Preprocess → RelevanceGuard → Extract → Resolve (code) → Write (×N) → Link check → commit → Supersede
```

The model keeps every judgment call and makes no navigation calls. Extract reads the rendered
index and proposes a target page per claim, Resolve verifies it in code, and Lint buys back the
self-correction a tool loop would have had. Every call stays single-shot, so `AIGateway`,
`DeadlineProfile`, `CostTier` and the Resilience4j configuration are unchanged.

The `Agent` / `AgentContext` / `AgentResult` / `AgentCapability` abstraction and the planned
registry and DAG are **deleted**. Ingest calls a handful of concrete services with concrete
signatures in a known order; an interface whose every caller knows the implementation it holds
is a tax, not a seam. `ParserRegistry` stays as the one genuinely open extension point, because
new file formats arrive from outside the design.

## Considered Options

- **Tool-calling agent loop** (the reference implementation): chosen there because watching the
  model decide was the point of a talk. It costs unpredictable iteration, a re-cut gateway, and
  a guard inventory (iteration budgets, nudges, path checks) that the pipeline makes
  unrepresentable.
- **Keep `Agent` with typed inputs and outputs**: strictly better than before, but still a
  registry and capability records for services that never vary.

## Consequences

- Bodies are generated outside a transaction and committed in one. Partial success lands; zero
  pages fails the run. A claim cap at Extract fails loudly rather than truncating.
- Source immutability is a type: Write takes claims and returns a body.
- `SummarizerAgent` and `ConceptMapperAgent` disappear. Flashcard and quiz generation become
  on-demand services.

Decided in [T04](../wayfinder/tickets/04-ingest-execution-model.md), refined by
[T09](../wayfinder/tickets/09-query-retrieval-neo4j.md) and [T10](../wayfinder/tickets/10-lint-operation.md).

## Amendments

2026-09-12, from the spec review:

- **Extract runs once per heading-aware chunk, sequentially**, each call seeing the index and the pages earlier chunks
  planned. Two caps fail loudly: claims per call and page tasks per run
  ([T23](../wayfinder/tickets/23-extract-long-documents.md)).
- **Resolve groups claims into one task per final path** and accepts only live Concepts as targets; every claim carries a
  title ([T22](../wayfinder/tickets/22-resolve-and-title-rules.md)).
- **A conversation turn enters at Extract** through an edit prompt returning claims, deletions and retitles
  ([T15](../wayfinder/tickets/15-conversation-edit-entry.md)).
- **Supersede reads the sections of Concepts one link away** from what the run wrote, and code verifies every proposal
  against exactly those candidates ([T14](../wayfinder/tickets/14-supersession-inputs-and-guards.md)).
- `Preprocessor` is plain application code, not a model service
  ([T28](../wayfinder/tickets/28-minor-review-findings.md)). The per-run write semaphore becomes one global permit pool
  for background calls ([T17](../wayfinder/tickets/17-run-lifecycle.md)).
