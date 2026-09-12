# Two page types assigned by code; a page's path is its permanent identity

MindForge emits exactly two OKF `type` values — `Concept` (`concepts/<name>`) and
`Source Summary` (`sources/<lesson-id>`) — fixed by MindForge, not per knowledge base, and
**assigned by code, never chosen by the model**. A page's path equals its OKF Concept ID, is
derived once from the title at creation (with Polish-safe transliteration), and never changes:
a later title change leaves the path alone, and no rename operation exists.

The taxonomy is judged by how well Ingest's Resolve step can land a claim on an existing page.
One knowledge kind gives a claim exactly one place to land; every extra kind (`Topic`,
`Comparison`, `Entity`) is a second place, which becomes a duplicate-page generator in a wiki
that compounds. Hierarchy is expressed by links. Taking classification away from the model
also removes a misclassification failure mode and any need for a per-knowledge-base conventions
layer.

## Considered Options

- **Per-knowledge-base taxonomy** (the reference implementation's `AGENTS.md`): reintroduces a
  model classification call on every page, a conventions layer to carry it into prompts, and a
  settings surface. It also leaves study and Query unable to know what a page is.
- **Renameable slugs**: every rename rewrites every inbound body. Never renaming makes that cost
  zero.

## Consequences

- A type is added only by whatever first produces pages of it.
- Homonyms inside one knowledge base merge into one page; an exact path match is the same page.
- Frontmatter carries only OKF's recommended keys (`type`, `title`, `description`, `timestamp`).

Decided in [T06](../wayfinder/tickets/06-page-taxonomy.md).

## Amendments

2026-09-12, from the spec review:

- **Titles are fixed at creation too** ([T22](../wayfinder/tickets/22-resolve-and-title-rules.md)). The writer returns no
  title; a Concept's title changes only by an explicit `Retitle` in a conversation edit, and the path still never moves.
  "Two live pages must not share a title" is not an invariant — duplicate Concept titles are a health finding.
- **One identifier grammar** ([T18](../wayfinder/tickets/18-lesson-and-path-identity.md)) — `[a-z0-9]+(-[a-z0-9]+)*`,
  at most 80 characters — for page names, lesson ids and heading anchors, shared with the export validator. `slugify`
  maps Greek letters and appends a hash when it drops a letter, so it never returns empty and never merges titles by
  loss. A derived name on a reserved word gains a suffix. An upload whose lesson id exists is a new version only when the
  user says so; otherwise it is rejected with 409.
- **Only level-1 headings are sections** and carry anchors
  ([T26](../wayfinder/tickets/26-markdown-and-link-rules.md)).
