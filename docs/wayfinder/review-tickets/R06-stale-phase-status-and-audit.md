---
id: R06
title: Phase 3b's checkbox and the stale spec audit
type: docs
status: open
severity: low
assignee:
blocked_by: []
---

## Problem

Two documentation artifacts disagree with the repository.

### 1. Phase 3b is self-declared incomplete

`docs/project/implementation-plan.md:407` reads `## [ ] Phase 3b — Wiki Pivot Cleanup`, and its
completion checklist still carries one unchecked item (`:495`):

```
- [ ] Local databases dropped and recreated (the squash invalidates Flyway checksums).
```

But `docs/project/roadmap.md:10` lists 3b among the completed phases, and the code side is
genuinely done — verified: no reference to `DocumentArtifact`, `Agent`, `StepFingerprint`,
`GraphIndexer`, Neo4j, pgvector or `embed` survives in `src/`, and `V1__baseline.sql` is the
only baseline.

The unchecked item is a **local developer chore**, not a code deliverable. It cannot be
"completed" in the repository at all — it describes something each developer does to their own
database.

### 2. `verification/spec-audit.md` is stale and actively misleading

The file audits the **v2.0, pre-implementation** documentation set, and its findings are now
contradicted by the repository. Examples:

- `:104` — "`docs/project/deployment.md` ... does not exist". It exists; Phase 13 created it.
- `:97` — "Phase 0-8 task bodies describe Python artifacts". None remain.
- `:52` — "is false; no source exists", about a source that now exists.

At 837 lines it is the largest document under `verification/`, and a future agent reading it
would draw wrong conclusions about the current state of Phases 0-13.

## Why it matters

Low severity — neither costs a user anything. But both are traps for the next agent, which is
precisely the audience this repository's documentation is written for. An agent that trusts
`spec-audit.md` will "fix" things that are not broken.

## Fix

1. Change `implementation-plan.md:407` to `## [x] Phase 3b`, and either strike the local-database
   line or move it out of the completion checklist into a note — it is an operational step, not
   an acceptance criterion. Prefer the latter, worded as a warning to anyone with an old local
   database.
2. For `verification/spec-audit.md`, pick one and do it:
   - **delete it** (it served its purpose; git keeps it), or
   - **add a header** stating it audited the v2.0 docs on its original date, that it is
     historical, and that it must not be read as evidence about the current codebase.

   Deleting is preferred and matches `docs/standards/global/minimal-implementation.md`
   ("delete exploration artifacts").
3. If `verification/` ends up empty, remove the directory, and update `docs/INDEX.md` if it
   references either file.

## Acceptance criteria

- [ ] `implementation-plan.md` and `roadmap.md` agree on Phase 3b's status.
- [ ] No completion checklist contains an item that cannot be satisfied in the repository.
- [ ] `verification/spec-audit.md` is deleted, or clearly marked historical.
- [ ] `docs/INDEX.md` matches whatever was decided.

## Resolution

<!-- filled on close -->
