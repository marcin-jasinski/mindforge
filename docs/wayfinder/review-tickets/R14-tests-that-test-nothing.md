---
id: R14
title: Four tests assert nothing about production code
type: test
status: open
severity: medium
assignee:
blocked_by: []
---

## Problem

The suite is genuinely strong — 263 tests, 91.1% instruction coverage, and a deliberate hunt for
vacuous tests turned up only these four. Each passes for a reason unrelated to the behaviour its
name claims.

### 1. `unit/domain/StubAIGatewayTest.java` — tests the test double

```java
// :23
assertThat(result.content()).isEqualTo("canned summary");
```

after the test itself called `.willReturn(ModelTier.LARGE, "canned summary")`. The whole file —
49 lines, 3 tests — exercises `StubAIGateway`, which is test infrastructure under
`src/test/java/dev/mindforge/support/`. Zero production coverage.

**Fix:** delete it. If the stub's routing is considered worth pinning — and after R01 there is an
argument that it is, since its silent fallback caused a day of confusion — then keep exactly one
test that pins the *fallback* behaviour, and move the file under `support/`. Do not keep the
echo assertions.

### 2. `unit/application/KnowledgeBaseServiceTest.java:53` — never arranges its scenario

`shouldRefuseToDeleteAKnowledgeBaseWhileARunIsActive` **never sets up an active run**. It passes
because `mock(KnowledgeBaseRepository.class)` returns Mockito's default `false` from
`deleteIfIdle`. The test would pass identically against a repository that always refuses, always
fails, or does nothing.

**Fix:** stub `deleteIfIdle` to return `false` explicitly *and* add the paired test where it
returns `true` and deletion succeeds. The second test is the one that would actually catch an
inverted boolean.

### 3. `unit/agent/ModelServiceVersionTest.java:31` — asserts a constant exists

```java
assertThat(Modifier.isStatic(field.getModifiers()) && Modifier.isFinal(field.getModifiers())).isTrue();
// ... and isNotBlank()
```

This proves `VERSION` is `static final` and non-empty. The rule it is guarding —
`docs/standards/backend/ai_agents.md`: *`VERSION` is bumped on logic or prompt change and
recorded on every run in `step_versions`* — is untouched. A `VERSION = "1"` left stale across a
complete prompt rewrite passes.

**Fix:** the reflective check is cheap, keep it. But add the test that matters: that a run
records a `step_versions` entry for **every** model service it invoked, with the value the
service declares. That is enforceable and it is what the standard actually asks for. Note this
overlaps R11 item 3 — there are currently two code paths writing those rows, and this test would
pin both.

### 4. `unit/application/RunWorkerTest.java:100` — stops asserting one step early

`shouldRequeueAnInterruptedIngestAsItsNextAttemptAtMostTwice` verifies `enqueue` happened once,
but **never asserts what happens to the third attempt**. If the worker silently drops a run at
attempt 3 instead of failing it, the test is green.

**Fix:** assert the terminal state of attempt 3 — that the run is marked `FAILED` with a
non-retryable reason, and that no further `enqueue` occurs. The "at most twice" in the test name
is the untested half.

## Why it matters

Medium. None of these hides a known bug today. But each is a test that will stay green through
the exact regression it appears to guard, which is worse than no test — it buys false confidence
in `KnowledgeBaseService`'s delete guard, the `step_versions` audit trail, and the run retry cap.

## Acceptance criteria

- [ ] `StubAIGatewayTest` is deleted or reduced to a single fallback-behaviour test under `support/`.
- [ ] The delete-guard test arranges its scenario and has a passing-case sibling.
- [ ] A test asserts `step_versions` content after a run, not just that constants exist.
- [ ] The retry test asserts attempt 3's terminal state.
- [ ] Coverage does not drop below the 70% gate (it is 91.1% today, so there is room).

## Resolution

<!-- filled on close -->
