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
