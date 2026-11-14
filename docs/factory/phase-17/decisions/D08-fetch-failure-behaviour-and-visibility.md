---
id: D08
question: Q08
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q08.r1.tally.md]
adr:
---
## Decision
Use A. When one article cannot be ingested, skip it, log it with a sanitised URL and a fixed reason code, and
continue with the next URL. Store nothing about the skip. The parent upload, its document and its run are not
affected. Do not add a metrics counter in this phase.

## Why
- D01/ADR 0024 (0024:5-11): the fetch runs in an `AFTER_COMMIT` listener after the parent upload commits. The parent
  run is independent, and a completed run is not rewritten (T17). So B has no run to write to.
- Plan 17.2 (implementation-plan.md:1529) says an existing lesson is "skipped and logged". A matches this exactly.
- Metrics live in infrastructure behind ports (`ObservationRunMeter.java:17`); a counter needs a new port and
  belongs to Phase 14, which is not done. C can be added later with no change to A.
- Fixed reason codes follow `IngestRunFailedException.reasonFor` and D02's `EgressDeniedException.Reason`.

## Rejected
- B: verified hard objection; the parent run is not open when the fetch runs, and a completed run is not rewritten.
- C: correct but adds a new metrics port before Phase 14; defer.

## Dissent
None. Risk to watch: a skip is visible only in logs. If users need to see per-article failures, that is a separate
design change (a per-document note), not a patch to runs.

## Implementation notes
- Typed exception in the application layer, e.g. `ArticleFetchException` (extends a meaningful base, not bare
  `RuntimeException`) with a `Reason` enum: POLICY, TIMEOUT, HTTP_STATUS, CONTENT_TYPE, TOO_LARGE, NO_TITLE,
  LESSON_EXISTS, DUPLICATE. Map `EgressDeniedException` to POLICY. Messages are fixed text; never the remote body.
- The fetch application service catches per URL; one failure never stops the loop. Also catch unexpected
  `RuntimeException` per URL in the listener path and log it with a fixed reason, so the executor task never dies
  silently mid-list.
- Log: `log.warn("article skipped reason={} url={}", reason, safeUrl)` for POLICY, TIMEOUT, HTTP_STATUS,
  CONTENT_TYPE, TOO_LARGE, NO_TITLE; `log.info` for LESSON_EXISTS and DUPLICATE. `safeUrl` = scheme + host + port +
  path; strip userinfo, query and fragment.
- Tests (plain Mockito, StubAIGateway where needed): each reason is logged and skipped; the second URL is still
  ingested after the first fails; nothing is written for a skipped URL; the parent run and document are unchanged;
  the logged URL has no query or userinfo; no remote body text appears in any exception message.
