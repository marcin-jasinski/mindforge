---
id: D06
question: Q06
status: decided
method: unanimous
class: local
votes: { pragmatist: A, architect: A, skeptic: A }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-17/ballots/Q06.r1.tally.md]
adr:
---
## Decision
Use A. Take `http`/`https` URLs from TEXT and HEADING blocks only (never CODE, IMAGE, AUDIO, VIDEO). Normalise each
URL by dropping the fragment, keep distinct URLs in document order, and fetch the first N (default 10). Log the count
of URLs over the cap with a fixed message. Accept only `text/html` and `application/xhtml+xml`; any other or missing
Content-Type fails that URL. Cap the body at 2 MB: the read stops at the cap and the fetch fails, it never truncates.
Use a 5 s connect timeout and a 15 s total timeout per URL; the total covers all redirect hops (D04: max 5 hops).
Put the four limits in `AppProperties` and document them in `docs/project/deployment.md`.

## Why
- Each URL becomes a run with Extract and Write calls; a count cap is the only bound on cost per upload (Q06 constraints).
- `ai_agents.md:109`: omission is acceptable here and is counted; an over-size body fails, never truncates.
- The Phase 16 image rule rejects the upload (`ImageExtractor.java:116-118`), but D01 fetches after commit, so
  over-cap URLs are skipped and counted, not rejected.
- HTML only keeps the untrusted surface to the jsoup converter (D05); B would route remote bytes to PDF/DOCX parsers
  and couple the fetcher to the registry-derived upload allowlist (D05, `IngestionConfig.java:38-41`).
- Unanimous, three ballots at confidence 0.8-0.85.

## Rejected
- B: widens the parser attack surface on untrusted remote bytes; no requirement asks for it; additive later.
- C: no count cap gives unbounded model spend per upload (verified hard objection).

## Dissent
None on the choice. The skeptic asked for a per-document wall-clock deadline (60 s). Not adopted: the fetch runs on
a virtual thread after commit (D01), so 10 x 15 s sequential holds no request or lease. Risk to watch: if the cap is
raised a lot, add that deadline then.

## Implementation notes
- `AppProperties`: nested JavaBean class (follow the existing getter/setter style, not a record), e.g.
  `app.articles.max-per-document=10`, `max-body-bytes=2097152`, `connect-timeout=5s`, `request-timeout=15s`.
  Validate positive values at startup. Document all four in `docs/project/deployment.md`.
- URL extractor (pure code, application layer or a JDK-only helper): scan `ContentBlock` text of TEXT and HEADING
  blocks; match Markdown `[..](url)` and bare `https?://...`; trim trailing `.,;:!?)]"'`; keep only `http`/`https`;
  drop the fragment and keep the query; `LinkedHashSet` for order; `limit(N)`; log
  `log.info("Article URLs over cap skipped: documentId={} skipped={}", ...)` when skipped > 0.
- Fetcher (infrastructure, HC5 per D04): Content-Type check on the final response, case-insensitive, parameters
  ignored, missing = fail. Read through a bounded stream; reading byte cap+1 fails with a fixed domain-exception
  message. A `Content-Length` above the cap may fail early, but the byte count read is the real check. One URL
  failing never stops the others (D01).
- Tests (plain JUnit, tiny limits via settings): CODE-block URL skipped; duplicate and fragment-only variants
  deduped; document order kept; cap=2 with 3 URLs fetches 2 and logs skipped=1; trailing punctuation trimmed;
  `ftp:`/`mailto:` ignored. Mock-server tests: oversize body (with and without Content-Length) fails; `application/pdf`
  and missing Content-Type fail; slow response hits the total timeout.
