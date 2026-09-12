---
id: T18
title: Lesson id collisions and one identifier grammar for paths
type: review
status: closed
assignee: claude
blocked_by: []
---

## Question

Raised by the 2026-09-12 spec review. **Blocker** — unrelated uploads overwrite each other, and some valid lesson ids
make export fail forever.

**1. Unrelated documents with the same lesson id become "versions" of one lesson.**
- T06 justified `sources/<lesson-id>` by `idx_documents_kb_lesson` being unique
  (`docs/wayfinder/tickets/06-page-taxonomy.md:127`).
- T07 then dropped that constraint (`docs/wayfinder/tickets/07-idempotency-and-failure.md:111`).
- The lesson id falls back to the filename stem (`src/main/java/dev/mindforge/domain/model/LessonIdentity.java:61`).

So two unrelated `notatki.pdf` uploads share a lesson. The second silently rewrites the first's Source Summary, and
every citation to the first document lands on the second's digest. Before the re-cut, the constraint rejected this.

**2. The validator rejects lesson ids that identity accepts.** The export validator requires
`(concepts|sources)/[a-z0-9]+(-[a-z0-9]+)*.md` (`docs/wayfinder/tickets/11-bundle-export.md:146`). `LessonIdentity`
accepts `[a-z0-9\-_]+` (`LessonIdentity.java:29`), and an explicit frontmatter `lesson_id` is taken as written
(`:47`). Phase 3b.2 (`docs/project/implementation-plan.md:423`) changes only `slugify`. So `bio_3`, `a--b` or `-x` pass
identity and fail export, and because paths never change, that knowledge base's export returns 500 forever.

**3. `slugify` can return nothing, or merge different concepts.** NFD plus stripping marks
(`tickets/06-…:129`) does not transliterate Greek, Cyrillic or CJK:
- "π" becomes an empty string;
- "α-helisa" and "β-helisa" both become `helisa`.

The exact-path rule (`tickets/06-…:138`) then treats them as one page, and Resolve merges their claims. Greek letters are
routine in biology, chemistry and maths.

Decide:

- **Version vs. collision.** *Recommended:* an upload whose lesson id already exists is a version only through an
  explicit "new version of lesson X" action; otherwise it is rejected with 409, asking for a distinct lesson id.
  *Alternative:* auto-suffix the id (`notatki-2`) and make versioning always explicit.
- **One identifier grammar.** *Recommended:* `[a-z0-9]+(-[a-z0-9]+)*`, at most 80 characters, not `index`, `log` or
  `default`. Enforce it in `LessonIdentity` and `PagePath`, and use it in the validator. An explicit `lesson_id` must
  match it — no silent rewrite.
- **Empty or lossy slugs.** *Recommended:* a small Greek-letter map (`α→alfa`, `β→beta`, …) plus a hash fallback
  (`c-<8 hex>`) when the result is empty. Decide whether any remaining lossy clash is accepted as the same page.

## Answer

Decided 2026-09-12 under the standing instruction to take the recommended option.

**Summary:**
- An upload never becomes a version of an existing lesson by accident. That takes an explicit flag; otherwise the upload
  gets a 409.
- Lesson ids, page names and heading anchors share one identifier grammar and one slug function.
- The slug function transliterates what it can, and appends a hash wherever it had to drop a letter.

### Decisions

**1. Version or collision.** The upload takes two optional fields:
- `lessonId` — an override, validated against the grammar;
- `newVersion` — default `false`.

The resolved lesson id is the override if given, else `LessonIdentity.resolve`:

| Resolved lesson id | `newVersion=false` | `newVersion=true` |
|---|---|---|
| no document of that lesson in the knowledge base | new lesson | 422 — nothing to version |
| a document of that lesson exists | **409 `LESSON_EXISTS`**; the body carries the lesson's id and title | new version of that lesson |

The SPA answers a 409 with a dialog offering two choices: *new version of "Biologia — lekcja 3"*, or *a new lesson* with
an editable id.

The check and the insert both run inside the upload transaction, after
`SELECT … FROM knowledge_bases WHERE kb_id = :kb FOR UPDATE`, so two concurrent uploads cannot both create the same
lesson. An identical upload still returns the existing id first (T07 decision 1). The lesson check applies only when a
row would be inserted.

*Why not auto-suffix:* `notatki-2` makes the collision silent in the other direction — a real second version becomes a
separate lesson unless the user notices. Asking once costs one click and gets both cases right.

Exceptions:
- **Conversation turns** use the reserved `conversation` lesson and are never checked (T15).
- **Fetched articles** (Phase 17) have nobody to ask, so an article whose lesson id already exists is skipped and logged.

**2. One identifier grammar**, in a pure domain value `Identifier`:
- pattern `[a-z0-9]+(-[a-z0-9]+)*`, 1–80 characters;
- reserved at every use: `index`, `log` (OKF §3.1);
- reserved for lesson ids only: `default`, `conversation`.

The grammar is enforced in four places:
- `LessonIdentity` — replacing `[a-z0-9\-_]+`, so `_` is no longer legal;
- `PagePath` — the name after `concepts/` or `sources/`;
- heading anchors, which T26 builds with the same function;
- `BundleConformanceValidator` rule 4, which uses `Identifier.PATTERN` rather than keeping its own copy.

Explicit and derived ids are treated differently:
- An **explicit** id — frontmatter `lesson_id` or the `lessonId` field — must already match the grammar, or the upload
  fails with 422. It is never rewritten.
- A **derived** id (from a title or filename) that lands on a reserved word gets a suffix: `-lesson` for lesson ids and
  `-concept` for page names. So an `index.md` upload works, and so does a Concept titled *Index*.

Every path is built from an identifier that has already passed the validator's pattern, so no path can fail export.

**3. `Identifier.slugify(text)` never returns empty and never merges titles by dropping letters.** It runs these steps:

1. Apply NFC and trim.
2. Process each code point:
   - letters and digits that NFD-decompose to ASCII keep their base letter (`ó→o`);
   - `ł→l`, `ø→o`, `ß→ss`, `đ→d`, `æ→ae`, `œ→oe`, `þ→th`;
   - Greek letters map to their Polish names, upper case alike: `α→alfa`, `β→beta`, `γ→gamma`, `δ→delta`, `ε→epsilon`,
     `ζ→dzeta`, `η→eta`, `θ→theta`, `ι→jota`, `κ→kappa`, `λ→lambda`, `μ→mi`, `ν→ni`, `ξ→ksi`, `ο→omikron`, `π→pi`,
     `ρ→ro`, `σ/ς→sigma`, `τ→tau`, `υ→ypsilon`, `φ→fi`, `χ→chi`, `ψ→psi`, `ω→omega`;
   - every other letter or digit is **dropped, and the drop is remembered**;
   - any other character is a separator.
3. Lowercase with `Locale.ROOT`, collapse separators to `-`, and trim hyphens.
4. **If anything was dropped**, append `-` plus the first 8 hex characters of SHA-256 over the NFC text. If nothing is
   left, the result is `x-<8 hex>` on its own.
5. Cap at 80 characters, cutting the base first so a hash suffix always survives.

Examples:
- `α-helisa` → `alfa-helisa`, and `β-helisa` → `beta-helisa`;
- `π` → `pi`;
- `細胞` → `x-3f2a91c0`;
- `細胞 A` and `細胞 B` stay distinct.

Polish never drops a letter, so Polish paths never carry a hash.

**Remaining clashes are accepted as the same page.** `Mitoza`, `mitoza` and `Mitoza!` share a path, and T06's homonym
rule already says an exact path is the same page.

Ceiling: a knowledge base in a non-Latin script gets hash-named paths. Add a full transliterator (ICU4J `Any-Latin`)
when such a knowledge base appears.

### Feeds

- **T15** — `conversation` reserved.
- **T19** — the export filename can no longer be empty.
- **T26** — anchors use `slugify`.
- **T29**:
  - 3b.2 `LessonIdentity` (grammar, reserved words, explicit ids not rewritten, suffixes) and 3b.8 tests;
  - 5.1 `Identifier` / `PagePath`;
  - 4.5 the collision rule, 9.7 the upload fields, 9.6 `LessonAlreadyExistsException` → 409;
  - 9b.2 the shared pattern;
  - ADR 0011 and the lesson identity section of `ai_agents.md`.
