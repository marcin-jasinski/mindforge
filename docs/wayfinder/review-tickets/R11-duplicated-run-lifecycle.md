---
id: R11
title: Duplicated run lifecycle and LintService's data clump
type: refactor
status: open
severity: low
assignee:
blocked_by: [R10]
---

## Problem

Three related smells around how a run is driven, all judgement calls rather than rule breaches.

### 1. Duplicated Code — the run lifecycle

`IngestPipeline.java:123-146` and `LintService.java:96-116` implement the same shape: start the
run, run the body, catch `RunFenced`, catch `RuntimeException`, mark the run failed in a
`finally`. Two copies of the fencing-and-failure contract means a fix to one can miss the other —
and this contract is exactly where correctness lives (it is what T17 and ADR 0015 specify).

### 2. Data Clumps — LintService's parameter triple

```java
LintService.review(run, failures, versions)   // :118
LintService.fail(run, reason, failures, versions)  // :195
```

`run`, `failures` and `versions` travel together through the whole class. `IngestPipeline`
already has the type that wants to exist here — its private `RunState` — holding precisely those
three. Hoist `RunState` to a shared type and both problems shrink.

### 3. Inconsistent step-version recording

`IngestPipeline` records step versions through a helper:

```java
state.version(LinkChecker.class, LinkChecker.VERSION, LinkChecker.TIER);
```

`LintService.java:141` does it inline instead:

```java
versions.put(LinkChecker.class.getSimpleName(), settings.stepVersion(...));
```

Two ways to write the same row into `step_versions`. `docs/standards/backend/ai_agents.md` makes
that row load-bearing — it is how a prompt change is attributed to a run — so it should have one
writer.

### 4. A duplicated literal

`"(nowa strona)"` appears at `IngestPipeline.java:91` and `PageWriter.java:51`. It is the
placeholder a page writer sees for a create. If one changes and the other does not, the pipeline
and the prompt disagree about what a new page looks like. Promote it to one constant — most
naturally on `PageWriter`, since it is the prompt's vocabulary.

## Why it matters

Low. Nothing here is broken today. But item 1 duplicates the fencing contract and item 3
duplicates the audit trail, and both are areas where a silent divergence would be expensive to
notice.

## Fix

1. Promote `RunState` out of `IngestPipeline` into `dev.mindforge.application` as a shared type
   (it carries `run`, `failures`, `versions` and the `version(...)` and `step(...)` helpers).
   Do this **after R10**, so it carries `List<RunFailure>` rather than the untyped map.
2. Have `LintService` use it, which removes the parameter clump and item 3 in one move.
3. Extract the lifecycle wrapper — something like
   `runs.drive(kbId, run, state -> { ... })` owning start/catch/fail — and call it from both
   `IngestPipeline` and `LintService`.
4. Move `"(nowa strona)"` to a single constant.

Keep this genuinely small. If step 3 turns into a framework, stop and do only steps 1, 2 and 4;
the duplication of a 20-line try/catch is cheaper than an abstraction nobody can follow.

## Note — `IngestPipeline` is 617 lines

Flagged as **Divergent Change**: one file carries extract, resolve, draft, link-check, two commit
blocks and supersede. It is not part of this ticket's scope and should **not** be split
speculatively — it reads as one pipeline because it *is* one pipeline, and `docs/adr/0013`
deliberately chose a typed pipeline over composable agents. Record it here so it is a conscious
choice rather than an oversight, and revisit only if a future phase adds a step.

## Acceptance criteria

- [ ] One shared `RunState`, used by both services.
- [ ] One code path writes `step_versions`.
- [ ] `LintService.review`/`fail` no longer take the three-parameter clump.
- [ ] `"(nowa strona)"` exists once.
- [ ] No new abstraction beyond the above; the diff is a net deletion.

## Resolution

<!-- filled on close -->
