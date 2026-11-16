---
id: Q12
status: answered
impact: low
blocks: [W07]
answered_by: docs/project/implementation-plan.md#phase-20--security-hardening (task 20.2); docs/standards/global/minimal-implementation.md
---
## Question
Does the OWASP audit also cover the frontend's npm dependencies (`frontend/package-lock.json`)?

## Context
- Plan 20.2 names only `dependency-check-maven` and the checklist only `mvn dependency-check:check`.
- The production JAR embeds the built SPA (static files), not `node_modules`.
- `dependency-check-maven` scans the Maven project's artifacts; its Node analyzers would scan `package-lock.json` only
  if configured to.

## Known options
- A: Maven dependencies only; Node/RetireJS analyzers off (see Q07); an npm audit is not part of Phase 20.

## Constraints
- Plan scope; `minimal-implementation.md`. Answer: A.
