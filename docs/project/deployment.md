# Deployment

MindForge ships as one container: the Spring Boot JAR with the Angular SPA embedded, next to PostgreSQL.

## Build

```bash
docker build -t mindforge .
```

The Dockerfile builds the SPA (`npm ci && npm run build`), packages the JAR with the build copied to
`classpath:/static/`, and runs it on `eclipse-temurin:21-jre-alpine` as a non-root user. Without Docker,
`mvn package -DskipFrontend=false` does the same on the host.

## Run locally

```bash
docker compose up --build
curl http://localhost:8080/health   # {"status":"UP"}
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

## Railway

1. Create a project from the repository; Railway builds the `Dockerfile` (see `railway.json`).
2. Add the PostgreSQL plugin and set `DATABASE_URL` to its JDBC form, with the username and password variables.
3. Set `JWT_SECRET`, `OPENROUTER_API_KEY` and the OAuth variables.
4. Keep **one replica**. The health check is `/health`.

On Render, create a Docker web service from the same repository with the same variables and health check path. The
`Procfile` covers buildpack platforms that run a pre-built JAR.

## One live instance

Ingest runs are coordinated by an in-process set of executing runs (T17):

- The run sweep treats a run executing in any other process as abandoned.
- During a deploy's overlap the old and new instances briefly run side by side. Fenced commits keep the wiki correct,
  and a run the new instance sweeps is re-queued and re-generated once.
- Running two instances permanently needs a heartbeat lease (`lease_renewed_at`) instead of the in-memory set.

Shutdown is graceful (`server.shutdown: graceful`, 30 s per phase): in-flight requests finish and the worker stops
claiming new runs as the context closes. A run still generating is swept by the next instance.
