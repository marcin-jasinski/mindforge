# Journal — phase-20
2026-11-16T17:13:03+01:00 - start preflight ok; base ca220ea77751ec980cf898a5924fe0ee27c5281f; baseline failures: ApiFlowTest.theLoginRouteIsTheSinglePageApp
2026-11-16T17:13:20+01:00 - tracker start #54
2026-11-16T17:19:34+01:00 - plan planner@opus 13 questions (5 answered, 8 open, 0 blocker), 8 work items
2026-11-16T17:31:47+01:00 Q01 decided majority votes=A,A,B(opus) changes=ADR 0038 -> auth rate limiter keyed by client address (getRemoteAddr) on POST /api/v1/auth/login|register; 429 RATE_LIMITED + Retry-After
2026-11-16T17:33:21+01:00 Q02 decided majority votes=A,B,A(opus) changes=ADR 0039 -> server.forward-headers-strategy=native with default private-range internal-proxies; limiter keys on getRemoteAddr(); SERVER_TOMCAT_REMOTEIP_INTERNAL_PROXIES documented override
2026-11-16T17:33:21+01:00 Q04 decided unanimous votes=A,A,A(sonnet) changes=ADR 0040 -> authenticate always runs one PasswordHasher.matches (dummy hash for unknown email / OAuth-only); register 409 kept as accepted risk bounded by the rate limit
2026-11-16T17:34:56+01:00 Q03 decided unanimous votes=A,A,A(sonnet) changes=- -> mindforge.rate-limits.auth.requests=10 / .window=PT1M (AUTH_RATE_LIMIT_REQUESTS/_WINDOW); login+register share one bucket per address; ApiFlowTest needs a high limit
2026-11-16T17:34:56+01:00 Q11 decided judge votes=A(sonnet) changes=- -> one 'Enforced by: <classes>; tested by <tests>' line per section of web-security.md
2026-11-16T17:37:03+01:00 Q05 decided unanimous votes=B,B,B(sonnet) changes=ADR 0041 -> CorsConfigurationSource in SecurityConfig, one origin from CORS_ALLOWED_ORIGIN, none by default; '*' or comma fails startup; ADR 0008 supersession left to human
2026-11-16T17:37:03+01:00 Q06 decided majority votes=A,A,D(sonnet) changes=- -> dependency-check-maven declared (failBuildOnCVSS=7, suppression file), no bound execution; explicit mvn dependency-check:check; Phase 21 follow-up for scheduled job
2026-11-16T17:37:03+01:00 Q07 decided majority votes=A,C,A(sonnet) changes=- -> NVD key optional from NVD_API_KEY env only; OSS Index, Node/npm/Yarn/pnpm, RetireJS, assembly analyzers off
2026-11-16T17:41:51+01:00 - tickets ticket-writer@opus 8 tickets P20-01..P20-08
2026-11-16T17:42:23+01:00 - tickets committed ab226a7; issues #55-#62
2026-11-16T17:49:11+01:00 P20-01 implement@inherit DONE
2026-11-16T18:05:58+01:00 P20-01 test@sonnet r1 PASS; review r1 correctness@opus,standards@sonnet,spec-security@opus approve; test-guard clean -> PASS
2026-11-16T18:05:58+01:00 P20-01 commit 5b1ca3b
2026-11-16T18:11:10+01:00 P20-02 implement@inherit DONE
2026-11-16T18:21:10+01:00 P20-02 test@sonnet r1 PASS; review r1 correctness@opus,standards@sonnet,spec-security@opus approve; test-guard clean -> PASS
