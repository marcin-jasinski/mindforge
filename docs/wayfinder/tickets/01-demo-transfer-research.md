---
id: T01
title: What transfers from the demo agent to a Java server
type: research
status: closed
assignee: claude
blocked_by: []
---

## Question

`D:\Dokumenty\Projekty\llm-wiki-okf-demo` is a working LLM-Wiki-over-OKF agent with 27 ADRs.
Each ADR encodes a decision made after hitting something real. Most were made for a
**single-user Python CLI over local files**; MindForge is a **multi-user Java server over a
database**. Which of those decisions are universal properties of LLM-maintained wikis, and
which are artifacts of the demo's runtime?

Read `wikiagent/` and `docs/adr/` and produce a markdown summary at
`docs/wiki/demo-transfer-notes.md` classifying every ADR as **transfers**, **adapts** (and how),
or **does not apply** (and why).

Pay particular attention to the ones that look like they encode a model failure rather than a
preference — those are the expensive ones to rediscover:

- 0021 stored link integrity / dangling links, 0015 backend-aware link rendering,
  0017 stored link rewriting — how a cross-link survives a storage layer that isn't a filesystem.
- 0024 "write path assumes wiki and dead links are not links".
- 0022 explicit supersession — how a newer source corrects an older page's specific claim.
- 0006 human-gated ingest, 0014 automatic answer filing, 0013 remote review gate — feeds T03.
- 0002 tool-calling loop, 0008 single-process router, 0005 MCP high-level only — feeds T04.
- 0007 structural-only lint — feeds T10.
- 0025 domain pack, and the deliberate rule that tool descriptions are *not* overridable
  because each encodes a guard.

Also record: what the six file primitives are, what the inner tool loop looks like, and how
`write_file` sandboxing enforces source immutability at the tool level rather than by convention.

Skip anything under the map's **Out of scope** section — Verify, Evidence Tree, xWiki, import.

## Answer

Full notes: [`docs/wiki/demo-transfer-notes.md`](../../wiki/demo-transfer-notes.md) — all 27
ADRs classified, plus the six primitives, the inner tool loop and the write sandbox.

**The split runs along one line: the ADRs that encode a *model* failure transfer; the ADRs that
encode a *runtime* choice do not.** Nine transfer (0001, 0006, 0007, 0014, 0020, 0021, 0022,
0024, 0025), six adapt (0002, 0004, 0005, 0011, 0013, 0015, 0017), twelve do not apply — five of
those because the map already ruled Verify and its schema out, four because they are entry-point
choices `AIGateway` and the SPA already own, three because they are xWiki mechanics.

The four findings that change MindForge's design:

1. **Links must be stored as page identity, not text** (0015/0017/0021/0024 §3, four ADRs and one
   lesson). The demo needed a rewrite-on-write/reverse-on-read seam per store, a
   promote-only-if-the-target-exists check, a post-Operation re-store sweep, and finally an
   HTML-comment de-linking hack — all because a cross-link is prose in a file. A `page_link` row
   makes "does the target exist" a join, makes the sweep unnecessary, and makes the dangling
   report a query. This is the strongest argument in the map for the database substrate, and it
   only pays off if T02/T05 refuse to store hrefs in page bodies.

2. **Supersession is the whole of the compounding story** (0022), and doubly so here because the
   map cut Verify: it is now MindForge's *only* mechanism for a newer upload correcting an older
   page. Claim-level (`[SUPERSEDED] ` on the exact heading), two flat pointers one per direction,
   orthogonal to any status field, detected by the model but written by deterministic code, and
   the single write the demo gates on a human — because it edits a page the user already read.
   T08 cannot be answered without it: a flashcard cut from a superseded claim teaches something
   false.

3. **A run that wrote nothing must fail** (0024). The demo's worst bug — three Ingests narrating
   thirteen pages and writing none — survived a week because `required_tool` was satisfied by the
   *attempt* rather than the *result*, and because the honest reports (`wrote:` in `log.md`, the
   dangling-link note) were correct and ignored, since nothing failed. The transferable rule: the
   record of what was written is derived from the persistence layer, never from the model's
   narration. In MindForge that record is free — it is the rows the transaction inserted.

4. **The LLM proposes content; membership, ordering and deletion are code's** (0007, 0006/0014,
   0025). Index entries appended mechanically, Lint writes rejected if they shrink a page, answer
   filing a fixed y/n rather than a tool the model may call, and tool descriptions deliberately
   not overridable because each one is scar tissue from an observed failure. The general form,
   worth carrying into the architecture doc: **every rule that matters is stated in the prompt and
   enforced in code.**

For T04 specifically: ADR 0002 chose the tool loop for a demo-shaped reason ("watching the LLM
decide is the point of the talk") and names its cost. What transfers is not the choice but the
inventory of guards a loop needs to be safe — required-tool-must-succeed, the nudge cap, the
iteration wrap-up, the dispatch whitelist, the shrink guard, per-Operation tool menus. If T04
picks a typed pipeline, most of that inventory becomes unnecessary rather than unbuilt; that is a
real point in the pipeline's favour and it should be weighed as one.
