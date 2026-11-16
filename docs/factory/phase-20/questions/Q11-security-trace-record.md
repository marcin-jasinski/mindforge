---
id: Q11
status: decided
decision: D11
impact: low
blocks: [W08]
---
## Question
Where is the checklist result "every `web-security.md` requirement traced to implementing code" recorded so that it
stays true after Phase 20?

## Context
- Completion checklist item 1 asks for a trace; the plan names no file.
- Earlier phases recorded results in the plan's "As built" note and the factory `REPORT.md`; neither is read by later
  work on security.
- `web-security.md` is the standard reviewers load for `api/**` changes (`.claude/factory.md` `standards_map`). A
  trace inside it would put file paths into a standard; a separate doc adds a file to `docs/INDEX.md`.
- Tests that enforce a requirement (e.g. `OutboundHttpGuardTest`, `ApiFlowTest.assertNoSensitiveFields`) keep it true
  better than a document does.

## Known options
- A: A short "Enforced by" line under each `web-security.md` section naming the class and the test.
- B: A new `docs/project/security-trace.md` table (requirement → code → test), listed in `docs/INDEX.md`.
- C: Only the plan's "As built" note and `REPORT.md`.

## Constraints
- `docs/standards/global/commenting.md` / `minimal-implementation.md`: no change logs in code; keep docs lean.
- `docs/INDEX.md` must list any new doc (factory `docs_to_update`).
