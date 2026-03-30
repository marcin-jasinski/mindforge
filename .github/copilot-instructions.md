# MindForge Workspace Guidelines

## Architecture

- MindForge has five main runtime surfaces: the lesson-processing pipeline in `mindforge.py` and `processor/`, the quiz runner in `quiz_agent.py`, the FastAPI app in `api/`, the Discord bot in `discord_bot/`, and the Angular SPA in `frontend/`.
- Treat `state/artifacts/` as the canonical source of truth for processed lessons. When you add or change outputs, extend the artifact model, pipeline, and renderers instead of creating disconnected side paths.
- Keep API routers and Discord cogs thin. Shared business logic belongs in `processor/`, `quiz_agent.py`, or focused helpers, not inside FastAPI handlers or Discord callbacks.
- The API serves the built Angular app when `frontend/dist/frontend/browser` exists; during local UI development, the Angular dev server runs separately on port 4200.
- Use [docs/architecture.md](./docs/architecture.md) for system design and [docs/implementation-plan.md](./docs/implementation-plan.md) for delivery sequencing.

## Build And Test

- Prefer the existing startup documentation in [README.md](../README.md) and [scripts/STARTUP_GUIDE.md](../scripts/STARTUP_GUIDE.md) over inventing new run commands.
- Python work assumes a repo-root `.env` copied from `env.example`, a local virtual environment, and dependencies installed with `pip install -r requirements.txt`.
- Common Python commands from the repo root:
  - `python mindforge.py --once`
  - `python -m uvicorn api.main:app --host 0.0.0.0 --port 8080 --reload`
  - `python quiz_agent.py`
  - `python -m discord_bot.bot`
  - `pytest tests`
- Frontend commands run in `frontend/`: `npm install`, `npm start`, `npm run build`, and `npm test`.
- For Docker flows, prefer the platform startup scripts in `scripts/` or the profiles defined in `compose.yml`. Compose profiles are `app`, `gui`, `quiz`, `discord`, `observability`, and `graph`.

## Docker And Runtime

- `Dockerfile` is multi-stage: Node builds Angular, then Python runs the pipeline, API, or Discord entrypoints.
- `compose.yml` orchestrates the app surfaces (`app`, `api`, `quiz-agent`, `discord-bot`) plus Neo4j and the Langfuse stack. Keep healthchecks, init containers, and named volumes aligned when changing services.
- Prefer official upstream images for Neo4j, Langfuse, Redis, Postgres, ClickHouse, and MinIO. Maintain custom container logic only for MindForge-owned code.
- When changing SPA deployment, keep FastAPI static serving and Docker build output synchronized with `frontend/dist/frontend/browser`.

## Security And Cost Guardrails

- Preserve the server-authoritative quiz flow. Browser-facing payloads must not expose quiz grounding context or reference answers; follow `api/routers/quiz.py`, `api/quiz_session_store.py`, and `tests/test_quiz_session.py`.
- Treat uploaded filenames, lesson links, and image URLs as untrusted input. Use the shared upload sanitization and egress-policy helpers instead of ad-hoc filesystem or HTTP handling.
- Discord features must enforce allowlists and interaction ownership; auth flows must keep OAuth state validation and environment-aware cookie hardening intact.
- Prefer graph or lexical retrieval before embeddings, reuse generated `reference_answer` during grading, and keep summarizer context bounded to relevant prior concepts instead of the whole index.
- Shared JSON state (`processed.json`, `article_cache.json`, `knowledge_index.json`, `sr_state.json`) is cross-process state. Preserve locking and idempotency behavior and avoid duplicate processing races between API uploads, watcher, and bot flows.

## Conventions

- Run Python entry points from the repository root. Several entry points intentionally add the repo root to `sys.path`; do not introduce alternate import assumptions unless you are cleaning that pattern up consistently.
- Keep API contracts synchronized across `api/schemas.py` and `frontend/src/app/core/models/api.models.ts`.
- For Angular changes, keep HTTP integration in `frontend/src/app/core/services/` and follow the standalone, lazy-loaded routing pattern in `frontend/src/app/app.routes.ts`.
- Use representative files before introducing new patterns: `processor/pipeline.py` for orchestration, `api/main.py` plus `api/routers/` for backend endpoints, `quiz_agent.py` for shared assessment logic, and `frontend/src/app/core/services/api.service.ts` for client API usage.
- Preserve existing user-facing Polish content unless the task explicitly changes product language.
- Some README snippets are legacy. Prefer current entry points such as `mindforge.py`, `quiz_agent.py`, and `api.main` over older `markdown_summarizer.py` references.

## Reference Docs

- System overview and pipeline details: [README.md](../README.md)
- Startup modes, local/Docker workflows, and troubleshooting: [scripts/STARTUP_GUIDE.md](../scripts/STARTUP_GUIDE.md)
- Architecture reference: [docs/architecture.md](./docs/architecture.md)
- Delivery roadmap: [docs/implementation-plan.md](./docs/implementation-plan.md)
- Security and cost review baseline: [reviews/mindforge-deep-code-review-2026-04-01.md](./reviews/mindforge-deep-code-review-2026-04-01.md)
- Angular CLI basics for the SPA: [frontend/README.md](../frontend/README.md)
