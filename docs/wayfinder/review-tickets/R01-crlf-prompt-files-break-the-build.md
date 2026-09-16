---
id: R01
title: Prompt files check out as CRLF and break five tests
type: bug
status: open
severity: critical
assignee:
blocked_by: []
---

## Problem

`mvn test` fails on a fresh checkout on Windows. **5 of 263 tests fail**, all of them in
`IngestRunTest` — the flagship integration test of the Phase 6 ingest pipeline:

```
IngestRunTest.aConversationEditDeletesAndRetitlesLiveConceptsAndDropsWhatItMayNotChange:239
IngestRunTest.aDocumentIngestsIntoLinkedPagesEndToEndAndARevertRemovesThem:112
IngestRunTest.aNewVersionWhoseDraftsAreAllUnchangedCompletesWithNoRevisionsAndNoLogLine:163
IngestRunTest.aSupersessionIsKeptOnlyOnAShownSectionOfAPageOneLinkAway:205
IngestRunTest.partialSuccessLandsAndRecordsTheFailedPageAndTheLinkToIt:148
  -> expected: COMPLETED but was: FAILED   [run failed with no page task succeeded]
```

They fail deterministically, in isolation, in 0.08s — this is not flakiness and not test
pollution.

## Root cause

Fully traced. Each step was verified:

1. **There is no `.gitattributes` at the repository root.** The only `.editorconfig` in the
   repo is `frontend/.editorconfig`, which does not cover `src/main/resources/`.
2. `git config core.autocrlf` is `true` on a default Windows install. The committed blobs are
   LF (`git show HEAD:src/main/resources/prompts/pl/page_writer.pl.md | grep -c $'\r'` -> `0`),
   but **all 12 files in `src/main/resources/prompts/pl/` are checked out with CRLF**
   (`file` reports "with CRLF line terminators"; `od -c` shows `\r \n` after every line).
3. `PageWriter.write` (`src/main/java/dev/mindforge/agent/PageWriter.java:45`) renders
   `page_writer.pl.md`, whose line 7 is `- Ścieżka: {{path}}`. The rendered prompt therefore
   contains `- Ścieżka: concepts/mitoza\r\n`.
4. The test routes its canned answer with
   `Prompts.writing(path)` = `"- Ścieżka: " + path + "\n"`
   (`src/test/java/dev/mindforge/support/Prompts.java:29`). **`\n` never matches `\r\n`.**
5. `StubAIGateway.complete` finds no matching answer and falls through to its placeholder,
   `"stub response for LARGE"` (`src/test/java/dev/mindforge/support/StubAIGateway.java:38`).
6. `ModelJson.read` throws `ModelOutputException: PageWriter returned output it could not read:
   no JSON object` (`ModelJson.java:23`), which `IngestPipeline.draft` converts to `TaskFailed`
   (`IngestPipeline.java:321-323`).
7. Every page draft fails, so `drafted` is empty and `IngestPipeline.java:225` throws
   `IngestRunFailedException("no page task succeeded")`. The run ends `FAILED`.

## Why it matters

- The build is red for any contributor on a default Windows checkout, and the failure message
  (`no page task succeeded`) points at the ingest pipeline rather than at line endings, so it
  reads as a product bug.
- The same CRLF reaches production prompts. It is harmless to the model but wastes tokens on
  every single LLM call, and it means the prompt text the model sees is not byte-identical to
  the prompt text in the repository.
- It silently disarms the `StubAIGateway` routing mechanism that six integration test classes
  depend on. The fallback is a *placeholder string*, not a failure, so any future prompt-fragment
  mismatch will fail the same obscure way.

## Fix

Add `.gitattributes` at the repository root. This is the root-cause fix: it covers every prompt
file, every contributor and every platform, rather than patching the one test helper.

```gitattributes
* text=auto eol=lf
```

Then renormalize the existing checkout:

```
git add --renormalize .
git commit -m "fix: normalize line endings to LF via .gitattributes"
```

Two follow-ons in the same ticket:

1. **Add a root `.editorconfig`.** `docs/INDEX.md:69` states that UTF-8, LF line endings and
   no-trailing-whitespace are "all enforced via `.editorconfig`" — but the only `.editorconfig`
   is `frontend/.editorconfig`. Either add the root file the docs promise, or correct
   `docs/INDEX.md`. Adding it is preferred; the standard is right, it was just never applied
   outside the frontend.
2. **Make the stub fail loudly.** `StubAIGateway`'s fallback placeholder turned a routing bug
   into a confusing product-level failure. Consider making an unmatched prompt in a test that
   registered *any* answers throw instead of returning a placeholder — or at minimum make
   `Prompts.writing` tolerant of both line endings. Decide which; do not do both.

## Acceptance criteria

- [ ] `.gitattributes` exists at the repository root and the tree is renormalized.
- [ ] `file src/main/resources/prompts/pl/*.md` reports no CRLF after a fresh clone on Windows.
- [ ] `mvn test` is **263/263 green** on Windows and on Linux.
- [ ] `docs/INDEX.md:69`'s claim about `.editorconfig` is true, or corrected.
- [ ] A prompt-fragment mismatch in a test fails with a message naming the mismatch.

## Verified

With the 12 prompt files converted to LF and nothing else changed, the suite is
**263 tests, 0 failures, 0 errors, BUILD SUCCESS**, at **91.1% jacoco instruction coverage**.
The working tree was restored afterwards; this fix is *not* applied.

## Resolution

<!-- filled on close -->
