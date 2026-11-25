---
id: Q02
status: decided
decision: D02
impact: medium
blocks: [W06, W07]
---
## Question
At which threshold does SpotBugs fail the build, and how are current findings handled (fix, exclude filter, or both)?

## Context
Plan 21.1 lists SpotBugs second in CI. `pom.xml` has no `spotbugs-maven-plugin`. SpotBugs on Spring/JPA/records code
reports many `EI_EXPOSE_REP`/`EI_EXPOSE_REP2` findings (records with `List` fields, injected beans held in fields) that
are false positives for this design, and some `CT_CONSTRUCTOR_THROW` findings on validating constructors. The choice
decides how much code changes and how noisy the gate is. Whether to add the FindSecBugs plugin is a related choice
(security rules exist in `docs/standards/security/web-security.md`, but the plan names only SpotBugs).

## Known options
- A: `threshold=Medium`, `effort=Max`, fail on any finding, with an exclude filter (`config/spotbugs-exclude.xml`)
  that drops the `EI_EXPOSE_REP*` family and `CT_CONSTRUCTOR_THROW` project-wide, each with a reason; fix the rest.
- B: `threshold=High` only (fail on high-confidence bugs), no exclude filter.
- C: Option A plus FindSecBugs.

## Constraints
- `docs/standards/global/conventions.md` § Minimal Dependencies.
- No change to behaviour to satisfy a false positive; an exclusion carries a reason.
- SpotBugs must support Java 21 bytecode (4.8+).
- The factory gate must stay green.
