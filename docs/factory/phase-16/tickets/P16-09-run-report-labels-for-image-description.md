---
id: P16-09
title: Label the image description step, its skipped images and its run failure in the run report
status: ready
plan_tasks: [16.2]
decisions: [D04]
depends_on: [P16-08]
size: S
issue:
commit:
fix_rounds: 0
---
## Goal
The frontend shows the new `describe` run step in the learner's words: a skipped image reads
"image description · block 3: ..." in the run report, a running run shows a progress label for the describe step (if
P16-08 sends one), and the run failure "no image could be described" has a readable sentence.

## Context
- `frontend/src/app/shared/labels.ts`:
  - `FAILURE_STEPS` (line ~58): `resolve`, `linkCheck`, `review`, `write`, `supersede` -> labels; used by
    `failureLine({step, item, path, reason, count})`, which joins `[step label, item, path]` with `' · '` and appends
    `': ' + failureReasonLabel(reason)`.
  - `FAILURE_REASONS` (regex -> sentence list, line ~45), used by `failureReasonLabel`; unknown reasons show as sent.
  - `PROGRESS_STEPS` (line ~80): `extract`, `resolve`, `write`, `linkCheck`, `supersede`, `review`; used by
    `progressStepLabel`.
- Spec: `frontend/src/app/shared/labels.spec.ts` (cases for `failureLine`, `failureReasonLabel`, `progressStepLabel`).
- P16-08 records `RunFailure.ofItem("describe", "block " + position, null, reason)` with reason `"not relevant"` or
  `"no usable description"`, fails an all-dropped image-only run with `"no image could be described"` (retryable),
  and may send a progress step named `describe` (check `IngestPipeline` for `step(state, "describe"`; read P16-08's
  Notes).
- Standards: `docs/standards/frontend/angular-patterns.md`, `accessibility.md`, `css.md`. Interface text is plain
  English, short.

## Decisions to apply
**D04 — Decision:** when the vision model returns blank or unusable output for one image, drop that IMAGE block,
record a `RunFailure` with step `describe` on the run, and continue. If the document had IMAGE blocks, every one was
dropped, and no TEXT block remains, fail the run with a fixed, retryable reason, not "the document has no text".

**D04 — Implementation notes (frontend part):**
- Add `describe: 'image description'` to `FAILURE_STEPS` in `frontend/src/app/shared/labels.ts`, with a spec case for
  `failureLine`.

Also (same behaviour, needed so the new texts read well):
- Add to `FAILURE_REASONS`: `[/^no image could be described/, 'No image in the document could be described. Retry the run.']`.
- If P16-08 sends a `describe` progress step, add `describe: 'Describing images'` to `PROGRESS_STEPS`.
- Reasons `"not relevant"` and `"no usable description"` are already readable; do not add entries for them.

## Acceptance criteria
- [ ] AC1: `failureLine({ step: 'describe', item: 'block 3', reason: 'not relevant' })` returns
  `'image description · block 3: not relevant'`.
- [ ] AC2: `failureReasonLabel('no image could be described')` returns the new sentence.
- [ ] AC3: If the backend sends a `describe` progress step, `progressStepLabel('describe')` returns
  `'Describing images'`.
- [ ] AC4: `cd frontend && npm test -- --no-watch && npm run build` passes.

## Test plan
- Frontend unit (Angular test runner): add cases to `frontend/src/app/shared/labels.spec.ts` for AC1–AC3.

## Files likely touched
- `frontend/src/app/shared/labels.ts`
- `frontend/src/app/shared/labels.spec.ts`

## Out of scope
- Any other frontend change: no image rendering, no new component, no API model change.
- Backend changes.

## Notes
