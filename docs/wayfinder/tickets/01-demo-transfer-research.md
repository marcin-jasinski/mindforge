---
id: T01
title: What transfers from the demo agent to a Java server
type: research
status: open
assignee:
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
