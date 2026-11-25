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

`mvn test`, `mvn package` and `mvn verify` also run Checkstyle (`checkstyle-check`, bound to the `validate` phase)
over main and test sources, with the ruleset in `config/checkstyle/checkstyle.xml`. A violation fails the build.
`-Dcheckstyle.skip` skips the check.

### Dependency check

The OWASP dependency check audits the Maven dependencies against the NVD. No build phase runs it: `mvn verify` does not
start it. Run the goal by hand:

```bash
mvn -B dependency-check:check
```

- The check fails when a dependency has a vulnerability with CVSS 7.0 or higher. Fix a finding with a dependency
  upgrade. When a finding does not apply or cannot be fixed, add a suppression to `dependency-check-suppressions.xml`
  (at the repository root): one package and one vulnerability per entry, each with a `<notes>` reason.
- `NVD_API_KEY` is optional (free from nvd.nist.gov). Set it as an environment variable, never in a committed file.
  Without it, the first run downloads all NVD data slowly (about 30 minutes) and NVD can throttle it. The data is kept
  in `~/.m2`, so the next runs take about one minute.
- The goal needs the network: it does not run with `mvn -o`.
- The OSS Index analyzer and the Node, npm, Yarn, pnpm and RetireJS analyzers are off: the check covers the Maven
  dependencies only.
- No CI job runs the check yet; a scheduled job is Phase 21 work
  ([#63](https://github.com/marcin-jasinski/mindforge/issues/63)).

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
| `AUTH_RATE_LIMIT_REQUESTS`, `AUTH_RATE_LIMIT_WINDOW`, `CORS_ALLOWED_ORIGIN`, `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | Optional. See **Sign-in limit, client address and CORS** below |

### Sign-in limit, client address and CORS

| Variable | Default | Notes |
|---|---|---|
| `AUTH_RATE_LIMIT_REQUESTS` | `10` | `mindforge.rate-limits.auth.requests`: sign-in and sign-up POSTs per client address in one window. Both share one allowance, and failed attempts count. Over the limit: `429` with `Retry-After` ([ADR-0038](../adr/0038-auth-rate-limit-is-keyed-by-client-address.md)) |
| `AUTH_RATE_LIMIT_WINDOW` | `PT1M` | `mindforge.rate-limits.auth.window`: the window (ISO-8601 duration) |
| `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` | Spring's private ranges | Optional. The proxies whose `X-Forwarded-*` headers the server trusts: a comma-separated CIDR list or a Java regex. Unset: the private, loopback, link-local, CGNAT and IPv6 ULA ranges ([ADR-0039](../adr/0039-client-address-comes-from-tomcats-remote-ip-valve-with-private-range-proxies.md)) |
| `CORS_ALLOWED_ORIGIN` | blank | Optional. Blank admits no cross-origin caller. Set it only when the SPA is served from another origin than the API: one exact origin, never a list and never `*` (the server does not start with either) ([ADR-0041](../adr/0041-cors-admits-one-origin-from-the-environment-and-none-by-default.md)) |

- The client address is the direct peer, unless the peer is a trusted proxy: then it is the rightmost
  `X-Forwarded-For` address that is not a trusted proxy. The server trusts `X-Forwarded-For` and `X-Forwarded-Proto`
  only from a peer on a private address.
- **Self-hosted.** A reverse proxy or the Docker userland proxy on a private address is trusted, and so is every other
  host on that private network. When other hosts can reach the server on a private address, set
  `SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES` to the address of your proxy only. When the proxy is not trusted, every
  user gets the proxy's address, so all sign-ins share one allowance.
- The sign-in limit counts in memory: it is correct only with one live instance (see
  [One live instance](#one-live-instance)).
- The limit is per address, not per account
  ([#64](https://github.com/marcin-jasinski/mindforge/issues/64)).

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

### Articles

The server can fetch the web articles that an upload links to and ingest each one as a document of its own
(`ARTICLE`), with its own Ingest Run. The setting is global and off by default (`mindforge.articles.*`):

| Variable | Default | Notes |
|---|---|---|
| `FETCH_EXTERNAL_ARTICLES` | `false` | Turns on the fetch of linked articles for new uploads, for all knowledge bases |
| `ARTICLES_MAX_PER_DOCUMENT` | `10` | The first N distinct article URLs of a document are fetched; the remainder are skipped and logged |
| `ARTICLES_MAX_BODY_SIZE` | `2MB` | A larger response is not read and the article is skipped |
| `ARTICLES_CONNECT_TIMEOUT` | `5s` | Per connection |
| `ARTICLES_REQUEST_TIMEOUT` | `15s` | Total per article URL, across up to 5 redirects |

- The server fetches after the upload commits, on a background thread; the upload does not wait. A crash or a
  shutdown before the fetch completes loses that upload's articles. A retry of the run does not fetch again.
- The server takes URLs only from the text and headings of an uploaded document (not from a fetched article and not
  from a chat turn). It fetches only `http` and `https` URLs on port 80 or 443 whose host resolves only to public
  addresses, and it checks each redirect again ([ADR-0022](../adr/0022-egress-allowlist-schemes-ports-public-addresses.md)).
  There is no host allowlist and no egress setting: when the fetch is on, any public host is reachable.
- The response must be HTML (`text/html` or `application/xhtml+xml`).
- An article run may only create pages. It does not change a live page and it does not run Supersede.
- A skipped article (refused URL, host not reachable, timeout, error status, not HTML, too large, too many
  redirects, no text, no usable title, duplicate, or a lesson that already exists) shows only in the server log, with
  a fixed reason. Nothing shows in the UI or on the parent run.
- A document uploaded with the Discord `/upload` command or sent to the Slack bot in a DM is scanned for article URLs
  like an API, CLI or file-watcher upload, only when `FETCH_EXTERNAL_ARTICLES` is true.

### Discord bot

The server can run a Discord bot with the slash commands `/ask`, `/quiz start`, `/quiz answer` and `/upload`, and a
daily direct message about due flashcards. The bot is off unless the token is set (`mindforge.discord.*`):

| Variable | Default | Notes |
|---|---|---|
| `DISCORD_BOT_TOKEN` | empty | **Secret.** The bot token from the Discord developer portal. When it is blank, the bot does not start |
| `MINDFORGE_DISCORD_ALLOWED_GUILD_IDS` | empty | Required when the token is set: comma-separated guild (server) ids. When it is empty, the server does not start ([ADR-0028](../adr/0028-discord-commands-admitted-by-a-fail-closed-guild-and-role-allowlist.md)) |
| `MINDFORGE_DISCORD_ALLOWED_ROLE_IDS` | empty | Optional: comma-separated role ids. Empty admits any member of an allowed guild; otherwise the member needs at least one listed role |
| `MINDFORGE_DISCORD_REMINDER_CRON` | `0 0 9 * * *` | `mindforge.discord.reminder-cron`: when the due-flashcard reminders go out (Spring cron, six fields) |
| `MINDFORGE_DISCORD_REMINDER_ZONE` | `UTC` | `mindforge.discord.reminder-zone`: the time zone of the cron |

- An invalid token stops the startup. The bot never runs in the `cli` profile.
- The commands work only in an allowed guild. A command in a DM, in another guild or from a member without an
  allowed role gets a fixed "not available" reply. The bot sends DMs only for the reminders.
- The bot uses the same rate limit as the web (`RATE_LIMIT_REQUESTS` per `RATE_LIMIT_WINDOW`, default 30 per minute)
  per Discord user, with a counter of its own. Every command counts.
- Every reply is ephemeral: only the caller sees it
  ([ADR-0032](../adr/0032-discord-replies-are-ephemeral.md)). The Discord account cannot sign in to the web (see
  **Accounts** below), so the ephemeral reply is the only copy the user sees until account linking
  ([#43](https://github.com/marcin-jasinski/mindforge/issues/43)) exists.
- `/ask`, `/quiz start` and `/upload` take a `kb` option with the caller's knowledge bases
  ([ADR-0030](../adr/0030-discord-commands-name-the-knowledge-base-and-provisioning-creates-a-default-one.md)).
- Each `/ask` is a one-turn Query interaction: a second `/ask` does not see the first one.
- `/quiz answer` answers the caller's current quiz. The server keeps this pointer in memory: after a restart, run
  `/quiz start` again.
- `/upload` reads the attachment only over https from `cdn.discordapp.com` or `media.discordapp.net`, and only up to
  `UPLOAD_MAX_SIZE` ([ADR-0029](../adr/0029-discord-attachments-downloaded-by-the-platform-client-from-a-fixed-cdn-host-list.md)).
- The reminder DM gives the number of due flashcards per knowledge base, and only for knowledge bases with due cards. It
  never contains card text. A user with no due cards gets no DM. A failed DM is logged, and the job continues.
- **Accounts.** On first contact, a Discord user gets a separate account with the email
  `discord-<id>@users.invalid` and one knowledge base named "Discord"
  ([ADR-0027](../adr/0027-bot-users-provisioned-with-a-reserved-invalid-email.md)). This account cannot see the
  knowledge bases of a web account, and there is no link between the two. Nobody can register or sign in on the web
  with an email in the `users.invalid` domain.
- A live test against real Discord is not automated: it needs a bot token and a test guild.

### Slack bot

The server can run a Slack bot with the slash commands `/mf-ask` and `/mf-quiz`, and it ingests a file sent to the bot
in a direct message. The bot connects over Socket Mode, so it needs no public URL. It is off unless both tokens are set
(`mindforge.slack.*`):

| Variable | Default | Notes |
|---|---|---|
| `SLACK_BOT_TOKEN` | empty | **Secret.** The bot token (`xoxb-`). Set it together with `SLACK_APP_TOKEN`: when only one of the two is set, the server does not start. When both are blank, the bot does not start |
| `SLACK_APP_TOKEN` | empty | **Secret.** The app-level token (`xapp-`) that opens the Socket Mode connection |
| `SLACK_ALLOWED_TEAM_IDS` | empty | Required when the tokens are set: comma-separated team (workspace) ids. When it is empty, the server does not start ([ADR-0034](../adr/0034-slack-commands-admitted-when-workspace-and-caller-team-are-allowed.md)) |

**Slack app setup** (at api.slack.com/apps):

1. Turn on **Socket Mode**. Make an app-level token with the `connections:write` scope: this is `SLACK_APP_TOKEN`.
2. Give the bot token these scopes: `commands`, `chat:write`, `im:history`, `files:read`. Install the app in the
   workspace: the bot token is `SLACK_BOT_TOKEN`.
3. Subscribe to the bot event `message.im`.
4. In **App Home**, enable the **Messages** tab, so that users can send files to the bot.
5. Make the slash commands `/mf-ask` and `/mf-quiz`.

- The bot never runs in the `cli` profile. A failure to connect (for example, an invalid token) is logged, and the rest
  of the server continues to run. After a connection is lost, the bot connects again by itself. The WebSocket client is
  Java-WebSocket ([ADR-0037](../adr/0037-slack-socket-mode-uses-the-java-websocket-backend.md)).
- The bot admits a caller only when the workspace of the request and the caller's own team are both in
  `SLACK_ALLOWED_TEAM_IDS`. Other callers get a fixed "not available" reply.
- The bot uses the same rate limit as the web (`RATE_LIMIT_REQUESTS` per `RATE_LIMIT_WINDOW`, default 30 per minute)
  per Slack user, with a counter of its own. Every command and every upload counts.
- Every command reply is ephemeral: only the caller sees it
  ([ADR-0032](../adr/0032-discord-replies-are-ephemeral.md)).
- The commands and the upload act on the caller's only knowledge base and name no knowledge base
  ([ADR-0036](../adr/0036-slack-commands-act-on-the-callers-only-knowledge-base.md)).
- `/mf-ask <question>` is a one-turn Query interaction: a second `/mf-ask` does not see the first one.
- `/mf-quiz start` starts a quiz; `/mf-quiz answer <text>` answers the caller's current quiz. The server keeps this
  pointer in memory: after a restart, run `/mf-quiz start` again.
- **Upload.** Send one file to the bot in a direct message
  ([ADR-0035](../adr/0035-slack-uploads-are-files-sent-to-the-bot-in-a-direct-message.md)). The message text may hold
  only `lesson_id=<value>` and `new_version`, each one time at most. The bot replies in the same direct message. The
  bot ignores files in channels. It reads the file only over https from `files.slack.com`, with the bot token, and
  only up to `UPLOAD_MAX_SIZE`
  ([ADR-0029](../adr/0029-discord-attachments-downloaded-by-the-platform-client-from-a-fixed-cdn-host-list.md)).
- There are no reminder DMs for Slack users.
- **Accounts.** On first contact, a Slack user gets a separate account with the email
  `slack-<team>-<user>@users.invalid` and one knowledge base named "Slack"
  ([ADR-0027](../adr/0027-bot-users-provisioned-with-a-reserved-invalid-email.md),
  [ADR-0033](../adr/0033-slack-identity-keyed-by-home-team-and-user-id.md)). The key is the caller's home team and user
  id, so one user id in two teams gives two accounts. This account is separate from a Discord account and from a web
  account, and it cannot sign in to the web.
- A live test against real Slack is not automated: it needs a Slack app, a bot token, an app-level token and a test
  workspace ([#53](https://github.com/marcin-jasinski/mindforge/issues/53)).

## Railway

1. Create a project from the repository; Railway builds the `Dockerfile` (see `railway.json`).
2. Add the PostgreSQL plugin and set `DATABASE_URL` to its JDBC form, with the username and password variables.
3. Set `JWT_SECRET`, `OPENROUTER_API_KEY` and the OAuth variables.
4. Keep **one replica**. The health check is `/actuator/health`.

Railway and Render put a proxy on a private address in front of the app. The server trusts `X-Forwarded-*` only from a
private-range peer, so it gets the real client address and `https` from that proxy without a setting. A public client
cannot forge the headers, because it cannot connect from a private address.

On Render, create a Docker web service from the same repository with the same variables and health check path. The
`Procfile` covers buildpack platforms that run a pre-built JAR. A service created before the actuator moved still
checks `/health`, which now answers 404: set its health check path to `/actuator/health` in the dashboard.

## CLI

The same JAR has two command-line entry points. The `cli` profile starts no web server, no run sweep, no Discord or Slack bot
and no article fetch: the process exits after its own run, so it does not register the listener that fetches linked articles, even
when `FETCH_EXTERNAL_ARTICLES` is true. The CLI
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
- The rate limits (`mindforge.rate-limits`, including the sign-in limit) count in memory per instance. With two
  instances, each one gives its own allowance.

Shutdown is graceful (`server.shutdown: graceful`, 30 s per phase): in-flight requests finish and the worker stops
claiming new runs as the context closes. A run still generating is swept by the next instance.

The CLI (`--spring.profiles.active=cli`) is also a live instance, but it runs no sweep. Stop the server before you
run the CLI on the same database, or use a different database. If the server is live, its sweep fails the CLI's run
within one sweep interval and re-queues it: the server then generates the run again (the model cost is spent two
times, and fenced commits keep the wiki correct). The CLI drains the queue of the knowledge base, so it can also claim
a different QUEUED run. If the CLI claims such a run just before it exits, that run stays RUNNING, and the next
server start re-queues it.
