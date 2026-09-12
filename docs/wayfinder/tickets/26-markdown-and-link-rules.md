---
id: T26
title: What counts as a heading and a link in a page body
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Gap** — six components parse page bodies (the PageWriter check, `LinkParser`,
link insertion, the health checks, supersession checks and export notes), and no grammar is written down. T14 and T21
depend on it.

**1. Headings.** T06 decision 5 says "top-level `#` sections, anchored by `slugify`"
(`docs/wayfinder/tickets/06-page-taxonomy.md:159`). Study material for programming is full of fenced bash and Python
containing `# comment` lines. A naive `^# ` match treats those as headings. That creates phantom anchors, and it
misfires PageWriter's rejection of an H1 repeating the title or a `# Citations` section
(`docs/project/implementation-plan.md:650`). No Markdown library is on the classpath, and T11 adds none.

**2. Subheadings.** Whether `##` and deeper get anchors, and whether link fragments or supersessions may target them.

**3. Links.** `LinkParser` handles only the canonical `[Title](/dir/slug.md#fragment)` (`implementation-plan.md:556`).
Unspecified:
- relative `./x.md`, a missing `.md`, uppercase paths, percent-encoding;
- image links, and links inside code;
- external `https://` links.

A non-canonical internal link is neither parsed into `page_links` nor reported as dangling, so it ships as an invisible
broken link. Whether bodies may contain external URLs at all is also open (the wiki is meant to be grounded in uploads).

**4. Where link insertion may wrap text.** The eligibility rule lists existing links, code spans and headings
(`docs/wayfinder/tickets/10-lint-operation.md:96`). It omits fenced code blocks, inline HTML and autolinks.

**5. LinkChecker accepts planned targets.** It accepts targets "live or created by this run"
(`implementation-plan.md:654`). If a planned create's write failed, the check inserts a link that dangles at commit.

**6. The health query isn't SQL.** "Dangling supersession" is listed as SQL (`tickets/10-…:123`), but matching an anchor
to a heading requires parsing bodies in Java.

Decide:

- **Heading grammar.** *Recommended:* an ATX `#` at column 0, outside ``` and `~~~` fences. Decide whether levels ≥ 2 are
  anchored.
- **Link grammar.** *Recommended:* an internal link is exactly `](/(concepts|sources)/<id>.md(#<anchor>)?)`. Any other
  link to a `.md` or a relative path makes the draft fail validation — never normalised, since guessing launders dead
  links (demo ADR 0021). External links: allowed, or rejected?
- **Insertion exclusions.** Add fenced code, inline HTML and autolinks.
- **LinkChecker targets.** Live pages plus pages successfully drafted in this run.
- **One shared parser.** *Recommended:* a single pure `MarkdownStructure` utility in the domain, used by every consumer
  above.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option. Where the ticket left a choice open,
the choice and its reason are stated.

**One pure parser, `MarkdownStructure` (`dev.mindforge.domain.model`), defines what a heading, a section and a link are.
Every consumer uses it, and nobody writes a second regex.** It replaces Phase 5.1's `LinkParser`. Its consumers:

- PageWriter's draft checks and link derivation into `page_links`;
- `LinkInsertionApplier` and the health view;
- supersession checks (T14) and heading preservation (T21);
- superseded-section marking and stripping (Query, Write, study) and export notes.

### Decisions

**1. Headings and sections.**

- **Fence.** A line of up to three spaces, then three or more `` ` `` or `~`, opens a fenced block. It closes at a line
  (up to three spaces of indent) holding at least as many of the same character. An unclosed fence runs to the end of
  the body.
- **Heading.** Outside a fence, a line starting at column 0 with 1–6 `#` followed by a space. Its text is the rest of
  the line, with trailing `#`s and whitespace removed.
- **Section.** Only **level-1** headings (`# `) are sections. A section runs from its heading to the next level-1
  heading outside a fence; `##` and deeper are prose inside their section.
- **Anchor.** `slugify(stripLinks(text))`, using T18's identifier function, so it is never empty. When one body has
  duplicate anchors, the first wins (T06).

Only sections have anchors, so link fragments, supersession `section_anchor` and flashcard `section_anchor` all name a
level-1 anchor. Deeper headings are not addressable. That keeps one anchor namespace, and supersession and study already
work at section granularity (T06 decision 5, T08).

**2. Links.** Outside fences and code spans, an inline link `[text](destination)` is classified by its destination:

| Destination | Kind | Effect |
|---|---|---|
| `/concepts/<id>.md` or `/sources/<id>.md`, optionally `#<anchor>`; `<id>` and `<anchor>` match T18's grammar | internal | parsed into `page_links` (`target_path`, `fragment`) |
| `http://…`, `https://…`, and autolinks `<https://…>` | external | allowed; not stored, not checked |
| Anything else (see below) | invalid | the draft fails validation |

Invalid destinations include:

- relative links (`./x.md`, `x.md`), a missing `.md`, uppercase, percent-encoding;
- another directory, or a reserved final segment;
- `mailto:` and a bare `#fragment`.

Also invalid:

- **Image syntax** `![…](…)`. There is no asset storage, and an external image leaks a request from every reader.
- **Link reference definitions** (`[x]: …`). A reference-style internal link would be exactly the invisible broken link
  this ticket found.

Nothing is normalised, because guessing launders dead links (demo ADR 0021).

**External links are allowed.** Uploaded sources carry URLs, and rejecting them would fail drafts over legitimate
references. A hallucinated URL is the same risk as hallucinated prose, which the design already accepts. The SPA renders
external links with `rel="noopener noreferrer nofollow"`.

Ceiling: if hallucinated URLs show up, require each URL to appear verbatim in the run's source text or the existing body.
That is one containment check.

Inline HTML is not part of the grammar. The SPA renders bodies with raw HTML escaped, never executed.

**3. Insertion exclusions.** `LinkInsertionApplier` may wrap a phrase only outside:

- existing links, inline and autolinks;
- code spans and fenced blocks;
- heading lines of any level;
- tag-like `<…>` spans.

The rest of ADR 0017 is unchanged. If an insertion has a fragment, it must be an anchor of the target's body: the
in-memory draft for this run's pages, otherwise the live body.

**4. LinkChecker targets.** A target must be one of:

- a live page, other than pages this run deletes (T15);
- a page whose draft succeeded in this run.

A planned create is not enough. A create whose write failed is never a target, so the check cannot insert a link that
dangles at commit. PageWriter's linkable index still lists planned creates. A link the writer itself writes to a create
that then fails is an ordinary forward reference (T02).

**5. Dangling supersession is computed in Java.** SQL returns each live supersession with its superseded page's body and
whether the superseding page is live. `MarkdownStructure` then checks the anchor. The rest of the health view stays SQL.

### Feeds

- **T14** — "heading" in the supersession checks means a level-1 anchor as defined here.
- **T21** — heading preservation compares level-1 anchors.
- **T29** — 5.1 swaps `LinkParser` for `MarkdownStructure`; 6.5 lists the draft rules; 6.6 lists the insertion
  exclusions and targets; 7.1 adds the Java-side check; ADR 0017 gains the exclusions.
