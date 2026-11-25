---
id: Q15
status: answered
impact: low
blocks: [W11]
answered_by: docs/project/implementation-plan.md#Phase 9b (As built note and 9b.2; checklist "Every exported bundle passes the conformance validator")
---
## Question
What does "export bundle (validates)" in the E2E journey check?

## Context
`BundleExporter` already runs `BundleConformanceValidator.violations` before it returns and fails the request with
`BundleNotConformantException` (500) on any violation (plan 9b "As built";
`src/main/java/dev/mindforge/infrastructure/export/BundleExporter.java:94`). So a 200 download is a validated bundle.

## Known options
- A: The E2E clicks Export, receives the download, and checks it is a zip named `<slug>-okf.zip` whose entries include
  `index.md`, `log.md` and the page files of the journey (as `ApiFlowTest` does at API level).
- B: The E2E re-runs a validator on the downloaded files.

## Constraints
The server-side validator is the oracle (9b). A covers "validates" without a second validator. Implement A.
