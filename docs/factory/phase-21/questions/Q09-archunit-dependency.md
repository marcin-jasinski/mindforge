---
id: Q09
status: answered
impact: low
blocks: [W01]
answered_by: docs/project/implementation-plan.md#Phase 21 task 21.3; docs/adr/0023-egress-policy-guards-untrusted-urls-only.md#Considered Options
---
## Question
Does Phase 21 add the ArchUnit test dependency, given that ADR 0023 chose a source scan over ArchUnit?

## Context
ADR 0023 rejected "An ArchUnit rule as the guard: a new test dependency for one rule; a source scan does the same
job." Phase 21.3 explicitly names ArchUnit and lists five rules; `docs/wayfinder/tickets/25-port-read-surface.md`
says "an ArchUnit rule in 21.3 enforces that". The ADR's reason (one rule) does not hold for five, and the ADR's
decision (EgressPolicy scope) is not touched.

## Known options
- A: Add `com.tngtech.archunit:archunit-junit5` (test scope) for 21.3; keep `OutboundHttpGuardTest` as it is.
- B: Write all five rules as source scans.

## Constraints
A is the plan's stated intent and does not contradict ADR 0023. Keep the existing source-scan guards; do not migrate
them (out of scope). Document the dependency's purpose (conventions.md § Minimal Dependencies).
