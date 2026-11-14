---
id: P18-08
title: Upload a Document from Discord with /upload, downloading the attachment only from Discord's CDN hosts
status: ready
plan_tasks: [18.2]
decisions: [D07, D12]
depends_on: [P18-05]
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
`/upload kb:<knowledge base> file:<attachment> [lesson_id] [new_version]` downloads the attachment from Discord's CDN
(fixed host list, size-capped), ingests it through the same `IngestionService.ingest` path as the web and CLI with
`UploadSource.DISCORD`, and replies ephemerally that the run is queued. Discord uploads fetch linked external articles
like API/CLI uploads when that feature is on.

## Context
- After P18-05: `DiscordCommand` plug-in, gate (allowlist -> rate limit (counted once per command, not per attachment)
  -> ephemeral defer -> identity), `DiscordReply`, shared `kb` option and `resolveKnowledgeBase`. `DiscordCall` must
  carry the attachment for this command (extend it, or pass the JDA `Message.Attachment` to the command; keep the
  command testable with a mocked `Attachment`).
- `src/main/java/dev/mindforge/domain/model/UploadSource.java`: `API, CLI, FILE_WATCHER, CONVERSATION, ARTICLE`.
  `documents.upload_source` is `VARCHAR(50)` without CHECK, mapped with `valueOf` — a new constant needs no migration.
  Phase-15 D06: one constant per real channel, added with its producer (this ticket).
- `src/main/java/dev/mindforge/application/service/DocumentUpload.java`: `DocumentUpload(kbId, uploadedBy, source,
  filename, mimeType, content, lessonId, newVersion)`. `IngestionService.ingest(DocumentUpload)` (:86) returns the
  document id (or the identical existing document's id) and throws `UploadRejectedException`, `LessonIdentityException`,
  `LessonAlreadyExistsException`, `UnknownLessonException`, and may throw `KnowledgeBaseBusyException`;
  `UploadPolicy.admit` enforces MIME and `mindforge.upload.max-size` (`AppProperties.Upload`) on every channel.
- Options and limits (answered question Q15): `/upload file:<attachment> [lesson_id] [new_version]`, mirroring web and
  CLI; filename and MIME from the attachment; exceptions map to fixed reply texts equivalent to the web's 409/422
  messages (e.g. "Lesson exists; send new_version or another lesson_id."); an identical upload replies with the existing
  document as the web does; the reply says the run is queued and does not wait for the run.
- `src/main/java/dev/mindforge/application/service/ArticleFetcher.java:43`: `SCANNED_SOURCES = EnumSet.of(API, CLI,
  FILE_WATCHER)`; test `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java`.
- Egress guard: `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java` (`MARKERS` :28,
  `OPERATOR_ENDPOINT_FILES` :36). ADR 0029 (`docs/adr/0029-discord-attachments-downloaded-by-the-platform-client-from-a-fixed-cdn-host-list.md`)
  is already written; ADR 0023 stays Accepted.
- Never call a model inside a transaction; the download blocks on a virtual thread outside any transaction.
- Reply visibility (D14, applied in P18-05): assert ephemeral for this path.

## Decisions to apply
**D07 — Attachment download and egress (ADR 0029).** Decision: treat Discord's API and its attachment CDN as the
operator-configured platform endpoint (ADR 0023 trust tier), and download `/upload` attachment bytes with JDA's own
client. Before any byte is read, require the attachment URL to be `https`, default port, no userinfo, with a host in the
fixed set `cdn.discordapp.com`, `media.discordapp.net`, and require the declared `Attachment.getSize()` to be within
`mindforge.upload.max-size`. Read at most max-size + 1 bytes; more than max-size fails, never truncates. Extend
`OutboundHttpGuardTest` with JDA/OkHttp download markers and allow only the one downloader file.
Implementation notes:
- One class in `dev.mindforge.discord`, e.g. `DiscordAttachments` (final): `byte[] read(Message.Attachment attachment, long maxBytes)`.
  - `private static final Set<String> CDN_HOSTS = Set.of("cdn.discordapp.com", "media.discordapp.net");`
  - Parse `attachment.getUrl()` with `URI`; require scheme `https`, `getUserInfo() == null`, port `-1` or 443,
    lower-cased host in `CDN_HOSTS`; else throw a typed exception (no bare `RuntimeException`) mapped to a fixed reply.
    Log at WARN the host only, never the signed URL query.
  - `if (attachment.getSize() > maxBytes)` -> the same "file too large" outcome as web upload; no download call.
  - `attachment.getProxy().download()` -> `get(timeout, SECONDS)` with a fixed deadline constant (e.g. 60 s); in
    try-with-resources `in.readNBytes((int) Math.min(Integer.MAX_VALUE - 8, maxBytes + 1))`; length > maxBytes -> too
    large. Never truncate.
- Handler order in `/upload`: allowlist -> identity -> ownership -> (deferred) -> `DiscordAttachments.read` ->
  `IngestionService.ingest(new DocumentUpload(..., UploadSource.DISCORD, filename, contentType, bytes, ...))`;
  `UploadPolicy.admit` runs there as on web and CLI. (P18-05's gate defers before identity; keep that.)
- `OutboundHttpGuardTest`: add markers `"okhttp3"`, `".getProxy()"`, `".download("`; add the downloader's exact path to
  `OPERATOR_ENDPOINT_FILES` with a comment citing ADR 0029; add a case that another Discord file containing
  `.download(` is reported.
- Docs: one sentence in `docs/standards/security/web-security.md` § Untrusted Input Handling naming ADR 0029.
- Unit tests (no Spring, fake/mocked `Attachment`/`AttachmentProxy`): off-list host refused; `http` refused; userinfo
  refused; declared size over cap refused and `download()` never called; body of maxBytes + 1 fails; body of exactly
  maxBytes passes.
- Dependency check: JDA brings OkHttp; verify JDA runs against the resolved OkHttp in the integration gate.

**D12 — Discord uploads and article fetch.** Decision: add `UploadSource.DISCORD` to `ArticleFetcher.SCANNED_SOURCES`,
so a Discord document fetches the external articles it links to exactly like an API, CLI or file-watcher upload, and
only when `mindforge.articles.fetch-external-articles` is on (default `false`). `ARTICLE` and `CONVERSATION` stay out.
Implementation notes:
- `ArticleFetcher.java`: `EnumSet.of(UploadSource.API, UploadSource.CLI, UploadSource.FILE_WATCHER, UploadSource.DISCORD)`.
- Unit tests in `ArticleFetcherTest` (plain Mockito): a `DISCORD` document with a link fetches it when the setting is
  on; a `DISCORD` document fetches nothing when the setting is off; `CONVERSATION` and `ARTICLE` documents fetch nothing.
- Docs note in the article-fetch docs is done in P18-10.

## Acceptance criteria
- [ ] AC1: `UploadSource.DISCORD` exists and `/upload` is its only producer; `/upload` is registered with required
  autocompleted `kb`, required attachment `file`, optional string `lesson_id`, optional boolean `new_version`.
- [ ] AC2: An owned knowledge base and a valid CDN attachment lead to one `IngestionService.ingest` call with
  `UploadSource.DISCORD`, the resolved user, the attachment filename and content type, the downloaded bytes,
  `lesson_id` and `new_version`; the reply says the run is queued (or names the existing document for an identical upload).
- [ ] AC3: `DiscordAttachments.read` refuses an off-list host, `http`, userinfo and a non-default port without
  downloading; refuses a declared size over max-size without calling `download()`; fails on a body of max-size + 1;
  passes a body of exactly max-size.
- [ ] AC4: `LessonAlreadyExistsException`, `UnknownLessonException`, `LessonIdentityException`,
  `UploadRejectedException`, too large, refused host and `KnowledgeBaseBusyException` each map to a fixed reply without
  the exception message; WARN logs carry the host only, never the signed URL.
- [ ] AC5: A not-owned or non-UUID `kb` replies "Unknown knowledge base." and nothing is downloaded or ingested.
- [ ] AC6: `ArticleFetcher.SCANNED_SOURCES` includes `DISCORD`; the three D12 tests pass.
- [ ] AC7: `OutboundHttpGuardTest` has the new markers, allows only the downloader file (comment cites ADR 0029), reports
  another Discord file containing `.download(`, and passes.
- [ ] AC8: The `/upload` reply is ephemeral; `web-security.md` § Untrusted Input Handling names ADR 0029.

## Test plan
- Unit (plain Mockito, no Spring context): `src/test/java/dev/mindforge/unit/discord/DiscordAttachmentsTest.java` (new)
  for AC3; `src/test/java/dev/mindforge/unit/discord/UploadCommandTest.java` (new) for AC1, AC2, AC4, AC5, AC8 (mocked
  `IngestionService`, recording fake `DiscordReply`).
- Unit: `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java` for AC6.
- Unit: `OutboundHttpGuardTest.java` for AC7.
- Gate: `mvn -B verify -DskipFrontend=true` proves JDA's OkHttp resolves at runtime with the rest of the classpath.

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/UploadSource.java`
- `src/main/java/dev/mindforge/application/service/ArticleFetcher.java`
- `src/main/java/dev/mindforge/discord/DiscordAttachments.java` (new)
- `src/main/java/dev/mindforge/discord/UploadCommand.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordCall.java`, `DiscordListener.java`, `DiscordConfig.java`
- `src/test/java/dev/mindforge/unit/infrastructure/security/OutboundHttpGuardTest.java`
- `src/test/java/dev/mindforge/unit/application/ArticleFetcherTest.java`
- `docs/standards/security/web-security.md`
- tests listed above (new)

## Out of scope
- A second HTTP client or `EgressPolicy` fetcher for the CDN (ADR 0029 rejected it); changes to `HttpArticleSource`.
- Waiting for the ingest run or reporting its result in Discord.
- New upload settings or rules specific to Discord.

## Notes
