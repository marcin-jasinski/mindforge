---
id: Q11
status: decided
decision: D11
impact: medium
blocks: [W02]
---
## Question
What counts as a "repository" in the rule "No `@RestController` method accesses a repository directly", and how is
`AuthController`'s Spring Security `ClientRegistrationRepository` treated?

## Context
Plan 21.3 rule 2. Candidates for "repository": the domain ports in `dev.mindforge.domain.port` named `*Repository`
(`DocumentRepository`, `IngestRunRepository`, ...), the other tenant-scoped stores (`WikiStore`, `StudyProgressStore`,
`QuizSessionStore`, `InteractionStore`, query ports), and Spring Data JPA repositories in
`dev.mindforge.infrastructure.persistence.jpa`. `src/main/java/dev/mindforge/api/controller/AuthController.java`
injects `org.springframework.security.oauth2.client.registration.ClientRegistrationRepository` to list OAuth providers;
a rule by simple name suffix "Repository" flags it. The definition decides whether controllers may keep reading query
ports directly (thin read endpoints) or must always go through an application service.

## Known options
- A: Controllers may not depend on any class in `dev.mindforge.infrastructure.persistence..` or on any
  `dev.mindforge.domain.port` type whose name ends in `Repository` or `Store`; query ports and third-party framework
  types (ClientRegistrationRepository) are allowed.
- B: Controllers may not depend on any `dev.mindforge.domain.port` type or any persistence class; they go only through
  `dev.mindforge.application` services (may require moving current direct port uses into services).
- C: Name rule: no dependency on a type whose simple name ends in `Repository`, with `ClientRegistrationRepository`
  as a named exception.

## Constraints
- CLAUDE.md § API / Controllers: thin controllers — validation + auth check + delegate to an application service.
- `docs/standards/backend/api.md`; `docs/standards/architecture/hexagonal.md`.
- The rule checks controllers (`@RestController` classes) in every adapter package; Discord/Slack command classes are
  not controllers.
