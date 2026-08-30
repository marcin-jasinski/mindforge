---
id: T04
title: Whether Ingest is an agent loop or a typed pipeline
type: grilling
status: closed
assignee: claude
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

**Inherited from T03.** Ingest's input is **not homogeneous**: it must accept a conversation turn as a
source, not only a parsed document, because T03 made every user edit an Ingest run rather than a fourth
Operation. A document is a *source*; a correction is an *instruction*. An agent loop takes both as a
different opening message and pays nothing; a rigid typed pipeline takes the instruction as a second
entry shape and pays for it. Weigh that here — it is a real cost on the typed-pipeline branch that did
not exist when this ticket was written.

Also T03's: there is no approval gate to design around, so the loop's writes land as they are made;
whether a supersession is a typed output mid-run or a later pass is still this ticket's call.

**A typed pipeline with a dynamic fan-out over pages.** Retrieval replaces the loop's wandering; Lint
replaces its iteration. The model keeps every judgment call and stops making navigation calls.

```
Preprocess → RelevanceGuard → Extract (LLM) → Resolve (code) → Write (LLM, ×N) → Supersede (LLM, ×1)
```

### The six decisions

**1. Pipeline with fan-out, not a tool loop.**

ADR 0002 chose the loop *"because watching the LLM decide was the point of the talk"* — an explicitly
demo-shaped reason — and named its own cost as unpredictability with weaker models. A server needs the
resumability and cost prediction a demo does not.

The step the loop existed for is **Resolve**, and it is retrieval, which MindForge is already building
in Phases 7 and 11. Handing navigation to code is not a concession: it is the part the demo burned
iterations on — `list_dir` grew a `recursive` flag because walking a deep tree cost 41 calls against a
40-call budget.

What this preserves: **`AIGateway` is untouched.** Every call stays single-shot, so `DeadlineProfile`,
`CostTier`, `CompletionResult`'s cost accounting and the Resilience4j retry and circuit breaker all keep
working as built. The loop branch would have required re-cutting the one piece of AI infrastructure that
actually exists.

What this makes unrepresentable rather than guarded — the whole of T01 §24:

- No paths anywhere, so the two-path-vocabulary bug cannot occur.
- The write **is** the step's typed output, so "produced no page" fails the step by construction rather
  than via an accumulator someone remembers to consult.
- No iteration budget, no wrap-up nudge, no `MAX_NUDGES`, no `is_answer()` sentinel, no tool-name
  whitelist dispatch.
- **Source immutability is a type, not a prefix check**: the Write step takes claims and returns a body,
  and never holds a writable handle on a source. Exactly the equivalent T01 predicted.

Cost accepted: a single-shot page write cannot self-correct, which is the loop's one real advantage.
It is bought back out-of-band — **Lint is the second pass** (see Feeds, T10).

Cost accepted: the orchestrator needs fan-out over a runtime-determined set rather than a static DAG.
`OrchestrationGraph` is Phase 5 and unbuilt, so this is design, not rework.

**2. The agent roster.**

- **Die**: `SummarizerAgent` (a summary is a page of type `Source Summary` per T02; the Write step
  produces it), `ConceptMapperAgent` (cross-linking is what Ingest now *is*, and the concept map is
  `PageLink` rows). This answers T09's open question directly.
- **Survive in ingest**: `PreprocessorAgent`, and `RelevanceGuardAgent` — which matters *more* now, not
  less. Junk that got through used to spoil one document's artifact; under a compounding wiki it is
  woven into pages permanently and every later run builds on it. A SMALL-tier filter at the mouth is
  the cheapest guard on the map.
- **Relocate out of ingest**: `FlashcardGeneratorAgent`, `QuizGeneratorAgent` — T08 cuts these from the
  wiki on demand, not per document. They become services, not pipeline steps.
- **Untouched**: `QuizEvaluatorAgent` — quiz runtime, never touched ingest.
- **New**: claim extractor, page writer, supersession detector. **Resolve is code**; giving retrieval a
  `VERSION` and a prompt would be inventing an LLM call where a query does the job.

**3. The `Agent` abstraction is deleted outright.**

`Agent`, `AgentContext`, `AgentResult`, `AgentCapability` and the planned `AgentRegistry` go. Ingest
calls concrete services with concrete signatures:

```java
ClaimSet claims = claimExtractor.extract(blocks);
List<PageWriteTask> tasks = pageResolver.resolve(kbId, claims);   // code, no LLM
String body = pageWriter.write(task, existingBody, linkableIndex);
```

The abstraction was designed for an open-ended DAG that decision 2 just closed. `AgentRegistry` and the
open/closed rule buy plugin extensibility over a set of agents that is now fixed at four steps in a
known order. `AgentCapability`'s tiers buy orchestrator planning nothing does. `AgentResult` existed to
feed step-fingerprint checkpointing, which T02 already replaced with a run record that counts **rows the
transaction inserted**, never what a step reports about itself. What remains is an interface whose only
caller knows exactly which implementation it holds at every call site — a tax, not a seam.

`AgentContext` was going to need re-cutting anyway: it carries a `DocumentArtifact` that T02 killed, and
`AgentResult.Success.outputKey` indexes into an accumulator that died with it.

**Explicitly not touched.** CLAUDE.md's actual agent rules — all LLM calls through `AIGateway`, models
requested by `ModelTier` never a provider string, `VERSION` bumped only on logic or prompt change —
concern the gateway and prompt versioning, not the interface. They survive verbatim on concrete
services. **`ParserRegistry` also stays**: MIME dispatch is a genuinely open extension point, because new
file types arrive from outside the design. The open/closed rule was doing real work for parsers and
imagined work for agents.

Rejected alternative: keep `Agent` non-generic, swap `DocumentArtifact` for a typed `AgentInput` and
`outputKey` for a sealed `AgentOutput` union. Strictly better than today and idiomatic, but it leaves a
registry and a capability record maintained for four services that never vary.

**4. Supersession is its own step, after the writes, scoped by retrieval.**

Not folded into the writer, because **the common case is cross-page**: new page *Meiosis* supersedes a
section of *Cell Division*, a page this run never revises, so the writer never has its body. Folding it
in catches only the subset where the contradiction lands on an already-touched page.

Not left to Lint, because T01 called supersession *"the whole compounding story now Verify is out"*, and
T10 has Lint as cost-heavy and on demand. Deferring the compounding mechanism to an optional pass is
backwards.

One call per run, not per page: Resolve has already narrowed the candidates, so the step gets the claim
set plus the sections retrieval flagged as *related but not revised*. It never reads the whole bundle.
Detection stays LLM — lexical similarity finds candidates but cannot judge contradiction.

Keeping it separate also means it is a row insert outside the page-write transaction, a failure degrades
the run rather than losing it, and T03's per-row removal in the run report is a clean seam.

**5. Generate outside a transaction, commit in one. Partial success at generation.**

All bodies are produced in memory, then page rows, revisions, the run record and the outbox event land in
a single `@Transactional` boundary — exactly CLAUDE.md's rule. Bodies are strings; holding fifteen costs
nothing.

Rejected: one transaction wrapping the run (holds a connection and locks across minutes of LLM calls) and
one transaction per page (tears the wiki mid-run, so a reader between page 7 and 8 sees half a run).

If some writes fail, the successes land and the failures are recorded on the run. **Zero successes fails
the run** — T01 §24's guard is about zero, not partial. Discarding fourteen good calls to punish one bad
one is waste.

Partial landing is safe because T02 already made forward references a supported state: links resolve by
join at read, so page A linking to an unwritten page B is live the moment B appears and sits in the
dangling report until then. Nothing new to build.

**Ingest runs serialize per `KnowledgeBase`.** Resolve happens minutes before commit, so two concurrent
uploads into one wiki both read the same state, both revise page X, and the second commit silently
discards the first's work. T01 flagged the demo's single-process lock as *"a real finding wearing local
clothes"* — it does not go away because there is a database. One run at a time per KB, queue the rest;
every alternative (optimistic locking with retry, re-resolve at commit, merge) is machinery bought for a
case nobody asked for. **The mechanism is T07's; the constraint is this ticket's.**

**6. Claim cap at Extract, fail loud, parallel fan-out.**

The cap goes at Extract because that is the only point where "too many" is still cheap — one call in,
before anything has been spent. Generous, sized so a dense document is not clipped: the guard exists for
a bad extraction returning 200 fragments, not to limit how much a document may teach.

**On hit, fail the run — never truncate.** T01 §24 again: the demo's `grep` caps at 200 hits and *says so
when truncated*, because silent truncation lets the model reason from a partial view it believes is
complete. Here it is worse than there — clipped claims are not a bad answer, they are omissions from a
wiki that compounds, and nothing downstream will ever notice they were meant to exist.

Writes fan out in parallel over virtual threads (already enabled) with a bounded pool — they are
independent by construction, each writing a different page. The bound is for provider rate limits, not
correctness; a semaphore, not a scheduler.

**No per-run cost budget.** N is bounded by the claim cap and cost is bounded by N. Add one when a real
run shows the ceiling is in the wrong place.

**T03's input constraint, resolved.** A conversation turn skips Extract and enters at Resolve, because
the instruction *is* the claim set. One entry point earlier or later on the same pipeline — not the
second entry shape the typed-pipeline branch was feared to need.

### Feeds

- **T05** — confirms `WikiStore` needs no file primitives at all: with no tool loop there is no caller for
  `read`/`write`/`list`/`grep`. The page-shaped surface T02 chose is the only one required.
- **T06** — Resolve matches claims to existing pages, so slug and taxonomy policy is now a *retrieval*
  input, not only an identity decision.
- **T07** — inherits the per-`KnowledgeBase` serialization mechanism, the run record's shape (which pages,
  which failures, what it cost), and the fact that step fingerprints have no home now that `Agent` is
  deleted. Partial-success semantics are settled here; making a re-run idempotent against them is T07's.
- **T08** — flashcard and quiz generation are on-demand services, not pipeline steps.
- **T09** — `ConceptMapperAgent` is dead, confirmed. Retrieval carries new weight: Resolve is a pipeline
  step, so Phase 7/11 retrieval is on the ingest critical path, not only the read path.
- **T10** — Lint now carries real load: it is where a single-shot page write's inability to self-correct
  is bought back. That strengthens the case for running it after ingest rather than purely on demand.
- **T12** — deletes `Agent`, `AgentContext`, `AgentResult`, `AgentCapability` from Phase 1. Keeps
  `AIGateway`, `AIGatewayAdapter`, `CompletionResult`, `DeadlineProfile`, `CostTier`, `ModelTier`,
  `ProcessingSettings` untouched.
- **T13** — `docs/standards/backend/ai_agents.md` is largely rewritten: the `Agent` interface section and
  `AgentCapability` example go, the gateway, `ModelTier`, prompt-file and lesson-identity sections stay.
  Phases 5 and 6 are re-cut around four services and a fan-out rather than seven agents and a DAG.
