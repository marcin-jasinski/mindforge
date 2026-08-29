---
id: T03
title: Whether wiki revisions need human approval
type: grilling
status: open
assignee:
blocked_by: []
---

## Question

The demo gates every write: Ingest proposes, the human presses `y`, and on the local backend
you review `git diff` before committing. That gate exists because an LLM revising an existing
page can silently destroy correct knowledge.

MindForge's ingestion contract is the opposite: upload returns HTTP 202 immediately and a
background virtual thread does the work. There is no human at the keyboard.

Does a wiki revision land automatically, or does it wait for approval?

Consequences either way:

- **Automatic.** You need page history and rollback, because the only recovery from a bad
  revision is reverting it. That is a storage requirement (feeds T05) and probably a Flyway
  migration. It also means a user can lose knowledge to a bad model run without noticing.
- **Gated.** Ingestion stops being fire-and-forget. Something has to hold pending revisions,
  the SPA grows a review surface (fog: SPA surfaces), and the 202-and-forget contract in
  `architecture.md` changes shape.
- **Split.** New pages land automatically, revisions of existing pages are gated. Destructive
  edits are the risk; creation is not.

Related but distinct: the demo's **supersession** mechanism (ADR 0022) is the one Ingest write
that *is* gated even in an otherwise unguarded flow — marking a heading `[SUPERSEDED]` when a
newer source corrects an older claim. Decide whether MindForge adopts that, and whether it
follows the same rule.

Also decide: does the user ever edit a page by hand in the SPA? The map rules out *external*
editing, not in-app editing. If they can, "the LLM owns the wiki layer" from the LLM Wiki spec
stops being true and you need a merge story.

## Answer
