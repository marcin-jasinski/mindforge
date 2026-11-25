---
id: Q13
status: answered
impact: low
blocks: [W08]
answered_by: docs/project/deployment.md#Dependency check; docs/project/implementation-plan.md#Phase 20 (header note and "As built": "A scheduled job is Phase 21 work (#63)"); GitHub issue #63
---
## Question
Is the scheduled OWASP dependency-check job part of Phase 21, and what shape does it take?

## Context
Phase 21's task list does not name it, but Phase 20 assigned it to Phase 21 in the plan and in `deployment.md`
("No CI job runs the check yet; a scheduled job is Phase 21 work (#63)"). Issue #63 states the requirements.

## Known options
- A: A separate workflow (or job) on `schedule` (weekly) and `workflow_dispatch` running `mvn -B dependency-check:check`
  with `NVD_API_KEY: ${{ secrets.NVD_API_KEY }}` and an `actions/cache` of
  `~/.m2/repository/org/owasp/dependency-check-data`.
- B: Leave it for later.

## Constraints
Issue #63: not in `verify`, not on every push; key from a secret, never committed; cache the NVD data; a failure fails
the job. Setting the secret is a human action (record it in REPORT.md); the job runs without it, slowly. Implement A.
Update `deployment.md` § Dependency check to drop "No CI job runs the check yet".
