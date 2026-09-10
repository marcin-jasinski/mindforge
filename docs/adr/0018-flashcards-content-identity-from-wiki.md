# Flashcards are identified by their content and cut lazily from Concept pages

Study material is cut from the wiki, never stored in it. A flashcard's identity is
`sha256(kbId | pageId | cardType | front | back)`, and its SM-2 schedule hangs on that identity.
When a page's `revision` moves past the revision a card was cut from, its cards are regenerated,
and the generator is told to reuse still-correct cards verbatim. So:

- an **unchanged card** keeps its history;
- a card whose **answer changed** is a new card, and the old one is retired — correctly, since the
  learner memorized something the wiki now says is wrong;
- a **revert** regenerates identical content, which revives the retired cards *with their history*.

Monotonic revisions and content-derived identity compose into card-level undo nobody had to build.
Cards carry the `section_anchor` they were cut from, so cards from a superseded section are skipped
by join, and heal the moment a false supersession is removed.

Cards are generated **lazily** — at most a bounded number of new pages per study session — rather
than at ingest. Quizzes are ephemeral: one generation call per session, with reference answers
held only in the server-side session row.

## Considered Options

- **Anchor SM-2 to a page or concept**: a page rewritten underneath its cards has no defensible
  review history.
- **Flashcards and quizzes as wiki pages**: a reference answer in an exportable page body is a
  security leak, and graded items are consumed, not compounded.
- **Generate cards at ingest**: pays for every page, including the ones never studied.

## Consequences

- A pure rewording of a still-correct card resets its schedule; the verbatim-reuse instruction
  keeps that rare.
- SM-2 state lives on the card row because a knowledge base has one owner. Shared knowledge bases
  would need per-user schedules.

Decided in [T08](../wayfinder/tickets/08-study-artifacts-from-wiki.md).
