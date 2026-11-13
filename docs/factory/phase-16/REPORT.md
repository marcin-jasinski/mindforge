# Factory run report — Phase 16 (Image Analysis)

- **Run:** phase-16 · started 2026-11-13T09:17:18Z · finished 2026-11-13T21:06:01Z
- **Outcome:** merged into `develop` at ec06c8c (--no-ff, tree equal to the gated phase branch). 10 tickets done, 0 parked.
- **Branch:** `factory/phase-16` · base 2b45984 (full: 2b459846543927342e1e8fcee4aca04611a368ae)

## Audit first
1. Open manual check: no live VISION call was made against a real provider. All tests use `StubAIGateway`. Upload one real PDF or DOCX with an image and read the description in the wiki.
2. Open manual check: the Phase 14 manual Langfuse check is still open. Phase 16 did not change it.
3. D09 (image position) was decided by the judge on one ballot. D02, D03 and D05 were majority decisions with one dissent each.
4. ADRs 0020 and 0021 are new (Accepted). ADR 0013 has a dated amendment for the Describe first step (P16-10).
5. The fixer added a limit that no decision names: `ImageExtractor.MAX_PIXELS` = 50 megapixels. An image above it is skipped before decode (P16-03 fix round 1, against a decompression attack). The orchestrator accepted it as local.
6. Two holistic findings are deferred: H1 (single-document GET and `remove` still load image bytes) and H2 (image size checks copied into `PdfParser`).

## Tickets
| Ticket | Title | Status | Commit | Fix rounds | Notes |
|---|---|---|---|---|---|
| P16-01 | ImageInput value; IMAGE content block carries it | done | f33d2b3 | 0 | no migration |
| P16-02 | Content blocks off document list, dedup lookup and responses | done | eaa9ec0 | 0 | JPQL projection `DocumentWithoutBlocks`; one API 403 retried |
| P16-03 | ImageExtractor: filter, re-encode, dedup, cap | done | ce554aa | 1 | fixed C1, C2, S1 (format from the bytes; 50 MP pre-decode guard) |
| P16-04 | PdfParser emits IMAGE blocks after page text | done | 44555b5 | 1 | fixed C1, S1 (per-parse COS object set; cap per image); tester r2 gave no report, orchestrator read the gate log |
| P16-05 | DocxParser emits IMAGE blocks after holding paragraph or table | done | 2378acb | 0 | |
| P16-06 | AIGateway image overload, VISION only | done | 0421127 | 0 | |
| P16-07 | ImageDescriber model service on VISION | done | ddc0062 | 0 | 1 minor deferred |
| P16-08 | Ingest run describes images first | done | c52c990 | 0 | |
| P16-09 | Run report labels for image description | done | 444cb9d | 0 | frontend test and build pass |
| P16-10 | ADR 0013 amendment, architecture, limits, close Phase 16 | done | 15e4e26 | 0 | docs only |

## Decisions
| Id | Method | Class | Decision | Dissent / risk |
|---|---|---|---|---|
| D01 | unanimous | adr | Describe in the run as first step; bytes in `documents.content_blocks` JSONB | large JSONB; H1 shows a read-path gap |
| D02 | majority | local | Nullable last component `ImageInput image` on `ContentBlock`; no migration | pragmatist voted B |
| D03 | majority | adr | Abstract overload `complete(tier, prompt, ImageInput, deadline)`; VISION only | pragmatist voted A |
| D04 | unanimous | local | Drop unusable image as `describe` RunFailure; gateway and deadline errors fail the run (retryable) | none |
| D05 | majority | local | Raster only; PNG/JPEG kept, others to PNG; skip <64 px and >5 MB; SHA-256 dedup; >100 images rejects upload | pragmatist voted A |
| D06 | unanimous | local | Title plus last 1500 chars as context; JSON `{relevant, description}`; TEXT "Image: "+description | C1 doubled marker deferred |
| D09 | judge | local | PDF: after page TEXT block; DOCX: after holding paragraph or table | single ballot; audit first |

Method counts: unanimous 3 · majority 3 · debate 0 · judge 1. Blockers 0. Q07, Q08, Q10 and Q11 were answered by the docs and have no decision files.

## Panel statistics
Unanimous 3, majority 3, judge 1. Round-2 debates 0. Vote changes 0. Ballots discounted for false evidence 0. Disputed review findings 0 (no `rulings/`).

## ADRs written
- `docs/adr/0020-images-described-in-the-run-bytes-kept-in-content-blocks.md` (D01)
- `docs/adr/0021-aigateway-image-overload.md` (D03; first written as a second 0020 in parallel, renumbered by the orchestrator)
- Amendment to `docs/adr/0013-ingest-typed-pipeline-no-agent-abstraction.md` (Describe first step)

## Deferred findings
- P16-03 C3 (minor) `ImageExtractor.java`: a null `mimeType` throws `NullPointerException`, not a skip.
- P16-07 C1 (minor) `ImageDescriber.java`: the prompt forbids a leading "Image:", but the code does not check it, so "Image: Image: ..." is possible.
- Holistic H1 (minor) `IngestionService.java`: the single-document GET and `remove` load all image bytes through `findById`. `DocumentWithoutBlocks` fits both.
- Holistic H2 (minor) `PdfParser.java`: the 64 px and 50 MP checks duplicate `ImageExtractor`.
- P16-03 S2 (MIME trust) is not deferred: the fix round for C1 fixed it.

## Manual checks still open
- No live VISION call against a real provider.
- Phase 14 manual Langfuse trace check.

## Statistics
- 10 tickets done, 0 parked. Fix rounds 2 (P16-03, P16-04). Disputed findings 0. Judge calls 6.
- Baseline had 1 failure (`ApiFlowTest.theLoginRouteIsTheSinglePageApp`, frontend not built). Integration gate: `mvn verify` 103 IT, 0 failures; frontend test and build pass.
- Forced continuations 2, stalled 0. Agent calls 84: planner@opus 1, solver@opus 6, solver@sonnet 13, judge@opus 6, ticket-writer@opus 1, implementer@inherit 11, tester@sonnet 12, reviewer@opus 22, reviewer@sonnet 8, fixer@inherit 2, scribe@sonnet 2.
- One API 403 error at the P16-02 implementer spawn; the retry passed.
