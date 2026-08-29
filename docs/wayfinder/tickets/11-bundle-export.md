---
id: T11
title: How a bundle gets exported
type: grilling
status: open
assignee:
blocked_by: [T05, T06]
---

## Question

Export is the agreed justification for adopting OKF rather than inventing a page schema: a user
can download their knowledge base, open it in Obsidian, or hand it to another agent. If the
export is not actually conformant, OKF is doing no work.

Decide:

- **What comes out?** A zip, or an initialized git repo with history. Git is the recommended OKF
  distribution form and gives the user the version history the spec assumes — but MindForge does
  not run git today and the map rules git out as a storage *backend* (not as an export format).
- **Are raw sources included?** The LLM Wiki architecture has three layers; the wiki is layer
  two. A bundle without its sources is not reproducible, and one with them may be large and may
  re-export copyrighted uploads.
- **Link rewriting on the way out.** If pages are Postgres rows (T05), stored links must be
  converted to the bundle-relative form so they resolve in Obsidian. Demo ADRs 0015/0017/0021.
- **`index.md` and `log.md` generation** if they are synthesized rather than stored.
- **Conformance check before handing the file over.** OKF §9 is three rules; validating them on
  export is cheap and turns "OKF-conformant" from a claim into a test.
- **Does anything have to be stripped?** `reference_answer` and grounding context must never
  reach a client (`docs/standards/security/web-security.md`), and an export is a client. If T08
  put quiz state in page bodies, this is where it bites.
- **Is export a synchronous download or a background job?**

## Answer
