---
id: Q10
status: answered
impact: medium
blocks: [W01]
answered_by: CLAUDE.md#Architecture: Hexagonal (Ports and Adapters); docs/standards/architecture/hexagonal.md (layer table)
---
## Question
Do the ArchUnit layer rules cover only the plan's line ("no domain class imports from `infrastructure` or `api`") or
the full layer table?

## Context
21.3 lists one layer rule; the checklist says "ArchUnit enforces layer and model-service boundaries". CLAUDE.md and
`hexagonal.md` define the full table: domain JDK only; application → domain, agent, `org.springframework.transaction.*`,
`org.slf4j.*`; agent → domain + `infrastructure.ai.*`; infrastructure → domain + application + third-party; adapters
`api`, `cli`, `discord`, `slack` → all layers. A grep of current imports shows the code already meets the full table,
so the wider rules cost no code change.

## Known options
- A: Encode the full table (domain JDK-only, application and agent allowlists, nothing inward depends on an adapter).
- B: Only the plan's domain rule.

## Constraints
The standards settle the table, and the checklist asks for layer boundaries. Implement A. The domain rule is "JDK
only" (stronger than the plan's "not infrastructure or api"). `MindForgeApplication` in the root package is allowed.
