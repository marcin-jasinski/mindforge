# Journal — phase-21
2026-11-25T09:36:04+01:00 - start preflight ok; base 76c75fafa98fb39c9ec4598f8028143dd29061cb; baseline failures: ApiFlowTest.theLoginRouteIsTheSinglePageApp
2026-11-25T09:36:17+01:00 - tracker start #65
2026-11-25T09:42:37+01:00 - plan planner@opus 15 questions (6 answered, 9 open, 0 blocker), 14 work items
2026-11-25T09:45:41+01:00 Q03 decided unanimous votes=A,A,A(sonnet) changes=none -> bind checkstyle:check to validate and spotbugs:check to verify; CI keeps one mvn -B verify step
2026-11-25T09:45:42+01:00 Q02 decided unanimous votes=A,A,A(sonnet) changes=none -> SpotBugs Medium/Max on verify; exclude EI_EXPOSE_REP, EI_EXPOSE_REP2, CT_CONSTRUCTOR_THROW in config/spotbugs-exclude.xml; fix the rest; FindSecBugs deferred
2026-11-25T09:45:43+01:00 Q08 decided unanimous votes=A,A,A(sonnet) changes=none -> separate e2e job in ci.yml (needs: linux) on every push and PR; compose stack; suite runs outside Maven verify
2026-11-25T09:45:52+01:00 Q01 decided unanimous votes=A,A,A(opus) changes=none -> project Checkstyle ruleset config/checkstyle/checkstyle.xml encoding only stated rules (whitespace, LF, imports, field @Autowired in src/main, logger 'log', LineLength 120); failOnViolation in validate; wrap the 125 long lines; no baseline
2026-11-25T09:56:11+01:00 Q06 decided unanimous votes=B,B,B(opus) changes=none -> pinned WireMock model-stub in test-only compose.e2e.yml as the OpenAI-compatible provider (OPENROUTER_BASE_URL); static mappings by prompt fragment in src/test/e2e/model-stub/mappings/, 400 catch-all; no src/main change; ADR 0042
2026-11-25T09:56:12+01:00 Q11 decided unanimous votes=B,B,B(sonnet) changes=none -> a @RestController must not depend on dev.mindforge.domain.port.. or infrastructure.persistence..; third-party types out of scope
2026-11-25T09:56:13+01:00 Q12 decided unanimous votes=B,B,B(sonnet) changes=none -> source-scan guard test for Pattern literals with markdown heading or escaped link syntax; MarkdownStructure owner; BundleConformanceValidator allowlisted by exact path with reason
2026-11-25T09:56:15+01:00 Q14 decided judge votes=A(sonnet) changes=none -> test fixture src/test/resources/static/index.html with <app-root> so theLoginRouteIsTheSinglePageApp passes under -DskipFrontend
2026-11-25T09:58:48+01:00 Q07 decided debate votes=r1 C,C,C -> r2 A,A,A(opus) changes=pragmatist,architect,skeptic C->A -> Playwright Test (TypeScript) as its own npm project in src/test/e2e/ next to the D06 mappings, run against compose outside Maven; test-writing.md gains a browser-suite line
2026-11-25T10:05:04+01:00 - tickets ticket-writer@opus 12 tickets P21-01..P21-12
