---
id: T08
title: How flashcards and quizzes are cut from the wiki
type: grilling
status: open
assignee:
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

## Answer
