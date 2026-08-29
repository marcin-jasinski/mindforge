---
id: T02
title: What a wiki page is in the domain model
type: grilling
status: open
assignee:
blocked_by: []
---

## Question

The root decision. Today MindForge's knowledge lives in `DocumentArtifact` — one record per
document, holding `SummaryData`, `FlashcardData` and `ConceptMapData` as JSONB, fingerprinted
per step. The wiki model says knowledge lives in *pages* that many documents contribute to and
that later documents revise.

What is a page in `dev.mindforge.domain`?

Things to settle:

- **Is a page markdown-first or record-first?** Either a page is a `String` body plus parsed
  frontmatter (OKF is the native representation, and structure is a parsing concern), or a page
  is a typed domain record that *renders to* OKF markdown (structure is native and markdown is
  a projection). This choice propagates into T05, T12 and T13.
- **What is the OKF `type:` field in domain terms** — an enum, a free string, or a value object?
  OKF says consumers must tolerate unknown types.
- **What survives of the current domain records?** `DocumentArtifact`, `SummaryData`,
  `ConceptMapData`, `FlashcardData`, `StepCheckpoint`, `StepFingerprint`, `ContentBlock`,
  `BlockType`, `Hashes`. Some of these are pipeline plumbing that outlives the change; some are
  the old knowledge model and should go.
- **What is the relationship between `Document` and pages?** A document is no longer the owner
  of its outputs — it is a *source* that contributed to N pages. Is provenance tracked
  (page ← which sources), and if so, does it live in frontmatter, in a join table, or both?
  OKF's `# Citations` convention is the format-level answer; the domain-level one is separate.
- **What is a bundle?** `KnowledgeBase` is the agreed boundary — does it become a domain
  aggregate that owns pages, or stay a thin owner record with pages addressed by bundle id?
- **`index.md` and `log.md`** are reserved OKF filenames, not concepts. Are they domain objects,
  derived views, or storage artifacts?

Constraint: `dev.mindforge.domain` imports JDK only. Whatever a page is, it parses and renders
without a framework, or the parsing lives in infrastructure.

## Answer
