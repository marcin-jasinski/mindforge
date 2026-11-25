---
id: D11
question: Q11
status: decided
method: unanimous
class: local
votes: { pragmatist: B, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-21/ballots/Q11.r1.tally.md]
adr: 
---
## Decision
Use B: a "repository" for plan 21.3 rule 2 is any type in `dev.mindforge.domain.port..` (repositories, stores, query
ports and all other ports) or in `dev.mindforge.infrastructure.persistence..` (adapters, JPA repositories, entities,
mappers). No `@RestController` class in any package may depend on such a type; controllers reach data only through
`dev.mindforge.application` services. Third-party types outside `dev.mindforge` (for example Spring Security's
`ClientRegistrationRepository` in `AuthController`) are not covered and need no exception.

## Why
- The rule is structural (package based), so a rename cannot bypass it, and it matches CLAUDE.md § API / Controllers
  and `docs/standards/backend/api.md:35` ("delegation to application service").
- No controller imports a `domain.port` or `persistence` type today (grep of `src/main/java/dev/mindforge/api/`),
  so the rule passes without refactor.
- `ClientRegistrationRepository` is `org.springframework...`, so the package scope excludes it with no special case.

## Rejected
- A: the query-port carve-out leaves a gap that the thin-controller rule does not allow, and nobody needs it today.
- C: name heuristic; misses `WikiStore`/`QuizSessionStore`/query ports and flags the framework type (verified hard objection).

## Dissent
None. Risk to watch: a future thin read endpoint must add a pass-through application-service method instead of
reading a query port directly.

## Implementation notes
- ArchUnit test (no Spring context), for example in `src/test/java/dev/mindforge/architecture/`:
  `noClasses().that().areAnnotatedWith(RestController.class).should().dependOnClassesThat()
  .resideInAnyPackage("dev.mindforge.domain.port..", "dev.mindforge.infrastructure.persistence..")`.
- Import the whole `dev.mindforge` package so controllers in every adapter package are checked; select by the
  annotation `org.springframework.web.bind.annotation.RestController`, not by package. `@RestControllerAdvice`
  (`GlobalExceptionHandler`) is not selected; Discord/Slack command classes are not controllers.
- Do not add an exception for `ClientRegistrationRepository`; the test must pass with `AuthController` as it is.
- Out of scope: controller imports of `infrastructure.event`/`security`/`export` types (`SseProgressNotifier`,
  `JwtService`, `BundleExporter`); do not forbid them in this rule.
- Name the rule clearly, e.g. `controllers_do_not_access_ports_or_persistence_directly`, and add `.because(...)` citing
  the thin-controller rule.
