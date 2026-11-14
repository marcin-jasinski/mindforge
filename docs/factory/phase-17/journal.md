# Journal — phase-17

2026-11-14T07:38:32Z start phase-17 base=cbc87b704ec92e74e7c01dc6e906abc11e50c932 baseline_failures=ApiFlowTest.theLoginRouteIsTheSinglePageApp (gate run without frontend)
2026-11-14T07:38:47Z tracker start #21
2026-11-14T07:44:09Z - plan@opus 7 items, 14 questions (open: Q01-Q09,Q13)
2026-11-14T07:49:17Z Q01 decided majority votes=B,B,E -> DocumentAccepted event, AFTER_COMMIT listener fetches on runExecutor (ADR 0024)
2026-11-14T07:49:17Z Q02 decided unanimous votes=A,A,A -> scheme/port constants + public-unicast addresses, no host list (ADR 0022)
2026-11-14T07:49:17Z Q03 decided unanimous votes=A,A,A -> EgressPolicy guards untrusted URLs only; source-scan guard test (ADR 0023)
2026-11-14T07:49:17Z Q04 round1 split B/D-HC5/D-OKHTTP -> round 2
2026-11-14T07:52:40Z Q04 decided unanimous(r2) votes=D-HC5x3 -> Apache HttpClient 5 with EgressPolicy DnsResolver, manual redirects max 5, other clients pinned to jdk (ADR 0025)
2026-11-14T07:52:40Z Q05 decided unanimous votes=A,A,A -> jsoup converter private to fetcher, no text/html parser (ADR 0026)
2026-11-14T07:52:40Z Q06 r1 ballots A,A,A
2026-11-14T07:54:00Z Q06 decided unanimous votes=A,A,A -> first 10 distinct http(s) URLs from TEXT/HEADING, html/xhtml only, 2MB cap, 5s/15s timeouts in AppProperties
2026-11-14T07:54:00Z Q07 r1 ballots A,A,A
2026-11-14T07:55:17Z Q07 decided unanimous votes=A,A,A -> title from <title>/og:title/h1, URL in sourceFilename, parent's uploader, text hash, new ingestArticle entry, no migration
2026-11-14T07:55:17Z Q08 r1 ballots A,A,A
2026-11-14T07:56:15Z Q08 decided unanimous votes=A,A,A -> skip failed article, log sanitised URL + fixed reason code, parent unaffected
2026-11-14T07:56:15Z Q09 r1 ballots A,A,A
2026-11-14T07:57:45Z Q09 decided unanimous votes=A,A,A -> domain port ArticleSource, infra HttpArticleSource, application ArticleFetcher
2026-11-14T07:57:45Z Q13 r1 ballots A,A,B
2026-11-14T07:59:28Z Q13 decided unanimous(merged) votes=A,A,B -> JDK HttpServer + package-private EgressPolicy(resolver, addressPredicate, portPredicate) test seam
2026-11-14T08:06:25Z - tickets@opus 10 tickets: P17-01,02,04,09,03,05,06,07,08,10
2026-11-14T08:07:16Z - tracker tickets #22-#31
2026-11-14T08:13:36Z P17-01 implement@inherit DONE
2026-11-14T08:18:52Z P17-01 test@sonnet r1 PASS
2026-11-14T08:20:50Z P17-01 review r1 correctness@opus changes(1 major) standards@sonnet approve spec-security@opus changes(1 major)
2026-11-14T08:23:01Z P17-01 fix@inherit r1 C1,S1 FIXED
2026-11-14T08:28:50Z P17-01 gate r2 PASS (test@sonnet PASS, correctness@opus approve, spec-security@opus approve)
2026-11-14T08:29:01Z P17-01 commit fda2bb4 done
2026-11-14T08:31:46Z P17-02 implement@inherit DONE
2026-11-14T08:37:32Z P17-02 gate r1 PASS (test@sonnet PASS, correctness@opus approve, standards@sonnet approve, spec-security@opus approve)
2026-11-14T08:37:37Z P17-02 commit 65457ca done
2026-11-14T08:41:28Z P17-04 implement@inherit DONE
2026-11-14T08:47:03Z P17-04 gate r1 PASS (test@sonnet PASS, correctness@opus approve, standards@sonnet approve, spec-security@opus approve; 2 deferred)
2026-11-14T08:47:11Z P17-04 commit 0f127cf done
2026-11-14T08:50:53Z P17-09 implement@inherit DONE
2026-11-14T08:56:01Z P17-09 gate r1 PASS (test@sonnet PASS, correctness@opus approve, standards@sonnet approve, spec-security@opus approve; 1 deferred)
2026-11-14T08:56:01Z P17-09 commit 932681a done
2026-11-14T08:59:21Z P17-03 implement@inherit DONE
2026-11-14T09:05:09Z P17-03 gate r1 PASS (test@sonnet PASS, correctness@opus approve, standards@sonnet approve, spec-security@opus approve)
