# MindForge

[![CI](https://github.com/marcin-jasinski/mindforge/actions/workflows/ci.yml/badge.svg)](https://github.com/marcin-jasinski/mindforge/actions/workflows/ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

**MindForge is an AI-powered learning platform built on a compounding wiki.** You upload documents into a knowledge
base. MindForge reads each one and weaves what it teaches into that knowledge base's wiki of LLM-written pages: it
creates pages for new concepts, revises existing ones, links them together, and marks where a newer source corrects an
older one. Flashcards, quizzes and answers to your questions are then cut from the wiki — not from a single upload — so
every document you study makes everything you studied before richer. The wiki exports as a portable
[OKF](docs/wiki/llm_wiki.md) bundle.

- **Backend:** Java 21, Spring Boot 4.1, Spring AI 2.0 (OpenRouter or any OpenAI-compatible endpoint), Spring Data
  JPA / Hibernate, Flyway, Maven
- **Frontend:** Angular 21 standalone SPA, Angular Material, Signals, Cytoscape.js
- **Data:** PostgreSQL is the only data store, plus Caffeine as an in-process cache. No Neo4j, no pgvector, no object
  storage.
- **Delivery:** one deployable JAR (the Angular build is embedded) in one Docker image, next to PostgreSQL
- **Channels:** web UI, REST API, CLI, Discord bot, Slack bot

---

## Table of contents

1. [Why MindForge](#why-mindforge)
2. [How it works](#how-it-works)
3. [Features](#features)
4. [Architecture](#architecture)
5. [Repository structure](#repository-structure)
6. [Quick start (Docker Compose)](#quick-start-docker-compose)
7. [Local development](#local-development)
8. [Configuration](#configuration)
9. [Deployment](#deployment)
10. [Command-line interface](#command-line-interface)
11. [Discord and Slack bots](#discord-and-slack-bots)
12. [REST API](#rest-api)
13. [Testing and quality gates](#testing-and-quality-gates)
14. [Security model](#security-model)
15. [Operational limits](#operational-limits)
16. [Project status and roadmap](#project-status-and-roadmap)
17. [Documentation map](#documentation-map)
18. [Contributing](#contributing)
19. [License](#license)

---

## Why MindForge

Raw learning material is hard to internalize, and notes made from it rot: each new source must be reconciled by hand
with everything already written. MindForge gives that bookkeeping to an LLM. The learner curates sources, asks
questions and studies; the LLM summarizes, cross-links and keeps the wiki consistent as sources accumulate.

**Core value loop:**

1. Upload a document (Markdown, PDF, DOCX or TXT).
2. Ingest weaves it into the knowledge base's wiki.
3. Study flashcards and quizzes cut from the wiki, and ask the wiki questions.
4. Track retention per page while the wiki keeps growing.

**Principles:**

- **The wiki is the substrate, not the product.** Pages exist to be studied from.
- **The LLM is the sole author of prose.** You change the wiki by telling MindForge what to change in chat, never by
  editing text. Every change can be reverted while it is the latest one.
- **Grounded in what you uploaded.** Pages cite their sources. Nothing is written from the model's own knowledge
  without a source behind it.
- **Portable.** A knowledge base leaves as a conformant OKF bundle that any tool can read.
- **The model proposes, code decides.** Page type, path, title, sources, membership and deletion are owned by code;
  every rule a prompt teaches is also enforced in code.

## How it works

### The wiki model

| Term | Meaning |
|---|---|
| **Knowledge Base** | A user-owned collection of documents and the one wiki built from them. Exports as exactly one OKF bundle. |
| **Page** | One unit of the wiki: title, one-sentence description, type and LLM-written prose. |
| **Page Type** | Assigned by code: **Concept** (`concepts/<name>`, one thing the knowledge base teaches) or **Source Summary** (`sources/<lesson-id>`, the digest of one uploaded document). |
| **Page Path** | A page's identity. It never changes after creation. |
| **Section** | A level-1 heading of a page and the prose under it. Links, supersessions and flashcards address sections by anchor. |
| **Page Link** | A link in a page's prose to another page (optionally to one of its sections). |
| **Supersession** | A record that a section of one page has been corrected by another page. It never changes the superseded prose. |
| **Index** | The catalog of every live page (path, title, description). It is what the model reads to pick pages. |
| **Lesson** | The stable identity that all uploaded versions of the same material share. |
| **Ingest Run** | One execution that changes the wiki (ingest, conversation edit, revert or Lint) and the record of what it changed. |
| **Page Revision** | An immutable snapshot of a page after one write. Revisions are kept forever. |

The full glossary is in [`CONTEXT.md`](CONTEXT.md).

### Ingest pipeline

An upload returns `202 Accepted` immediately. A background worker then runs a fixed, typed pipeline:

```
Upload → parse (ParserRegistry) → resolve lesson identity → dedup under the knowledge-base row lock
       → persist Document + QUEUED run                                         [HTTP 202]
Worker claims the run (one active run per knowledge base, via a lease)
  1. Describe images   (VISION model, one call per image in a PDF or DOCX)
  2. Preprocess        (code: heading-based chunking)
  3. Relevance guard   (SMALL model)
  4. Extract           (LARGE model, per chunk: titled claims with proposed target pages)
  5. Resolve           (code: verify targets, derive paths, one task per path, caps)
  6. Write             (LARGE model, in parallel: description and body of each page; drafts validated)
  7. Link check        (SMALL model: proposed link insertions)
  ── COMMIT 1: pages, revisions, sources, links ──
  8. Supersede         (LARGE model: which sections a new source corrects; proposals verified)
  ── COMMIT 2: supersessions; lease released ──
Progress is streamed to the browser over SSE at every step.
```

Key properties:

- **No model call inside a database transaction.** Content is generated first and committed once.
- **Fenced commits.** A commit changes a run only from its expected status while it holds the lease, so a stale worker
  can never write.
- **Partial success is legal.** A run fails only when no page task succeeded.
- **Caps fail loudly, never truncate** (claims per Extract call, page tasks per run).
- **A sweep** at startup and every minute settles interrupted runs and re-queues them (up to three attempts).
- **Revert** is per run, restore-forward and tip-only: it appends revisions that restore each page's pre-run state.
- **Conversation edits** are ingest runs whose source is a chat turn. They are the only way to revise, create, delete
  or retitle a Concept page.

### Query, study and Lint

- **Query** — the `PageSelector` (SMALL) reads the rendered index and picks pages; code loads their bodies (plus
  one-hop links) within a token budget; the `AnswerWriter` (LARGE) answers and cites page paths. Multi-turn.
- **Flashcards** — generated lazily per Concept section, scheduled by SM-2 spaced repetition. A flashcard is identified
  by its content and goes stale when its page changes.
- **Quizzes** — server-authoritative sessions. Reference answers live only in the server-side session and never reach
  the client. Quizzes target **Weak Pages** (the last five ratings and scores average below 3 on a 0–5 scale).
- **Lint** — live structural health checks, a link check in every ingest, and an on-demand full review. Lint writes
  only link insertions; everything else (dangling links, contradictions) is reported as a finding.
- **Export** — one read-only snapshot rendered to frontmatter, bodies, supersession notes, citations, `index.md` and
  `log.md`, validated against OKF and streamed as a zip. Study data never enters an export.

## Features

| Area | What you get |
|---|---|
| Ingest | Markdown, PDF, DOCX, TXT; image descriptions with a vision model; lesson versions; duplicate detection; per-run revert |
| Wiki browser | Page index, page view with sections and citations, revision history, cross-link graph (Cytoscape.js) |
| Runs | Run reports with per-page diffs, costs and failures; revert; live progress over SSE |
| Health | Structural checks (dangling links, orphans, index size) and an on-demand Lint run with suggestions |
| Study | Flashcards with SM-2 scheduling, quizzes over a whole knowledge base, a lesson or a page with its neighbours |
| Chat | Multi-turn Query with citations, and conversation edits of the wiki |
| Export | OKF bundle as a zip download |
| Articles | Optional fetch of the web articles an upload links to, behind an SSRF-safe egress policy |
| CLI | `pipeline` (ingest a local file) and `quiz` (interactive quiz in the terminal) |
| Bots | Discord (`/ask`, `/quiz`, `/upload`, daily due-card reminders) and Slack (`/mf-ask`, `/mf-quiz`, file upload in a DM) |
| Auth | Email and password (BCrypt), Google and GitHub OAuth2, JWT in HttpOnly cookies |
| Observability | Optional Langfuse tracing over OpenTelemetry; per-run LLM cost tracking |

## Architecture

MindForge follows **Hexagonal Architecture (Ports and Adapters)**. Dependencies always point inward:
adapters → application → domain. ArchUnit tests enforce the boundaries.

```
                 ┌───────────────────────────────────────────────────────────┐
   Driving       │  api (REST)   cli   discord   slack     Angular SPA        │
   adapters      └──────────────────────────┬────────────────────────────────┘
                                            ▼
                 ┌───────────────────────────────────────────────────────────┐
   Application   │  IngestionService, RunWorker, IngestPipeline, QueryService │
                 │  FlashcardService, QuizService, LintService, RevertService │
                 │  ──────────────▶ agent (model services: ClaimExtractor,   │
                 │                   PageWriter, LinkChecker, AnswerWriter…) │
                 └──────────────────────────┬────────────────────────────────┘
                                            ▼
                 ┌───────────────────────────────────────────────────────────┐
   Domain        │  WikiPage, IngestRun, Document, Flashcard, StudyScope …    │
   (JDK only)    │  Ports: WikiStore, AIGateway, DocumentRepository, …        │
                 └──────────────────────────▲────────────────────────────────┘
                                            │ implements
                 ┌──────────────────────────┴────────────────────────────────┐
   Driven        │  infrastructure: persistence (JPA), ai (Spring AI +        │
   adapters      │  Resilience4j), parsing, export, event, web, security      │
                 └───────────────────────────────────────────────────────────┘
```

| Layer | Package | Allowed imports |
|---|---|---|
| Domain | `dev.mindforge.domain` | JDK only — zero I/O, zero framework |
| Application | `dev.mindforge.application` | domain, `agent`, Spring transaction API, SLF4J |
| Model services | `dev.mindforge.agent` | domain + `infrastructure.ai` |
| Infrastructure | `dev.mindforge.infrastructure` | domain + application + any third-party library |
| Adapters | `dev.mindforge.api`, `cli`, `discord`, `slack` | all layers; thin, no business logic |

### Model services

Every LLM call goes through the `AIGateway` port. Models are requested by role (`ModelTier`), never by provider name.
Each service has a `VERSION` constant that is recorded on every run. Prompts are versioned Markdown files in
`src/main/resources/prompts/en/`.

| Service | Tier | Used by |
|---|---|---|
| `ImageDescriber` | VISION | Ingest (documents only) |
| `RelevanceGuard` | SMALL | Ingest (documents only) |
| `ClaimExtractor` | LARGE | Ingest — claims per chunk; edit items for conversation turns |
| `PageWriter` | LARGE | Ingest — page description and body |
| `LinkChecker` | SMALL | Ingest, Lint |
| `SupersessionDetector` | LARGE | Ingest |
| `WikiReviewer` | LARGE | Lint |
| `PageSelector` / `AnswerWriter` | SMALL / LARGE | Query |
| `FlashcardGenerator`, `QuizGenerator` / `QuizEvaluator` | LARGE / SMALL | Study |

`AIGatewayAdapter` uses Spring AI with an OpenAI-compatible client (OpenRouter by default, LM Studio for local work),
wrapped in a Resilience4j retry and circuit breaker, with per-call deadlines and a global permit pool for background
calls.

### Data model (PostgreSQL)

| Group | Tables |
|---|---|
| Accounts | `users`, `knowledge_bases` (with the `active_run_id` lease), `external_identities` |
| Sources | `documents` |
| Wiki | `wiki_pages`, `page_links`, `page_revisions`, `page_sources`, `page_supersessions` |
| History | `ingest_runs` (also the run queue) |
| Study | `flashcards`, `study_events`, `quiz_sessions` |
| Query | `interactions`, `interaction_turns` |

- Every table carries `knowledge_base_id`; deleting a knowledge base cascades. Every tenant-scoped port method takes
  `kbId` first, so tenancy is structural.
- History tables have no FK to `wiki_pages`, so history outlives a deleted page.
- Schema migrations are Flyway scripts in `src/main/resources/db/migration/` and run at startup. Hibernate only
  validates the schema (`ddl-auto: validate`).
- Retrieval is the rendered index read by the model — there is no vector store (ADR 0016).

A full description is in [`docs/project/architecture.md`](docs/project/architecture.md).

## Repository structure

```
mindforge/
├── src/main/java/dev/mindforge/
│   ├── domain/            # model/ and port/ — pure Java, JDK only
│   ├── application/       # use cases: ingest/, wiki/, study/, service/
│   ├── agent/             # model services that call AIGateway
│   ├── infrastructure/    # ai/, persistence/, parsing/, export/, event/, web/, security/, cache/, storage/, config/
│   ├── api/               # REST controllers, DTOs, MapStruct mappers, security config
│   ├── cli/               # pipeline and quiz command-line runners
│   ├── discord/           # Discord bot adapter (JDA)
│   └── slack/             # Slack bot adapter (Bolt, Socket Mode)
├── src/main/resources/
│   ├── application.yml    # main configuration (every value can be set from the environment)
│   ├── application-dev.yml, application-cli.yml
│   ├── db/migration/      # Flyway migrations (V1…V8)
│   └── prompts/en/        # versioned prompt files
├── src/test/
│   ├── java/dev/mindforge/  # unit/, integration/ (Testcontainers), api/, discord/, infrastructure/, support/
│   └── e2e/                 # Playwright browser E2E suite + WireMock model stub mappings
├── frontend/              # Angular 21 SPA (pages: dashboard, kb, wiki, graph, runs, health, study, chat, login)
├── config/                # Checkstyle rules and SpotBugs exclusions
├── docs/
│   ├── INDEX.md           # start here: map of all documentation
│   ├── project/           # vision, architecture, tech stack, deployment, roadmap, implementation plan
│   ├── standards/         # coding, backend, frontend, security and testing standards
│   ├── adr/               # Architecture Decision Records 0001–0042
│   ├── wayfinder/         # wiki re-cut decision map, code review map and tickets
│   ├── factory/           # records of the automated build runs (phases 15–21)
│   └── wiki/              # background on the LLM Wiki pattern and OKF
├── .github/workflows/     # ci.yml, dependency-check.yml
├── Dockerfile             # multi-stage build: SPA → JAR → JRE 21 runtime
├── compose.yml            # app + PostgreSQL
├── compose.override.yml   # local development overrides (picked up automatically)
├── compose.e2e.yml        # test-only E2E stack with the WireMock model stub
├── env.example            # every environment variable, with comments
├── railway.json, Procfile # platform deployment descriptors
├── CONTEXT.md             # domain glossary
└── CLAUDE.md              # instructions for AI coding agents
```

## Quick start (Docker Compose)

**Prerequisites:** Docker with Compose v2, and an [OpenRouter](https://openrouter.ai) API key (or another
OpenAI-compatible endpoint).

```bash
git clone https://github.com/marcin-jasinski/mindforge.git
cd mindforge
cp env.example .env          # then set OPENROUTER_API_KEY (and optionally the models)
docker compose up --build
```

- The app is at <http://localhost:8080>. Register with an email and a password, create a knowledge base and upload a
  document.
- Health check: `curl http://localhost:8080/actuator/health` returns `{"status":"UP"}`.
- `compose.yml` starts `postgres` (data in the `postgres-data` volume) and `app`, which waits for PostgreSQL to be
  healthy. Flyway migrates the schema at startup.
- `compose.override.yml` is merged automatically for local work: it publishes PostgreSQL on port 5432, turns off
  `Secure` cookies (plain HTTP) and supplies a development JWT secret. **Do not use it in production.**
- Without OAuth credentials, compose sets placeholder client ids, so sign-in by email works and the Google and GitHub
  buttons do not.

Stop and remove the data:

```bash
docker compose down -v
```

## Local development

**Prerequisites:** JDK 21, Maven 3.9+, Node.js 22 with npm 11, Docker (for PostgreSQL and the integration tests).

### 1. Database

Start only PostgreSQL from compose (published on `localhost:5432` by the override):

```bash
docker compose up -d postgres
```

### 2. Backend

```bash
# Linux / macOS / Git Bash
export OPENROUTER_API_KEY=sk-or-...
export JWT_SECRET=$(openssl rand -hex 32)
export AUTH_SECURE_COOKIES=false
mvn spring-boot:run
```

```powershell
# Windows PowerShell
$env:OPENROUTER_API_KEY = "sk-or-..."
$env:JWT_SECRET = "local-development-secret-at-least-32-bytes"
$env:AUTH_SECURE_COOKIES = "false"
mvn spring-boot:run
```

The API listens on <http://localhost:8080>. The default datasource is
`jdbc:postgresql://localhost:5432/mindforge` with user and password `mindforge`.

**`dev` profile (fully local models).** `--spring.profiles.active=dev` (or `SPRING_PROFILES_ACTIVE=dev`) points Spring
AI at [LM Studio](https://lmstudio.ai) on `http://localhost:1234/v1` with the model name `local-model` for all tiers,
uses the database `mindforge_dev`, and enables Swagger UI (`/swagger-ui.html`) and `/v3/api-docs`. Create the
`mindforge_dev` database first.

### 3. Frontend

```bash
cd frontend
npm ci
npm start          # ng serve on http://localhost:4200
```

`proxy.conf.json` forwards `/api`, `/oauth2`, `/login/oauth2` and `/v3/api-docs` to `http://localhost:8080`, so the
SPA and the API share one origin and no CORS setting is needed.

Regenerate the TypeScript API types after an API change (the backend must run with `API_DOCS_ENABLED=true` or the
`dev` profile):

```bash
npm run openapi:generate   # writes src/app/core/models/api.generated.ts
```

### 4. Build the single JAR

```bash
mvn package -DskipFrontend=false   # builds the SPA and embeds it under classpath:/static/
java -jar target/mindforge-1.1.0-SNAPSHOT.jar
```

By default (`skipFrontend=true`) Maven does not build the SPA. Or build the Docker image, which always includes it:

```bash
docker build -t mindforge .
```

## Configuration

All settings are in `src/main/resources/application.yml`, and each one can be set with an environment variable.
[`env.example`](env.example) lists every variable with a comment. Copy it to `.env` (it is git-ignored) and never
commit secrets.

### Required

| Variable | Default | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/mindforge` | JDBC form |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | `mindforge` / `mindforge` | |
| `JWT_SECRET` | — | At least 32 bytes. The app does not start with a shorter one. Generate with `openssl rand -hex 32` |
| `OPENROUTER_API_KEY` | — | Model provider key. For LM Studio, any non-empty string |

### Model provider

| Variable | Default | Notes |
|---|---|---|
| `OPENROUTER_BASE_URL` | `https://openrouter.ai/api/v1` | Any OpenAI-compatible endpoint (e.g. `http://localhost:1234/v1` for LM Studio) |
| `AI_MODEL_SMALL` | `openai/gpt-4o-mini` | Classification and cheap steps |
| `AI_MODEL_LARGE` | `openai/gpt-4o` | Synthesis steps |
| `AI_MODEL_VISION` | `openai/gpt-4o` | Image descriptions |
| `AI_DEADLINE_INTERACTIVE` / `_BATCH` / `_BACKGROUND` | `10s` / `60s` / `900s` | Per-call deadlines |
| `AI_CLIENT_TIMEOUT` | `PT16M` | HTTP timeout per model call; keep it above `AI_DEADLINE_BACKGROUND` so the deadline governs slow local models |
| `AI_BACKGROUND_PERMITS` | `4` | Global limit of concurrent background model calls |
| `AI_COST_WARNING_USD` | `0.50` | A run above this cost logs a warning |
| `AI_RETRY_*`, `AI_CB_*` | see `application.yml` | Resilience4j retry and circuit breaker tuning |

### Web, security and limits

| Variable | Default | Notes |
|---|---|---|
| `PORT` | `8080` | Set by Railway and Render |
| `JWT_EXPIRY_SECONDS` | `86400` | Session cookie lifetime |
| `AUTH_SECURE_COOKIES` | `true` | Set `false` only for local plain HTTP |
| `GOOGLE_CLIENT_ID` / `_SECRET`, `GITHUB_CLIENT_ID` / `_SECRET` | — | OAuth2 sign-in. A non-empty placeholder disables that provider in practice |
| `UPLOAD_MAX_SIZE` | `50MB` | Largest document on any upload channel |
| `RATE_LIMIT_REQUESTS` / `RATE_LIMIT_WINDOW` | `30` / `PT1M` | Per user: Query, quiz start, upload, retry, Lint (also per bot user) |
| `AUTH_RATE_LIMIT_REQUESTS` / `AUTH_RATE_LIMIT_WINDOW` | `10` / `PT1M` | Sign-in and sign-up per client address; `429` with `Retry-After` |
| `CORS_ALLOWED_ORIGIN` | blank | One exact origin, only when the SPA is served from another origin. Never a list, never `*` |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | private ranges | Proxies whose `X-Forwarded-*` headers are trusted |
| `QUIZ_SESSION_TTL` | `PT2H` | Quiz session lifetime |
| `RUNS_SWEEP_INTERVAL` | `PT1M` | How often the run sweep runs |
| `SHUTDOWN_TIMEOUT` | `30s` | Graceful shutdown per phase |
| `API_DOCS_ENABLED` | `false` | Serve `/v3/api-docs` and Swagger UI |

### Optional features

| Feature | Variables | Default |
|---|---|---|
| Article fetch | `FETCH_EXTERNAL_ARTICLES`, `ARTICLES_MAX_PER_DOCUMENT`, `ARTICLES_MAX_BODY_SIZE`, `ARTICLES_CONNECT_TIMEOUT`, `ARTICLES_REQUEST_TIMEOUT` | off; `10`, `2MB`, `5s`, `15s` |
| Discord bot | `DISCORD_BOT_TOKEN`, `MINDFORGE_DISCORD_ALLOWED_GUILD_IDS`, `MINDFORGE_DISCORD_ALLOWED_ROLE_IDS`, `MINDFORGE_DISCORD_REMINDER_CRON`, `MINDFORGE_DISCORD_REMINDER_ZONE` | off; reminders `0 0 9 * * *` UTC |
| Slack bot | `SLACK_BOT_TOKEN`, `SLACK_APP_TOKEN`, `SLACK_ALLOWED_TEAM_IDS` | off |
| Langfuse tracing | `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY`, `LANGFUSE_HOST` | off unless both keys are set; host `https://cloud.langfuse.com` |
| Dependency check | `NVD_API_KEY` | read only by `mvn dependency-check:check` |

**Images** (constants, not settings): only raster images are described; images smaller than 64 px, over 50
megapixels or over 5 MB are skipped; duplicates are kept once; an upload with more than 100 images is rejected. Each
run attempt pays the vision calls again, and image bytes count toward the database size.

**Article fetch:** when on, the server fetches the `http(s)` URLs found in the text of a new upload (at most
`ARTICLES_MAX_PER_DOCUMENT`), only on ports 80/443 and only to hosts that resolve to public addresses, checking every
redirect. Each article becomes an `ARTICLE` document with its own run, which may only create pages. A skipped article
is only logged.

Full details: [`docs/project/deployment.md`](docs/project/deployment.md).

## Deployment

MindForge ships as one container (the Spring Boot JAR with the SPA embedded) next to PostgreSQL. The Dockerfile has
three stages: `node:22-alpine` builds the SPA, `maven:3.9-eclipse-temurin-21` builds the JAR, and the runtime is
`eclipse-temurin:21-jre-alpine` as a non-root user with `-XX:MaxRAMPercentage=75`.

### Production checklist

1. Provide PostgreSQL (15 or later) and set `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`.
2. Set a strong `JWT_SECRET` (32+ bytes) and keep `AUTH_SECURE_COOKIES=true`; serve over HTTPS.
3. Set `OPENROUTER_API_KEY` and the `AI_MODEL_*` values.
4. Set the OAuth2 client ids and secrets, or placeholders if you use email sign-in only.
5. Keep **exactly one replica** (see [Operational limits](#operational-limits)).
6. Use `/actuator/health` as the health check path.
7. Do not use `compose.override.yml` or `compose.e2e.yml` in production.

### Docker Compose on a server

```bash
cp env.example .env           # set JWT_SECRET, OPENROUTER_API_KEY, DATABASE_PASSWORD, …
docker compose -f compose.yml up -d --build
```

Name `compose.yml` explicitly so that the local override is not merged. Put a TLS-terminating reverse proxy in front
of port 8080. A proxy on a private address is trusted for `X-Forwarded-*`; when other hosts can reach the server on a
private address, set `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to the proxy address only.

### Railway

1. Create a project from the repository. Railway builds the `Dockerfile` (see `railway.json`: one replica, health
   check `/actuator/health`, restart on failure).
2. Add the PostgreSQL plugin and set `DATABASE_URL` to its **JDBC** form, with the username and password variables.
3. Set `JWT_SECRET`, `OPENROUTER_API_KEY` and the OAuth variables.

### Render

Create a Docker web service from the repository with the same variables and the health check path
`/actuator/health`. The `Procfile` covers buildpack platforms that run a pre-built JAR.

Railway and Render put a proxy on a private address in front of the app, so the client address and `https` are
detected without extra settings.

## Command-line interface

The same JAR has two command-line entry points in the `cli` profile. This profile starts no web server, no run sweep,
no bots and no article fetch. It reads the same configuration as the server (`DATABASE_*`, `OPENROUTER_API_KEY`,
`JWT_SECRET` of 32+ bytes). **Stop the server first**, or use a different database. Give option values in the
`--name=value` form.

Ingest a local file and print step progress:

```bash
java -jar mindforge.jar --spring.profiles.active=cli \
  pipeline --file=<path> --kb=<uuid> --user=<email> [--lesson-id=<id>] [--new-version]
```

The command waits for its run to complete (`mindforge.cli.run-timeout`, default `PT30M`). An identical file queues no
run.

Run an interactive quiz in the terminal:

```bash
java -jar mindforge.jar --spring.profiles.active=cli quiz --kb=<uuid> --user=<email>
```

| Exit code | Meaning |
|---|---|
| `0` | Success |
| `1` | The ingest failed, the wait timed out, or an unexpected error occurred |
| `2` | The input was refused before a run was queued (usage error, unknown user or knowledge base, not the owner, refused upload) |

The CLI trusts the operator: it asks for no password and acts as `--user`. The database credentials are the trust
boundary (ADR 0019).

## Discord and Slack bots

Both bots are optional, off by default, and never run in the `cli` profile. They call the same application services
as the web UI and share the per-user rate limit (with their own counters). A bot user gets a **separate account**
(`discord-<id>@users.invalid` or `slack-<team>-<user>@users.invalid`) with one default knowledge base; it cannot sign
in to the web, and there is no link to a web account yet (issue #43).

### Discord

- Set `DISCORD_BOT_TOKEN` and `MINDFORGE_DISCORD_ALLOWED_GUILD_IDS` (required with the token; the server does not
  start without it). `MINDFORGE_DISCORD_ALLOWED_ROLE_IDS` optionally limits access to members with a listed role.
- Commands (guild only, replies are ephemeral): `/ask`, `/quiz start`, `/quiz answer`, `/upload` (attachments only
  from `cdn.discordapp.com` or `media.discordapp.net`). `/ask`, `/quiz start` and `/upload` take a `kb` option.
- A daily DM reports the number of due flashcards per knowledge base (never card text).

### Slack

The bot uses Socket Mode, so it needs no public URL.

1. At api.slack.com/apps, turn on **Socket Mode** and create an app-level token with `connections:write`
   → `SLACK_APP_TOKEN`.
2. Give the bot token the scopes `commands`, `chat:write`, `im:history`, `files:read` and install the app
   → `SLACK_BOT_TOKEN`.
3. Subscribe to the bot event `message.im`, and enable the **Messages** tab in **App Home**.
4. Create the slash commands `/mf-ask` and `/mf-quiz`.
5. Set `SLACK_ALLOWED_TEAM_IDS` (required with the tokens). Set both tokens or neither.

Usage: `/mf-ask <question>`, `/mf-quiz start`, `/mf-quiz answer <text>`, and send one file to the bot in a DM (the
message text may hold `lesson_id=<value>` and `new_version`). Commands act on the caller's only knowledge base.

## REST API

All endpoints are under `/api/v1` and need a session cookie, except registration, sign-in and the provider list.
Every method checks that the caller owns the resource.

| Resource | Endpoints |
|---|---|
| Auth | `POST /auth/register`, `POST /auth/login`, `POST /auth/logout`, `GET /auth/providers`, `GET /auth/me`; OAuth2 under `/oauth2/authorization/{google,github}` |
| User | `PATCH /users/me` |
| Knowledge bases | `GET/POST /knowledge-bases`, `GET/PATCH/DELETE /knowledge-bases/{kbId}` |
| Documents | `POST /knowledge-bases/{kbId}/documents` (multipart, `202`), `GET …/documents`, `GET/DELETE …/documents/{id}`, `POST …/documents/{id}/runs` (retry) |
| Wiki | `GET …/{kbId}/index`, `GET …/{kbId}/pages/{directory}/{name}`, `GET …/pages/{directory}/{name}/revisions`, `GET …/{kbId}/graph`, `GET …/{kbId}/pages/search` |
| Runs | `GET …/{kbId}/runs`, `GET …/runs/{runId}`, `POST …/runs/{runId}/cancel` (stop a queued or running ingest or lint run before it writes pages, ADR 0043), `POST …/runs/{runId}/revert`, `DELETE …/supersessions/{id}`, `GET …/{kbId}/progress` (SSE) |
| Health and Lint | `GET …/{kbId}/health`, `POST …/{kbId}/lint-runs` |
| Query | `POST/GET …/{kbId}/query-sessions`, `POST …/query-sessions/{id}/messages`, `POST …/query-sessions/{id}/edits` |
| Flashcards | `GET …/{kbId}/flashcards`, `POST …/flashcards/refresh`, `POST …/flashcards/{cardId}/reviews` |
| Quizzes | `POST …/{kbId}/quiz-sessions`, `GET …/quiz-sessions/{id}/next`, `POST …/quiz-sessions/{id}/answers` |
| Export | `GET …/{kbId}/export` (`application/zip`, OKF bundle) |
| Actuator | `GET /actuator/health` (public) |

The OpenAPI description is at `/v3/api-docs` and Swagger UI at `/swagger-ui.html` when `API_DOCS_ENABLED=true` or the
`dev` profile is active.

## Testing and quality gates

| Command | What it runs | Needs Docker |
|---|---|---|
| `mvn test` | Checkstyle + unit tests (no Spring context, `StubAIGateway` for LLM answers) | No |
| `mvn verify` | Checkstyle + unit + integration and Java E2E tests (Testcontainers PostgreSQL) + SpotBugs + JaCoCo gate (70% instruction coverage) | **Yes** |
| `mvn -B dependency-check:check` | OWASP dependency check; fails on CVSS ≥ 7.0 | No (needs network) |
| `cd frontend && npm test` | Angular unit tests (Vitest) | No |
| Playwright suite in `src/test/e2e/` | Browser journey from sign-up to a downloaded OKF bundle | Yes |

Skip flags for local runs: `-Dcheckstyle.skip`, `-Dspotbugs.skip`, `-DskipITs` (pair it with `-Djacoco.skip=true`,
because the coverage gate then measures unit tests only). `mvn jacoco:report` after `mvn verify` writes the HTML
report to `target/site/jacoco/`.

### Browser E2E suite

The E2E stack replaces the model provider with a WireMock stub that answers from static mappings (ADR 0042). It is
**test only**.

```bash
docker compose -f compose.yml -f compose.e2e.yml up -d --build --wait
cd src/test/e2e
npm ci
npx playwright install chromium
npm test                     # report in playwright-report/, traces in test-results/
cd ../../..
docker compose -f compose.yml -f compose.e2e.yml down -v
```

Set `E2E_BASE_URL` to test another address than `http://localhost:8080`.

### Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request:

| Job | Runs |
|---|---|
| `linux` | Frontend tests, then `mvn -B verify` (all gates); uploads JaCoCo, Checkstyle and SpotBugs reports |
| `windows` | `mvn -B package` (Checkstyle and unit tests) |
| `e2e` | After `linux`: the Playwright suite against the E2E stack; uploads the Playwright report and logs |

`.github/workflows/dependency-check.yml` runs the OWASP check every Monday and on manual dispatch (it reads the
`NVD_API_KEY` repository secret).

## Security model

- **Server-authoritative state.** The server owns grading, scoring, session and run state. `reference_answer`,
  `grounding_context`, `raw_prompt`, `raw_completion` and `cost` are never sent to the client.
- **Authentication.** Passwords are hashed with BCrypt (cost ≥ 12); a password sign-in always runs one hash comparison.
  The JWT is sent only in an HttpOnly, Secure, SameSite=Lax cookie. OAuth2 CSRF protection stays on.
- **Tenancy.** Ownership is checked in every controller method; every tenant-scoped port is scoped by `kbId`; every
  table carries `knowledge_base_id`.
- **Untrusted input.** Uploaded filenames are sanitized. Every URL taken from content or from a user passes the
  `EgressPolicy`: `http`/`https` only, ports 80/443, public unicast addresses only, every redirect checked, and the
  client connects only to the checked addresses.
- **Rendering.** Page bodies render with raw HTML escaped; external links carry `rel="noopener noreferrer nofollow"`.
- **Rate limits.** Per user for expensive operations and per client address for sign-in and sign-up.
- **CORS.** No cross-origin caller by default; at most one exact origin from the environment.
- **Study data** never enters a wiki page or an export.
- **Retention.** A document's text is kept while its knowledge base exists. To erase it, delete the knowledge base.

Rules for contributors: [`docs/standards/security/web-security.md`](docs/standards/security/web-security.md).

## Operational limits

- **One live instance.** Ingest runs are coordinated by an in-process set of executing runs, and the rate limits,
  quiz pointers of the bots and SSE emitters are in memory. During a deploy overlap, fenced commits keep the wiki
  correct and an interrupted run is re-generated once. Permanent multiple instances need a heartbeat lease.
- **The CLI is also a live instance** without a sweep. Stop the server before you run the CLI on the same database.
- **Graceful shutdown.** In-flight requests finish (30 s per phase) and the worker stops claiming new runs.
- **Index size.** Past about 20K tokens of index, a warning is logged and shown in the health view; nothing truncates
  the index (a lexical prefilter is planned).

## Project status and roadmap

Version **1.1.0-SNAPSHOT**. Phases 0–21 of the implementation plan are complete (wiki and ingest, Lint, API, export,
study, Query, Angular frontend, Docker deployment, CLI, image analysis, article fetcher, Discord and Slack bots,
security hardening, E2E testing and CI/CD). Open work:

- the manual Langfuse check of Phase 14 (observability and tracing);
- the Phase 21 follow-ups listed in the implementation plan;
- the Technical Debt Backlog: wider API integration tests, multi-tenant hardening;
- account linking between bot and web accounts (#43) and a per-account sign-in limit (#64).

Future considerations: a lexical prefilter for large indexes, multi-instance deployment, FSRS scheduling, shared
knowledge bases, a mobile layout. See [`docs/project/roadmap.md`](docs/project/roadmap.md) and
[`docs/project/implementation-plan.md`](docs/project/implementation-plan.md).

## Documentation map

| Document | Content |
|---|---|
| [`docs/INDEX.md`](docs/INDEX.md) | Start here: the index of all documentation |
| [`CONTEXT.md`](CONTEXT.md) | Domain glossary (ubiquitous language) |
| [`docs/project/vision.md`](docs/project/vision.md) | Product vision, principles and goals |
| [`docs/project/architecture.md`](docs/project/architecture.md) | Layers, data flows, wiki model, guards |
| [`docs/project/tech-stack.md`](docs/project/tech-stack.md) | Technology choices and rationale |
| [`docs/project/deployment.md`](docs/project/deployment.md) | Build, run, configuration, CI, platforms, CLI |
| [`docs/project/roadmap.md`](docs/project/roadmap.md) | Phase status |
| [`docs/adr/`](docs/adr/) | Architecture Decision Records 0001–0042 |
| [`docs/standards/`](docs/standards/) | Coding, API, model service, JPA, migration, Angular, security and testing standards |
| [`docs/wiki/llm_wiki.md`](docs/wiki/llm_wiki.md) | The LLM Wiki pattern and OKF |

## Contributing

Issues are tracked in [GitHub Issues](https://github.com/marcin-jasinski/mindforge/issues). Before you change code:

1. Read [`docs/INDEX.md`](docs/INDEX.md) and the standards for the area you change.
2. Keep the layer boundaries: no framework or I/O imports in `dev.mindforge.domain`; thin controllers; constructor
   injection; beans wired in `@Configuration` classes.
3. Never call a model inside a database transaction; call models only through `AIGateway` and by `ModelTier`.
4. To add a document format, register a new parser — do not change `ParserRegistry`.
5. Bump a model service's `VERSION` only when its logic or prompt changes.
6. Write tests: plain Mockito unit tests without a Spring context, `StubAIGateway` for LLM answers, Testcontainers for
   integration tests, AssertJ assertions, `make*` factory methods for test data.
7. Run `mvn verify` (Docker required) before you open a pull request. Work branches from `develop`; `master` is the
   main branch.

Files use UTF-8, LF line endings and no trailing whitespace (`.editorconfig`). The frontend uses `npm@11` only.

## License

MindForge is released under the [MIT License](LICENSE). Copyright (c) 2026 marcin-jasinski.
