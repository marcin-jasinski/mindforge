# What transfers from the demo agent to MindForge

Research notes for wayfinder ticket **T01**. Source: `D:\Dokumenty\Projekty\llm-wiki-okf-demo`
(`wikiagent/`, `docs/adr/0001`–`0027`, `README.md`, `CONTEXT.md`) read at 2026-08-29.

The demo is a **single-user Python CLI over local files** (or xWiki) driving an
OpenAI-compatible chat model. MindForge is a **multi-user Java server over PostgreSQL** with
`AIGateway`, a checkpointed pipeline and an SPA. So the question for every ADR is: does it
encode a property of *LLM-maintained wikis*, or a property of *that runtime*?

The short answer: **the model-failure ADRs transfer almost unchanged; the storage and
entry-point ADRs do not.** Everything the demo learned the expensive way is about a model that
narrates work it did not do, and that failure is runtime-independent.

## Classification of all 27 ADRs

| ADR | Subject | Verdict |
|---|---|---|
| 0001 | Wiki is an OKF-conformant bundle | **Transfers** — the map's premise |
| 0002 | Tool-calling loop, not a scripted pipeline | **Adapts** — feeds T04; see §2 |
| 0003 | One OpenAI-compatible client for both backends | **Does not apply** — `AIGateway` + `ModelTier` already is this seam, better |
| 0004 | Git stays outside the agent | **Adapts** — "never give the loop a consequential irreversible tool" survives; there is no git |
| 0005 | MCP exposes Operations, never primitives | **Adapts** — becomes "the REST API exposes Operations, never page CRUD"; see §2 |
| 0006 | Query answers are filed only when a human says so | **Transfers** — feeds T03 |
| 0007 | Lint is structural-only, additive-only | **Transfers** — feeds T10; the two refinements are the load-bearing part |
| 0008 | Single-process Router REPL + watcher, one lock | **Does not apply** — the SPA is the entry point; but the lock is a real finding, see §2 |
| 0009 | Slack out of scope | **Does not apply** — MindForge phases 18–19 own that |
| 0010 | No Obsidian integration | **Does not apply** — the SPA is the viewer |
| 0011 | Pluggable Wiki Store behind an unchanged primitive surface | **Adapts** — the *uniform surface* idea transfers to a repository port; pluggability does not (external editing is out of scope) |
| 0012 | OKF bundle to xWiki space mapping | **Does not apply** — out of scope |
| 0013 | Remote review gate = ingest approval + native history | **Adapts** — "the store's own revision history is the audit trail" is what a page-revision table buys; feeds T03/T05 |
| 0014 | Answer filing is a fixed y/n prompt, not an LLM tool | **Transfers** — see §6; one of the two most valuable ADRs for T03 |
| 0015 | Query-answer links rewritten per backend | **Adapts** — the *problem* transfers verbatim to an SPA; see §L |
| 0016 | Frontmatter fenced as a yaml block on xWiki | **Does not apply** — xWiki renderer quirk |
| 0017 | Stored links rewritten to live URLs | **Adapts** — see §L: MindForge is squarely in the "storage is not a filesystem" case |
| 0018 | Verify Operation | **Does not apply** — out of scope |
| 0019 | OKF drift schema | **Does not apply** — out of scope, except `source_docs` (see §22) |
| 0020 | .docx converts at the `read_file` seam | **Transfers** — see §20; directly constrains MindForge's parser adapters |
| 0021 | Stored link integrity, promote-only-if-target-exists, post-Operation refresh sweep | **Transfers** (mechanism adapts) — see §L |
| 0022 | Explicit supersession: `[SUPERSEDED]` tag + two-way flat pointers, gated structured tool | **Transfers** — see §22; the single most important ADR for a *compounding* wiki |
| 0023 | Verification section v2 | **Does not apply** — out of scope |
| 0024 | `write_file` assumes `wiki/`; a required tool must SUCCEED; a dead link is stored as text | **Transfers** — see §24; the most expensive lesson in the repo |
| 0025 | Domain Pack: prompts are files, tool descriptions are not | **Transfers** — see §25; feeds the map's "prompt layer" fog |
| 0026 | Evidence Tree | **Does not apply** — out of scope |
| 0027 | Ingest-requested verification | **Does not apply** — out of scope |

## The mechanics the ticket asked to record

### The six file primitives

`wikiagent/primitives.py`. One class, ~225 lines, three virtual roots (`wiki/`, `sources/`,
`evidence/`):

- `read_file(path)` — routes `wiki/` to the Wiki Store, others to local disk.
- `write_file(path, content)` — **the only write**, only ever under `wiki/`.
- `list_dir(path, recursive=False)` — `recursive` exists because a deep tree cost 41 calls
  against a 40-iteration budget; the model walked directories until it was cut off.
- `grep(pattern, root="wiki")` — regex, capped at 200 hits and **says so when truncated**;
  silent truncation would let the model reason from a partial view it believes is complete.
- `fetch_url(url)` — offered only when the source actually is a URL, because a weak model
  handed a local path with `fetch_url` on the menu tries to fetch it and burns iterations.
- `append_log(message)` — `log.md` is append-only; the model supplies only the sentence, the
  timestamp and placement are code's.

Only the first five are ADR 0011's original set; `append_log` was added to make the history file
un-clobberable.

### The inner tool loop

`agent.run_tool_loop` (`agent.py:359`), one exchange per Operation, `MAX_ITERATIONS = 60`:

1. `chat.completions.create(messages, tools)`.
2. No `tool_calls` → treat as a finish **only if** `required_tool` has succeeded *and* the
   content is non-empty; otherwise nudge (max `MAX_NUDGES = 3`) and continue.
3. Each tool call is dispatched through a **name whitelist** (`name not in TOOL_SPECS` →
   error), never `getattr` on a model-supplied string, and every exception becomes an
   `error: …` string the model reads rather than one that kills the session.
4. `required_tool_called` is set **from the dispatch result**, not from the call appearing.
5. At `MAX_ITERATIONS - WRAP_UP_MARGIN` (5) the loop injects a wrap-up: *"you have about 5 tool
   calls left… an incomplete result is worth something; being cut off is worth nothing"* —
   because a ceiling the model cannot see is a ceiling it does not ration against. Measured:
   seven of fourteen pages in one run died with findings in hand and no turn left to report them.
6. On exhaustion it returns a `"stopped: …"` **sentinel**, and callers gate on `is_answer()`
   before treating the string as content.

Per-Operation the tool menu differs and dispatch is wrapped: Query simply is not given
`write_file` (read-only **by tool omission, not by prompt**); Lint's dispatch rejects a
`write_file` that shrinks a page by more than 10%; Ingest's dispatch turns `record_supersession`
into a queue entry rather than a write.

### How `write_file` enforces source immutability

At the tool level, not by convention (`primitives.py`):

- `_split()` validates the virtual path: root must be one of three, `..` and `:` rejected.
- `READ_ONLY_ROOTS = ("sources", "evidence")` — there is no code path from a tool call to a
  write outside `wiki/`. The LLM Wiki spec's "raw sources are immutable" is therefore
  structural, not a prompt instruction the model can drift off.
- `wiki/AGENTS.md` (conventions) is read-only; `wiki/log.md` is append-only via its own tool;
  `wiki/index.md` writes go through `_check_index_keeps_links`.
- Content is normalized (`normalize_links`, `clean_frontmatter`) at this single choke point
  every wiki write passes through.

**For MindForge**: the equivalent is not a path check — it is that the ingest path holds a
read-only handle on `DocumentArtifact` and a writable one only on wiki pages. Same guarantee,
enforced by types instead of a string prefix. Feeds T04 and T12.

## The expensive ADRs, in detail

### §2 — loop, surface, concurrency (0002, 0005, 0008)

ADR 0002 chose a tool loop **because watching the LLM decide was the point of the talk** — an
explicitly demo-shaped reason — and it names the cost: "tool-calling loops are less predictable
than scripted flows, especially with weaker local models". That is the whole of T04's tension:
MindForge's checkpointed pipeline buys resumability and cost prediction, which a demo does not
need and a server does. What transfers is not the choice but the **inventory of guards a loop
needs to be safe** (§24) — if T04 picks a typed pipeline, most of those guards become
unnecessary rather than unbuilt.

ADR 0005 ("expose Operations, never primitives") transfers with its reason changed: the demo
protects *which model does the reasoning*; MindForge protects *ownership and cost* — a REST
endpoint letting a client write a page directly is the same hole as an MCP host doing the
reasoning. **The API surface is Ingest / Query / Lint / Export, never page CRUD.**

ADR 0008's single-process lock is a real finding wearing local clothes: the watcher and the REPL
share one wiki, so Operations are serialized. MindForge's equivalent is per-`KnowledgeBase`
concurrency — two uploads ingesting into one wiki at once is the same interleaved-write problem,
and it does *not* disappear because there is a database. Feeds T05/T07.

### §L — links (0015, 0017, 0021, 0024 §3)

Four ADRs, one lesson, and it is the one that survives the storage change most completely:

**A cross-link's stored form is the store's business, not the model's.** Every layer above the
store sees exactly one convention — bundle-relative `[title](/path/page.md)` — and each store
rewrites on write and reverses on read. The demo needed this because a filesystem, a browser and
xWiki resolve `/path/page.md` differently; **MindForge needs it more**, because a page in
Postgres has no path at all and the SPA renders at some `/kb/{id}/page/{slug}` route.

The failure sequence, in order:

1. 0015: a bundle-relative link inside a *rendered answer* is dead — it resolves against the
   filesystem or web root. Fixed by `Store.page_url(rel)`, at the store seam, not with an `if`
   in the agent.
2. 0017: the same links *inside stored pages* were dead too. Same seam, same fix.
3. 0021: rewriting **without checking the target exists** laundered a dead link into a confident
   live URL that 404s. A link is now promoted **only if the store holds its target** (measured:
   +32% on a write, paid gladly). And because promotion happened once at write time, a link to a
   page written *later in the same Ingest* stayed inert forever — fixed by
   `refresh_stored_links`, a **re-store sweep after every Operation** that re-runs promotion
   against the wiki as it now stands and *reports* every unresolvable target without removing it
   (OKF §5.3 allows forward-references; a phantom page is a human's call).
4. 0024 §3: "inert is honest" was false — a root-absolute `.md` href still renders as a
   clickable `<a>` that 404s. A dangling target is now stored as **visible label text** with the
   target in HTML comments, so the sentence reads normally, no `<a>` is emitted, and `read`
   reconstructs the link exactly. Reversibility is mandatory: the dangling *report* is sourced
   from `read`.

**For MindForge (T02/T05):** store links as **page identity, not text** — a `page_link` row or a
slug reference resolved at render time. That turns "promote only if the target exists" into a
join, makes `refresh_stored_links` unnecessary (a link goes live the moment its target row
appears), and makes the dangling report a query. This is the clearest case in the map where a
database is simply better than the demo's substrate — but only if the domain model refuses to
store hrefs as prose. Note also 0021 §3: an anchored link (`page.md#section`) broke a regex that
required the href to *end* in `.md`; a link model carrying `(target_page, fragment)` as fields
makes that class of bug impossible.

### §22 — supersession, the compounding-wiki ADR

The one ADR about knowledge *compounding* rather than links or loops, and the one MindForge's
destination most needs — a user uploading lecture 7 that corrects lecture 3.

- **Claim-level, not page-level**: the corrected *heading* gets a `[SUPERSEDED] ` prefix by exact
  string substitution; the original prose is untouched.
- **Two flat frontmatter scalars, one per direction**: `superseded_by:` on the old page,
  `supersedes:` on the new. Bidirectional because the question must be answerable starting from
  either page.
- **Orthogonal to `status`**, deliberately not a value of it — one field must not mean different
  things depending on which Operation wrote it last. MindForge's out-of-scope Verify removes the
  other axis, so only supersession survives: a simplification.
- **The model detects; deterministic code writes.** `record_supersession(old_page, section,
  new_page)` takes *args only, no prose*. The model never freehand-edits an already-published
  page — its tool description says exactly that, and ADR 0025 forbids a domain pack from
  retuning that sentence away.
- **It is the one Ingest write that is human-gated**, precisely because it touches a page the
  user has already read and trusted.

Also usable without Verify: **`source_docs:`** (from 0019) — a flat list of one verbatim
provenance string per source. That is MindForge's `DocumentArtifact`-to-page audit trail, and it
is a flat list of strings *because* nested YAML got mangled by the frontmatter cleaner.

**For MindForge (T02/T08):** this is a first-class domain relationship
(`supersedes`/`superseded_by` between page rows, plus a section anchor), not frontmatter — and it
directly determines what a flashcard generator must skip. A card cut from a superseded claim
teaches a student something false, so T08 cannot be answered without this.

### §24 — the three-layer failure

Read this one before designing anything in T04. A live Ingest **narrated six created pages and
created none**, three runs in a row, and every reporting layer said success:

1. **Two path vocabularies.** The prompt taught bundle-relative paths (`services/x.md`); the tool
   wanted `wiki/services/x.md`. Worse, `sources/` was simultaneously an OKF page kind and a
   read-only sandbox root, so the rejection read as *"source summaries may not be written"*.
   Fix: `write_file` **assumes `wiki/`** when the root is missing, and returns the *normalized*
   path so every success teaches the right form. The prompt was fixed too — *"the prompt is the
   belt and the default is the braces: the observed run ignored six explicit `error:` results
   across three Ingests"*.
2. **`required_tool` was satisfied by the attempt, not the result.** Fixed by setting the flag
   from the dispatch result via the uniform `error: ` prefix, and changing the nudge from "you
   did not call it" to "no call has *succeeded* — read the error and fix the arguments rather
   than repeating them".
3. **The wreckage was then published as clickable links** (see §L).

The meta-lesson is the transferable one: *"this is the layer that made the bug invisible for a
week… Both reports were correct, and both were ignored, because nothing failed."* The mechanical
`wrote:` line in `log.md` had been recording the truth the whole time.

**For MindForge:** an ingest that produced no page must **fail the pipeline step**, not log a
warning — and the record of what was written must be derived from the persistence layer, never
from the model's own report. `primitives._writes` is exactly that: a per-Operation accumulator
appended by the *successful* write and consumed by `append_log`. In MindForge that accumulator is
free — it is the set of rows the transaction inserted. Feeds T04 and T07.

### §7 — Lint's two refinements

The ADR body matters more than its title. Both refinements came from the LLM being "too radical
in practice":

- **Index membership is deterministic, not LLM-driven.** `ensure_index_entries` mechanically
  appends a catalog line for every page not already linked — never removes, never reorders — and
  runs after Ingest, after Lint, and after answer-filing. Lint no longer touches the catalog at
  all. `index.md` writes are additionally guarded: a write dropping a link to a page that
  *exists* is rejected as a clobber, while dropping a link to a page that does *not* exist is
  allowed, because it is the only repair path in the system.
- **Lint's writes are content-additive only.** A `write_file` during Lint that makes a page more
  than 10% shorter is rejected outright — *the mechanical guard that stops the "removed too much"
  failure without trusting the prompt alone.*

**For MindForge (T10):** the index is a query, not a page, so `ensure_index_entries` mostly
evaporates — but the principle behind both refinements is what to keep: **the LLM proposes
content; membership, ordering and deletion are code's.** A Lint that can only add is a Lint you
can run unattended.

### §6 — the human gate (0006, 0014)

The pair that feeds T03. 0006 made filing human-gated *and deterministic*: the write is
`wrap_frontmatter` on the approved text verbatim, **not a second LLM pass**, so the stored page is
guaranteed identical to what the human approved. 0014 then moved the *trigger* out of the LLM's
hands entirely — a fixed `y/n` prompt after every answer — and **removed** the `file_answer` tool,
because "two overlapping paths to the same action" is drift. Naming became a deterministic slug of
the question rather than an LLM-picked path.

The asymmetry is the interesting part, and a defensible default for T03: **Ingest's own writes
land unguarded; the write that edits an already-published page is gated** (§22). The line is not
"writes are risky" but "*re-writing what a human has already read* is risky".

### §20 — parsers, at the read seam

Format conversion belongs **inside `read_file`**, not in a new primitive: *"it would make format a
decision the model has to get right before it can read a file"*. Three corollaries that transfer
straight into MindForge's parser adapters:

- `grep` converts through the **same helper**, or a `.docx` raises `UnicodeDecodeError`, gets
  skipped, and the model reasons from a whole-tree scan that silently omitted the documents the
  run is about.
- **Fidelity is structural, not visual**, and *document order is load-bearing*: `python-docx`
  exposes paragraphs and tables as two separate collections, so the naive walk lifts every table
  out of the prose that qualifies it. MindForge's PDF/DOCX parsers own the same trap.
- Rejected: converting to a `.md` sidecar on ingest — "a converted copy is a second source of
  truth that drifts from the first". Bears on whether MindForge persists parsed text.

### §25 — the prompt layer

Feeds the map's "prompt layer" fog. The demo's split is sharp and worth copying wholesale:

- **Prompts are Markdown files, read at call time** (not at import), overridable per domain with
  fallback to the shipped default. A file read per Operation is free next to an LLM round trip,
  and a prompt edit lands on the next turn.
- **Tool descriptions stay in code, deliberately non-overridable**: *"each encodes a guard… a pack
  author retuning them would silently drop guards they have no way to know about."* The
  `write_file` path warning, `read_file`'s "never refuse a file based on its extension", and
  `record_supersession`'s "never edit the old page yourself" are all scar tissue. Same for the
  loop's nudge and `error:` strings, one degree stronger — their text is load-bearing for
  `required_tool` and `MAX_NUDGES`, so externalizing them makes those guards reconfigurable by
  prose, which is how they stop working.
- **The wiki's conventions doc (`AGENTS.md`) lives in the wiki, not in the app**: different owner,
  different lifetime. MindForge's analogue is a per-`KnowledgeBase` conventions page — the wiki
  stays self-describing and the SPA can show it.
- `conventions.md` is replaceable *wholesale, including its invariants*, and that is only safe
  because **every invariant it teaches is also enforced in code**: "the prompt teaches the rules
  so the model does not have to learn them by trial; the sandbox enforces them so a pack that
  drops a line gets an `error:` result rather than a corrupted bundle."

That last sentence is where the whole repo converges, and the line worth carrying into
MindForge's architecture doc: **every rule that matters is stated in the prompt and enforced in
code.**

## What does not survive, and why

- **Everything downstream of "the store might be xWiki"** (0011–0013, 0016, and 0017's mechanism):
  MindForge has one store, and external editing is out of scope.
- **Everything downstream of "there is an authoritative corpus"** (0018, 0019, 0023, 0026, 0027):
  a learning platform's uploads *are* the evidence, and Ingest already read them. Note the cost:
  MindForge loses the demo's only mechanism for detecting that a page went stale. Supersession
  (§22) is the entire replacement.
- **Entry-point ADRs** (0003, 0008, 0009, 0010): `AIGateway`, the SPA and the phase plan already
  own those decisions.
