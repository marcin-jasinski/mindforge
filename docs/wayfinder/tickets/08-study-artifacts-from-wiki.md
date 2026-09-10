---
id: T08
title: How flashcards and quizzes are cut from the wiki
type: grilling
status: closed
assignee: claude
blocked_by: [T02, T06]
---

## Question

Agreed: study artifacts are generated *from the wiki*, on demand, not per document. That is
strictly better than today — a quiz can span everything ingested rather than one upload. It
also means Phase 10 needs re-cutting.

Decide:

- **What is the unit of scope?** One page, a tag, a directory, a user-chosen set of pages, the
  whole bundle, or a natural-language topic the user types. Each implies a different UI and a
  different retrieval step in front of generation.
- **Generated on demand or cached?** On demand is honest — the wiki changed, so the quiz should
  — but costs an LLM call per session. Cached needs invalidation keyed to page revisions.
- **Are flashcards and quiz questions themselves wiki pages?** The LLM Wiki spec argues good
  Query answers should be filed back so exploration compounds. A generated quiz is arguably the
  same thing. If yes, they get `type:` values (T06) and appear in `index.md`; if no, they are
  ordinary database rows and the wiki stays purely explanatory.
- **What does SM-2 anchor to?** Today it would anchor to a flashcard tied to a document.
  Anchoring to a page or a concept is what makes cross-document spaced repetition work — but
  pages get rewritten, and a card whose page changed underneath it has an unclear review history.
  This is the subtlest question in the ticket.
- **Weak-concept detection.** Phase 10 targets questions at weak concepts via Graph RAG. If the
  graph is now the wiki's own cross-links (T09), rework this.
- **`QuizEvaluatorAgent` and `reference_answer`.** The security rule that reference answers
  never reach the client still binds. If a quiz page lives in the wiki and the wiki is
  exportable (T11), a reference answer in a page body is a leak. Decide where grading state lives.

**Inherited from T02–T05.** `revision` on `WikiPage` is monotonic even across revert (T03, T05), so it is a
safe cache key. Skipping superseded claims is an inner join `page_supersessions → wiki_pages`, which also
stops applying automatically when the superseding page is deleted (T05). Generation is an on-demand service,
not a pipeline step (T04).

**Inherited from T06.** Two page types: `Concept` (study material) and `Source Summary` (a digest of one
upload). There is no `Topic` type to scope by — scope candidates are a page, a link neighbourhood, the pages a
source contributed to, or the whole bundle. Page paths never change, so a path is a stable anchor. T06 ruled
that flashcards and quizzes get no page type — the question "are they wiki pages?" is answered *no* unless this
ticket finds a reason to reopen it — and that quiz state, SM-2 data and reference answers never enter
frontmatter or bodies.

## Answer

All decisions taken on the recommended option under the user's standing instruction to proceed with
recommendations.

**Flashcards are persisted database rows cut lazily from `Concept` pages, identified by their content, and
regenerated when their page's revision moves. Quiz questions are ephemeral, generated per session and held only
in server-side session state. Neither is ever a wiki page, and neither ever leaves the server in an export.**

```sql
flashcards   (knowledge_base_id FK→knowledge_bases CASCADE, card_id VARCHAR(16),
              page_id,                       -- no FK to wiki_pages, like the history tables (T05)
              section_anchor NULL, card_type, front, back,
              generated_at_revision INT, retired_at NULL,
              ease_factor, interval_days, repetitions, due_at NULL,
              PRIMARY KEY (knowledge_base_id, card_id))
study_events (knowledge_base_id FK CASCADE, page_id, card_id NULL, kind,   -- CARD | QUIZ
              score SMALLINT, occurred_at)                                  -- 0–5
quiz_sessions(session_id PK, knowledge_base_id FK CASCADE, user_id, scope JSONB,
              questions JSONB,               -- incl. reference answers + grounding; server-only
              cursor INT, expires_at)
```

```java
record Flashcard(String cardId, UUID pageId, String sectionAnchor, CardType cardType,
                 String front, String back, int generatedAtRevision) {
    static String computeCardId(UUID kbId, UUID pageId, CardType type, String front, String back) { … }
}
sealed interface StudyScope {
    record WholeKnowledgeBase() implements StudyScope {}
    record Lesson(String lessonId) implements StudyScope {}   // pages its documents contributed to
    record Page(UUID pageId) implements StudyScope {}         // the page + its 1-hop outbound links
}
```

### The seven decisions

**1. Not wiki pages.** T06 already gave them no type, and the reasons hold independently. A reference answer in an
exportable body is a security leak (`web-security.md`). SM-2 state is database state. And the LLM Wiki's "file
good answers back" argument is about *explanations* that compound, not graded study items that are consumed.
The wiki stays purely explanatory; study material is cut from it.

**2. Scope is one of three, all simple queries: whole knowledge base (default), a lesson, or a page.**

- **Whole knowledge base** — weakness-targeted (decision 5). The default study session.
- **Lesson** — every page that any `Document` of that lesson contributed to, via `page_sources`. This is the natural
  "study what I just uploaded" flow, and it survives the loss of the per-document artifact.
- **Page** — the page plus its 1-hop outbound `page_links`: "quiz me on this", from the page browser.

Rejected: tags and directories (neither exists — T06), hand-picked page sets (a UI with no demand yet), and a
natural-language topic ("quiz me on the Krebs cycle"). The last is a retrieval step in front of generation, and
Query (T09) owns retrieval; add it as a fourth `StudyScope` when Query exists to resolve it.

**3. Flashcards: generated lazily, cached by page revision, bounded like Anki's new-card limit.**

- Cards are cut **only from `Concept` pages** (T06). A Source Summary digests an upload; it is not knowledge in its
  own right, and cards from it would duplicate the Concept cards.
- **Lazy.** Nothing is generated at ingest (T04: study generation is a service, not a pipeline step). When a deck is
  opened for a scope, cards are generated for pages in scope that have none, **at most `newPagesPerSession` pages
  per session** (default 10, in `ProcessingSettings`). A card that doesn't exist cannot be due, so introducing new
  material at a bounded rate is SM-2's model anyway — and it bounds the first-session cost of a 300-page knowledge
  base to ten calls, not three hundred.
- **Stale is `page.revision > card.generated_at_revision`**, checked when a page's cards come due. Revision is
  monotonic across revert (T05), so it is a safe key.
- **Generation takes no lease** (T07) — it writes no pages. A revision that lands mid-generation is caught by the
  next staleness check.

**4. SM-2 anchors to the card; the card's identity is its content; history survives exactly as far as the content
does.** This is the subtle question, and content-derived identity answers it without a merge policy.

`card_id = sha256(kbId | pageId | cardType | front | back)[:16]` — today's `FlashcardData.computeCardId` with
`lessonId` swapped for `pageId`. On regeneration for a new revision, the generator receives the page's current
cards and **reuses them verbatim where they are still correct**. Then:

- **An unchanged card** hashes to the same id and keeps its schedule and history.
- **A card whose answer changed** is a new card, and the old one gets `retired_at`. That is the *right* outcome, not
  a loss: a learner who memorised the old answer memorised something the wiki now says is wrong, and resetting the
  schedule is the correction.
- **Revert** bumps revision (T05), regeneration reproduces the prior cards, identical content hashes to the
  retired ids, and they are un-retired *with their history*. Monotonic revisions and content-derived ids compose
  into card-level undo nobody had to build.
- **A deleted page** — cards are excluded because the due query inner-joins `wiki_pages` (T05's pattern); revert of
  the deletion brings them back.
- Cost accepted: a pure rewording of a still-correct card resets its schedule. The verbatim-reuse instruction keeps
  that rare.

**Superseded claims are skipped by join, not by prefix.** Each card carries the `section_anchor` it was cut from, and
the due query excludes cards whose `(page_id, section_anchor)` has a live `page_supersessions` row joined to a live
superseding page. A supersession does not touch the superseded page's revision (T02), so a revision key alone would
miss it — the join is what makes a false supersession silent (T03) and a removed one heal instantly. Generation
input has superseded sections stripped, so new cards are never cut from them.

SM-2 state lives on the card row. A `KnowledgeBase` has one owner, so a separate per-user schedule table would be a
join for a case that does not exist. **Ceiling:** shared knowledge bases need `(card_id, user_id)` schedules.

**5. Weakness is per page, from one event log; targeting uses `page_links`, not Graph RAG.**

Every card rating and every graded quiz answer appends a `study_events` row `(page_id, kind, score)`. A page's
weakness is the mean of its last few scores. The whole-knowledge-base session targets the weakest pages first, then
their 1-hop link neighbours — a question on *Meiosis* when *Mitoza* is weak. That is two SQL queries over tables
that already exist. `RetrievalPort.findWeakConcepts()` and Phase 10's "Graph RAG question targeting" go: the graph
Phase 10 wanted is `page_links`, and it lives in Postgres (T05). Whether Neo4j exists for anything else is T09's.

**6. Quizzes: one generation call per session, never cached.**

At session start, `QuizGenerator` (LARGE) receives the targeted pages' bodies, superseded sections stripped, and
returns a batch of questions. Each carries its `page_id`, `section_anchor`, reference answer and grounding excerpt.
The batch lives in `quiz_sessions.questions` — server-authoritative state with a TTL, exactly Phase 10.2's table —
and is discarded when the session expires.

Not cached, because a quiz is consumed once: a cached question bank is stale study material, and one call per
session is cheap next to one per question. `nextQuestion` returns the question text only.

**7. Grading stays server-side and never touches the wiki.** `QuizEvaluatorAgent` becomes a concrete `QuizEvaluator`
service (T04 deleted the `Agent` interface), SMALL tier. It grades the free-text answer against the session's
reference answer and grounding excerpt, returns score and feedback, and appends a `study_events` row. The
reference answer and grounding exist in exactly one place — the session row — and appear in no page, no export
(T11), no `log.md` and no API response. That is the whole of `web-security.md`'s rule, satisfied by where the data
lives rather than by redaction.

### Feeds

- **T09** — Phase 10 no longer needs a graph store: weak-page targeting is SQL over `study_events` and `page_links`.
  A natural-language `StudyScope` is added when Query can resolve a topic to pages.
- **T10** — Lint's "you have a page mentioning Y but nothing on Y" suggestions are study prompts, not study items;
  nothing here consumes them.
- **T11** — export contains no study data of any kind: no cards, no sessions, no events.
- **T12** — `FlashcardData` → **change** to `Flashcard` (`pageId` replaces `lessonId` in the id; add `pageId`,
  `sectionAnchor`, `generatedAtRevision`). `CardType` **keep**. New tables `flashcards`, `study_events`,
  `quiz_sessions`. `ArtifactRepository.countFlashcards` dies with its port.
- **T13** — Phase 6.5 `FlashcardGeneratorAgent` and 6.7 `QuizGeneratorAgent` leave Phase 6 and become
  `FlashcardGenerator` and `QuizGenerator` services in Phase 10, and 6.8's evaluator becomes `QuizEvaluator`. Phase
  10.4's `RetrievalPort.findWeakConcepts()` becomes weak-page SQL. 10.1 gains `StudyScope`, and 10.5 gains lazy
  generation with the new-page limit.
