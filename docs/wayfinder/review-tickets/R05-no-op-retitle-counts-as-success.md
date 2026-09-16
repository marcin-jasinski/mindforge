---
id: R05
title: A no-op retitle counts as a succeeded page task
type: bug
status: open
severity: high
assignee:
blocked_by: []
---

## Problem

The spec defines precisely what makes a page task succeed
(`docs/project/implementation-plan.md:741-742`):

> **A run fails when no page task succeeded** — a task succeeds when its draft passed validation
> (changed or not), or its delete or retitle **applied**.

The code counts every retitle as succeeded *before* testing whether it applies
(`src/main/java/dev/mindforge/application/service/IngestPipeline.java:214-222`):

```java
int succeeded = drafted.size() + plan.deletions().size();
for (Map.Entry<String, String> retitle : plan.retitles().entrySet()) {
    WikiPage page = livePages.get(retitle.getKey());
    succeeded++;                                   // <-- unconditional
    if (!page.title().equals(retitle.getValue())) {  // <-- the "applied" test
        writes.add(new Drafted(...));
    }
}
if (succeeded == 0) {
    throw new IngestRunFailedException(conversation ? "no applicable change" : "no page task succeeded",
        !conversation);
}
```

`succeeded++` is outside the `if`, so a retitle to the title a page already has still counts.

## Failure scenario

A conversation edit whose only instruction is a retitle to the page's current title — e.g. the
user says "rename Mitoza to Mitoza", or more realistically the model proposes a retitle that
normalises to the existing title. Then:

- `plan.retitles()` has one entry, `drafted` is empty, `plan.deletions()` is empty.
- `succeeded` becomes 1, so the guard at `:223` does not fire.
- `writes` is empty, so the run commits zero revisions and **completes**.

Expected per spec: the run **fails** with `"no applicable change"` and `retryable = false`.

## Why it matters

The comment directly above the throw states the intent:

```java
// a conversation edit that changed nothing would replay the same instruction on retry
```

That is exactly the case this bug lets through. The user is told their edit succeeded when
nothing changed, the run report shows a completed run with no revisions, and the
non-retryable signal that was designed to stop a pointless replay never fires.

Note this is distinct from the legitimate T21 case at `implementation-plan.md:742` — "a run
whose drafts were all unchanged completes with no revisions". That case has drafts that *passed
validation*; this one has no applied task at all.

## Fix

Move `succeeded++` inside the `if`:

```java
for (Map.Entry<String, String> retitle : plan.retitles().entrySet()) {
    WikiPage page = livePages.get(retitle.getKey());
    if (!page.title().equals(retitle.getValue())) {
        succeeded++;
        writes.add(new Drafted(new PageWrite(page.pageId(), page.path(), retitle.getValue(),
            page.description(), page.type(), page.markdownBody()), List.of(), false));
    }
}
```

While here, check `livePages.get(retitle.getKey())` — if a retitle names a path that is not in
`livePages`, this NPEs. Confirm Resolve guarantees it cannot, and if it does, leave it; if not,
that is a second defect to record here.

## Acceptance criteria

- [ ] `succeeded` counts a retitle only when the title actually changes.
- [ ] A new test in `IngestRunTest`: a conversation edit consisting solely of a retitle to the
      existing title ends `FAILED` with reason `"no applicable change"` and `retryable = false`.
- [ ] The existing test `aConversationEditDeletesAndRetitlesLiveConceptsAndDropsWhatItMayNotChange`
      still passes — it exercises a retitle that *does* change the title.

## Resolution

<!-- filled on close -->
