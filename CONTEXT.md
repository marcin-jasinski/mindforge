# MindForge

An AI-powered learning platform. Uploaded documents are ingested into a per-knowledge-base wiki
that compounds with every upload; study artifacts are cut from that wiki, and the wiki is
exportable as an OKF bundle.

## Language

### The wiki

**Knowledge Base**:
A user-owned collection of documents and the one wiki built from them. Exports as exactly one OKF bundle.
_Avoid_: Bundle (except when talking about the export), workspace, library

**Bundle**:
The OKF-conformant directory of markdown files a knowledge base exports as. It exists only as an export, never as stored state.
_Avoid_: Archive, dump, backup

**Page**:
One unit of the wiki: a title, a one-sentence description, a type, and prose written by the LLM. Its title is set when the page is created and changes only when the learner retitles it.
_Avoid_: Concept document, article, artifact, note

**Page Path**:
A page's identity within its knowledge base — the bundle-relative path without `.md` (`concepts/mitoza`). Equals OKF's Concept ID; never changes after creation. Its name follows the one identifier grammar shared with lesson ids and section anchors.
_Avoid_: Slug, Concept ID, URL

**Page Type**:
The kind of a page, assigned by code: **Concept** or **Source Summary**.
_Avoid_: Category, kind, tag

**Concept**:
A page about one thing the knowledge base teaches.
_Avoid_: Topic, entity, term page

**Source Summary**:
The one page that digests a single uploaded document.
_Avoid_: Summary artifact, document page

**Section**:
A level-1 heading of a page and the prose under it, addressed by its anchor. Page links, supersessions and flashcards point at sections; deeper headings are prose inside their section.
_Avoid_: Paragraph, block, chapter

**Source**:
A document whose content contributed to a page. A conversation turn can be a source.
_Avoid_: Reference, citation (a citation is how a source is rendered)

**Index**:
The catalog of every live page — path, title and one-line description — rendered on demand. It is what the model reads to decide which pages matter.
_Avoid_: Table of contents, sitemap, search index

**Page Link**:
A link inside a page's prose to another page's path, optionally to one of its sections.
_Avoid_: Cross-reference, edge, relation

**Dangling Link**:
A page link whose target path has no live page.
_Avoid_: Broken link, dead link

**Supersession**:
A record that a section of one page has been corrected by another page. Never alters the superseded page's prose.
_Avoid_: Contradiction, override, deprecation

### Sources

**Lesson**:
The stable identity that every uploaded version of the same material shares within a knowledge base. An upload joins an existing lesson only when the learner says it is a new version.
_Avoid_: Course, chapter, topic

**Document**:
One uploaded version of a lesson, or one conversation turn, exactly as received.
_Avoid_: File, upload, source file

### History

**Ingest Run**:
One execution that changes the wiki — integrating a document or conversation turn, reverting an earlier run, or a full Lint — and the record of what it changed. Runs queue per knowledge base; at most one is active.
_Avoid_: Job, pipeline run, artifact, checkpoint

**Page Revision**:
An immutable snapshot of a page's title, description, type and prose immediately after one write.
_Avoid_: Version, history entry

**Tombstone**:
The page revision recording that a page was deleted.
_Avoid_: Soft delete, deleted flag

**Revert**:
Undoing an ingest or Lint run by writing new revisions that restore each page's pre-run state, offered only while the run is still the latest to touch the page — or removing one supersession. A revert cannot itself be reverted.
_Avoid_: Rollback, undo (except as the user's own word), rewind

### Study

**Flashcard**:
A front/back recall item cut from one section of one Concept page, scheduled by spaced repetition. Identified by its content; goes stale when its page's content changes.
_Avoid_: Card (unqualified), note, question

**Quiz Session**:
A short, server-held sequence of generated questions over a study scope, graded against reference answers that never leave the server.
_Avoid_: Test, exam, quiz (as a stored object)

**Study Scope**:
What a study session draws from: the whole knowledge base, one lesson, or one page with its linked pages.
_Avoid_: Deck, filter, topic

**Weak Page**:
A page whose last five flashcard ratings and quiz scores average below 3 on the 0–5 scale.
_Avoid_: Weak concept, knowledge gap

### Operations

**Ingest**:
The operation that integrates a source into the wiki.
_Avoid_: Processing, pipeline, import

**Conversation Edit**:
An ingest run sourced from an explicit edit in chat. It may revise, create, delete or retitle Concept pages, and it is the only way a learner changes a page.
_Avoid_: Manual edit, hand edit, revise

**Query**:
The operation that answers a question from the wiki.
_Avoid_: Chat, search, RAG

**Lint**:
The operation that health-checks the wiki: it adds missing page links and reports problems it must not fix.
_Avoid_: Validation, verify, audit

**Link Insertion**:
Lint's only kind of write — wrapping a phrase already present in a page in a link to another page.
_Avoid_: Fix, edit, rewrite

**Finding**:
A problem Lint reports but does not fix, such as a dangling link or a contradiction between pages.
_Avoid_: Issue, warning, error
