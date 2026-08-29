---
id: T06
title: The page taxonomy and bundle layout
type: grilling
status: open
assignee:
blocked_by: [T02]
---

## Question

OKF requires a non-empty `type:` and deliberately refuses to define a taxonomy — that is the
producer's job. The demo pushes it into a per-wiki conventions doc (`AGENTS.md`) that the wiki
owner writes, precisely so it is *not* baked into the agent.

MindForge is a product, not an agent you point at your own repo. Decide:

- **What `type:` values does MindForge emit?** For a learning domain the candidates are things
  like `Concept`, `Source Summary`, `Topic`, `Comparison`, `Question`. Fixed set or open?
- **What directories does a bundle have?** OKF's structure is domain-independent; something has
  to choose. `concepts/`, `sources/`, `topics/`?
- **Is the taxonomy fixed by MindForge or per-`KnowledgeBase`?** A user learning organic
  chemistry and a user learning Spanish grammar want different page kinds. But a fixed taxonomy
  is what lets the quiz generator (T08) and Query (T09) know what they are looking at.
  If per-KnowledgeBase, this graduates the prompt-layer fog: something must carry the
  taxonomy into the Ingest prompt.
- **Title and slug rules**, and therefore Concept IDs — OKF's Concept ID is the file path minus
  `.md`, so slug policy is an identity decision, not cosmetics.
- **What goes in `index.md`** and whether it is generated or maintained. It is MindForge's
  retrieval index if T09 goes that way, which makes its shape load-bearing.
- **What goes in `log.md`** and whether users ever see it.
- **Frontmatter beyond the OKF minimum.** OKF lets producers add keys; consumers must preserve
  unknowns. Provenance, quiz-generation state and SM-2 anchors are candidates — but frontmatter
  a *foreign* consumer cannot act on is arguably database state that leaked into the format.

## Answer
