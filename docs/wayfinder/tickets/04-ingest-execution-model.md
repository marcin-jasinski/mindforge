---
id: T04
title: Whether Ingest is an agent loop or a typed pipeline
type: grilling
status: open
assignee:
blocked_by: [T01, T02]
---

## Question

The sharpest architectural collision in this map.

**MindForge's design** (`docs/standards/backend/ai_agents.md`, Phases 5–6): agents are stateless
`@Service` beans implementing `Agent` with a `VERSION`, a `PROMPT_VERSION` and a single
`execute()` returning a typed `AgentResult`. A `PipelineOrchestrator` runs them as a DAG with
step-fingerprint checkpointing. Every LLM call goes through `AIGateway`. Agents never call each
other.

**The demo's design** (ADR 0002): Ingest is an *inner tool-calling loop* — the model reads the
source, greps the wiki, reads pages it thinks are relevant, and writes however many it decides
to, across an unbounded number of turns. Which pages get touched is a model decision made at
runtime, not a DAG edge.

These are not reconcilable by compromise. Decide:

- **Does the tool loop come inside MindForge**, and if so what does `AIGateway` look like when a
  call is a multi-turn loop with tool results rather than one completion? `CompletionResult`,
  `DeadlineProfile` and the Resilience4j retry/circuit-breaker config in Phase 3 all assume
  single-shot.
- **Or is Ingest decomposed into typed steps** — extract claims, resolve which pages they touch,
  write each page — keeping the orchestrator and paying for it with a model that can't
  follow a cross-link it discovers mid-run.
- **What are the tools?** The demo's six primitives (`read_file`, `write_file`, `list_dir`,
  `grep`, `fetch_url`, `append_log`) map onto a `WikiStore` port, not a filesystem. `append_log`
  is deliberately deterministic — the model does not format log entries.
- **Where does the sandbox live?** The demo enforces "raw sources are immutable" at the tool
  level, not by convention. MindForge's equivalent guard is the port surface: no tool the
  ingest loop can call may write outside its own bundle. This is a multi-tenant security
  boundary, not just hygiene — see `docs/standards/security/web-security.md`.
- **What happens to `RelevanceGuardAgent` and `PreprocessorAgent`?** These are cheap SMALL-tier
  filters that plausibly survive unchanged in front of the loop.
- **What happens to `SummarizerAgent` and `ConceptMapperAgent`?** Writing a summary page and
  cross-linking it is what Ingest *is*. They probably stop existing as separate agents.

## Answer
