---
id: T15
title: How a conversation edit finds its pages, and how anything gets deleted
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Blocker** — T03 routes every user edit, deletion included, through a
conversation turn, and the pipeline has no way to carry one out.

**1. Entering at Resolve cannot locate targets.** T04 sends a turn past Extract straight to Resolve, "because the
instruction *is* the claim set" (`docs/wayfinder/tickets/04-ingest-execution-model.md:205`,
`docs/project/implementation-plan.md:618`, `:936`). Resolve is code, and T09 removed lexical retrieval. "Fix the page
about cell division" has to become `concepts/podzial-komorki` somewhere, and reading the index to do that is Extract's
job.

**2. No step produces a deletion.** Phase 6 builds write tasks only (6.4, 6.5). `WikiStore.deletePage`
(`implementation-plan.md:568`) has no caller except revert, and 11.3 says only that deletion goes "through the same
channel".

**3. A chat turn's `Document` fields are unspecified.** `documents` requires `lesson_id`, `lesson_title`,
`content_hash`, `source_filename` and `mime_type` (`src/main/resources/db/migration/V3__create_documents.sql`). The
lesson id matters beyond the constraint: the Lesson study scope groups pages by it (T08 decision 2).

**4. Unstated behaviour:**
- whether a turn passes RelevanceGuard;
- what a turn that resolves to no page does (zero writes, so a FAILED run?), and what the chat then says;
- whether a conversation may delete a Source Summary.

Decide:

- **The entry point.** *Recommended:* through Extract with an edit prompt (instruction + index → claims with target
  paths), skipping RelevanceGuard and the Source Summary task. Resolve is unchanged.
- **Deletion.** *Recommended:* the claim set carries a typed delete item, say `Delete(path)`. Resolve verifies it names
  a live Concept, and the commit tombstones the page, so membership stays in code (T01 §7).
- **Field values for a turn.**
  - *Option:* one reserved lesson id `conversation` for every turn, excluded from the Lesson scope picker.
  - *Option:* one lesson id per turn, e.g. `conversation-<yyyymmdd-hhmmss>`.

  Also fix `content_hash` (e.g. SHA-256 of the UTF-8 text), `mime_type = text/plain`, and `source_filename`.
- **Unresolvable or empty edits.** Their run outcome and the user-facing message.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. Where the ticket offered options
without a recommendation, the choice is argued.

**A conversation edit enters at Extract through its own prompt and returns typed items: claims, deletions and retitles.
Resolve verifies them exactly as it verifies a document's claims.** The edit runs as the same `INGEST` run with
`upload_source = CONVERSATION` (T07 decision 5), minus the RelevanceGuard and the Source Summary task.

```java
sealed interface EditItem permits Claim, Delete, Retitle {}
record Delete(String path) implements EditItem {}
record Retitle(String path, String title) implements EditItem {}
// Claim as T22 defines it

ExtractResult  ClaimExtractor.extract(chunk, renderedIndex, plannedPages)          // documents: List<Claim> + chunkDigest
List<EditItem> ClaimExtractor.extractEdit(instruction, quotedAnswer, renderedIndex) // conversation turns
```

### Decisions

**1. Entry point: Extract, not Resolve.** T04's shortcut assumed the instruction already names pages. In practice it
names topics. Turning "fix the page about cell division" into `concepts/podzial-komorki` means reading the index, which
has been Extract's job since T09.

`ClaimExtractor` gains `extractEdit`, with its own prompt, `claim_extractor_edit.pl.md`. One `VERSION` covers both
prompts. The document method's result type cannot hold a `Delete`, so an uploaded file cannot delete a page — by type.

A turn skips two steps:

- **RelevanceGuard.** It filters junk uploads, and a turn is the owner's own instruction about their own wiki.
- **The Source Summary task.** A turn is not a source of knowledge (T06).

Supersede still runs. A user's correction to one page can contradict sections of other pages, which is exactly the
cross-page case Supersede exists for (T04 decision 4).

**2. Deletion is a typed item that Resolve verifies.**

- `Delete(path)` is accepted only if `path` names a live `Concept`.
- A deletion of a Source Summary (code-owned, one per document) or of a missing path is dropped and recorded in
  `failures`.
- The commit tombstones the page (T05).
- If one edit both deletes and writes a path, both items are dropped as contradictory and recorded.
- For the rest of the run, a deleted page is excluded from the linkable index and from LinkChecker's targets (T26).

Membership stays code's (T01 §7): the model proposes, Resolve decides.

**3. Retitle.** `Retitle(path, title)` changes a live Concept's title and nothing else. It is the only way a title
changes after creation (T22), and the path never moves (ADR 0011). If the edit has no claim for that page, it writes a
revision with the body and description unchanged, without calling the writer.

**4. Field values for a turn.**

| Column | Value |
|---|---|
| `lesson_id` | `conversation` — one reserved id for every turn (T18 reserves it for lessons) |
| `lesson_title` | `Conversation` |
| `content_hash` | SHA-256 of the UTF-8 bytes of `original_content` (`ContentHash.compute`); not unique, because the index is partial (T07) |
| `mime_type` | `text/plain` |
| `source_filename` | `conversation` |
| `original_content` | the instruction; for "save that", the instruction followed by the quoted answer |
| `content_blocks` | one `TEXT` block holding `original_content` |
| `upload_source` | `CONVERSATION` |

*One reserved id, not one per turn.*

- A per-turn id would put hundreds of one-document "lessons" in the Lesson scope picker, and each would need excluding.
  A single reserved id is excluded once.
- Its pages stay reachable through the whole-knowledge-base and page scopes.
- Citations render each turn as a dated label (T11), so provenance stays per turn.

**5. Empty or unresolvable edits.** An edit is an ordinary run, so the ordinary rule decides: a run fails when no page
task succeeded (T21).

| Outcome | Run | What chat says |
|---|---|---|
| Extract returns no items | `FAILED`, `failure_reason = "edit named no page"`, `retryable = false` | *"I couldn't tell which page to change — name the page or rephrase."* |
| Every item dropped | `FAILED`, reason `"no applicable change"`, dropped items in `failures` | the run report inline, saying why each item was dropped |
| Some items applied | `COMPLETED` | the report lists both applied and dropped items |

A `Claim` whose target is not a live Concept becomes a create from its title (T22), exactly as for a document. The inline
report shows it as "created".

### Feeds

- **T18** — reserve `conversation` among lesson ids.
- **T22** — `Claim` carries a title; titles change only through `Retitle`.
- **T29** — the Phase 6 contract and 6.3/6.4 carry the entry point; 11.3 re-cut; guard list in `ai_agents.md`;
  `CONTEXT.md` gains **Conversation Edit**.
