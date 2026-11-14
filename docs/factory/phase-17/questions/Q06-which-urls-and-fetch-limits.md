---
id: Q06
status: decided
decision: D06
impact: medium
blocks: [W02, W03, W04]
---
## Question
Which URLs are taken from a document's blocks, and what limits bound one fetch and one document (count, size, content
type, timeouts)?

## Context
- Plan 17.2: "Extracts URLs from a document's blocks … fetches with a timeout." No numbers.
- Block text: Markdown blocks keep their source, so `[text](https://…)` and bare URLs are present; CODE blocks hold
  code samples whose URLs are rarely articles; PDF/DOCX blocks hold visible text only.
- A document can name hundreds of URLs; each becomes a run that spends model calls (Extract, Write ×N). Phase 16 set
  comparable bounds for images (`ImageExtractor`: 100 images per upload rejects the upload).
- `ai_agents.md`: "Fail loudly, never truncate … where omission is acceptable, the omission is counted."
- Content types: `text/html` is the target; `text/plain`, `application/pdf` or Markdown links could be ingested with
  existing parsers.

## Known options
- A: `http`/`https` URLs in TEXT and HEADING blocks only (not CODE); distinct after normalising (drop fragment); first
  N in document order (e.g. 10), the rest logged as skipped; `text/html` (+ `application/xhtml+xml`) only; body cap
  (e.g. 2 MB, read stops at the cap → fetch fails); connect 5 s, total 15 s per URL. Settings in `AppProperties`.
- B: As A, but also accept content types that a registered parser handles (`text/plain`, `text/markdown`,
  `application/pdf`) through `ParserRegistry`.
- C: No count cap; every URL is fetched.

## Constraints
- Off by default; bounded work per upload (cost discipline, `ai_agents.md`).
- Omissions are logged/counted, not silent (`ai_agents.md`).
- New settings documented in `docs/project/deployment.md`.
