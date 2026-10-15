---
id: T19
title: How model-authored text is normalized before it becomes structural markdown
type: review
status: closed
assignee: claude
blocked_by: [T18]
---

## Question

Raised by the 2026-09-12 spec review. **Blocker** — one bad title can break a knowledge base's export for good, and
nobody can edit it.

**1. Model text goes unescaped into structural lines.** Model-authored titles and descriptions are interpolated into:
- index lines `* [title](/path.md) - description` (`docs/wayfinder/tickets/06-page-taxonomy.md:181`);
- log lines, citations and supersession notes (`docs/wayfinder/tickets/11-bundle-export.md:109`, `:111`, `:129`).

So do lesson titles, which come from PDF metadata or filenames. A `]` in a title, or a newline in a description,
produces a malformed line. That fails validator rule 3, so the export returns 500. Nobody can hand-edit a page
(ADR 0012), so the only recovery is a conversation edit. The same broken index also goes into every Extract, Write and
Query prompt.

**2. Rule 3 contradicts the renderer.** It says `log.md` "has only `## YYYY-MM-DD` date headings" (`tickets/11-…:145`),
which rejects the renderer's own `# Update Log` heading.

**3. Unspecified:**
- the index for an empty knowledge base, or a type with no pages (emit the `# ` section or not?);
- enforcement of "one sentence" for descriptions — a length cap, a single line;
- any title length limit (paths are capped at 80, titles are not);
- which column feeds `timestamp` in frontmatter;
- the export filename when the knowledge-base name slugifies to nothing (`-okf.zip`).

Decide:

- **Where text is made safe.** *Recommended:* validate at write time — `PageDraft` title and description single-line,
  control characters stripped, length caps (e.g. 200 and 300). Also escape `\`, `[` and `]` in link text at render.
- **Rule 3 wording.** E.g. `log.md` is one `# ` heading followed by `## YYYY-MM-DD` groups of `* ` lines.
- **Empty-index output.**
- **Timestamp source.** *Recommended:* `wiki_pages.updated_at`.
- **Export filename fallback.**

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option.

In short:

- Model text and lesson titles are normalised and validated at the point they enter the system.
- Renderers then escape the three characters that can break a link.
- `log.md`'s rule is re-worded to match what the renderer actually emits.

### Decisions

**1. `TextRules` (pure, domain) is the single normaliser** for every single-line string that reaches a structural line.

`singleLine(s)` does four things:

1. applies NFC;
2. turns every run of whitespace (including `\r`, `\n` and `\t`) into one space;
3. removes any remaining control and format characters (`Cc`, `Cf`);
4. trims.

How each field is handled:

| Field | Rule | Source | If out of range |
|---|---|---|---|
| **Page title** | `singleLine`, 1–200 characters | claim title candidates (T22); `Retitle` (T15) | an invalid title on a create fails that page task and is recorded in `failures`; it is never cut |
| **Description** | `singleLine`, 1–300 characters | `PageDraft` | fails the page task |
| **Lesson title** | `singleLine`, then cut to 200 characters with a trailing `…` | frontmatter, PDF metadata, filename | an empty result falls back to the filename stem, as today |
| **Body** (T21) | `\r\n` → `\n`, trailing whitespace stripped per line, exactly one final newline | `PageDraft` | — |

"One sentence" for a description is enforced as one line plus a length cap; code does not parse sentences.

A lesson title is cut rather than rejected. It is metadata, not model output, so the never-truncate rule does not apply,
and rejecting an upload because a PDF title is long helps nobody.

**2. Escaping at render.** Renderers escape `\`, `[` and `]` with a backslash wherever a title is written as link text:

- index lines;
- `log.md`;
- citations;
- supersession notes;
- the SPA's rendered index.

Descriptions after ` - ` need no escaping. They are single-line by construction, and the index rule reads everything after
the link to the end of the line.

Frontmatter stays SnakeYAML's job (T11). A title containing `*` renders as emphasis in some viewers; that is cosmetic and
accepted.

**3. Rule 3, re-worded** to match OKF §6 and §7 (amends T11 decision 5).

`index.md`:

- optional frontmatter, holding only `okf_version`;
- then `# ` section headings;
- each heading followed by zero or more `* [title](url)` lines, each optionally ending in ` - description`;
- blank lines allowed anywhere.

`log.md`:

- exactly one `# ` heading, first;
- then `## YYYY-MM-DD` date headings;
- each date heading followed by one or more `* ` lines;
- blank lines allowed anywhere.

**4. Empty output.**

- The index always emits both `# Concepts` and `# Sources`, even with no entries. The model prompt and every consumer then
  see a stable shape, and an empty section is not a violation.
- `log.md` for a knowledge base with no logged runs is the `# Update Log` heading alone.

**5. `timestamp` = `wiki_pages.updated_at`.** The commit sets it to the new revision's `created_at`. A Lint link insertion
moves it, since that is a change.

**6. Export filename.** The file is `<Identifier.slugify(kb name)>-okf.zip`, and the zip's root directory takes the same
name. T18's `slugify` never returns empty, so no fallback branch is needed.

### Feeds

- **T22** — claim titles pass through `TextRules`.
- **T29** — carries these answers into:
  - 5.5, the renderers (escaping, empty sections);
  - 6.3 and 6.5, validation;
  - 9b.1, timestamp;
  - 9b.2, rule 3;
  - 9b.3, filename;
  - the guard table ("text normalisation").
