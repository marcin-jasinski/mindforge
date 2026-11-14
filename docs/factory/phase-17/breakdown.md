# Phase 17 — Article Fetcher: draft breakdown

Plan tasks: 17.1 (`EgressPolicy`), 17.2 (`ArticleFetcher`: extract, validate, fetch, HTML → text, ARTICLE Document +
QUEUED run, lesson skip, create-only/no Supersede, off by default), 17.3 (tests), plus the completion checklist (CL1:
all outbound HTTP goes through `EgressPolicy`; CL2: disabled by default) and phase close-out docs. Tests ship with the
item that adds the behaviour (17.3 is spread over W01–W06).

| Id | Intent | Plan tasks | Size | Depends on | Questions |
|---|---|---|---|---|---|
| W01 | `EgressPolicy` in `infrastructure.security`: scheme/port/host allowlist, resolve-and-check every address (loopback, private, link-local, metadata, IPv6/mapped forms rejected), `file:`/`data:` rejected, typed rejection exception; test seam for resolution; `EgressPolicyTest` | 17.1, 17.3, CL1 | M | — | Q02, Q03, Q04, Q13 |
| W02 | Article download adapter: HTTP fetch of one URL through `EgressPolicy` (every redirect hop re-checked), connect/total timeout, body-size cap, content-type check; returns bytes + final URL + type; fetch tests against a JDK mock server, incl. a policy violation and a redirect to a private address | 17.2, 17.3, CL1 | M | W01 | Q04, Q06, Q09, Q13 |
| W03 | HTML → article content: title + HEADING/TEXT/CODE blocks, boilerplate dropped (new dependency documented in `tech-stack.md` if chosen); converter tests with fixed HTML strings | 17.2, 17.3 | M | — | Q05, Q07 |
| W04 | `ArticleFetcher` orchestration: URLs from a document's blocks (filters, distinct, count cap), fetch + convert each, queue each as an `ARTICLE` Document with a QUEUED run through the `IngestionService` transaction (dedup, lesson exists → skip and log, distinct title), per-article failures skipped and logged; `ArticleFetcherTest` (mock HTTP server or mocked port) | 17.2, 17.3 | L | W02, W03 | Q01, Q06, Q07, Q08, Q09, Q11, Q14 |
| W05 | Switch, trigger and wiring: `ProcessingSettings.fetchExternalArticles` (default false) from a `mindforge.*` property, egress/limit properties in `AppProperties`, beans in `@Configuration`, the trigger from Q01 (upload path, after-commit listener, or post-run hook); tests: default is off and a document with URLs fetches nothing; on → articles queued | 17.2, CL2 | M | W04 | Q01, Q02, Q08, Q10 |
| W06 | Integration test (Testcontainers, `StubAIGateway`): an `ARTICLE` run whose claims target a live Concept writes nothing there, inserts no supersession, completes with `supersession_skipped` and failure `"article"`; optionally an end-to-end upload → article queued with the setting on | 17.3 | S | — (W05 for the optional end-to-end case) | Q12 |
| W07 | Docs: plan 17.x and checklist `[x]` with "As built" notes, roadmap, `deployment.md` (new settings and egress rules), `architecture.md` (data flow: where articles are fetched; trust model: egress scope), `tech-stack.md` if a dependency is added, `web-security.md` example if the API differs, new ADR if Q01/Q03 decide a load-bearing rule, `docs/INDEX.md` if a description changes, CLAUDE.md "Project State" | close-out | S | W01–W06 | Q01, Q03 |

Notes:
- W01, W03 and W06 are independent and can run in parallel.
- If Q09 picks a domain port, W02 implements it and W04 holds the application `ArticleFetcher`; if not, W02 and W04
  sit together in infrastructure and W04 calls `IngestionService` directly.
- If Q01 picks the in-run option (D), W05 also amends ADR 0013 and touches `IngestPipeline`; W07 records it.
- Create-only and the Supersede skip already exist (Q12); no item changes `Resolver` or `IngestPipeline` for them.
