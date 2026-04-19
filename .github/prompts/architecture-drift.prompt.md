---
description: "Fast, automated scan for hexagonal layer-boundary violations, forbidden imports, and configuration discipline regressions. Run before every merge to main. Not a substitute for the full security audit."
name: "Architecture Drift"
argument-hint: "Optional: a specific mindforge/ subdirectory or surface to narrow the scan. Omit to scan the full package."
agent: "Code Review"
---

# MindForge Architecture Drift Scan

You are the MindForge architecture drift scanner. Your goal is a **fast,
mechanical pass/fail report** that catches layer-boundary violations and
configuration discipline regressions before they accumulate. This is not a
security audit and not a code review — skip narrative analysis and produce
only the structured output described at the end.

## Setup

Before scanning, read:

- [.github/copilot-instructions.md](.github/copilot-instructions.md) —
  layer rules, module layout, and configuration discipline.
- [.github/docs/architecture.md](.github/docs/architecture.md) —
  hexagonal layer boundaries and composition-root model.

If an argument was provided (e.g., `mindforge/application/`), restrict the
file scan to that subtree. Otherwise scan the full `mindforge/` package and
`frontend/src/`.

---

## Scan Dimensions

Work through every dimension below in order. For each violation found, record
the file path, line number, and a one-line description. Mark the dimension
PASS if no violations are found.

---

### D1 — Domain Layer Purity

`mindforge/domain/` must contain only pure Python: entities, value objects,
domain events, agent protocols, and port interfaces (ABCs / Protocols).

Search `mindforge/domain/` for:
- Any import from `mindforge.infrastructure`, `mindforge.application`,
  `mindforge.api`, `mindforge.discord`, `mindforge.slack`, or `mindforge.cli`.
- Any import of a third-party I/O library: `sqlalchemy`, `asyncpg`, `psycopg`,
  `neo4j`, `redis`, `httpx`, `aiohttp`, `requests`, `boto3`, `litellm`,
  `langfuse`, `fastapi`, `starlette`, `discord`, `slack_sdk`.
- Any use of `open()`, `os.path`, `pathlib.Path` for real filesystem access
  (type-annotation imports are acceptable).

---

### D2 — Application Layer Purity

`mindforge/application/` must import only from `mindforge.domain` and the
Python standard library (plus test utilities in test files). It must not
perform I/O.

Search `mindforge/application/` for:
- Any import from `mindforge.infrastructure`.
- Any direct import of: `sqlalchemy`, `asyncpg`, `psycopg`, `neo4j`, `redis`,
  `httpx`, `aiohttp`, `requests`, `boto3`, `litellm`, `langfuse`.
- Any use of `os.environ` (configuration must come in via injected ports, not
  read directly).

---

### D3 — Adapter Thinness

`mindforge/api/`, `mindforge/discord/`, `mindforge/slack/`, and `mindforge/cli/`
are thin driving adapters. Business logic must not accumulate there.

Search each adapter package for:
- Direct imports from `mindforge.infrastructure` that bypass the application
  layer (i.e., the adapter calls a repository or gateway directly without going
  through an application service).
- Domain entity construction or mutation outside of a delegated application
  service call.
- Any SQL query or ORM session usage that is not inside
  `mindforge/infrastructure/`.

---

### D4 — Composition Root Discipline

Each runtime surface (`mindforge/api/main.py`, `mindforge/discord/bot.py`,
`mindforge/slack/app.py`, `mindforge/cli/pipeline_runner.py`) must contain
exactly one composition root. No module outside these files should instantiate
infrastructure adapters at import time.

Search the full `mindforge/` package (excluding the four composition roots) for:
- Module-level instantiation of any class from `mindforge.infrastructure`
  (e.g., `repo = PostgresArtifactRepo(...)` at module scope).
- Module-level instantiation of external clients:
  `AsyncDriver(`, `AsyncConnectionPool(`, `redis.from_url(`, `AsyncClient(`,
  `LiteLLM(`, `Langfuse(`.
- `import-time side effects: any function call at module scope that opens a
  network connection, reads a file, or writes to a data store.

---

### D5 — Configuration Discipline

All environment configuration must be loaded once, at startup, via
`mindforge/infrastructure/config.py` (Pydantic `BaseSettings`). It must never
be read lazily at request time or at module level in other files.

Search the full `mindforge/` package (excluding `mindforge/infrastructure/config.py`
itself) for:
- `os.environ[` or `os.environ.get(` in any `.py` file.
- `os.getenv(` in any `.py` file.
- `dotenv.load_dotenv(` outside of `mindforge/infrastructure/config.py` or a
  composition root.

---

### D6 — sys.path Manipulation

The package is installed via `pip install -e .` and imported as `mindforge.*`.
No code should manipulate `sys.path`.

Search the full `mindforge/` package and `tests/` for:
- `sys.path.append(`, `sys.path.insert(`, `sys.path.extend(`.

---

### D7 — Import Discipline

All imports must be at module top level. Conditional or deferred imports are
allowed only for optional heavy packages and must use `try/except ImportError`.

Search `mindforge/` for:
- Import statements inside function bodies or class methods (excluding
  `try/except ImportError` blocks that guard optional packages).
- `__import__(` calls outside of `try/except ImportError` blocks.

---

### D8 — Frontend–Backend Contract Sync

`mindforge/api/schemas.py` and `frontend/src/app/core/models/api.models.ts`
must define matching field names for every shared model.

- Read `mindforge/api/schemas.py` and extract all Pydantic model field names
  for models that have a corresponding TypeScript interface.
- Read `frontend/src/app/core/models/api.models.ts` and extract interface
  field names for those same models.
- Report any field present in one file but absent or differently named in the
  other. camelCase ↔ snake_case conversions via FastAPI's alias generator are
  acceptable — flag only genuine mismatches.

---

## Output Format

Produce the report in this exact structure. Do not add narrative sections.

### Architecture Drift Report — `<date>`

**Scan scope:** `<full package | specified subtree>`

| Dimension | Status | Violations |
|-----------|--------|------------|
| D1 — Domain layer purity | PASS / FAIL | N violations |
| D2 — Application layer purity | PASS / FAIL | N violations |
| D3 — Adapter thinness | PASS / FAIL | N violations |
| D4 — Composition root discipline | PASS / FAIL | N violations |
| D5 — Configuration discipline | PASS / FAIL | N violations |
| D6 — sys.path manipulation | PASS / FAIL | N violations |
| D7 — Import discipline | PASS / FAIL | N violations |
| D8 — Frontend–backend contract sync | PASS / FAIL | N violations |

**Overall: PASS / FAIL**

---

### Violation Detail

For each FAIL dimension, list violations as:

```
[D1] mindforge/domain/models.py:42
     imports sqlalchemy.orm — I/O library forbidden in domain layer
```

One line per violation. No prose. No suggested fixes (use the Bug Fix agent
for remediation).
