# A wiki page is a typed record with an opaque prose body

Knowledge lives in wiki pages that many documents contribute to, not in a per-document
`DocumentArtifact`. A page's identity, links, provenance and supersession are typed fields and
rows; the prose the LLM writes is one `String`. The seam is drawn where the LLM's authorship ends.

The two halves invert deliberately. **Links are truth in the body** because the model writes
them mid-sentence: they are stored in one canonical bundle-relative form, parsed into
`page_links` on write, and resolved by join at read time. Nothing is resolved at write time, so
a forward reference goes live the moment its target appears and the dangling-link report is a
query. **Metadata is truth in rows** because code assigns it: type, path, sources and supersession
are never asserted by the model, and are projected into frontmatter and `# Citations` only on
export. `index.md` and `log.md` are projections, never stored.

Supersession is a relation (`page_supersessions`) and never mutates the superseded page's prose.
That turns the riskiest write in the system into a row insert.

## Considered Options

- **Markdown-first** (a body plus parsed frontmatter as the model): every cross-link becomes a
  regex problem forever, and the model's copy of metadata drifts from the truth. The reference
  implementation spent four ADRs on this.
- **Fully record-first** (sections and paragraphs modelled): parses the model's prose into
  structure and back on every write, for no consumer.

## Consequences

- `DocumentArtifact`, `SummaryData` and `ConceptMapData` are deleted. A summary is a
  `Source Summary` page; a concept map is `page_links`.
- `KnowledgeBase` stays a thin owner record. Every `WikiStore` method takes `kbId` first.

Decided in [T02](../wayfinder/tickets/02-wiki-page-domain-model.md).
