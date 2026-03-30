# MindForge — Architecture Fix Plan

> **Purpose:** This document translates every finding from the deep architecture
> review (April 2026) into concrete, actionable implementation tasks.  Each
> section identifies the problem, defines the target state in enough detail for
> an implementer to act without prior context, lists every file to create or
> modify, and provides ordered implementation steps.
>
> **Priority levels**
> - **CRITICAL** — data loss, event-loop blocking, or security regression.
>   Fix before the next production deployment.
> - **SEVERE** — production instability or silent correctness failures.
>   Fix within the current sprint.
> - **MODERATE** — maintainability debt that compounds with every new feature.
>   Fix in the next planned refactor window.

---

## Table of Contents

1. [CRITICAL-1 — Split the God Object `Config`](#critical-1)
2. [CRITICAL-2 — Move quiz logic into a proper package module](#critical-2)
3. [CRITICAL-3 — Add pipeline step checkpointing](#critical-3)
4. [CRITICAL-4 — Replace blocking `requests` with async LLM client](#critical-4)
5. [CRITICAL-5 — Unify the three canonical data stores](#critical-5)
6. [SEVERE-1 — Replace `BackgroundTasks` with a real task queue](#severe-1)
7. [SEVERE-2 — Make `QuizSessionStore` multi-worker safe](#severe-2)
8. [SEVERE-3 — Lock the article cache read-modify-write cycle](#severe-3)
9. [SEVERE-4 — Fix `Fact` node idempotency in Neo4j indexing](#severe-4)
10. [SEVERE-5 — Batch Neo4j writes with UNWIND](#severe-5)
11. [SEVERE-6 — Run pipeline off the watchdog observer thread](#severe-6)
12. [MODERATE-1 — Extract flashcard count into a repository abstraction](#moderate-1)
13. [MODERATE-2 — Replace manual `from_dict` with a schema-aware deserializer](#moderate-2)
14. [MODERATE-3 — Eliminate lazy imports inside feature-flag blocks](#moderate-3)
15. [MODERATE-4 — Introduce a domain interface (port) for retrieval](#moderate-4)
16. [MODERATE-5 — Fix allowlist evaluation order in `bot_auth`](#moderate-5)
17. [MODERATE-6 — Rename Polish-language `Config` path fields](#moderate-6)
18. [CRITICAL-6 — Make lesson identity explicit and immutable](#critical-6)
19. [CRITICAL-7 — Add content-hash deduplication and idempotent ingestion](#critical-7)
20. [CRITICAL-8 — Replace filesystem persistence with a database-backed document repository](#critical-8)
21. [SEVERE-7 — Rebuild spaced repetition around user-scoped stable card IDs](#severe-7)
22. [SEVERE-8 — Separate lesson evidence from the global Neo4j projection](#severe-8)
23. [SEVERE-9 — Centralize auth and security policy settings at startup](#severe-9)
24. [MODERATE-7 — Honor Neo4j database selection end-to-end](#moderate-7)
25. [MODERATE-8 — Package the application and remove `sys.path` surgery](#moderate-8)
26. [MODERATE-9 — Build explicit read models for API projections](#moderate-9)
27. [SEVERE-10 — AI Gateway with provider abstraction](#severe-10)
28. [SEVERE-11 — Event-driven architecture with agent orchestration](#severe-11)
29. [MODERATE-10 — Multimodal-ready data structures](#moderate-10)
30. [MODERATE-11 — Structured interaction model with full persistence](#moderate-11)

---

## CRITICAL-1 — Split the God Object `Config` {#critical-1}

### Problem

`processor/llm_client.py` contains a `Config` dataclass that bundles:
- a live `LLMClient` instance (infrastructure adapter),
- eleven filesystem `Path` fields,
- nine boolean feature flags,
- three Neo4j credential strings,
- four model-name strings.

All of this is loaded by a single 60-line `load_config()` function that also
initialises Langfuse tracing as a side-effect.  The class is named after the
LLM client but governs the entire application.  Any consumer needing a single
path must import and construct the full config including credentials.

### Target State

Three separate, independently constructable configuration objects:

**`AppSettings`** — pure data, no behaviour, no live objects.

```
processor/settings.py
```

```python
@dataclass
class AppSettings:
    # Directories (English names — see MODERATE-6)
    base_dir: Path
    new_dir: Path
    summaries_dir: Path
    archive_dir: Path
    flashcards_dir: Path
    quizzes_dir: Path
    diagrams_dir: Path
    knowledge_dir: Path
    state_file: Path
    # Feature flags
    enable_image_analysis: bool
    enable_flashcards: bool
    enable_quizzes: bool
    enable_diagrams: bool
    enable_knowledge_index: bool
    enable_validation: bool
    enable_tracing: bool
    enable_graph_rag: bool
    enable_embeddings: bool
    # Model names
    model_small: str
    model_large: str
    model_vision: str
    model_embedding: str
    # Neo4j connection (not credentials — see below)
    neo4j_uri: str
    neo4j_database: str

def load_settings(base_dir: Path | None = None) -> AppSettings:
    """Read all settings from .env / environment.  No side-effects."""
    ...
```

**`LLMCredentials`** — holds secrets, never logged, never passed to routers.

```
processor/llm_client.py  (keep existing LLMClient, add LLMCredentials)
```

```python
@dataclass
class LLMCredentials:
    api_key: str
    base_url: str
    neo4j_username: str
    neo4j_password: str

def load_credentials(base_dir: Path | None = None) -> LLMCredentials:
    """Load secrets from .env.  Raises ValueError if required keys are missing."""
    ...
```

**`LLMClient`** is constructed from `LLMCredentials` — no change to its
public interface.

**Composition root** — one place in each entry point wires the three objects:

```python
# In api/main.py lifespan, quiz_agent.py main(), mindforge.py main(), bot.py setup_hook()
settings = load_settings(ROOT)
creds = load_credentials(ROOT)
llm = LLMClient(base_url=creds.base_url, api_key=creds.api_key)
```

Langfuse initialisation moves to a dedicated `init_tracing(settings, creds)` 
helper called explicitly from each entry point — not as a side-effect of 
`load_config`.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/settings.py` |
| Modify | `processor/llm_client.py` — remove `Config`, remove `load_config`, keep `LLMClient`, add `LLMCredentials`, add `load_credentials` |
| Modify | `api/main.py` — lifespan wires `AppSettings` + `LLMCredentials` + `LLMClient` |
| Modify | `api/deps.py` — expose `get_settings()` and `get_llm_client()` separately |
| Modify | `mindforge.py` — replace `load_config` call |
| Modify | `quiz_agent.py` — replace `load_config` call |
| Modify | `discord_bot/bot.py` — replace `load_config` call |
| Modify | `backfill.py` — replace `load_config` call |
| Modify | `processor/pipeline.py` — function signature accepts `AppSettings + LLMClient` instead of `Config` |
| Modify | All processor agents, tools, and watcher that currently accept `Config` |
| Modify | `tests/` — update fixtures that construct `Config` |

### Implementation Steps

1. Create `processor/settings.py` with `AppSettings` dataclass and
   `load_settings()`.  Copy path and flag logic from the existing
   `load_config()`.  Do not import `LLMClient` from this module.

2. Add `LLMCredentials` dataclass and `load_credentials()` to
   `processor/llm_client.py`.  Remove `Config` and `load_config` from that
   file.  Ensure `LLMClient.__init__` takes `base_url` and `api_key` directly
   (it already does — no change needed to the class itself).

3. Update `api/main.py` lifespan to call `load_settings()` and
   `load_credentials()` separately, store both on `app.state`.

4. Update `api/deps.py`: add `get_settings(request) -> AppSettings` alongside
   the existing `get_config`.  Progressively replace `get_config` usages with
   `get_settings` in routers that do not need the LLM client.

5. Update `processor/pipeline.py` function signatures:
   `run(filepath, settings: AppSettings, llm: LLMClient, *, force, keep_in_place)`.

6. Update all agents and tools that receive `config: Config` to receive the
   narrowest set of parameters they actually use.  Most agents only need
   `llm: LLMClient` and `model: str` — they already accept those as explicit
   parameters.  The pipeline is the only place that distributes `Config`
   wholesale; fixing the pipeline signature forces the cleanup naturally.

7. Move Langfuse initialisation out of `load_config` into a standalone
   `processor/tracing.py` function `init_tracing(settings, creds)`.  Call it
   explicitly at the top of each entry-point `main()`.

8. Update `discord_bot/bot.py setup_hook` and `mindforge.py main()` and
   `quiz_agent.py main()` to use the new API.

9. Update all test fixtures that currently construct a `Config` to construct
   `AppSettings` + `LLMClient` instead.

10. Delete `processor/llm_client.Config` and `processor/llm_client.load_config`
    once all usages are gone.  Run `grep -r "load_config\|from processor.llm_client import.*Config"` 
    to verify nothing remains.

---

## CRITICAL-2 — Move quiz logic into a proper package module {#critical-2}

### Problem

`quiz_agent.py` is a CLI script at the repository root.  Both `api/routers/quiz.py`
and `discord_bot/cogs/quiz.py` import from it, including a **private** function
`_build_context`.  This makes a CLI script a shared library, breaks static
analysis, and couples two separate runtime surfaces to script internals.

### Target State

A new module `processor/agents/quiz_evaluator.py` owns all reusable quiz logic.
`quiz_agent.py` becomes a thin CLI wrapper that imports from the module.  No
other file ever imports from `quiz_agent.py`.

The public interface of `processor/agents/quiz_evaluator.py`:

```python
# Public API — no leading underscores
def build_context(result: RetrievalResult, topic: str) -> str: ...
def generate_question(topic: str, context: str, llm: LLMClient, model: str) -> Question: ...
def evaluate_answer(
    question: str,
    reference_answer: str,
    user_answer: str,
    context: str,
    llm: LLMClient,
    model: str,
) -> Evaluation: ...
```

The `Question`, `Evaluation`, and `SessionResult` dataclasses move to this
module (or to `processor/models.py` if they expose data shared with the API
schemas).

`quiz_agent.py` becomes:

```python
from processor.agents.quiz_evaluator import (
    build_context, generate_question, evaluate_answer, Question, Evaluation
)
# ... CLI argument parsing and interactive loop only
```

`api/routers/quiz.py` imports:

```python
from processor.agents.quiz_evaluator import build_context, generate_question, evaluate_answer
```

`discord_bot/cogs/quiz.py` imports the same module.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/agents/quiz_evaluator.py` |
| Modify | `quiz_agent.py` — remove all logic, keep only CLI plumbing |
| Modify | `api/routers/quiz.py` — update import path |
| Modify | `discord_bot/cogs/quiz.py` — update import path |
| Modify | `tests/test_quiz_cost.py` — update import path if it imports from `quiz_agent` |

### Implementation Steps

1. Create `processor/agents/quiz_evaluator.py`.  Copy into it:
   - `Question`, `Evaluation`, `SessionResult` dataclasses
   - `QUESTION_SYSTEM_PROMPT` and `EVALUATION_SYSTEM_PROMPT` constants
   - Rename `_build_context` → `build_context` (remove underscore)
   - `generate_question()`
   - `evaluate_answer()`
   - All helper functions called by those functions

2. In `quiz_agent.py`, replace the logic with imports from the new module.
   Leave only `main()`, argument parsing, the interactive loop, and the
   `SessionResult` accumulation logic (that is CLI-specific behaviour).

3. In `api/routers/quiz.py`, change:
   ```python
   # Before
   from quiz_agent import _build_context, generate_question
   # After
   from processor.agents.quiz_evaluator import build_context, generate_question
   ```
   Update all call sites from `_build_context(...)` to `build_context(...)`.

4. In `discord_bot/cogs/quiz.py`, apply the same import change.

5. Run `pytest tests/` to confirm no breakage.

6. Run `grep -r "from quiz_agent\|import quiz_agent"` in the repo root to
   confirm nothing outside `quiz_agent.py` itself still imports from the script.

---

## CRITICAL-3 — Add pipeline step checkpointing {#critical-3}

### Problem

The pipeline executes 16 sequential steps, several of which make expensive LLM
calls.  If step 12 (concept map) fails after steps 9, 10 have already succeeded,
all token spend is wasted and the pipeline re-runs from scratch.  There is no
partial result persistence between steps.

### Target State

The canonical document record is written incrementally to the database-backed
artifact repository introduced in CRITICAL-8.  After each LLM-producing step
(steps 7, 9, 10, 12), the artifact checkpoint is persisted transactionally.
On a fresh run for a document that was previously partially processed, the
pipeline loads the existing partial artifact from the repository and skips any
step whose output is already present and non-empty.

**Checkpoint contract:**

A step is skippable if and only if the corresponding artifact field is already
non-None (or non-empty list).  This is detected by inspecting the artifact
before the step runs.

**New function in `processor/pipeline.py`:**

```python
def _flush_artifact(
    repo: DocumentRepository,
    document_id: str,
    artifact: LessonArtifact,
    *,
    completed_step: str,
) -> None:
    """Persist current artifact state to the canonical database store."""
    repo.save_checkpoint(
        document_id=document_id,
        artifact=artifact,
        completed_step=completed_step,
    )
```

**Modified step guards:**

```python
# Step 9 — Summarize
if artifact.summary is None:
    artifact.summary = summarize(...)
    _flush_artifact(repo, document_id, artifact, completed_step="summary")
else:
    log.info("Step 9 — Summary loaded from checkpoint, skipping LLM call")

# Step 10 — Flashcards
if config.enable_flashcards and not artifact.flashcards:
    artifact.flashcards = generate_flashcards(...)
    _flush_artifact(repo, document_id, artifact, completed_step="flashcards")

# Step 12 — Concept map
if config.enable_diagrams and artifact.concept_map is None:
    artifact.concept_map = generate_concept_map(...)
    _flush_artifact(repo, document_id, artifact, completed_step="concept_map")
```

**Partial artifact loading:** At the start of `_run_steps`, before step 6
creates a new artifact, check whether the document repository already contains a
checkpoint for this `document_id`.  If it does, load it and use it as the
starting artifact instead of creating a fresh one.  The `force=True` flag
bypasses this and always starts fresh.

```python
# Step 6 — Create or load artifact
artifact = repo.load_checkpoint(document_id)
if not force and artifact is not None:
    log.info("Step 6 — Loaded partial artifact from checkpoint: %s", artifact.lesson_id)
else:
    artifact = LessonArtifact.create(...)
```

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/document_repository.py` — checkpoint-capable persistence abstraction |
| Modify | `processor/pipeline.py` — add `_flush_artifact`, repository-backed checkpoint loading, modify steps 6/7/9/10/12 |

### Implementation Steps

1. Create repository methods such as `save_checkpoint(document_id, artifact,
    completed_step)` and `load_checkpoint(document_id)` in the canonical
    persistence layer from CRITICAL-8.  Checkpoint updates must be transactional.

2. Add `_flush_artifact(repo, document_id, artifact, completed_step=...)`
    helper in `processor/pipeline.py`.

3. Replace filename-based `_find_artifact(...)` logic with repository lookup by
    `document_id`.

4. Modify the beginning of `_run_steps` to load a partial artifact when one
   exists and `force=False`.

5. Wrap each LLM-producing step (9, 10, 12) with an existence check before
   calling the LLM, then call `_flush_artifact` after.

6. Step 7 (preprocessor — also an LLM call) should be treated similarly:
   if `artifact.cleaned_content` already differs from the raw parsed content,
   treat it as already preprocessed.  A simple heuristic: keep a
   `preprocessed: bool = False` field on `LessonArtifact` and set it to `True`
   after step 7.

7. Add a test in `tests/test_pipeline_idempotency.py` that:
   - runs the pipeline on a fixture lesson with a mock LLM,
    - deletes the summary from the persisted checkpoint to simulate a mid-run
     failure after step 9,
   - re-runs the pipeline,
   - asserts the LLM was called only once for the summary (not twice).

---

## CRITICAL-4 — Replace blocking `requests` with async LLM client {#critical-4}

### Problem

`LLMClient.complete()` uses the synchronous `requests` library.  It is called
from:
- FastAPI async route handlers (`api/routers/quiz.py`, `api/routers/search.py`)
- Discord bot async cog handlers (`discord_bot/cogs/quiz.py`)

Each such call blocks the entire async event loop for up to 180 seconds, making
the application unresponsive to all other requests during that time.

### Target State

`LLMClient` gains an async variant, `AsyncLLMClient`, that uses `httpx.AsyncClient`
internally.  The sync `LLMClient` is preserved for use in the CLI pipeline
runner (`mindforge.py`, `quiz_agent.py` interactive loop) which runs outside an
async context.

**New class in `processor/llm_client.py`:**

```python
class AsyncLLMClient:
    """Async version of LLMClient — use in FastAPI and Discord bot handlers."""

    def __init__(self, base_url: str, api_key: str, timeout_seconds: int = 180,
                 default_headers: dict | None = None) -> None: ...

    async def complete(
        self,
        *,
        model: str,
        messages: list[dict],
        temperature: float = 0.0,
        response_format: dict | None = None,
    ) -> str: ...
```

The implementation mirrors `LLMClient.complete()` but uses `async with httpx.AsyncClient()` 
and `await client.post(...)`.  Token usage recording and Langfuse tracing calls
that are currently synchronous must be converted to their async equivalents or
moved to `asyncio.to_thread` if the Langfuse SDK is sync-only.

**Entry-point wiring:**

- `api/main.py`: store an `AsyncLLMClient` instance on `app.state.async_llm`.
- `api/deps.py`: add `get_async_llm_client(request) -> AsyncLLMClient`.
- All FastAPI route handlers: inject `AsyncLLMClient` instead of `LLMClient`.
- `discord_bot/bot.py`: store `AsyncLLMClient` on `self.async_llm`; cogs use it.

**`httpx` must be added to `requirements.txt`.**  It is already a transitive
dependency of many FastAPI stacks but must be explicit.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/llm_client.py` — add `AsyncLLMClient` |
| Modify | `requirements.txt` — add `httpx>=0.27` |
| Modify | `api/main.py` — instantiate `AsyncLLMClient`, store on `app.state` |
| Modify | `api/deps.py` — add `get_async_llm_client` |
| Modify | `api/routers/quiz.py` — use `AsyncLLMClient` |
| Modify | `api/routers/search.py` — use `AsyncLLMClient` |
| Modify | `discord_bot/bot.py` — add `self.async_llm` |
| Modify | `discord_bot/cogs/quiz.py` — use `self.bot.async_llm` |
| Modify | `discord_bot/cogs/search.py` — use `self.bot.async_llm` |

### Implementation Steps

1. Add `httpx>=0.27` to `requirements.txt`.

2. In `processor/llm_client.py`, implement `AsyncLLMClient` as a class that
   mirrors `LLMClient.complete()` using `httpx.AsyncClient`.  The fallback
   retry on `response_format` error applies identically but with
   `async with httpx.AsyncClient() as client: response = await client.post(...)`.

3. For Langfuse tracing (`tracing.start_generation`): if the Langfuse Python
   SDK is synchronous, wrap the `gen.end()` call in
   `await asyncio.to_thread(gen.end, ...)`.  This keeps the event loop free
   while flushing telemetry.

4. In `api/main.py` lifespan, after constructing `LLMClient` (for pipeline
   background tasks), also construct `AsyncLLMClient` with identical credentials
   and store it as `app.state.async_llm`.

5. Add `get_async_llm_client` to `api/deps.py`:
   ```python
   def get_async_llm_client(request: Request) -> AsyncLLMClient:
       return request.app.state.async_llm
   ```

6. Update `api/routers/quiz.py` — replace `llm: Any = Depends(get_llm_client)`
   with `llm: AsyncLLMClient = Depends(get_async_llm_client)`.  Add `await`
   to every `llm.complete(...)` call throughout the route handler and the
   helper functions it calls.

7. Update `api/routers/search.py` — the embedding call
   (`embed_texts`) is also synchronous.  Wrap it in `asyncio.to_thread`:
   ```python
   embeddings = await asyncio.to_thread(embed_texts, ...)
   ```

8. For the Discord bot: the cog handlers are already `async`.  Add
   `self.bot.async_llm: AsyncLLMClient` in `bot.py setup_hook`.  Update cog
   methods to `await self.bot.async_llm.complete(...)`.

9. Update `api/deps.py` `get_llm_client` — rename it to `get_sync_llm_client`
   and document that it is only for background pipeline tasks, not for
   route handlers.

---

## CRITICAL-5 — Unify the three canonical data stores {#critical-5}

### Problem

Concept data is written to three separate stores:
1. `state/artifacts/*.json` — the declared canonical source.
2. `state/knowledge_index.json` — flat JSON with normalization, aliases,
   confidence, used by the summarizer for prior-concept context.
3. Neo4j — graph database, used by the API and Discord bot for all queries.

Each store uses different normalization, different update semantics, and diverges
silently over time.

### Target State

**Single rule:** the application database introduced in CRITICAL-8 stores the
original upload and generated artifact as the canonical system of record.
Neo4j is the runtime query store.  `state/knowledge_index.json` and
`state/artifacts/*.json` are abolished.

Any code path that currently reads artifact JSON from disk must switch to the
repository introduced in CRITICAL-8.  Filesystem artifacts may exist only as a
one-time migration source, never as a live canonical store.

**Summarizer prior-concept context** is served by a thin Neo4j query, not by
the JSON index:

```python
# processor/agents/summarizer.py
def get_known_concepts_from_graph(driver, max_concepts: int = 50) -> dict[str, Any]:
    """Query Neo4j for existing concepts to pass as prior context."""
    with driver.session() as session:
        result = session.run(
            "MATCH (c:Concept) RETURN c.name AS name, c.definition AS definition "
            "ORDER BY c.name LIMIT $limit",
            limit=max_concepts,
        )
        return {r["name"]: {"definition": r["definition"]} for r in result}
```

The `summarize()` function signature gains an optional `driver` parameter.
When `driver` is provided and `enable_graph_rag` is true, it calls
`get_known_concepts_from_graph(driver)` instead of reading the JSON index.

The `known_concepts` parameter in the pipeline (step 9) switches from reading
`knowledge_index.json` to calling the graph function:

```python
# pipeline.py step 9
known_concepts = None
if config.enable_graph_rag and config.enable_knowledge_index and driver:
    known_concepts = get_known_concepts_from_graph(driver)
```

When `enable_graph_rag=False`, prior-concept injection is disabled (acceptable
— the knowledge index was already disabled by default in that mode).

**Concept normalization in Neo4j** is brought in line with the normalizer used
by the JSON index.  The `graph_rag.index_lesson` function is updated to call
`processor.tools.concept_normalizer.dedupe_key(concept.name)` for the
`normalized_key` property instead of the current `name.lower().strip()`.

**`state/knowledge_index.json` and related code** (`processor/tools/knowledge_index.py`,
`processor/tools/concept_normalizer.py`) are removed after the migration is
verified.  `generate_glossary()` and `generate_cross_references()` are either
ported to query Neo4j or dropped if not actively used.

**Migration path:**  Before deleting the JSON index, write a one-time migration
script `scripts/migrate_knowledge_index_to_neo4j.py` that reads
`state/knowledge_index.json`, queries the existing Neo4j graph, and for any
concept in the JSON index not already in the graph, merges it in with its
normalized key, aliases, confidence score and source lessons.

### Files to Touch

| Action | File |
|--------|------|
| Create | `scripts/migrate_knowledge_index_to_neo4j.py` |
| Modify | `processor/document_repository.py` — artifact reads move to the canonical DB store |
| Modify | `processor/agents/summarizer.py` — add `get_known_concepts_from_graph`, update `summarize()` signature |
| Modify | `processor/pipeline.py` — step 9 reads from graph not JSON file; step 15 (update_index) removed |
| Modify | `processor/tools/graph_rag.py` — use `dedupe_key` for `normalized_key` in `index_lesson` |
| Delete | `processor/tools/knowledge_index.py` (after migration) |
| Modify | `processor/tools/concept_normalizer.py` — keep (used by graph indexing) |
| Modify | `tests/` — remove tests that depend on the JSON knowledge index |

### Implementation Steps

1. Write `scripts/migrate_knowledge_index_to_neo4j.py` that reads the existing
   `knowledge_index.json` and merges all concepts into Neo4j using MERGE +
   SET operations, preserving `normalized_key`, `definition`, and aliases as
   node properties.

2. Add `get_known_concepts_from_graph(driver, max_concepts)` to
   `processor/agents/summarizer.py`.

3. Update `summarize()` to accept an optional `driver` parameter.  When
   provided, call `get_known_concepts_from_graph` instead of accepting
   a `known_concepts` dict parameter.  Update all call sites.

4. In `processor/pipeline.py` step 9, replace the JSON index read with the
    graph query.  Remove step 15 (`update_index` call) entirely.

5. In `processor/tools/graph_rag.py` `index_lesson`, replace:
   ```python
   normalized_key=concept.name.lower().strip()
   ```
   with:
   ```python
   from processor.tools.concept_normalizer import dedupe_key
   normalized_key=dedupe_key(concept.name)
   ```

6. Run the migration script against a development Neo4j instance.  Verify
   concept counts match.

7. Delete `processor/tools/knowledge_index.py` and remove its only call sites.

8. Update `processor/__init__.py` if it re-exports anything from
   `knowledge_index`.

9. As part of CRITICAL-8, remove any remaining live reads from
    `state/artifacts/*.json`; all artifact access must come through the canonical
    database repository.

---

## SEVERE-1 — Replace `BackgroundTasks` with a real task queue {#severe-1}

### Problem

`api/routers/lessons.py` uses FastAPI `BackgroundTasks` to run the full
lesson pipeline (potentially 10+ minutes, multiple LLM calls) after an upload.
`BackgroundTasks` has no queue, no retry, no progress surface, and no graceful
shutdown.  A process restart silently discards running pipelines.

### Target State

Use `asyncio.TaskGroup` (Python 3.11+) with a bounded semaphore to limit
concurrent pipeline executions, and expose a status endpoint.

**Simple in-process task manager** in `api/pipeline_task_manager.py`:

```python
import asyncio
import uuid
from dataclasses import dataclass, field
from enum import Enum
from typing import Any

class TaskStatus(str, Enum):
    PENDING = "pending"
    RUNNING = "running"
    DONE = "done"
    FAILED = "failed"

@dataclass
class PipelineTask:
    task_id: str
    filename: str
    status: TaskStatus = TaskStatus.PENDING
    error: str | None = None

class PipelineTaskManager:
    MAX_CONCURRENT = 2  # at most 2 pipelines at once

    def __init__(self) -> None:
        self._tasks: dict[str, PipelineTask] = {}
        self._semaphore = asyncio.Semaphore(self.MAX_CONCURRENT)

    def submit(self, filepath: Path, config: AppSettings, llm: LLMClient) -> str:
        task_id = str(uuid.uuid4())
        task = PipelineTask(task_id=task_id, filename=filepath.name)
        self._tasks[task_id] = task
        asyncio.create_task(self._run(task, filepath, config, llm))
        return task_id

    async def _run(self, task: PipelineTask, filepath: Path,
                   config: AppSettings, llm: LLMClient) -> None:
        async with self._semaphore:
            task.status = TaskStatus.RUNNING
            try:
                await asyncio.to_thread(pipeline.run, filepath, config, llm)
                task.status = TaskStatus.DONE
            except Exception as exc:
                task.status = TaskStatus.FAILED
                task.error = str(exc)

    def get_status(self, task_id: str) -> PipelineTask | None:
        return self._tasks.get(task_id)

pipeline_task_manager = PipelineTaskManager()
```

**Upload endpoint returns a `task_id`:**

```python
# api/schemas.py
class UploadResponse(BaseModel):
    filename: str
    task_id: str
    message: str
    status_url: str
```

```python
# api/routers/lessons.py
task_id = pipeline_task_manager.submit(dest, settings, sync_llm)
return UploadResponse(
    filename=dest.name,
    task_id=task_id,
    message="File uploaded. Pipeline processing started.",
    status_url=f"/api/tasks/{task_id}",
)
```

**New status router** `api/routers/tasks.py`:

```python
@router.get("/api/tasks/{task_id}")
async def get_task_status(task_id: str, _user: UserInfo = Depends(require_auth)):
    task = pipeline_task_manager.get_status(task_id)
    if task is None:
        raise HTTPException(404, "Task not found")
    return {"task_id": task.task_id, "filename": task.filename, "status": task.status, "error": task.error}
```

**Graceful shutdown:** In `api/main.py` lifespan teardown, add a wait loop
that allows running tasks to finish before shutdown (with a configurable
timeout, e.g., 5 minutes):

```python
# lifespan teardown
await pipeline_task_manager.drain(timeout_seconds=300)
```

### Files to Touch

| Action | File |
|--------|------|
| Create | `api/pipeline_task_manager.py` |
| Create | `api/routers/tasks.py` |
| Modify | `api/main.py` — register tasks router; add drain to lifespan teardown |
| Modify | `api/routers/lessons.py` — replace `BackgroundTasks` with `pipeline_task_manager.submit` |
| Modify | `api/schemas.py` — update `UploadResponse` to include `task_id` and `status_url` |
| Modify | `frontend/src/app/core/models/api.models.ts` — synchronize `UploadResponse` model |
| Modify | `frontend/src/app/core/services/api.service.ts` — handle new `task_id` field |

### Implementation Steps

1. Create `api/pipeline_task_manager.py` with `PipelineTask`, `TaskStatus`,
   and `PipelineTaskManager` as described above.  Store the module-level
   singleton `pipeline_task_manager`.

2. Create `api/routers/tasks.py` with the status endpoint.

3. In `api/main.py`, import and register the tasks router.  Add
   `pipeline_task_manager.drain(timeout_seconds=300)` to the lifespan
   teardown block.

4. Update `api/routers/lessons.py`: remove `BackgroundTasks` parameter from
   `upload_lesson`, import `pipeline_task_manager`, call `.submit(...)`, and
   return a `task_id` in the response.

5. Update `api/schemas.py` `UploadResponse` to add `task_id: str` and
   `status_url: str`.

6. Update `frontend/src/app/core/models/api.models.ts`:
   ```typescript
   export interface UploadResponse {
     filename: string;
     task_id: string;
     message: string;
     status_url: string;
   }
   ```

7. Add a test in `tests/test_pipeline_task_manager.py` that verifies:
   - Submitting a task returns a UUID.
   - Status transitions from `pending` → `running` → `done`.
   - Status returns `failed` and captures the error message on exception.
   - Semaphore limits concurrency to `MAX_CONCURRENT`.

---

## SEVERE-2 — Make `QuizSessionStore` multi-worker safe {#severe-2}

### Problem

`api/quiz_session_store.py` uses an in-process Python dict.  It explicitly
documents that it breaks under multiple workers.  The production deployment
does not prevent multi-worker configuration.

### Target State

Replace the in-process dict with a Redis-backed store.  When Redis is
unavailable (local dev without Redis running), fall back to the in-process
dict with a loud warning at startup — do not silently hide the degraded mode.

**New interface in `api/quiz_session_store.py`:**

The `QuizSessionStore` class keeps its existing public method signatures
(`create_session`, `get_question`) unchanged so all callers continue to work
without modification.

**Redis implementation:**

```python
import json
import redis

class RedisQuizSessionStore:
    def __init__(self, redis_url: str) -> None:
        self._redis = redis.from_url(redis_url, decode_responses=True)

    def create_session(self, user_id: str, questions: list[dict]) -> str:
        session_id = str(uuid.uuid4())
        payload = {
            "user_id": user_id,
            "created_at": time.time(),
            "questions": questions,   # questions already contain context + reference_answer
        }
        self._redis.setex(
            f"quiz:session:{session_id}",
            SESSION_TTL_SECONDS,
            json.dumps(payload),
        )
        return session_id

    def get_question(self, user_id: str, session_id: str, question_id: int) -> StoredQuestion | None:
        raw = self._redis.get(f"quiz:session:{session_id}")
        if raw is None:
            return None
        data = json.loads(raw)
        if data["user_id"] != user_id:
            return None
        for q in data["questions"]:
            if q["question_id"] == question_id:
                return StoredQuestion(**q)
        return None
```

**Factory function** replaces the module-level singleton:

```python
def make_quiz_session_store(redis_url: str | None) -> QuizSessionStore | RedisQuizSessionStore:
    if redis_url:
        try:
            store = RedisQuizSessionStore(redis_url)
            store._redis.ping()  # verify connectivity at startup
            log.info("Quiz session store: Redis (%s)", redis_url)
            return store
        except Exception:
            log.warning(
                "Redis unavailable — falling back to in-process session store. "
                "This is UNSAFE for multi-worker deployments.",
                exc_info=True,
            )
    log.warning(
        "REDIS_URL not configured — using in-process session store. "
        "Set REDIS_URL for multi-worker safety."
    )
    return QuizSessionStore()
```

**Wiring in `api/main.py`** lifespan:

```python
redis_url = os.environ.get("REDIS_URL", "").strip() or None
app.state.quiz_session_store = make_quiz_session_store(redis_url)
```

**Dependency:**

```python
# api/deps.py
def get_quiz_session_store(request: Request):
    return request.app.state.quiz_session_store
```

`api/routers/quiz.py` injects the store via dependency instead of using the
module-level singleton.

**`redis` must be added to `requirements.txt`.**  Add:
```
redis[hiredis]>=5.0
```

`compose.yml` already contains a Redis service (used by Langfuse stack).  The
API service should add its own `REDIS_URL` env var pointing at the shared Redis
instance.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `api/quiz_session_store.py` — add `RedisQuizSessionStore`, `make_quiz_session_store` |
| Modify | `api/main.py` — call `make_quiz_session_store`, store on `app.state` |
| Modify | `api/deps.py` — add `get_quiz_session_store` |
| Modify | `api/routers/quiz.py` — inject store via dependency |
| Modify | `requirements.txt` — add `redis[hiredis]>=5.0` |
| Modify | `compose.yml` — add `REDIS_URL` env var to `api` service |
| Modify | `tests/test_quiz_session.py` — ensure tests still pass against the in-process store |

### Implementation Steps

1. Add `redis[hiredis]>=5.0` to `requirements.txt`.

2. Add `RedisQuizSessionStore` and `make_quiz_session_store` to
   `api/quiz_session_store.py`.  Keep `QuizSessionStore` (in-process) intact
   for the fallback path and tests.

3. In `api/main.py` lifespan, read `REDIS_URL` from environment, call
   `make_quiz_session_store(redis_url)`, and store the result on `app.state`.

4. In `api/deps.py`, add:
   ```python
   def get_quiz_session_store(request: Request):
       return request.app.state.quiz_session_store
   ```

5. In `api/routers/quiz.py`, inject the store:
   ```python
   store = Depends(get_quiz_session_store)
   ```
   and remove all direct references to the module-level `quiz_session_store`
   singleton.

6. In `compose.yml`, add to the `api` service environment:
   ```yaml
   REDIS_URL: redis://redis:6379/0
   ```
   Verify a `redis` service already exists or add one (the Langfuse stack
   includes Redis; confirm the service name and reuse it).

7. Existing `tests/test_quiz_session.py` tests construct `QuizSessionStore()`
   directly — they continue to pass unchanged.  Add one integration-style test
   that verifies two separate store instances (simulating two workers) both
   return the correct question when backed by the same Redis connection.

---

## SEVERE-3 — Lock the article cache read-modify-write cycle {#severe-3}

### Problem

`processor/tools/article_fetcher.py` performs a read-modify-write on
`state/article_cache.json` without any file lock.  Two pipeline processes
running concurrently will each read a stale snapshot and the later writer
will silently overwrite the earlier writer's cache entries.  The `state.py`
module in the same codebase uses `filelock` for exactly this problem.

### Target State

The entire `fetch_relevant_articles` function acquires the same `filelock`
pattern before reading the cache and holds it until after the cache is saved.

```python
# processor/tools/article_fetcher.py

from processor.tools.file_ops import _lock_path  # or duplicate the pattern inline

def fetch_relevant_articles(links, llm, model):
    cache_path = _get_cache_path()
    lock_path = cache_path.with_suffix(".lock")

    if _HAVE_FILELOCK:
        ctx = _FileLock(str(lock_path), timeout=30.0)
    else:
        ctx = contextlib.nullcontext()

    with ctx:
        cache = _load_cache()
        result = _classify_and_fetch(links, llm, model, cache)
        _save_cache(cache)

    return result
```

The internal classification and fetch logic is extracted into a private
`_classify_and_fetch(links, llm, model, cache)` function that mutates the cache
dict in place — no file I/O inside this function.

The `filelock` import and fallback warning pattern should be factored into a
shared utility (e.g., `processor/tools/file_ops.py` `acquire_file_lock(path)`)
rather than repeated in every module that needs it.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/tools/article_fetcher.py` — wrap read-modify-write in filelock |
| Modify | `processor/tools/file_ops.py` — add `acquire_file_lock(path)` helper |

### Implementation Steps

1. In `processor/tools/file_ops.py`, add:
   ```python
   import contextlib
   try:
       from filelock import FileLock as _FileLock
       _HAVE_FILELOCK = True
   except ImportError:
       _HAVE_FILELOCK = False

   def acquire_file_lock(path: Path, timeout: float = 30.0):
       """Return a context manager that holds an advisory file lock.
       Falls back to a no-op context if filelock is not installed."""
       if _HAVE_FILELOCK:
           return _FileLock(str(path.with_suffix(".lock")), timeout=timeout)
       return contextlib.nullcontext()
   ```

2. In `processor/tools/article_fetcher.py`, refactor `fetch_relevant_articles`
   to:
   - call `acquire_file_lock(_get_cache_path())` using the new helper,
   - do both `_load_cache()` and `_save_cache()` inside the lock context,
   - extract classify + fetch logic into `_classify_and_fetch(links, llm,
     model, cache)` that operates only on the in-memory dict.

3. Optionally backport `acquire_file_lock` usage to `processor/state.py` to
   remove the duplicated `filelock` import and fallback warning there.

4. Add a test in `tests/test_article_fetcher.py` that:
   - Spawns two threads both calling `fetch_relevant_articles` with the same
     URL simultaneously,
   - Mocks the LLM and HTTP calls,
   - Asserts the final cache contains exactly one entry for that URL (not zero
     or two), confirming the lock prevents a lost-update race.

---

## SEVERE-4 — Fix `Fact` node idempotency in Neo4j {#severe-4}

### Problem

`processor/tools/graph_rag.py` `index_lesson` uses `CREATE` for `Fact` nodes.
Every re-indexing run (reprocessing, backfill, bug fix re-run) duplicates all
facts for a lesson in the graph.  `clear_lesson` mitigates this for deliberate
re-runs but leaves a data loss window if the delete succeeds and the re-insert
fails.

### Target State

**Facts use `MERGE` with a deterministic ID.**  The ID is a short hash of
`lesson_number + "|" + fact_text` (first 16 hex characters of SHA-256):

```python
import hashlib

def _fact_id(lesson_number: str, fact_text: str) -> str:
    raw = f"{lesson_number}|{fact_text}"
    return hashlib.sha256(raw.encode()).hexdigest()[:16]
```

**Updated Cypher in `index_lesson`:**

```cypher
MERGE (f:Fact {id: $fact_id})
SET f.text = $text, f.lesson_number = $lesson
MERGE (l:Lesson {number: $lesson})
MERGE (l)-[:HAS_FACT]->(f)
```

**Unique constraint on `Fact.id`** added to `ensure_indexes`:

```python
"CREATE CONSTRAINT fact_id IF NOT EXISTS FOR (f:Fact) REQUIRE f.id IS UNIQUE",
```

This makes every `Fact` upsert idempotent regardless of retry or re-run.
`clear_lesson` continues to work — it deletes `Fact` nodes that have
`lesson_number = $lesson`, which `DETACH DELETE` already handles.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/tools/graph_rag.py` — `_fact_id` helper, `ensure_indexes`, `index_lesson` Cypher |

### Implementation Steps

1. Add `_fact_id(lesson_number, fact_text) -> str` helper at module level in
   `graph_rag.py`.

2. In `ensure_indexes`, add:
   ```python
   "CREATE CONSTRAINT fact_id IF NOT EXISTS FOR (f:Fact) REQUIRE f.id IS UNIQUE",
   ```

3. In `index_lesson`, change the fact creation block to use `MERGE` with the
   generated ID and `SET` for mutable fields.

4. Verify the `clear_lesson` query still covers `Fact` nodes: the existing
   `MATCH (l:Lesson {number: $lesson}) OPTIONAL MATCH (l)-[:HAS_FACT]->(f:Fact) DETACH DELETE f`
   pattern continues to work because the `DETACH DELETE` targets the node
   object, not a `CREATE` clause.

5. Write a test that calls `index_lesson` twice with the same fixture artifact,
   then queries the graph and asserts the fact count equals the number of unique
   facts in the fixture (not double).

---

## SEVERE-5 — Batch Neo4j writes with UNWIND {#severe-5}

### Problem

`processor/tools/graph_rag.py` `index_lesson` issues one Neo4j round-trip per
concept, per fact, per chunk, and per chunk-concept mention.  A lesson with 10
concepts, 5 facts, 20 chunks, and moderate concept coverage produces 200+
individual queries.

### Target State

All bulk write operations use `UNWIND` transactions.  The entire `index_lesson`
function is restructured into 5 explicit batch transactions:

1. **Merge lesson node** (single query, unchanged)
2. **Merge concepts** (UNWIND over concept list)
3. **Merge facts** (UNWIND over fact list)
4. **Merge chunks + embeddings** (UNWIND over chunk list)
5. **Merge concept map relationships** (UNWIND over relationship list)
6. **Merge chunk-concept MENTIONS edges** (UNWIND over (chunk_id, concept_name) pairs)

Example for concepts:

```python
concept_params = [
    {
        "name": c.name,
        "definition": c.definition,
        "normalized_key": dedupe_key(c.name),
        "lesson": artifact.lesson_number,
    }
    for c in artifact.summary.key_concepts
]
session.run(
    """
    UNWIND $concepts AS c
    MERGE (concept:Concept {name: c.name})
    SET concept.definition = c.definition,
        concept.normalized_key = c.normalized_key
    MERGE (l:Lesson {number: c.lesson})
    MERGE (l)-[:HAS_CONCEPT]->(concept)
    """,
    concepts=concept_params,
)
```

Example for facts:

```python
fact_params = [
    {"id": _fact_id(artifact.lesson_number, t), "text": t, "lesson": artifact.lesson_number}
    for t in artifact.summary.key_facts
]
session.run(
    """
    UNWIND $facts AS f
    MERGE (fact:Fact {id: f.id})
    SET fact.text = f.text, fact.lesson_number = f.lesson
    MERGE (l:Lesson {number: f.lesson})
    MERGE (l)-[:HAS_FACT]->(fact)
    """,
    facts=fact_params,
)
```

Chunks must be split into two passes: first UNWIND to create/update chunk nodes,
then a second UNWIND to create MENTIONS edges (because UNWIND + MATCH on the
same query can produce large cross-products).

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/tools/graph_rag.py` — refactor `index_lesson` to use UNWIND batches |

### Implementation Steps

1. Extract `_batch_concepts(session, artifact)`, `_batch_facts(session, artifact)`,
   `_batch_chunks(session, chunks, embeddings, artifact)`, and
   `_batch_relationships(session, artifact)` as private helpers within
   `graph_rag.py`.

2. Each helper builds a list of parameter dicts and issues a single
   `session.run(UNWIND_QUERY, params=param_list)`.

3. For embeddings: Neo4j's Bolt protocol accepts lists of floats as parameters.
   Include `embedding` in the chunk param dict only when non-None; use a
   conditional SET in Cypher:
   ```cypher
   SET ch.text = c.text, ch.position = c.position, ch.lesson_number = c.lesson
   SET ch.embedding = CASE WHEN c.embedding IS NOT NULL THEN c.embedding ELSE ch.embedding END
   ```

4. Replace the body of `index_lesson` with calls to the five batch helpers.

5. Run `EXPLAIN` in Neo4j browser against the new queries on a real dataset to
   confirm index usage.

---

## SEVERE-6 — Run pipeline off the watchdog observer thread {#severe-6}

### Problem

`processor/watcher.py` `LessonFileHandler.on_created` calls `pipeline.run()`
directly.  Watchdog runs event handlers on the observer thread.  Blocking the
observer thread for the duration of the pipeline means no other file events can
be processed while a lesson is being processed.

### Target State

The handler submits the filepath to an `asyncio.Queue` (or `queue.Queue` for
the threaded case) and returns immediately.  A dedicated worker thread consumes
the queue and runs the pipeline.

Because the watcher runs in a purely synchronous context (`mindforge.py`
entry point), use `queue.Queue` with a `threading.Thread`:

```python
import queue
import threading

class LessonFileHandler(FileSystemEventHandler):
    def __init__(self, config: AppSettings, llm: LLMClient) -> None:
        super().__init__()
        self._config = config
        self._llm = llm
        self._queue: queue.Queue[Path] = queue.Queue()
        self._worker = threading.Thread(target=self._process_loop, daemon=True)
        self._worker.start()
        self._last_event: dict[str, float] = {}

    def on_created(self, event: FileCreatedEvent) -> None:
        if event.is_directory:
            return
        filepath = Path(event.src_path)
        if filepath.suffix.lower() != ".md":
            return
        now = time.time()
        if now - self._last_event.get(filepath.name, 0) < DEBOUNCE_SECONDS:
            return
        self._last_event[filepath.name] = now
        log.info("Queuing lesson file: %s", filepath.name)
        self._queue.put(filepath)

    def _process_loop(self) -> None:
        while True:
            filepath = self._queue.get()
            if filepath is None:  # sentinel — stop signal
                break
            time.sleep(1.0)  # allow file write to complete
            try:
                pipeline.run(filepath, self._config, self._llm)
            except Exception:
                log.error("Pipeline failed for %s", filepath.name, exc_info=True)
            finally:
                self._queue.task_done()

    def stop(self) -> None:
        self._queue.put(None)  # send sentinel
        self._worker.join()
```

`start_watcher` must call `handler.stop()` during shutdown:

```python
try:
    while observer.is_alive():
        observer.join(timeout=1)
except KeyboardInterrupt:
    observer.stop()
handler.stop()
observer.join()
```

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/watcher.py` — add worker thread queue pattern to `LessonFileHandler` |

### Implementation Steps

1. Add `_queue`, `_worker`, `_process_loop`, and `stop()` to
   `LessonFileHandler` as described.

2. Move the `time.sleep(1.0)` from `on_created` into `_process_loop`
   (runs before `pipeline.run`).

3. Update `start_watcher` to call `handler.stop()` in the finally/interrupt
   handler so the worker thread joins cleanly on shutdown.

4. Update `pipeline.run` signature to accept the separate `AppSettings` and
   `LLMClient` parameters (from CRITICAL-1 fix) instead of `Config`.

---

## MODERATE-1 — Extract flashcard count into a repository abstraction {#moderate-1}

### Problem

`api/routers/lessons.py` `_count_flashcards` navigates the filesystem using
`__file__`, silently swallows all exceptions, and uses a glob pattern that
matches on partial lesson number substrings.

### Target State

A dedicated repository method encapsulates artifact access from the canonical
database store introduced in CRITICAL-8.  The router calls it through the DI
system rather than reading artifact files directly.

**New repository contract:**

```python
class ArtifactRepository(Protocol):
    def get_artifact(self, lesson_id: str) -> LessonArtifact | None: ...
    def count_flashcards(self, lesson_id: str) -> int: ...
```

Key improvements:
- No filesystem navigation from the router.
- No glob-based partial matching.
- No silent JSON decoding paths at request time.
- Count queries operate against the canonical DB artifact or a derived read model.

**Wiring in `api/deps.py`:**

```python
def get_artifact_repository(request: Request) -> ArtifactRepository:
    return request.app.state.artifact_repository
```

**Router update:**

```python
# api/routers/lessons.py
@router.get("")
async def list_lessons(
    driver = Depends(get_neo4j_driver),
    artifact_repo: ArtifactRepository = Depends(get_artifact_repository),
    _user = Depends(require_auth),
):
    ...
    flashcard_count = artifact_repo.count_flashcards(lesson_id)
```

### Files to Touch

| Action | File |
|--------|------|
| Modify | `api/repositories/document_repository.py` — add artifact query helpers |
| Modify | `api/routers/lessons.py` — replace `_count_flashcards` with repository use |
| Modify | `api/deps.py` — add `get_artifact_repository` |

### Implementation Steps

1. Add `get_artifact()` and `count_flashcards()` to the canonical artifact
   repository.

2. Add `get_artifact_repository` to `api/deps.py`.

3. Replace the `_count_flashcards` private function and all its call sites in
   `api/routers/lessons.py` with repository calls.

4. Write a unit test for `count_flashcards` that uses persisted artifact rows,
   including a case where two lessons have overlapping display labels, and
   verifies only the exact `lesson_id` match is returned.

---

## MODERATE-2 — Replace manual `from_dict` with a schema-aware deserializer {#moderate-2}

### Problem

`processor/models.py` `LessonArtifact.from_dict` manually reconstructs every
nested dataclass field.  Every time a field is added to any nested model, the
deserializer must be updated by hand.  Missing updates cause silent data loss
when loading artifacts from disk.

### Target State

Switch to `dacite` for deserialization.  `dacite.from_dict` handles nested
dataclasses, optional fields, and type coercion automatically.

**`requirements.txt` addition:** `dacite>=1.8`

**Updated `LessonArtifact.from_dict`:**

```python
@classmethod
def from_dict(cls, data: dict[str, Any]) -> "LessonArtifact":
    import dacite
    return dacite.from_dict(
        data_class=cls,
        data=data,
        config=dacite.Config(
            strict=False,           # ignore unknown keys (backwards compat)
            cast=[str, int, float], # handle JSON number types
        ),
    )
```

This replaces all 40+ lines of the current manual implementation.
`dacite.Config(strict=False)` ensures old artifacts without new optional fields
still load correctly.

The `SummaryData`, `ConceptMapData`, and all nested classes need no changes —
dacite traverses them automatically.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/models.py` — replace `from_dict` body with dacite call |
| Modify | `requirements.txt` — add `dacite>=1.8` |

### Implementation Steps

1. Add `dacite>=1.8` to `requirements.txt` and install it.

2. Replace the body of `LessonArtifact.from_dict` with the dacite call.

3. Run `pytest tests/` with existing artifact fixture files to verify that
   round-trip serialization (`.to_dict()` → `.from_dict()`) produces equal
   objects.

4. Add a test that verifies loading an artifact dict that is missing an
   optional field (e.g., `concept_map` is absent) does not raise an exception
   and produces `None` for that field.

---

## MODERATE-3 — Eliminate lazy imports inside feature-flag blocks {#moderate-3}

### Problem

`processor/pipeline.py` uses lazy `import` statements inside `if config.enable_X:`
blocks.  Import errors are invisible until runtime under the specific flag
combination.  Static analysis cannot trace the dependency graph.  Testing all
paths requires a 2^9 = 512 flag-combination matrix.

### Target State

All imports are at the top of the module.  Feature-flag guards protect only
the function *calls*, not the imports.  This is the standard Python convention.

```python
# Top of processor/pipeline.py — unconditional imports
from processor.agents.image_analyzer import analyze_images, format_image_descriptions
from processor.agents.flashcard_generator import generate_flashcards
from processor.agents.concept_mapper import generate_concept_map
from processor.validation import validate_artifact
from processor.evals import evaluate_artifact
from processor.tools.knowledge_index import update_index, generate_glossary, generate_cross_references
from processor.tools.graph_rag import GraphConfig, connect, ensure_indexes, index_lesson, clear_lesson, get_known_concepts_from_graph
from processor.tools.chunker import chunk_content
from processor.tools.embeddings import embed_texts
```

The `if config.enable_X:` guards remain around the actual function calls.
Only the `import` statements are moved to module level.

If a module is conditionally available (e.g., `neo4j` is not installed),
guard the import with a try/except at module level and set a module-level
boolean flag:

```python
try:
    from processor.tools.graph_rag import ...
    _GRAPH_RAG_AVAILABLE = True
except ImportError:
    _GRAPH_RAG_AVAILABLE = False
    log.warning("neo4j package not installed — graph-RAG disabled")
```

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/pipeline.py` — move all deferred imports to top of file |

### Implementation Steps

1. Audit every `from ... import ...` inside `if` blocks or function bodies in
   `processor/pipeline.py`.

2. Move each import to the top of the file.  For packages that have optional
   installation (e.g., `langfuse`, `neo4j`, `filelock`), wrap in a
   `try/except ImportError` guard that sets a `_HAVE_X` boolean at module level.

3. Replace inline `from X import Y` checks inside `if` blocks with the
   `_HAVE_X` boolean where needed.

4. Run `python -c "from processor import pipeline"` to confirm the module
   imports cleanly even when optional packages are absent (by mocking or
   using the try/except guards).

---

## MODERATE-4 — Introduce a domain interface (port) for retrieval {#moderate-4}

### Problem

`api/routers/quiz.py`, `api/routers/search.py`, and `discord_bot/cogs/quiz.py`
all import `processor.tools.graph_rag` functions directly.  There is no domain
interface.  Switching backends or unit testing requires monkey-patching the
`graph_rag` module.

### Target State

A `RetrievalPort` abstract interface separates the domain from the infrastructure.
The Neo4j implementation is one adapter.  Tests use a stub adapter.

**New file `processor/retrieval.py`:**

```python
from abc import ABC, abstractmethod
from processor.tools.graph_rag import RetrievalResult  # value object only

class RetrievalPort(ABC):
    @abstractmethod
    def retrieve(self, query: str, *, max_results: int = 10,
                 query_embedding: list[float] | None = None) -> RetrievalResult: ...

    @abstractmethod
    def get_all_concepts(self) -> list[dict]: ...

    @abstractmethod
    def get_lesson_concepts(self, lesson_number: str) -> list[dict]: ...
```

**New file `processor/adapters/neo4j_retrieval.py`:**

```python
from processor.retrieval import RetrievalPort
from processor.tools import graph_rag

class Neo4jRetrievalAdapter(RetrievalPort):
    def __init__(self, driver) -> None:
        self._driver = driver

    def retrieve(self, query, *, max_results=10, query_embedding=None) -> RetrievalResult:
        return graph_rag.retrieve(self._driver, query,
                                  max_results=max_results,
                                  query_embedding=query_embedding)

    def get_all_concepts(self):
        return graph_rag.get_all_concepts(self._driver)

    def get_lesson_concepts(self, lesson_number):
        return graph_rag.get_lesson_concepts(self._driver, lesson_number)
```

**Wiring in `api/main.py` lifespan:**

```python
if driver:
    app.state.retrieval = Neo4jRetrievalAdapter(driver)
else:
    app.state.retrieval = None
```

**In `api/deps.py`:**

```python
def get_retrieval(request: Request) -> RetrievalPort:
    r = request.app.state.retrieval
    if r is None:
        raise HTTPException(503, "Retrieval backend not available")
    return r
```

**Routers** now inject `retrieval: RetrievalPort = Depends(get_retrieval)` and
call `retrieval.retrieve(...)` instead of `graph_rag.retrieve(driver, ...)`.

The same pattern applies to the Discord bot cogs — store a
`Neo4jRetrievalAdapter` on `self.bot.retrieval` in `setup_hook`.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/retrieval.py` |
| Create | `processor/adapters/__init__.py` |
| Create | `processor/adapters/neo4j_retrieval.py` |
| Modify | `api/main.py` — instantiate and store `Neo4jRetrievalAdapter` |
| Modify | `api/deps.py` — add `get_retrieval` |
| Modify | `api/routers/quiz.py` — inject `RetrievalPort` |
| Modify | `api/routers/search.py` — inject `RetrievalPort` |
| Modify | `api/routers/concepts.py` — inject `RetrievalPort` if it uses graph_rag |
| Modify | `discord_bot/bot.py` — store `Neo4jRetrievalAdapter` on `self.retrieval` |
| Modify | `discord_bot/cogs/quiz.py` — use `self.bot.retrieval` |
| Modify | `discord_bot/cogs/search.py` — use `self.bot.retrieval` |

### Implementation Steps

1. Create `processor/retrieval.py` with the abstract `RetrievalPort`.

2. Create `processor/adapters/neo4j_retrieval.py` with `Neo4jRetrievalAdapter`.

3. In `api/main.py`, after connecting the Neo4j driver, construct
   `Neo4jRetrievalAdapter(driver)` and store it as `app.state.retrieval`.

4. In `api/deps.py`, add the `get_retrieval` dependency.

5. Update `api/routers/quiz.py` and `api/routers/search.py` to use the port.
   Remove all direct `graph_rag` imports from the routers.

6. Apply the same changes to Discord cogs.

7. For tests, implement a `StubRetrievalAdapter(RetrievalPort)` in
   `tests/conftest.py` that returns fixture data without a Neo4j connection.
   Update router tests to inject the stub via FastAPI's `app.dependency_overrides`.

---

## MODERATE-5 — Fix allowlist evaluation order in `bot_auth` {#moderate-5}

### Problem

`discord_bot/bot_auth.py` evaluates the allowlist environment variables at
import time, before `load_dotenv` is called in `main()`.  If the module is
imported before the `.env` is loaded, allowlists are silently empty and the bot
falls into unrestricted public mode.

### Target State

The allowlist constants are replaced by a lazy-evaluated singleton that is
populated on first access or on an explicit `reload_auth_config()` call.
`validate_auth_config()` is called after `load_dotenv` and forces the
evaluation.

```python
# bot_auth.py — no module-level _parse_ids calls

_auth_config: "_AuthConfig | None" = None

@dataclass
class _AuthConfig:
    allowed_guild_ids: frozenset[int]
    allowed_role_ids: frozenset[int]
    allowed_user_ids: frozenset[int]
    require_auth: bool

def _load_auth_config() -> _AuthConfig:
    return _AuthConfig(
        allowed_guild_ids=_parse_ids("DISCORD_ALLOWED_GUILD_IDS"),
        allowed_role_ids=_parse_ids("DISCORD_ALLOWED_ROLE_IDS"),
        allowed_user_ids=_parse_ids("DISCORD_ALLOWED_USER_IDS"),
        require_auth=os.environ.get("DISCORD_REQUIRE_AUTH", "true").lower()
                     not in ("0", "false", "no"),
    )

def get_auth_config() -> _AuthConfig:
    global _auth_config
    if _auth_config is None:
        _auth_config = _load_auth_config()
    return _auth_config

def validate_auth_config() -> None:
    cfg = get_auth_config()
    any_allowlist = bool(cfg.allowed_guild_ids or cfg.allowed_role_ids or cfg.allowed_user_ids)
    if cfg.require_auth and not any_allowlist:
        raise RuntimeError(
            "DISCORD_REQUIRE_AUTH is true but no allowlist is configured. "
            "Set at least one of DISCORD_ALLOWED_GUILD_IDS, DISCORD_ALLOWED_ROLE_IDS, "
            "or DISCORD_ALLOWED_USER_IDS, or set DISCORD_REQUIRE_AUTH=false."
        )
```

`ALLOWED_GUILD_IDS` (used in `bot.py` for guild-scoped command sync) becomes a
property that calls `get_auth_config().allowed_guild_ids`.

`validate_auth_config()` must be called in `bot.py setup_hook` **after**
`load_dotenv` and before cog loading — which it already is.  The call order
is now correct regardless of import order since evaluation is deferred.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `discord_bot/bot_auth.py` — replace module-level evaluation with lazy singleton |
| Modify | `discord_bot/bot.py` — update any direct access to `ALLOWED_GUILD_IDS` |

### Implementation Steps

1. Replace the three module-level `ALLOWED_*_IDS = _parse_ids(...)` and
   `REQUIRE_AUTH = ...` evaluations with the lazy `_AuthConfig` singleton
   pattern described above.

2. Expose `ALLOWED_GUILD_IDS` as a module-level accessor property or simple
   function `get_allowed_guild_ids()` to keep `bot.py` working.
   Update `discord_bot/bot.py` usages from `ALLOWED_GUILD_IDS` to
   `get_allowed_guild_ids()`.

3. Add a unit test that:
   - patches `os.environ` to set `DISCORD_ALLOWED_GUILD_IDS=12345` *after*
     the module is first imported with no env vars set,
   - calls `reload_auth_config()` (add this function to reset `_auth_config`),
   - asserts `get_auth_config().allowed_guild_ids == frozenset({12345})`.

---

## MODERATE-6 — Rename Polish-language `Config` path fields {#moderate-6}

### Problem

`Config` (and consequently `AppSettings` after the CRITICAL-1 fix) contains
Polish-language field names: `nowe_dir`, `podsumowane_dir`, `archiwum_dir`.
These are opaque to non-Polish contributors and make the codebase's interface
language inconsistent.

### Target State

The `AppSettings` dataclass (created in CRITICAL-1) uses English field names
from the start.  This fix is implemented as part of the CRITICAL-1 refactor
— not as a separate post-hoc rename — to avoid two waves of call site changes.

| Old name | New name | Meaning |
|----------|----------|---------|
| `nowe_dir` | `new_dir` | Inbound lessons waiting to be processed |
| `podsumowane_dir` | `summaries_dir` | Generated summary markdown files |
| `archiwum_dir` | `archive_dir` | Processed source lesson files |

The actual **directory names on disk** (`nowe/`, `podsumowane/`, `archiwum/`)
do not need to change — they are user-facing folders, not code identifiers.
Only the Python attribute names change.

### Files to Touch

This is a mechanical rename applied to all files in CRITICAL-1.  No additional
files beyond those listed in CRITICAL-1.

### Implementation Steps

1. When creating `processor/settings.py` (CRITICAL-1 step 1), use the English
   names from the start.

2. In `load_settings()`, map env-var-configured paths to the new field names.

3. Perform a global `grep -r "nowe_dir\|podsumowane_dir\|archiwum_dir"` to
   find all remaining usages and update them.  Expect hits in:
   - `processor/pipeline.py`
   - `processor/watcher.py`
   - `mindforge.py`
   - `backfill.py`
   - `tests/` fixtures

---

## CRITICAL-6 — Make lesson identity explicit and immutable {#critical-6}

### Problem

`processor/models.py` derives `lesson_number` from `source_filename` using a
single `sXXeYY` regex and falls back to the literal string `"unknown"` for
everything else.  That derived field is then reused as the persisted identity
for:
- artifact filenames (`state/artifacts/{lesson_number}.json`),
- study-pack filenames,
- Neo4j `Lesson` nodes,
- chunk IDs and flashcard tags,
- API lesson filtering.

Both upload surfaces accept arbitrary sanitised `.md` filenames, so two valid
uploads that do not match the regex silently collapse into the same persisted
identity.  This is data loss, not a cosmetic naming issue.

### Target State

Lesson identity is resolved once at intake and then treated as immutable.

**New identity model:**

```python
@dataclass(frozen=True)
class LessonIdentity:
     lesson_id: str                 # canonical primary key, always present
     lesson_number: str | None      # optional human-facing label, e.g. S01E01

def resolve_lesson_identity(
     source_filename: str,
     metadata: dict[str, Any],
) -> LessonIdentity:
     """Resolve the canonical persisted identity for a lesson.

     Priority:
     1. metadata["lesson_id"]
     2. metadata["lesson_number"]
     3. sanitised filename stem
     Raises ValueError if no stable identifier can be produced.
     """
     ...
```

**Rules:**

- `lesson_id` is the stable business key on the canonical document record and
    the graph key everywhere.
- `lesson_number` is optional metadata for display and filtering only.
- The sentinel `"unknown"` is deleted from the model entirely.
- No regex or filename convention is ever allowed to act as a primary key,
  deduplication key, or processing guard.
- Uploads that cannot produce a stable `lesson_id` are rejected at intake,
  not allowed to proceed with a fake placeholder.

**Persistence changes:**

- Canonical document row: `documents.lesson_id` is unique
- Internal persistence key may be a separate surrogate `document_id`, but it is
    never derived from a filename convention
- Neo4j lesson node key: `(:Lesson {id: lesson_id})`
- Chunk and flashcard IDs derive from `lesson_id`, not `lesson_number`

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/models.py` — add `LessonIdentity`, `lesson_id`, resolver |
| Modify | `processor/pipeline.py` — carry `lesson_id` through artifact creation and output publishing |
| Modify | `api/repositories/document_repository.py` — persist `lesson_id` as a unique business key |
| Modify | `processor/tools/graph_rag.py` — key lesson nodes and dependent data by `lesson_id` |
| Modify | `processor/renderers.py` — render `lesson_number` as metadata only |
| Modify | `api/schemas.py` — expose `lesson_id` where the UI needs a stable handle |
| Modify | `api/routers/lessons.py` — stop assuming `lesson_number` is the storage key |
| Modify | `api/routers/flashcards.py` — use `lesson_id` for card identity |
| Modify | `backfill.py` — locate stored artifacts by `lesson_id`, not guessed lesson number |
| Create | `scripts/migrate_lesson_identity.py` |
| Modify | `tests/` — cover non-`SxxExx` filenames and migration cases |

### Implementation Steps

1. Add `lesson_id: str` to `LessonArtifact` and make `lesson_number: str | None` optional metadata.

2. Create `resolve_lesson_identity()` in `processor/models.py` and stop using
    regex extraction as a persisted primary key.

3. Update all artifact, study-pack, graph, chunk, and flashcard writers to use
    `lesson_id` as the canonical key.

4. Update API responses and frontend models so lesson actions can target
    `lesson_id` directly while still displaying `lesson_number` when present.

5. Add ingestion validation in both upload surfaces so an unresolved identity
    fails fast with a clear error.

6. Write `scripts/migrate_lesson_identity.py` to backfill `lesson_id` into
    existing persisted records.  Any existing collisions (`unknown`, duplicate
    stems) should be written to a quarantine report for manual resolution
    instead of guessed away.

7. Update Neo4j indexing and retrieval code to key `Lesson` nodes by `id` and
    treat `number` as optional display metadata.

8. Add regression tests that upload two valid non-`SxxExx` filenames and prove
    they no longer overwrite each other in the canonical store or in the graph.

---

## CRITICAL-7 — Add content-hash deduplication and idempotent ingestion {#critical-7}

### Problem

The current duplicate-processing guard in `processor/state.py` is keyed only by
filename.  That prevents the exact same filename from running twice in one
workspace, but it does **not** prevent the same document content from being:

- uploaded twice under different filenames,
- uploaded once through the API and again through Discord,
- re-ingested after a rename,
- reprocessed after being copied between `new/`, `archive/`, and backfill flows.

That is a direct cost leak because the expensive part of the system is not the
filename, it is the content sent to the LLMs.

### Target State

All ingestion paths are idempotent by content hash, not by filename.

**Canonical deduplication key:**

```python
def content_sha256(raw_bytes: bytes) -> str:
     return hashlib.sha256(raw_bytes).hexdigest()
```

**Document registry:**

```python
documents(
     document_id UUID PRIMARY KEY,
    lesson_id TEXT NOT NULL UNIQUE,
     content_sha256 CHAR(64) NOT NULL UNIQUE,
     source_filename TEXT NOT NULL,
     upload_source TEXT NOT NULL,
     uploaded_by TEXT NULL,
     status TEXT NOT NULL,
     current_task_id UUID NULL,
     created_at TIMESTAMPTZ NOT NULL,
     updated_at TIMESTAMPTZ NOT NULL
)
```

**Ingestion rules:**

- Compute `content_sha256` before queueing or processing.
- If a document with the same hash is already `done`, return the existing
  `document_id` and skip processing.
- If a document with the same hash is already `pending` or `running`, return
  the existing task status instead of enqueuing another pipeline run.
- `force_reprocess` is an explicit administrative action against the existing
  stored document, never an accidental side effect of renaming a file.

`processor/state.py` `processed.json` is removed once the registry is live.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/document_registry.py` |
| Create | `api/document_ingestion_service.py` |
| Modify | `api/routers/lessons.py` — hash before submit; return existing document/task on duplicates |
| Modify | `discord_bot/cogs/upload.py` — use the same ingestion service |
| Modify | `mindforge.py` — watcher submits through the registry, not raw filename state |
| Modify | `backfill.py` — dedupe by stored document hash or document ID |
| Modify | `processor/pipeline.py` — run by `document_id`, not by filename claim |
| Modify | `processor/state.py` — delete or replace filename-based processed tracking |
| Modify | `tests/` — add duplicate upload coverage across API/Discord/watcher paths |

### Implementation Steps

1. Create a document registry table with a unique constraint on `content_sha256`.

2. Introduce a single ingestion service used by API uploads, Discord uploads,
    watcher ingestion, and backfill.  That service computes the content hash
    before any expensive work starts.

3. Replace filename-based `claim_for_processing()` with a database-backed claim
    or row lock keyed by `document_id` / `content_sha256`.

4. Update the upload APIs to return one of three outcomes:
    - new document accepted and queued,
    - existing processed document returned,
    - existing in-flight task returned.

5. Remove all assumptions that duplicate prevention depends on a naming format
    such as `SXXEXX` or any other filename convention.

6. Add tests that submit the same bytes under two different filenames and prove
    the second submission does not trigger a second pipeline run.

7. Add tests that submit the same content through two different surfaces
    (for example API and Discord) and verify they resolve to one canonical
    `document_id`.

---

## CRITICAL-8 — Replace filesystem persistence with a database-backed document repository {#critical-8}

### Problem

MindForge currently persists business data across a sprawl of local directories
and JSON files:

- original uploads in `new/` and `archive/`,
- generated outputs in `summarized/`, `flashcards/`, `diagrams/`, `knowledge/`,
- canonical artifacts in `state/artifacts/*.json`,
- pipeline state in `state/processed.json` and `state/study_packs/*.json`.

This violates the requirement that original and generated documents must end up
in a database, and it makes the runtime depend on shared local disk semantics.

### Target State

The application database is the canonical persistence layer for:

- the original uploaded document,
- the structured canonical artifact,
- generated summaries / flashcards / concept maps,
- processing status and checkpoints,
- any read-model projections needed by the GUI and Discord bot.

Neo4j remains a derived retrieval graph, not the archive.  The local filesystem
may be used only for transient temporary files during parsing or upload, and any
such files must be deleted after ingestion.  No user-facing or business-critical
document is persisted under repo directories.

**Suggested canonical schema (PostgreSQL):**

```sql
documents(
     document_id UUID PRIMARY KEY,
    lesson_id TEXT NOT NULL UNIQUE,
     content_sha256 CHAR(64) NOT NULL UNIQUE,
     source_filename TEXT NOT NULL,
     mime_type TEXT NOT NULL,
     original_content TEXT NOT NULL,
     uploaded_by TEXT NULL,
     uploaded_at TIMESTAMPTZ NOT NULL,
     status TEXT NOT NULL,
     latest_artifact_version INT NOT NULL DEFAULT 0
)

document_artifacts(
     document_id UUID NOT NULL REFERENCES documents(document_id),
     version INT NOT NULL,
     artifact_json JSONB NOT NULL,
     summary_json JSONB NULL,
     flashcards_json JSONB NULL,
     concept_map_json JSONB NULL,
     validation_json JSONB NULL,
     completed_step TEXT NULL,
     created_at TIMESTAMPTZ NOT NULL,
     PRIMARY KEY (document_id, version)
)
```

Rendered markdown / TSV / Mermaid are optional cached projections in the same
database, or can be rendered on demand from the structured artifact.  They are
not written to files on disk.

**Architectural consequences:**

- `pipeline.run()` operates on `document_id`, not `Path`.
- API and Discord read generated content from repositories or read models, not
  from `state/artifacts/` or output folders.
- The file watcher becomes an ingestion adapter for local development only:
  it reads a local file, stores it in the database, then removes the local copy.

This section supersedes the filesystem assumptions in CRITICAL-3,
CRITICAL-5, MODERATE-1, and MODERATE-9.

### Files to Touch

| Action | File |
|--------|------|
| Create | `api/db.py` |
| Create | `api/repositories/document_repository.py` |
| Create | `api/repositories/read_model_repository.py` |
| Create | `scripts/migrate_filesystem_documents_to_db.py` |
| Modify | `compose.yml` — add a dedicated application database or schema |
| Modify | `api/main.py` — initialize database connections and repositories |
| Modify | `processor/pipeline.py` — load source and persist outputs via repositories |
| Modify | `processor/tools/file_ops.py` — delete persistent artifact/output writers or reduce them to temp-only utilities |
| Modify | `api/routers/lessons.py` — store uploads directly in the database |
| Modify | `discord_bot/cogs/upload.py` — store uploads directly in the database |
| Modify | `api/routers/flashcards.py` — read from repositories / read models |
| Modify | `api/routers/concepts.py` — stop crawling artifact files |
| Modify | `mindforge.py` and `processor/watcher.py` — ingest file contents into the DB and remove local copies |
| Modify | `tests/` — migrate fixtures away from disk-backed artifacts |

### Implementation Steps

1. Choose a transactional application database as the canonical store.  A
    dedicated PostgreSQL database is the default recommendation.

2. Create repositories for documents, artifacts, checkpoints, and read models.
    Keep Neo4j as a derived projection fed from the canonical DB-backed artifact.

3. Change all upload paths to write the original document directly into the
    database before any pipeline work begins.

4. Change `pipeline.run()` to load the original document from the repository
    and persist every generated output back to the repository instead of writing
    summary, flashcard, diagram, or artifact files.

5. Replace all GUI / Discord read paths that currently inspect local folders or
    JSON files with repository or read-model queries.

6. Provide a one-time migration script that imports `new/`, `archive/`,
    `summarized/`, `flashcards/`, `diagrams/`, `knowledge/`, and
    `state/artifacts/` into the database, preserving provenance and timestamps
    where possible.

7. Delete the repo-root persistence directories from the live runtime contract
    once migration is complete.  They can remain only as import/export tooling,
    not as application state.

---

## SEVERE-7 — Rebuild spaced repetition around user-scoped stable card IDs {#severe-7}

### Problem

The spaced-repetition subsystem is architecturally wrong in three separate ways:

1. `api/routers/flashcards.py` stores all review progress in a single global
   `state/sr_state.json`, so every authenticated user shares one queue.
2. Card IDs are derived from `lesson_number + array_index`, so reprocessing a
   lesson can silently remap old review history onto different card content.
3. The load-modify-save cycle has no cross-process lock or transaction, so
   concurrent reviews can drop updates.

`discord_bot/cogs/notifications.py` then reads the same global state file and
publishes a single aggregate due count, which leaks study state across users.

### Target State

Split flashcard *definition* from user-specific *progress*.

**Artifact change:**

```python
@dataclass
class FlashcardData:
    id: str
    front: str
    back: str
    card_type: str
    tags: list[str]
```

`FlashcardData.id` is deterministic and content-based:

```python
def flashcard_id(lesson_id: str, front: str, back: str, card_type: str) -> str:
    normalized = "|".join([lesson_id, card_type, normalize(front), normalize(back)])
    return hashlib.sha256(normalized.encode()).hexdigest()[:16]
```

**Progress store:**

```python
class StudyProgressRepository(Protocol):
    def load_many(self, user_id: str, card_ids: list[str]) -> dict[str, CardState]: ...
    def save(self, user_id: str, card_state: CardState) -> None: ...
    def due_count(self, user_id: str, *, today: date | None = None) -> int: ...
```

Use a transaction-safe store (`SQLite` by default in `state/study_progress.sqlite3`).
The key is `(user_id, card_id)`, not just `card_id`.

**Notification rule:**

The Discord bot must stop reading a global due-count file.  Reminders become
per-user (DM or user-targeted lookup) or remain disabled until a proper user
notification design exists.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/models.py` — add `FlashcardData.id` |
| Modify | `processor/agents/flashcard_generator.py` — generate deterministic card IDs |
| Modify | `processor/pipeline.py` — preserve flashcard IDs in the artifact |
| Create | `api/study_progress_store.py` |
| Modify | `api/sr_engine.py` — keep only the pure SM-2 algorithm |
| Modify | `api/deps.py` — provide the progress repository |
| Modify | `api/routers/flashcards.py` — scope reads/writes by authenticated user |
| Modify | `discord_bot/cogs/notifications.py` — remove global shared due-count reads |
| Modify | `tests/` — add reorder-stability and concurrent-review coverage |

### Implementation Steps

1. Add a deterministic `id` field to `FlashcardData` and stop deriving card
   identity from list position at request time.

2. Extract the SM-2 math into a store-agnostic module and create a separate
   `StudyProgressRepository` abstraction for persistence.

3. Implement `SQLiteStudyProgressRepository` keyed by `(user_id, card_id)`.
   Use real SQL transactions; do not replace the current JSON file with a
   different unlocked JSON layout.

4. Update the flashcard API so `get_due`, `get_all`, and `review` all inject
   the authenticated user ID and never touch another user's state.

5. Change Discord reminders to query due counts for specific users only or
   disable the reminder feature until per-user delivery is implemented.

6. Provide a best-effort migration for existing progress only when a stored
   card can be matched to an unchanged deterministic `card_id`.  If the old
   positional ID cannot be mapped safely, drop it rather than attaching it to
   the wrong card.

7. Add tests proving that:
   - two users reviewing the same card keep independent progress,
   - reordering flashcards in an artifact does not change card identity,
   - concurrent review writes do not lose updates.

---

## SEVERE-8 — Separate lesson evidence from the global Neo4j projection {#severe-8}

### Problem

`processor/tools/graph_rag.py` mixes lesson-local assertions into global graph
state.  Specifically:

- `Concept.definition` is overwritten globally during each lesson index run,
  so the last lesson wins.
- `RELATES_TO` edges are merged globally with no lesson provenance.
- `clear_lesson()` deletes chunks, facts, and `HAS_CONCEPT` edges, but it does
  not remove or recompute concept-map relationships and global definitions that
  were written by the deleted lesson.

The result is a graph that drifts over time and cannot be cleanly repaired by
re-indexing one lesson.

### Target State

Store lesson evidence separately from global projections.

**Lesson-owned assertions:**

```cypher
(:Lesson {id})-[:ASSERTS_CONCEPT {definition, confidence}]->(:Concept {key, name})
(:Lesson)-[:ASSERTS_RELATION]->(:RelationAssertion {
     source_key, target_key, label, description
})
```

**Derived projection:**

- `Concept.primary_definition` is computed from lesson assertions, not blindly
  overwritten by one lesson.
- Canonical `RELATES_TO` edges are rebuilt from `RelationAssertion` nodes and
  carry support metadata such as `support_count` or `source_lessons`.

`clear_lesson()` removes only lesson-owned assertions, then rebuilds the global
projection.  Retrieval uses the projection; provenance-aware views can still
drill into the lesson assertions when needed.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/tools/graph_rag.py` — add lesson-owned assertion model and projection rebuild |
| Modify | `processor/pipeline.py` — call projection rebuild after indexing / clearing |
| Modify | `api/routers/concepts.py` — read graph data from the new projection/provenance model |
| Modify | `processor/agents/summarizer.py` — query only canonical projection data for prior-concept context |
| Modify | `tests/` — add re-index and clear/rebuild regression coverage |

### Implementation Steps

1. Stop writing lesson-specific definitions and relationship semantics directly
    onto global concept nodes and edges.

2. Introduce lesson-owned `ASSERTS_CONCEPT` data and `RelationAssertion` nodes
    or equivalent lesson-scoped relationship evidence.

3. Add a projection rebuild helper that computes canonical concept definitions
    and `RELATES_TO` edges from the full assertion set.

4. Update `clear_lesson()` to delete lesson-owned assertions and rebuild the
    projection immediately afterward.

5. Update retrieval and concept-graph queries so they read from the canonical
    projection while preserving a path to show lesson provenance.

6. Backfill the existing graph by re-indexing lessons into the new schema and
    dropping stale projection-only relationships that cannot be traced to a
    source lesson.

7. Add tests that index lesson A, then lesson B with a different definition or
    relation for the same concept, then clear lesson B and prove the projection
    returns to lesson A's surviving evidence.

---

## SEVERE-9 — Centralize auth and security policy settings at startup {#severe-9}

### Problem

MindForge has split-brain runtime configuration:

- `api/main.py` successfully starts because `processor.llm_client.load_config()`
  reads `.env` directly.
- `api/auth.py` reads OAuth/JWT secrets from raw `os.environ` at request time,
  so the API can boot and then fail login or callback at runtime.
- `processor/tools/egress_policy.py` reads SSRF-policy flags once at import
  time, so `.env`-driven policy changes can be ignored depending on import
  order.

This is inconsistent startup behaviour in a security-sensitive part of the
system.

### Target State

All auth and egress policy settings are loaded once, validated once, and then
injected.

**New settings layer:**

```python
@dataclass(frozen=True)
class AuthSettings:
     discord_client_id: str
     discord_client_secret: str
     discord_redirect_uri: str
     jwt_secret: str
     allowed_discord_user_id: str | None
     secure_cookies: bool

@dataclass(frozen=True)
class EgressSettings:
     public_mode: bool
     allow_private: bool
     allow_nonstandard_ports: bool
```

`api/main.py` (or a shared settings module) loads these from `.env` / process
environment during startup and stores them on `app.state`.

`api/auth.py` stops calling `_get_env()` inside request handlers.

`processor/tools/egress_policy.py` becomes an `EgressPolicy` object configured
from `EgressSettings`, not a module with frozen import-time globals.

### Files to Touch

| Action | File |
|--------|------|
| Create | `api/settings.py` |
| Modify | `api/main.py` — load and validate auth / egress settings on startup |
| Modify | `api/auth.py` — replace ad-hoc env reads with injected settings |
| Modify | `processor/tools/egress_policy.py` — replace module globals with `EgressPolicy` |
| Modify | `processor/tools/article_fetcher.py` — accept or resolve the configured `EgressPolicy` |
| Modify | `tests/test_auth.py` |
| Modify | `tests/test_egress_policy.py` |

### Implementation Steps

1. Create `AuthSettings` and `EgressSettings` dataclasses and a single loader
    that reads `.env` / environment once at startup.

2. Make API startup fail fast when required OAuth or JWT secrets are missing,
    instead of allowing partial startup and late runtime failure.

3. Replace `_get_env()` and `_is_secure_cookie()` in `api/auth.py` with a
    dependency that returns the validated `AuthSettings` object.

4. Refactor `processor/tools/egress_policy.py` into an instance-based policy
    object so the active rules are explicit and testable.

5. Update article fetching and any other outbound HTTP call sites to use the
    configured `EgressPolicy` instance rather than module-level globals.

6. Add tests proving that `.env`-provided auth and egress settings are honoured
    without relying on module import order or external shell exports.

---

## MODERATE-7 — Honor Neo4j database selection end-to-end {#moderate-7}

### Problem

`processor/tools/graph_rag.py` defines `GraphConfig.database`, but no graph
operation actually uses it.  Every helper opens `driver.session()` against the
default database.  The application therefore advertises a configuration knob it
does not honour.

### Target State

Graph access is database-bound by construction.

```python
class Neo4jContext:
     def __init__(self, driver: Driver, database: str) -> None:
          self._driver = driver
          self._database = database

     @contextmanager
     def session(self):
          with self._driver.session(database=self._database) as session:
                yield session
```

All graph helpers (`ensure_indexes`, `retrieve`, `index_lesson`, concept list
queries, etc.) accept `Neo4jContext` or equivalent instead of a raw driver.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/tools/neo4j_context.py` |
| Modify | `processor/tools/graph_rag.py` — use database-bound sessions everywhere |
| Modify | `api/main.py` — construct the context with `neo4j_database` |
| Modify | `processor/pipeline.py` — pass the configured database for indexing |
| Modify | `discord_bot/bot.py` — pass the configured database |
| Modify | `quiz_agent.py` — pass the configured database |
| Modify | `tests/` — verify `driver.session(database=...)` is called |

### Implementation Steps

1. Introduce a single database-bound context/factory object for Neo4j sessions.

2. Replace every raw `driver.session()` call in graph code with that context.

3. Thread `neo4j_database` from startup settings into API, CLI, bot, and
    pipeline composition roots.

4. Add a regression test with a mocked driver proving that every session is
    opened against the configured database.

---

## MODERATE-8 — Package the application and remove `sys.path` surgery {#moderate-8}

### Problem

Multiple entry points and cogs mutate `sys.path` at runtime to make imports
work.  This hides packaging problems, weakens static analysis, and makes import
behaviour depend on which file is used to launch the process.

### Target State

MindForge is an installable Python project with declared entry points.

**New packaging baseline:**

```toml
[project]
name = "mindforge"

[project.scripts]
mindforge-pipeline = "mindforge:main"
mindforge-backfill = "backfill:main"
mindforge-quiz = "quiz_agent:main"
mindforge-discord = "discord_bot.bot:main"
```

All modules import through normal package resolution.  No runtime path mutation
remains in production code.

### Files to Touch

| Action | File |
|--------|------|
| Create | `pyproject.toml` |
| Modify | `mindforge.py` — remove `sys.path` mutation |
| Modify | `backfill.py` — remove `sys.path` mutation |
| Modify | `quiz_agent.py` — remove `sys.path` mutation |
| Modify | `api/main.py` — remove `sys.path` mutation |
| Modify | `discord_bot/bot.py` — remove `sys.path` mutation |
| Modify | `discord_bot/cogs/*.py` — remove `sys.path` mutation |
| Modify | `Dockerfile` — install the project in the image |
| Modify | `README.md` and `scripts/` — use package entry points consistently |

### Implementation Steps

1. Add `pyproject.toml` and install MindForge in editable mode for local
    development (`pip install -e .`).

2. Standardise entry points on module execution or console scripts; do not rely
    on importing files from arbitrary working directories.

3. Remove every `sys.path.insert(...)` workaround from application code and
    tests once the package resolves correctly.

4. Update Docker and startup scripts to use the packaged entry points so local,
    test, and container runtimes all import the code the same way.

5. Add a smoke test that imports the major entry-point modules in a fresh
    environment without any path mutation.

---

## MODERATE-9 — Build explicit read models for API projections {#moderate-9}

### Problem

The API still serves several endpoints by rescanning artifact data directly and
mixing low-level persistence I/O with graph queries inside routers:

- `api/routers/lessons.py` does an N+1 lesson query and ad-hoc artifact lookup.
- `api/routers/concepts.py` reaches into artifact storage to enrich node
    groups and colours.
- `api/routers/flashcards.py` rehydrates the flashcard catalogue on every
    request instead of reading a projection.

MODERATE-1 removes one bad helper, but it does not address the broader absence
of explicit read models.

### Target State

Publish read-optimised projections from the canonical artifact.

**Example projection model:**

```python
@dataclass
class LessonProjection:
     lesson_id: str
     lesson_number: str | None
     title: str
     processed_at: str
     flashcard_count: int
     concept_map_groups: list[dict[str, Any]]
```

The pipeline updates projections whenever it persists an artifact in the
canonical database.  API routers read those projections through a repository,
never by walking local folders or rehydrating artifacts on every request.
Graph-derived counts should come from one aggregated query, not one query per
lesson.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/read_models.py` |
| Create | `processor/read_model_store.py` |
| Modify | `processor/pipeline.py` — publish/update read models after artifact flush |
| Modify | `api/deps.py` — provide projection repositories |
| Modify | `api/routers/lessons.py` — replace N+1 + filesystem scan flow |
| Modify | `api/routers/concepts.py` — remove direct artifact spelunking |
| Modify | `api/routers/flashcards.py` — read flashcard catalogue from projection store |
| Modify | `tests/` — add projection update and query coverage |

### Implementation Steps

1. Define projection dataclasses for the API views that are currently assembled
    from ad-hoc artifact and graph reads.

2. Create a projection store/repository and update it as part of pipeline
    publish, not per request.

3. Replace filesystem access and ad-hoc artifact rehydration inside routers
   with repository lookups.

4. Collapse the lessons list endpoint to one aggregated graph query plus one
    projection lookup pass; do not issue one graph query per lesson.

5. Remove silent `except Exception: pass` behaviour from projection reads so
    malformed data fails loudly in the place it is produced.

6. Add tests proving that the concept graph endpoint and flashcards endpoint no
    longer need to rescan local artifact files or rehydrate full artifacts on
    each request.

---

## SEVERE-10 — AI Gateway with provider abstraction {#severe-10}

### Problem

`LLMClient` in `processor/llm_client.py` is hard-coupled to a single
OpenAI-compatible API endpoint.  Switching providers (e.g. Anthropic, Google,
local models) requires modifying the client class itself.  There is no unified
layer for:

- connection management and pooling,
- per-request settings (temperature, tokens, retry policy),
- provider-specific parameter mapping,
- cost tracking across heterogeneous models,
- automatic fallback between providers.

CRITICAL-1 splits the config and CRITICAL-4 adds an async variant, but neither
addresses the fundamental coupling to one provider format.

Additionally, the browser-facing API must not expose raw model access.  All
client-to-model interactions should go through purpose-built endpoints with
fixed input/output schemas (e.g. `/api/quiz/answer`, `/api/search/query`),
never through a generic `/api/chat`.

### Target State

A centralized AI Gateway layer sits between all application code and external
LLM providers.  The gateway:

1. Exposes a single unified interface for completions and embeddings.
2. Maps requests to provider-specific formats (OpenAI, Anthropic, etc.).
3. Manages connection lifecycle, pooling, timeouts, and retry.
4. Records cost, tokens, latency per call for observability.
5. Supports dynamic model selection and provider fallback.

**Implementation approach — choose one:**

- **LiteLLM**: Drop-in proxy supporting 100+ providers.  Minimal code change.
  ```python
  from litellm import acompletion
  response = await acompletion(model="anthropic/claude-3", messages=[...])
  ```
- **AI SDK adapter**: Build a thin adapter layer with a `Provider` protocol.
- **Custom format mapping**: Own API format mapped to provider-specific
  payloads; full control, higher maintenance.

**Provider protocol (if custom or AI SDK):**

```python
class LLMProvider(Protocol):
    async def complete(
        self, *, model: str, messages: list[dict],
        temperature: float, response_format: dict | None,
    ) -> CompletionResult: ...

    async def embed(
        self, *, model: str, texts: list[str],
    ) -> list[list[float]]: ...

@dataclass
class CompletionResult:
    content: str
    input_tokens: int
    output_tokens: int
    model: str
    provider: str
```

**Gateway class:**

```python
class AIGateway:
    def __init__(
        self,
        providers: dict[str, LLMProvider],
        default_provider: str,
        fallback_chain: list[str] | None = None,
    ) -> None: ...

    async def complete(self, *, model: str, messages: list[dict],
                       temperature: float = 0.0,
                       response_format: dict | None = None) -> CompletionResult: ...

    async def embed(self, *, model: str, texts: list[str]) -> list[list[float]]: ...
```

**API surface principle:** Endpoints that trigger model interactions are
purpose-built (e.g. quiz grading, search, summarization).  The gateway is an
internal infrastructure component, never directly exposed to browsers.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/ai_gateway.py` (or integrate LiteLLM) |
| Create | `processor/providers/` (if custom adapter approach) |
| Modify | `processor/llm_client.py` — refactor to use gateway or replace entirely |
| Modify | `api/main.py` — wire gateway at startup |
| Modify | `api/deps.py` — expose gateway dependency |
| Modify | All quiz, search, pipeline, and bot code that calls `LLMClient` / `AsyncLLMClient` |
| Modify | `requirements.txt` — add `litellm` or provider SDKs |

### Implementation Steps

1. Choose approach: LiteLLM for speed, custom adapter for full control.
   LiteLLM is recommended for initial implementation.

2. Create the gateway/adapter layer with unified completion and embedding
   interfaces.

3. Integrate provider-agnostic cost and usage tracking, replacing the manual
   token counting in the current `LLMClient`.

4. Replace `LLMClient` and `AsyncLLMClient` usage across all entry points and
   handlers with the gateway.

5. Verify provider-specific parameters (response_format, structured output)
   work through the abstraction.

6. Add a test that swaps provider configuration at startup and verifies
   completions route through the correct backend.

---

## SEVERE-11 — Event-driven architecture with agent orchestration {#severe-11}

### Problem

MindForge currently operates as a synchronous sequential pipeline with no event
propagation.  This causes several compounding issues:

1. **No event bus**: Pipeline steps cannot notify downstream consumers.  State
   changes (new document, processing complete, graph updated) are invisible to
   other surfaces.
2. **No agent orchestration**: Multi-agent collaboration is implicit and
   hard-coded in `pipeline.py`.  Adding new agents or changing interaction order
   requires modifying the orchestration monolith.
3. **No task durability**: SEVERE-1 addresses in-process task management, but
   tasks that exceed the HTTP connection lifetime or survive process restarts
   require durable persistence and resumption.
4. **No streaming progress**: The client receives SSE for pipeline completion
   but has no visibility into intermediate steps.

This compounds as MindForge gains more surfaces (API, Discord, Slack), more
agent types, and longer processing pipelines.

### Target State

Three layers of event-driven capability:

**Layer 1 — Internal event bus:**

An application-level event bus enables decoupled publish-subscribe within a
single process.

```python
class EventBus:
    async def publish(self, event: DomainEvent) -> None: ...
    def subscribe(self, event_type: type[DomainEvent], handler: Callable) -> None: ...

@dataclass
class DocumentIngested(DomainEvent):
    document_id: str
    lesson_id: str
    timestamp: datetime

@dataclass
class PipelineStepCompleted(DomainEvent):
    document_id: str
    step: str
    artifact_version: int

@dataclass
class GraphUpdated(DomainEvent):
    lesson_id: str
    concepts_added: int
```

**Layer 2 — Agent orchestration framework:**

Pipeline steps become distinct agents with a declarative orchestration graph
instead of a hard-coded sequence.

```python
class AgentOrchestrator:
    def __init__(self, agents: dict[str, Agent],
                 graph: OrchestrationGraph) -> None: ...

    async def run(self, document_id: str, *,
                  resume_from: str | None = None) -> None: ...
```

Agents communicate through the event bus.  The orchestrator manages execution
order, parallel branches, and error handling policy.

**Layer 3 — Durable task handling:**

Tasks that exceed HTTP connection lifetimes or process boundaries are persisted
to the application database.  Clients poll or subscribe via SSE/WebSocket.

```python
class DurableTaskStore:
    async def create(self, task: TaskRecord) -> str: ...
    async def update_status(self, task_id: str, status: str,
                            progress: dict) -> None: ...
    async def resume_pending(self) -> list[TaskRecord]: ...
```

On process restart, pending tasks are automatically resumed from their last
checkpoint (integrates with CRITICAL-3).

**Event streaming to client:**

SSE or WebSocket endpoint emits `PipelineStepCompleted`, `DocumentIngested`,
`TaskStatusChanged` events in real time.  The client can show per-step progress
bars and handle task completion regardless of browser tab lifecycle.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/events.py` — domain events and event bus |
| Create | `processor/orchestrator.py` — agent orchestration framework |
| Create | `api/durable_task_store.py` — persistent task records |
| Create | `api/routers/events.py` — SSE/WebSocket event streaming endpoint |
| Modify | `processor/pipeline.py` — replace hard-coded sequence with orchestrator |
| Modify | `api/pipeline_task_manager.py` — integrate durable persistence |
| Modify | `api/main.py` — wire event bus and orchestrator at startup |
| Modify | `frontend/` — subscribe to event stream for real-time progress |

### Implementation Steps

1. Implement an in-process event bus with typed domain events and async
   handlers.

2. Define domain events for document ingestion, pipeline step completion, graph
   updates, and task status changes.

3. Refactor pipeline steps into distinct agent instances with a declarative
   orchestration graph that replaces the current sequential logic in
   `pipeline.py`.

4. Implement durable task persistence so tasks survive process restarts and can
   be resumed from the last checkpoint.

5. Add an SSE or WebSocket endpoint that streams domain events to subscribed
   clients, replacing the current coarse-grained SSE notification.

6. Integrate the event bus with SEVERE-1 task manager and CRITICAL-3
   checkpointing so progress events are emitted automatically.

7. Update the Angular frontend to subscribe to the event stream and display
   per-step processing progress.

---

## MODERATE-10 — Multimodal-ready data structures {#moderate-10}

### Problem

The current data model in `processor/models.py` and the database schema
proposed in CRITICAL-8 assume text-only document content.  `LessonArtifact`
stores `cleaned_content: str` and `image_descriptions: list[ImageDescription]`,
but there is no structural support for:

- audio or video attachments as first-class content,
- non-text embeddings (image embeddings, audio embeddings),
- content blocks that interleave text, images, and other media within a single
  document,
- the database schema accommodating binary assets or references to object
  storage.

Adding multimodal support later would require breaking schema migrations and
model refactoring.

### Target State

The data model and database schema are designed with multimodal extensibility
from the start, even though the initial implementation processes only text and
images.

**Content block model:**

```python
@dataclass
class ContentBlock:
    block_type: str        # "text", "image", "audio", "video", "code"
    content: str | None    # text content or transcript
    media_ref: str | None  # reference to stored binary (object storage key)
    media_type: str | None # MIME type
    metadata: dict         # block-specific metadata (dimensions, duration, etc.)
    position: int          # ordering within the document
```

**Database schema extension (CRITICAL-8):**

```sql
document_content_blocks(
    block_id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(document_id),
    block_type TEXT NOT NULL,
    text_content TEXT NULL,
    media_ref TEXT NULL,
    mime_type TEXT NULL,
    metadata JSONB NOT NULL DEFAULT '{}',
    position INT NOT NULL
)

media_assets(
    asset_id UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents(document_id),
    mime_type TEXT NOT NULL,
    storage_key TEXT NOT NULL,  -- S3/MinIO key or local path
    size_bytes BIGINT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
)
```

**Embedding model extension:**

Chunk embeddings gain a `modality` field so text and image embeddings can
coexist:

```python
@dataclass
class EmbeddingRecord:
    chunk_id: str
    modality: str     # "text", "image", "audio"
    vector: list[float]
    model: str
```

The Neo4j schema gains a `modality` property on `Chunk` nodes.

### Files to Touch

| Action | File |
|--------|------|
| Modify | `processor/models.py` — add `ContentBlock`, `MediaAsset` |
| Modify | Database schema (CRITICAL-8) — add content blocks and media assets tables |
| Modify | `processor/tools/chunker.py` — chunk by content block, not raw text |
| Modify | `processor/tools/graph_rag.py` — index multimodal chunks |
| Modify | `api/schemas.py` — expose block-level content structure |

### Implementation Steps

1. Add the `ContentBlock` abstraction to `processor/models.py` and use it as
   the canonical content representation within `LessonArtifact`.

2. Extend the CRITICAL-8 database schema to include content blocks and media
   assets tables.

3. Ensure the chunker operates on content blocks rather than raw text so future
   media-type blocks can be processed by type-specific chunkers.

4. Add a `modality` field to chunk and embedding records so vector search can
   operate across or within modalities.

5. Preserve backward compatibility: existing text-only documents produce a
   single `text` content block automatically.

---

## MODERATE-11 — Structured interaction model with full persistence {#moderate-11}

### Problem

MindForge currently has ephemeral interactions:

- Quiz sessions expire from an in-memory store (or Redis after SEVERE-2).
- Search queries leave no trace beyond Langfuse telemetry.
- Pipeline runs are tracked only by processing status, not as interactions.
- Agent-to-agent communication within the pipeline is invisible — there is no
  record of what the summarizer asked the LLM and how the flashcard generator
  used the summary.

There is no unified concept of an "interaction" that can be inspected, replayed,
or audited.

### Target State

A first-class `Interaction` entity models every meaningful exchange between
actors (user, AI agent, system).  Interactions are persisted, not ephemeral.

**Interaction model:**

```python
@dataclass
class Interaction:
    interaction_id: str
    parent_interaction_id: str | None  # nested interactions (agent orchestration)
    interaction_type: str              # "quiz_session", "search", "pipeline_run", "agent_call"
    actors: list[Actor]
    status: str                        # "active", "completed", "failed"
    context: dict                      # type-specific context
    created_at: datetime
    completed_at: datetime | None

@dataclass
class Actor:
    actor_type: str   # "user", "agent", "system"
    actor_id: str     # user ID, agent name, or "system"

@dataclass
class InteractionTurn:
    turn_id: str
    interaction_id: str
    actor: Actor
    action: str          # "question", "answer", "evaluate", "generate", "retrieve"
    input_data: dict
    output_data: dict
    timestamp: datetime
    duration_ms: int | None
    cost: float | None
```

**Database schema:**

```sql
interactions(
    interaction_id UUID PRIMARY KEY,
    parent_interaction_id UUID NULL REFERENCES interactions(interaction_id),
    interaction_type TEXT NOT NULL,
    status TEXT NOT NULL,
    context JSONB NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL,
    completed_at TIMESTAMPTZ NULL
)

interaction_actors(
    interaction_id UUID NOT NULL REFERENCES interactions(interaction_id),
    actor_type TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    PRIMARY KEY (interaction_id, actor_type, actor_id)
)

interaction_turns(
    turn_id UUID PRIMARY KEY,
    interaction_id UUID NOT NULL REFERENCES interactions(interaction_id),
    actor_type TEXT NOT NULL,
    actor_id TEXT NOT NULL,
    action TEXT NOT NULL,
    input_data JSONB NOT NULL,
    output_data JSONB NOT NULL,
    timestamp TIMESTAMPTZ NOT NULL,
    duration_ms INT NULL,
    cost NUMERIC(10,6) NULL
)
```

**Audit trail:**

Every interaction and turn is immutable after creation (append-only).  A
separate `interaction_audit_log` table or column-level tracking records
administrative actions (deletion, redaction, export).

**Integration points:**

- Quiz sessions become `Interaction(type="quiz_session")` with turns for each
  question/answer/evaluation.
- Pipeline runs become `Interaction(type="pipeline_run")` with nested
  interactions for each agent call.
- Search queries become `Interaction(type="search")` with a single turn.
- Agent-to-agent calls during orchestration (SEVERE-11) become nested
  `Interaction(type="agent_call")`.

### Files to Touch

| Action | File |
|--------|------|
| Create | `processor/interactions.py` — interaction model and repository |
| Create | `api/routers/interactions.py` — interaction history and audit endpoints |
| Modify | `api/quiz_session_store.py` — back quiz sessions with interaction persistence |
| Modify | `api/routers/quiz.py` — record turns |
| Modify | `api/routers/search.py` — record turns |
| Modify | `processor/pipeline.py` — wrap runs in interactions |
| Modify | `processor/ai_gateway.py` — record LLM calls as turns |

### Implementation Steps

1. Define `Interaction`, `Actor`, and `InteractionTurn` models and a
   persistence repository.

2. Create database tables for interactions, actors, and turns.

3. Integrate interaction creation and turn recording into quiz sessions, search,
   and pipeline runs.

4. For agent orchestration (SEVERE-11), record each agent's LLM call as a
   nested interaction turn.

5. Build API endpoints for querying interaction history, supporting filtering by
   type, actor, date range, and status.

6. Ensure the audit trail is append-only and supports compliance queries
   (e.g. "all interactions by user X in the last 30 days").

---

## Implementation Priority Order

The following ordering respects dependencies between fixes and prioritises
production stability:

| Phase | Issues | Rationale |
|-------|--------|-----------|
| Phase 1 — Canonical Identity And Ingestion | CRITICAL-1, CRITICAL-2, CRITICAL-6, CRITICAL-7, CRITICAL-8, MODERATE-8 | Establishes one composition root, one identity model, one ingestion path, and one canonical persistence layer before more behaviour is added |
| Phase 2 — Runtime Safety | CRITICAL-4, SEVERE-9 | Eliminates event-loop blocking and split-brain security configuration before additional traffic hits production |
| Phase 3 — Data Integrity | CRITICAL-3, CRITICAL-5, SEVERE-3, SEVERE-4, SEVERE-7, SEVERE-8 | Prevents silent corruption across checkpoints, artifact state, study progress, and graph semantics |
| Phase 4 — Reliability | SEVERE-1, SEVERE-2, SEVERE-6 | Fixes long-running task handling, multi-worker safety, and watcher behaviour once canonical storage exists |
| Phase 5 — Query Layer And Performance | SEVERE-5, MODERATE-1, MODERATE-7, MODERATE-9 | Reduces query overhead and removes slow read-path assembly work |
| Phase 6 — Maintainability | MODERATE-2 through MODERATE-6 | Cleans up remaining technical debt after the higher-risk correctness issues are removed |
| Phase 7 — Platform Evolution | SEVERE-10, SEVERE-11, MODERATE-10, MODERATE-11 | Provider abstraction, event-driven architecture, multimodal readiness, and interaction model — forward-looking structural changes that enable the features in implementation-plan Phases 9–17 |

MODERATE-6 is implemented **as part of** Phase 1 (CRITICAL-1), not separately.
MODERATE-8 is also intentionally pulled into Phase 1 because packaging cleanup
removes the import hacks that currently hide structural dependency problems.
