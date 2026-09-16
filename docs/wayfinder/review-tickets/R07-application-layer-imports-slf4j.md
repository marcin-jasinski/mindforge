---
id: R07
title: The application layer imports slf4j against an exhaustive allow-list
type: decision
status: open
severity: medium
assignee:
blocked_by: []
---

## Problem

`docs/standards/architecture/hexagonal.md:12` states the application layer's permitted imports
as a closed list:

> | Application | `dev.mindforge.application` | `dev.mindforge.domain.*`, `dev.mindforge.agent.*`
> (the model services a use case sequences), plus Spring's transaction API
> (`org.springframework.transaction.*`) — transaction boundaries are part of a use case |

`CLAUDE.md` repeats it. `org.slf4j` is not on that list, and five application classes import it:

- `src/main/java/dev/mindforge/application/service/FlashcardService.java:20-21`
- `src/main/java/dev/mindforge/application/service/IngestPipeline.java:27-28`
- `src/main/java/dev/mindforge/application/service/LintService.java:13-14`
- `src/main/java/dev/mindforge/application/service/RunWorker.java:12-13`
- `src/main/java/dev/mindforge/application/wiki/IndexRenderer.java:8-9`

This is a **hard violation** of a documented, non-negotiable rule — but it is also the kind of
violation that suggests the rule is wrong rather than the code.

## The decision

This ticket is a decision, not a mechanical fix. Pick one:

### Option A — amend the standard (recommended)

Add `org.slf4j.*` to the application row, with the same parenthetical justification the
transaction API already gets. Rationale:

- slf4j is a **facade**, not an I/O implementation. The application layer depending on it is
  materially different from depending on JPA or a HTTP client.
- The rule that actually protects the architecture is the *domain* purity rule, and that one
  holds: the domain is JDK-only, verified.
- `IngestPipeline.java:321` logs the cause of a failed page draft. That log line is the only
  way to diagnose R01-class failures; routing it through a port would make it worse, not better.
- This is one line of documentation against five classes of churn.

If A is chosen, update **both** `docs/standards/architecture/hexagonal.md:12` and the
Architecture table in `CLAUDE.md`, and say explicitly that slf4j is permitted *because* it is a
facade with no I/O binding — so the exception does not get read as "any third-party facade".

### Option B — honour the standard as written

Introduce a logging port in `dev.mindforge.domain.port` and an infrastructure adapter. Costs: a
port and an adapter for something every Java developer expects to call directly, parameterised
logging becomes awkward, and `docs/standards/global/minimal-implementation.md` ("no speculative
abstractions") argues against it.

Only choose B if the closed allow-list is considered load-bearing for a reason this review did
not see.

## Why it matters

Medium, and mostly about the documentation being *true*. A standard that the codebase violates
in five places is a standard future agents will learn to ignore — which is more dangerous than
the import itself, because the same table holds the domain-purity rule that genuinely matters.

## Acceptance criteria

- [ ] A decision is recorded here with its rationale.
- [ ] The standard and the code agree — whichever moved.
- [ ] If A: `hexagonal.md` and `CLAUDE.md` both updated, and the exception is explained, not just
      listed.
- [ ] If B: no `org.slf4j` import remains under `dev.mindforge.application`.

## Resolution

<!-- filled on close -->
