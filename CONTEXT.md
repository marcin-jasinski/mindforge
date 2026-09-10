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
One unit of the wiki: a title, a one-sentence description, a type, and prose written by the LLM.
_Avoid_: Concept document, article, artifact, note

**Page Path**:
A page's identity within its knowledge base — the bundle-relative path without `.md` (`concepts/mitoza`). Equals OKF's Concept ID; never changes after creation.
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
The stable identity that every uploaded version of the same material shares within a knowledge base.
_Avoid_: Course, chapter, topic

**Document**:
One uploaded version of a lesson, or one conversation turn, exactly as received.
_Avoid_: File, upload, source file

### History

**Ingest Run**:
One execution that writes pages — integrating one document, or reverting an earlier run — and the record of what it wrote. At most one is active per knowledge base.
_Avoid_: Job, pipeline run, artifact, checkpoint

**Page Revision**:
An immutable snapshot of a page's title, description, type and prose immediately after one write.
_Avoid_: Version, history entry

**Tombstone**:
The page revision recording that a page was deleted.
_Avoid_: Soft delete, deleted flag

**Revert**:
Undoing an ingest run by writing new revisions that restore each page's pre-run state. Offered only while the run is still the latest to touch the page.
_Avoid_: Rollback, undo (except as the user's own word), rewind

### Study

**Flashcard**:
A front/back recall item cut from one section of one Concept page, scheduled by spaced repetition. Identified by its content.
_Avoid_: Card (unqualified), note, question

**Quiz Session**:
A short, server-held sequence of generated questions over a study scope, graded against reference answers that never leave the server.
_Avoid_: Test, exam, quiz (as a stored object)

**Study Scope**:
What a study session draws from: the whole knowledge base, one lesson, or one page with its linked pages.
_Avoid_: Deck, filter, topic

**Weak Page**:
A page whose recent flashcard ratings and quiz scores are low.
_Avoid_: Weak concept, knowledge gap

### Operations

**Ingest**:
The operation that integrates a source into the wiki.
_Avoid_: Processing, pipeline, import

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
