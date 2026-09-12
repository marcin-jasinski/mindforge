# Wiki revisions land automatically; recovery is a per-run, tip-only revert

Ingest writes land without human approval, and there is no pending or proposed state anywhere
in the system. Recovery is **revert of a whole ingest run**: restore-forward (append revisions
carrying the pre-run content; never delete history) and **tip-only** (offered for a page only
while no later run has touched it). The window closes at the next ingest touching the same page.

The LLM is the **sole author of prose**. The learner never hand-edits a page body; every change a
learner wants — correction, addition, deletion — is an ingest run sourced from a conversation
turn recorded as a `Document`. It therefore gets a run report, provenance and revert for free.

## Considered Options

- **Gate every revision**: pending revisions make the next upload ingest against a stale wiki.
  The system then either blocks ingestion on review (killing the 202 contract) or forks the wiki
  and merges. Gating also needs history anyway, for the revision a tired human clicks through.
- **Gate only revisions of existing pages**: pays the full pending-state cost to protect the one
  case history already covers.
- **In-app hand editing**: needs span-level authorship and a three-way merge the next time Ingest
  revises a touched page.
- **A fourth "Revise" operation**: duplicates run, report and revert machinery.

## Consequences

- The run report has two sections: pages written (each diffable) and claims superseded (each
  individually removable), because a false supersession is the one silent failure.
- Past the revert window, recovery is re-uploading or telling MindForge what to fix.

Decided in [T03](../wayfinder/tickets/03-human-approval-of-revisions.md).

## Amendments

2026-09-12, from the spec review:

- A revert deletes `page_sources` and `page_supersessions` **only for the pages it restores**; pages the run no longer
  tips keep its citations and supersessions ([T16](../wayfinder/tickets/16-revert-provenance.md)).
- **A REVERT run cannot be reverted.** Re-applying a reverted ingest is a retry of its document; T07's "reverting a revert
  is free" is withdrawn (T16).
- **Removing one supersession is a REVERT run** scoped to that row: it takes the lease, returns 409 while a run is active,
  and leaves a run and a log line (T16).
- A revert makes no model call, so it runs in **one transaction** under the lease (T16).
- **Conversation edits enter at Extract**, through an edit prompt returning claims, deletions and retitles, not at
  Resolve ([T15](../wayfinder/tickets/15-conversation-edit-entry.md)).
