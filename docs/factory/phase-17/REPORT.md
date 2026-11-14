# Factory run report — Phase 17, Article Fetcher

- **Run:** phase-17 · started 2026-11-13 · finished 2026-11-14
- **Outcome:** halted for human merge. All 10 tickets are done on the phase branch. The merge into `develop` is not done by this report. Integration gate: one baseline failure, `ApiFlowTest.theLoginRouteIsTheSinglePageApp` (it needs the frontend build; it is not caused by Phase 17).
- **Branch:** `factory/phase-17` · base cbc87b704ec92e74e7c01dc6e906abc11e50c932 · last commit 26caab8

## Audit first

1. No decision was made by the judge alone. D01 was a majority decision (2 of 3 for B; the pragmatist chose E). D04 went to round 2 and was then unanimous. Read D01 first. It sets when and where articles are fetched: best-effort, after commit, and an article is lost if the process stops after the upload commits.
2. Five ADRs were written (0022 to 0026). Read ADR 0023 (the egress policy guards only URLs from content or users; operator-configured endpoints are trusted) and ADR 0025 (new dependency `httpclient5`, with a pin of the checked addresses).
3. ADR 0024 text is wrong in one place (finding H2, below). A human must correct it.
4. This is a security-sensitive feature. Review `EgressPolicy` (P17-01) and `HttpArticleSource` (P17-05; it needed one fix round for 2 major correctness and 3 major security findings). The setting `FETCH_EXTERNAL_ARTICLES` is off by default.
5. The gate run for P17-07 was interrupted by an API usage limit (429) and was run again. The second run passed. No result was lost.

## Tickets

| Ticket | Title | Status | Commit | Fix rounds | Notes |
|---|---|---|---|---|---|
| P17-01 | EgressPolicy: http/https, default ports, public unicast addresses only | done | fda2bb4 | 1 | |
| P17-02 | Publish DocumentAccepted in the upload transaction | done | 65457ca | 0 | |
| P17-03 | IngestionService.ingestArticle | done | ff5609a | 0 | |
| P17-04 | HTML converter (jsoup), private to the fetcher | done | 0f127cf | 0 | 2 findings deferred |
| P17-05 | HttpArticleSource with HttpClient 5, address pin | done | 4681a24 | 1 | |
| P17-06 | Source-scan guard test for outbound HTTP | done | 27b0b7f | 0 | 1 finding deferred |
| P17-07 | ArticleFetcher service, behind fetchExternalArticles | done | b3b0d6f | 0 | gate interrupted by 429, rerun; 2 findings deferred |
| P17-08 | Fetch after upload commit, on the run executor | done | 8ba6b0d | 0 | |
| P17-09 | ARTICLE run is create-only test | done | 932681a | 0 | 1 finding deferred |
| P17-10 | Close-out docs | done | 26caab8 | 0 | |

No ticket is parked.

## Decisions

| Id | Question | Method | Class | Decision (one line) | Dissent / risk to watch |
|---|---|---|---|---|---|
| D01 | Q01 when and where articles are fetched | majority | adr | `DocumentAccepted` event in the upload transaction; AFTER_COMMIT listener fetches on the run executor | Pragmatist voted E. Fetch is best-effort: a crash after commit loses the articles |
| D02 | Q02 egress allowlist | unanimous | adr | http/https, default/80/443 ports, no userinfo, every address public unicast | - |
| D03 | Q03 scope of outbound HTTP | unanimous | adr | `EgressPolicy` guards URLs from content or users only; guard test pins client code | Exact-path exemption in the guard test (see H1) |
| D04 | Q04 DNS rebinding and redirects | debate (round 2) | adr | HttpClient 5, resolver returns only checked addresses, redirects followed in code and checked per hop | 2 vote changes, both toward the final decision |
| D05 | Q05 HTML to text | unanimous | adr | jsoup, private to the fetcher | - |
| D06 | Q06 URLs and limits | unanimous | local | TEXT and HEADING URLs only, cap per document, size and content-type limits | - |
| D07 | Q07 article identity | unanimous | local | Title from `<title>`, else `og:title`, else first `<h1>` | - |
| D08 | Q08 failure behaviour | unanimous | local | Skip, log with sanitised URL and fixed reason | Failures are visible in logs only |
| D09 | Q09 layering | unanimous | local | Domain port `ArticleSource`, infrastructure adapter, application service | - |
| D13 | Q13 mock server | unanimous | local | JDK `HttpServer`, no new dependency | - |

Method counts: unanimous 8 · majority 1 · debate 1 · judge 0. Blockers: 0.
Questions Q10, Q11, Q12 and Q14 were already answered by existing docs and ADRs and had no panel.

## Decision outcomes

| Decision | Method | Vote changes | Models (panel / judge) | Tickets applying it (status, fix rounds) | Findings / rulings citing it |
|---|---|---|---|---|---|
| D01 | majority | none | opus x3 / opus | P17-02 (done, 0), P17-08 (done, 0), P17-10 (done, 0) | P17-08.r1.spec-security |
| D02 | unanimous | none | opus x3 / opus | P17-01 (done, 1) | P17-01.r1.spec-security |
| D03 | unanimous | none | opus x3 / opus | P17-06 (done, 0), P17-10 (done, 0) | P17-10.r1.spec-security |
| D04 | debate | pragmatist B to D-HC5; skeptic D-OKHTTP to D-HC5 | opus x3 / opus | P17-05 (done, 1) | P17-05.r1 and r2 spec-security |
| D05 | unanimous | none | sonnet x3 / opus | P17-04 (done, 0) | P17-04.r1.spec-security |
| D06 | unanimous | none | sonnet x3 / opus | P17-05 (done, 1), P17-07 (done, 0) | P17-05.r1 correctness and spec-security, P17-07.r1.correctness |
| D07 | unanimous | none | sonnet x3 / opus | P17-03 (done, 0) | P17-03.r1.spec-security |
| D08 | unanimous | none | sonnet x3 / opus | P17-07 (done, 0) | P17-10.r1.spec-security |
| D09 | unanimous | none | sonnet x3 / opus | P17-05 (done, 1), P17-07 (done, 0) | none |
| D13 | unanimous | none | sonnet x3 / opus | P17-01 (done, 1), P17-05 (done, 1) | none |

The review files cite findings by file; each cited file is an `approve` or `changes` review. The 4 `changes` verdicts are P17-01.r1 (correctness, spec-security) and P17-05.r1 (correctness, spec-security); all were fixed in the next round. No finding was disputed.

## Panel statistics

Decisions per method: unanimous 8, majority 1, debate 1, judge 0. Round-2 debates: 1 (Q04). Vote changes: 2, both toward the final decision, none away. Ballots discounted for false evidence: 0 (no `counted: no` row in any tally). Disputed findings: 0 (the `rulings/` directory is empty): valid 0, invalid 0, defer 0.

## ADRs written

- 0022 egress allowlist: schemes, ports, public addresses (D02)
- 0023 egress policy guards untrusted URLs only (D03)
- 0024 articles fetched after upload commit from a document event (D01)
- 0025 article fetch pins checked addresses with HttpClient 5 (D04)
- 0026 fetched HTML converted with jsoup, private to the fetcher (D05)

## Deferred findings

Holistic review (approve; all minor):

- H1 (web-security.md:75, architecture.md:73): the docs said HTTP-client code lives only in the article fetch adapter, but `SecurityConfig` builds a `RestClient` and is exempt by exact path. **Fixed in this scribe step** in both docs (exception named). ADR 0023 line 10 keeps the old wording; it can stay or get the same note.
- H2 (ADR 0024 line 9): the ADR says articles are queued "through `IngestionService.ingest`". The code uses `IngestionService.ingestArticle` (no admit, no parse, no `DocumentAccepted`). **Not fixed: ADR text is for the human.** Suggested text: "through `IngestionService.ingestArticle` (same lock, dedup and lesson rule as `ingest`; no admit or parse)".
- H3 (architecture.md:143 and :1542 of the plan): the docs said CLI documents are scanned, but the `cli` profile registers no listener. **Fixed in this scribe step** in `architecture.md` and `implementation-plan.md`. Code still lists `UploadSource.CLI` in `ArticleFetcher.SCANNED_SOURCES`; it is dead. Optional code clean-up for the human (and `ArticleFetcherTest`).

Per-ticket minor findings (2 in P17-04, 1 in P17-06, 2 in P17-07, 1 in P17-09) are recorded in the review files under `reviews/` and were not fixed (minimal implementation).

## Manual checks still open

- Turn on `FETCH_EXTERNAL_ARTICLES=true` on a real deployment, upload a document with a public article link, and check that the article appears as its own document and run. No test covers a real network fetch.
- Check that a link to a private address in a real upload is skipped and logged.
- `ApiFlowTest.theLoginRouteIsTheSinglePageApp` fails without the frontend build. Run the full gate with the frontend to confirm it passes.
- Phase 14 Langfuse trace check stays open (out of scope for this run).

## Statistics

Tickets: 10 done, 0 parked. Fix rounds: 2 (P17-01: 1, P17-05: 1). Disputed findings: 0. Decisions: unanimous 8, majority 1, debate 1, judge 0.

Agent calls (hook state, total 103):

| Role @ model | Calls |
|---|---|
| planner @ opus | 1 |
| solver @ opus | 15 |
| solver @ sonnet | 18 |
| judge @ opus | 11 |
| ticket-writer @ opus | 1 |
| implementer @ default | 10 |
| tester @ sonnet | 12 |
| reviewer @ opus | 23 |
| reviewer @ sonnet | 9 |
| fixer @ default | 2 |
| scribe @ sonnet | 1 |

Forced continuations: 2. Stalled: 1.
