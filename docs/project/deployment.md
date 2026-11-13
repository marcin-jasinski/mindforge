# Deployment

MindForge ships as one container: the Spring Boot JAR with the Angular SPA embedded, next to PostgreSQL.

## Build

```bash
docker build -t mindforge .
```

The Dockerfile builds the SPA (`npm ci && npm run build`), packages the JAR with the build copied to
`classpath:/static/`, and runs it on `eclipse-temurin:21-jre-alpine` as a non-root user. Without Docker,
`mvn package -DskipFrontend=false` does the same on the host.

`mvn test` runs the unit tests only and needs no Docker daemon. `mvn verify` also runs the Testcontainers-backed
`integration/` and `e2e/` tests (failsafe) and enforces the 70% coverage gate on the combined result, so it
**requires a running Docker daemon**. `-DskipITs` skips the integration tests (and the Docker need), but the coverage
gate then measures unit tests only and will fail; pair it with `-Djacoco.skip=true`.

## Run locally

```bash
docker compose up --build
curl http://localhost:8080/actuator/health   # {"status":"UP"}
```

`compose.yml` starts `postgres` (data in the `postgres-data` volume) and `app`, which waits for PostgreSQL to be
healthy. `compose.override.yml` is picked up automatically for local work: it publishes PostgreSQL on 5432, turns off
`Secure` cookies for plain HTTP and supplies a development JWT secret. Flyway migrates the schema at startup.

## Configuration

Every variable is listed in `env.example`. The ones a deployment must set:

| Variable | Notes |
|---|---|
| `DATABASE_URL` | JDBC form: `jdbc:postgresql://host:5432/mindforge` |
| `DATABASE_USERNAME`, `DATABASE_PASSWORD` | |
| `JWT_SECRET` | At least 32 bytes; the app refuses to start with a shorter one |
| `OPENROUTER_API_KEY` | The model provider |
| `GOOGLE_CLIENT_*`, `GITHUB_CLIENT_*` | OAuth sign-in; any non-empty placeholder disables that provider in practice |
| `PORT` | Set by Railway and Render; defaults to 8080 |
| `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` | Optional. Export model-call traces to Langfuse; with either one unset, nothing is exported |
| `LANGFUSE_HOST` | Optional. Langfuse base URL; defaults to `https://cloud.langfuse.com` |

### Images

The ingest run describes the images of a PDF or DOCX upload with the model in `AI_MODEL_VISION`
(`mindforge.ai.model.vision`), one call per image, before the rest of the run. The image limits are constants, not
settings:

- Raster images only: PNG and JPEG are kept as they are, other decodable rasters are re-encoded to PNG, and EMF, WMF,
  SVG and images that do not decode are skipped.
- Images narrower or shorter than 64 px, images over 50 megapixels and images over 5 MB are skipped.
- A duplicate image is kept once per document.
- An upload with more than 100 images after these filters is rejected.

Each run attempt, including a retry, pays the vision calls again. Extracted image bytes are stored in
`documents.content_blocks`, so they count toward the database size.

## Railway

1. Create a project from the repository; Railway builds the `Dockerfile` (see `railway.json`).
2. Add the PostgreSQL plugin and set `DATABASE_URL` to its JDBC form, with the username and password variables.
3. Set `JWT_SECRET`, `OPENROUTER_API_KEY` and the OAuth variables.
4. Keep **one replica**. The health check is `/actuator/health`.

On Render, create a Docker web service from the same repository with the same variables and health check path. The
`Procfile` covers buildpack platforms that run a pre-built JAR. A service created before the actuator moved still
checks `/health`, which now answers 404: set its health check path to `/actuator/health` in the dashboard.

## CLI

The same JAR has two command-line entry points. The `cli` profile starts no web server and no run sweep. The CLI
reads the same configuration as the server (`DATABASE_*`, `OPENROUTER_API_KEY` and `JWT_SECRET`); Flyway migrates
the schema at startup. Set `JWT_SECRET` to at least 32 bytes also for the CLI. The CLI issues no tokens, but it does
not start with a shorter secret. Stop the server first (see [One live instance](#one-live-instance)). Give each option value in the
`--name=value` form: the form `--name value` does not bind a value.

`mindforge-pipeline` ingests a local file into a knowledge base and prints step progress:

```bash
java -jar mindforge.jar --spring.profiles.active=cli \
  pipeline --file=<path> --kb=<id> --user=<email> [--lesson-id=<id>] [--new-version]
```

| Option | Notes |
|---|---|
| `--file=<path>` | Required. The file to upload |
| `--kb=<id>` | Required. The knowledge base id (UUID) |
| `--user=<email>` | Required. The user who uploads; must own the knowledge base |
| `--lesson-id=<id>` | Optional. The lesson id, as in the web upload form |
| `--new-version` | Optional flag with no value. Upload the file as a new version of the lesson |

The command queues the Ingest Run, executes it in the same process and waits for that run to be COMPLETED or
FAILED. `mindforge.cli.run-timeout` sets the maximum wait (ISO-8601 duration, default `PT30M`). If the file is
identical to a document that the knowledge base has already, no run is queued: the command prints the state of the
latest run of that document and does not wait.

`mindforge-quiz` runs an interactive quiz in the terminal over the whole knowledge base:

```bash
java -jar mindforge.jar --spring.profiles.active=cli quiz --kb=<id> --user=<email>
```

The command prints each question, reads one answer line from standard input, and prints the score and the
feedback. It submits the answers through the same `QuizService` as the web UI, so the scores are recorded for
`--user`. The quiz stops when the questions are finished or at end of input, and the command prints a summary.

Exit codes are a stable contract for scripts. They do not change:

| Code | Meaning |
|---|---|
| `0` | Success |
| `1` | The ingest ran (or ran before) and failed, the wait timed out, or an unexpected error occurred |
| `2` | The input was refused before a run was queued: a usage error, an unknown user or knowledge base, a user who is not the owner, or a refused upload. Retry with the same input fails again |

### Trust model

The CLI trusts the operator ([ADR-0019](../adr/0019-cli-acts-as-a-named-user-without-password.md)). The database
credentials are the trust boundary: treat `DATABASE_*` as administrator access. The CLI takes `--user=<email>` and
asks for no password. It resolves the email to a user, checks that this user owns `--kb`, and then acts as that user:
the uploader of the document, and the owner of the quiz session and its scores. The per-user API rate limit
(`mindforge.rate-limits`) does not apply, because the CLI does not use the HTTP API.

## One live instance

Ingest runs are coordinated by an in-process set of executing runs (T17):

- The run sweep treats a run executing in any other process as abandoned.
- During a deploy's overlap the old and new instances briefly run side by side. Fenced commits keep the wiki correct,
  and a run the new instance sweeps is re-queued and re-generated once.
- Running two instances permanently needs a heartbeat lease (`lease_renewed_at`) instead of the in-memory set.

Shutdown is graceful (`server.shutdown: graceful`, 30 s per phase): in-flight requests finish and the worker stops
claiming new runs as the context closes. A run still generating is swept by the next instance.

The CLI (`--spring.profiles.active=cli`) is also a live instance, but it runs no sweep. Stop the server before you
run the CLI on the same database, or use a different database. If the server is live, its sweep fails the CLI's run
within one sweep interval and re-queues it: the server then generates the run again (the model cost is spent two
times, and fenced commits keep the wiki correct). The CLI drains the queue of the knowledge base, so it can also claim
a different QUEUED run. If the CLI claims such a run just before it exits, that run stays RUNNING, and the next
server start re-queues it.
