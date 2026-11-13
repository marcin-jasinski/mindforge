---
id: P15-05
title: Admit a file into a knowledge base through the pipeline command as a named user
status: done
plan_tasks: [15.1]
decisions: [D04, D07]
depends_on: [P15-03, P15-04]
size: M
issue: 6
commit: 6c58b61
fix_rounds: 0
---
## Goal
`--spring.profiles.active=cli pipeline --file=<path> --kb=<id> --user=<email>` resolves the user, checks that the user
owns the Knowledge Base, reads the file, names its MIME type from the extension and calls the same
`IngestionService.ingest` as an upload, with `UploadSource.CLI`. Every refusal before a run is queued ends with exit
code 2 and one line on stderr. (Waiting for the Ingest Run and the run outcome are P15-06; in this ticket the runner
prints the document id and reports exit 0 after `ingest` returns.)

## Context
- After P15-03: `dev.mindforge.cli.CliCommand` (`of(args)`, `PIPELINE`, `QUIZ`), `CliExit { OK(0), RUN_FAILED(1),
  REFUSED(2) }`, `CliUsageRunner`, `CliExitOnReady`, `CliConfig` (`@Configuration @Profile("cli")`). Runners implement
  `ApplicationRunner` + `ExitCodeGenerator`, never throw for an expected outcome and never call `System.exit`.
- After P15-04: `AccountService.byEmail(String)` (throws `NotFoundException("User")`), `UploadSource.CLI`.
- `KnowledgeBaseService.requireOwner(UUID kbId, UUID userId)` (`application/service/KnowledgeBaseService.java`) throws
  `NotFoundException` for an unknown KB and `NotOwnerException` (`domain/model/`) for another owner.
- `application/service/DocumentUpload.java`: `record DocumentUpload(UUID knowledgeBaseId, UUID uploadedBy,
  UploadSource source, String filename, String mimeType, byte[] content, String lessonId, boolean newVersion)`.
  The web path builds it in `api/controller/DocumentController.java` (~line 56-61).
- `IngestionService.ingest(DocumentUpload): UUID` throws `UploadRejectedException`, `LessonIdentityException`,
  `LessonAlreadyExistsException`, `UnknownLessonException` (all in `domain/model/`). `UploadSanitizer.admit` refuses any
  MIME type that `ParserRegistry.mimeTypes()` has no parser for ("Unsupported document type").
- Parser MIME constants: `infrastructure/parsing/MarkdownParser.MIME_TYPE`, `PlainTextParser.MIME_TYPE`,
  `PdfParser.MIME_TYPE`, `DocxParser.MIME_TYPE`.
- ADR-0019 (`docs/adr/0019-cli-acts-as-a-named-user-without-password.md`). Rules: never modify `ParserRegistry`;
  thin adapter; constructor injection; ownership verified before any resource access.

## Decisions to apply
**D04 — Decision:** Both CLI runners take a required `--user=<email>`. An application-service method resolves the email
to a `User`, then the runner calls `KnowledgeBaseService.requireOwner(kbId, user.userId())` before any work. That user
is `DocumentUpload.uploadedBy`. No password: the database credentials are the trust boundary. Do not add a default
that falls back to the KB owner.

**D04 — Implementation notes (part applied here):**
- `mindforge-pipeline`: `--file=<path> --kb=<id> --user=<email>`. Order: parse args, `byEmail`,
  `requireOwner(kbId, userId)`, then work. The runner never touches `UserRepository`.
- `new DocumentUpload(kbId, user.userId(), UploadSource.CLI, ...)` (D06).
- Missing `--user`, unknown email, unknown KB or `NotOwnerException`: one line to stderr, non-zero exit (2 per D08), no
  ingest. Message for KB failures: "knowledge base not found or not owned by <email>".
- Unit tests (Mockito, no Spring): unknown email -> non-zero exit, ingest never called; KB owned by another user ->
  non-zero exit, no ingest; happy path passes the resolved id into `DocumentUpload`.

**D07 — Decision:** The pipeline CLI finds the MIME type of `--file=<path>` from a fixed, case-insensitive extension
map (`.md`, `.markdown` -> `text/markdown`; `.txt` -> `text/plain`; `.pdf` -> `application/pdf`; `.docx` -> the DOCX
MIME type). Any other extension gives `application/octet-stream`, which `UploadSanitizer.admit` refuses with its normal
message. Do not call `Files.probeContentType` and do not add a `--mime` option.

**D07 — Implementation notes:**
- In the CLI command class: `private static final Map<String, String> MIME_BY_EXTENSION` and `static String
  mimeTypeOf(Path file)` — take the text after the last `.` of the file name, lowercase with `Locale.ROOT`; no extension
  or unknown extension -> `"application/octet-stream"`.
- Use the parser constants for the values (adapters may import all layers).
- Pass the result to the same ingest path as the web controller, so `UploadSanitizer.admit` does the refusal; do not
  add a second allowlist check in the CLI.
- Unit tests: `notes.md`, `NOTES.MD`, `a.markdown` -> `text/markdown`; `a.txt` -> `text/plain`; `a.pdf` ->
  `application/pdf`; `a.docx` -> DOCX type; `a.sh`, `README`, `archive.tar.gz` -> `application/octet-stream`; a CLI run
  with an unsupported file shows the sanitizer's "Unsupported document type" message.

**Exit codes (D08, contract defined in P15-03):** usage error (D03: `pipeline` without `--file`, `--kb` or `--user`, or
an unparsable `--kb` UUID), unknown user, unknown KB, `NotOwnerException`, unreadable `--file`,
`UploadRejectedException`, `LessonIdentityException`, `UnknownLessonException`, `LessonAlreadyExistsException` -> 2 with
the exception message on stderr. Any other unexpected exception -> 1 (logged with `log`).

## Acceptance criteria
- [x] AC1: `PipelineCommandRunner` does nothing (exit code 0, no service call) when the command is not `pipeline`.
- [x] AC2: `pipeline` without `--file`, `--kb` or `--user`, or with a `--kb` that is not a UUID, prints usage to stderr,
      exit code 2, and calls no service.
- [x] AC3: Unknown `--user` email -> one stderr line, exit 2, `requireOwner` and `ingest` never called.
- [x] AC4: KB unknown or owned by another user -> stderr "knowledge base not found or not owned by <email>", exit 2,
      `ingest` never called.
- [x] AC5: `--user` with mixed case and padding resolves through `AccountService.byEmail` (no `UserRepository` in
      `dev.mindforge.cli`).
- [x] AC6: Unreadable or missing `--file` -> one stderr line, exit 2, `ingest` never called.
- [x] AC7: On the happy path `ingest` receives `DocumentUpload(kbId, resolvedUserId, UploadSource.CLI, <file name>,
      <mime from map>, <file bytes>, null, false)`.
- [x] AC8: `mimeTypeOf` gives the D07 table results for all the listed file names.
- [x] AC9: Each of `UploadRejectedException`, `LessonIdentityException`, `UnknownLessonException`,
      `LessonAlreadyExistsException` from `ingest` -> its message on stderr, exit 2.
- [x] AC10: The runner is a bean in `CliConfig` (profile `cli`), constructor-injected, and contains no `System.exit`.

## Test plan
- AC1–AC7, AC9: unit, `src/test/java/dev/mindforge/unit/cli/PipelineCommandRunnerTest.java` (new): Mockito mocks of
  `AccountService`, `KnowledgeBaseService`, `IngestionService`; `DefaultApplicationArguments`; a temp file
  (`@TempDir`); stderr/stdout captured through constructor `PrintStream`s; `ArgumentCaptor<DocumentUpload>`.
- AC8 and the "Unsupported document type" message: unit, `src/test/java/dev/mindforge/unit/cli/MimeTypeOfTest.java`
  (new) or nested in the runner test; for the message, mock `ingest` to throw the `UploadRejectedException` the
  sanitizer throws, or use a real `UploadSanitizer` if it has no I/O.
- AC10: `integration/cli/CliProfileContextTest` asserts the `PipelineCommandRunner` bean exists in the `cli` context.

## Files likely touched
- `src/main/java/dev/mindforge/cli/PipelineCommandRunner.java` (new)
- `src/main/java/dev/mindforge/cli/CliConfig.java`
- `src/test/java/dev/mindforge/unit/cli/PipelineCommandRunnerTest.java` (new)
- `src/test/java/dev/mindforge/unit/cli/MimeTypeOfTest.java` (new, optional)
- `src/test/java/dev/mindforge/integration/cli/CliProfileContextTest.java`

## Out of scope
- `--lesson-id`, `--new-version`, draining the run, waiting, run-outcome exit codes, the dedup outcome (P15-06).
- `Files.probeContentType`, a `--mime` flag, any change to `ParserRegistry` or `UploadSanitizer`.
- A password prompt; a KB-owner fallback for `--user`.

## Notes
- Decided: `mimeTypeOf` lives in `PipelineCommandRunner` (the CLI command class) as `public static`, because the unit tests are in another package.
- Decided: refusal lines are "user not found: <email>" and "cannot read file: <path>" because the ticket fixes only the KB message; an invalid path string (`InvalidPathException`) is an unreadable file (exit 2).
- Decided: an option given more than once, or blank, counts as missing (usage, exit 2), because `ApplicationArguments` gives a list and the runner takes a single value; the `--user` value goes to `byEmail` unchanged so normalising stays in one place.
- Decided: `CliExitOnReadyWiringTest` registers mocks of `AccountService`, `KnowledgeBaseService`, `IngestionService`, because it loads `CliConfig` alone and the new runner bean needs them.
- AC1: PipelineCommandRunnerTest.anotherCommandDoesNothing
- AC2: PipelineCommandRunnerTest.aMissingOrInvalidOptionPrintsTheUsageAndCallsNoService
- AC3: PipelineCommandRunnerTest.anUnknownUserIsRefusedBeforeTheOwnershipCheck
- AC4: PipelineCommandRunnerTest.aKnowledgeBaseOfAnotherOwnerIsRefusedWithoutIngest, anUnknownKnowledgeBaseIsRefusedWithoutIngest
- AC5: PipelineCommandRunnerTest.aMixedCaseAndPaddedEmailIsResolvedByTheAccountService
- AC6: PipelineCommandRunnerTest.aMissingFileIsRefusedWithoutIngest
- AC7: PipelineCommandRunnerTest.theHappyPathIngestsTheFileAsTheResolvedUserFromTheCli
- AC8: MimeTypeOfTest.theExtensionNamesTheMimeType; PipelineCommandRunnerTest.anUnsupportedFileShowsTheSanitizersMessage
- AC9: PipelineCommandRunnerTest.eachRefusalOfTheIngestPrintsItsMessageAndReportsTheRefusedExitCode (exit 1 path: anUnexpectedFailureReportsTheRunFailedExitCode)
- AC10: CliProfileContextTest.thePipelineCommandRunnerIsABeanThatDoesNothingWithoutItsCommand
