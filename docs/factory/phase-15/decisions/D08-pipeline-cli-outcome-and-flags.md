---
id: D08
question: Q08
status: decided
method: unanimous
class: local
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q08.r1.tally.md]
adr:
---
## Decision
Use B: the pipeline CLI takes `--file`, `--kb`, `--user` (D04), an optional `--lesson-id=<id>` and a boolean
`--new-version` flag, and maps them 1:1 onto `DocumentUpload.lessonId` / `DocumentUpload.newVersion` with
`UploadSource.CLI` (D06). It waits for its own run as D01 says and ends with one of three stable exit codes:
**0** = success, **1** = the ingest ran (or ran before) and failed, **2** = the input was refused before any run
was queued (usage error, unknown user/KB/not owner, or a refused upload).

## Why
- C contradicts binding D01 (D01:14-20): under `cli` nothing drains a queued run, so exit-after-queue orphans it.
- A has a dead end: `LessonAlreadyExistsException` tells the user to "upload it as a new version or choose another
  lesson id" (LessonAlreadyExistsException.java:12-13), and only B lets the CLI do that.
- B adds no logic: both fields exist on `DocumentUpload` (DocumentUpload.java:18-19) and the web form maps them the same
  way (DocumentController.java:56-61); the service owns every lesson rule (IngestionService.java:95-99).
- Exit 2 vs 1 (2 of 3 ballots): a refusal means "change the input, retrying the same command will fail again"; a
  FAILED run means "the pipeline failed, a retry may work". Scripts need to tell these apart. Usage errors (D03) and
  D04 lookup failures are the same class as a refusal, so they share 2.
- Identical upload: the service returns the old id even when every run of that document failed
  (IngestionService.java:90-92, :166-168). Exit 0 there would report a failed document as ingested, so the exit code
  follows the latest run state. This refines D01's "already ingested, exit 0" for the FAILED case only.

## Rejected
- A: no way to ingest a second version of a lesson from the CLI; a scripted ingest hits a permanent refusal.
- C: contradicts D01; the queued run is never drained under `cli`.
- Refused upload = 1 (architect): merges "bad input" with "pipeline failed"; scripts cannot tell them apart.

## Dissent
None on the option. The architect put refused uploads on exit 1; overruled above. Risk to watch: exit codes are now a
script contract; document them in the CLI usage text and do not renumber them later.

## Implementation notes
- `dev.mindforge.cli` pipeline runner (D01/D03): options `--file`, `--kb`, `--user`, optional `--lesson-id` (single
  value; blank = absent), `--new-version` (flag, no value; `ApplicationArguments.containsOption`). No CLI-side rule
  about `--new-version` without `--lesson-id`: pass through and let `IngestionService` decide.
- One small mapping in `dev.mindforge.cli` (e.g. `enum CliExit { OK(0), RUN_FAILED(1), REFUSED(2) }`); runner implements
  `ExitCodeGenerator`, exit goes through `SpringApplication.exit` so the context closes and `RunWorker.stop()` runs.
  Never `System.exit` inside the runner.
- Outcome table:
  - new run COMPLETED -> 0; if it has per-page failures or `supersession_skipped`, print one warning line to stderr.
  - new run FAILED -> 1, print the failure reason. Wait timeout (D01 `mindforge.cli.run-timeout`) -> 1.
  - identical upload (no run created by this call): print document id and latest run state, do not wait;
    latest run COMPLETED / QUEUED / RUNNING -> 0; latest run FAILED -> 1 with a hint that the document must be
    retried or removed in the web UI.
  - `LessonAlreadyExistsException` -> 2, message names `--new-version` or another `--lesson-id`.
  - `UnknownLessonException`, `LessonIdentityException`, `UploadRejectedException` -> 2, exception message on stderr.
  - usage error (D03), missing/unknown `--user`, unknown KB, `NotOwnerException` (D04), unreadable `--file` -> 2.
  - any other unexpected exception -> 1 (logged).
- Usage text lists the flags and the three exit codes.
- Unit tests (Mockito, `DefaultApplicationArguments`, no Spring): `--lesson-id=x --new-version` reaches
  `DocumentUpload("x", true)`; no flags -> `(null, false)`; each refusal exception -> 2 and ingest-wait not started;
  LessonAlreadyExists message mentions `--new-version`; COMPLETED -> 0; COMPLETED with page failures -> 0 + warning;
  FAILED -> 1; timeout -> 1; identical upload with latest COMPLETED -> 0 without drain; identical upload with latest
  FAILED -> 1; usage error -> 2.

## Amendment (P15-03 review, round 1)
Option values use the `--name=value` form (`--file=<path> --kb=<id> --user=<email> --lesson-id=<id>`). Spring's `ApplicationArguments` binds a value only from `--name=value`; the space form (`--file <path>`) gives an option with no value and turns the value into a non-option argument. `--new-version` stays a flag with no value. No custom parser (no command framework).
