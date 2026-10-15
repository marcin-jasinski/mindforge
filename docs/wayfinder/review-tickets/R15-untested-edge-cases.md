---
id: R15
title: Untested edge cases on the model and validation boundary
type: test
status: closed
severity: medium
assignee:
blocked_by: [R13]
---

## Problem

Meaningful untested branches on paths that handle untrusted or model-generated input. Listed
most valuable first. (The `ModelJson` gap is the biggest of these and is covered separately by
**R13**; do that first.)

### 1. `QuizGenerator` returns model output unfiltered

`src/main/java/dev/mindforge/agent/QuizGenerator.java:36` returns `output.questions()` with no
validation. `QuizService.start` filters for null path, question and reference
(`QuizService.java:86`), so the guard exists — but nothing tests it.

**Input that probes it:** a completion of `{"questions":[{}]}`. Expected: a clean
`NotFoundException("Questions for this scope")`. Unverified: whether the filter NPEs first on a
question whose fields are absent rather than null.

Add a `StubAIGateway`-driven test for the empty-object case, the all-null-fields case, and
`{"questions":[]}`.

### 2. `UploadSanitizer` has no boundary test

`src/main/java/dev/mindforge/infrastructure/security/UploadSanitizer.java:104` truncates
filenames at 255 codepoints. `UploadSanitizerTest` has 7 tests but none at that boundary.

**Inputs to add:** a filename of exactly 255, of 256, one whose truncation lands mid-grapheme
(an emoji or a combining accent straddling the cut), and one composed entirely of unsafe
characters (`"@@@.md"`). The last is likely fine — it should become `"___.md"` — but it is the
allowlist's degenerate case and belongs in the test.

This is a trust boundary, so per the project's own rules it does not get simplified away.

### 3. `ReviewResult` range validation is unproven end to end

`src/main/java/dev/mindforge/domain/model/ReviewResult.java:9` rejects an out-of-range rating by
throwing from the record's compact constructor, and `ReviewRequest` carries `@Min`/`@Max`. Both
exist; nothing proves which one fires first.

**Input:** `POST` a flashcard review with `rating: 99`. Expected: **400** from bean validation.
Risk: if the DTO binds before validation runs, the record throws
`IllegalArgumentException`, which `GlobalExceptionHandler` maps to a **500**.

One API test settles it. If it is a 500, that is a bug to fix in this ticket.

### 4. Chunker boundaries

The heading-aware chunker is central to Phase 4 and to how much a document costs to ingest.
Confirm coverage for: an empty document, a document with no headings at all, a single heading
with no body, and a document larger than `chunkSizeTokens` with no heading to split on. If those
already exist, close this sub-item as verified and say so.

## Why it matters

Items 1 and 3 are user-visible 500s on input the system should reject cleanly. Item 2 is a trust
boundary. Item 4 is cost and correctness on the most-used path.

## Acceptance criteria

- [x] `QuizGenerator` malformed-output cases tested; no NPE reachable from model output.
- [x] `UploadSanitizerTest` covers the 255-codepoint boundary, a mid-grapheme cut, and the
      all-unsafe-characters case.
- [x] An API test proves an out-of-range rating returns 400, not 500 — and the code is fixed if
      it does not.
- [x] Chunker boundary coverage confirmed or added.

## Resolution

Tests only; no production bug surfaced.

1. `QuizServiceTest.shouldRefuseWhenTheModelWritesNoUsableQuestion` drives `StubAIGateway` with `{"questions":[{}]}`,
   `[null]`, `[]`, all-null fields and `{}`; each yields a clean `NotFoundException`, no NPE.
2. `UploadSanitizerTest` gained the 255/256-codepoint boundary, an emoji run (each emoji becomes one `_`, so no
   surrogate pair is split), a combining accent at the cut (NFC composes it first) and `"@@@.md"` -> `"___.md"`.
3. Already proven: `ApiFlowTest` posts `rating: 9` to the review endpoint and asserts 400 (bean validation fires
   before the record). No change needed.
4. `HeadingChunkerTest` already covered the empty document; added a lone heading with no body and a long document
   with no headings.
