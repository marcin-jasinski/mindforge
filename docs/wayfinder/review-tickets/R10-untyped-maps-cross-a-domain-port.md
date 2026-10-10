---
id: R10
title: failures and stepVersions cross a domain port as untyped maps
type: refactor
status: closed
severity: medium
assignee:
blocked_by: []
---

## Problem

Two untyped collections are part of the **domain port signature** and the domain record:

- `List<Map<String, Object>> failures`
- `Map<String, String> stepVersions`

in `src/main/java/dev/mindforge/domain/port/IngestRunRepository.java:33,37,40,44` and
`src/main/java/dev/mindforge/domain/model/IngestRun.java:23,27`.

They are built and read by string key in at least five places:

| Where | What it does |
|---|---|
| `application/service/IngestPipeline.java:289` | `Map.of("step", "write", "path", task.path(), "reason", e.getMessage())` |
| `application/service/Resolver.java:156` | builds a failure map with its own key literals |
| `application/service/LintService.java:153,163,170` | three more construction sites |
| `application/service/LintService.java` (`ReviewItem.toRow()`) | flattens a record *into* the map |
| `api/mapper/RunDtoMapper.java:48-64` | reads it back out with `text(failure, "step")` |

`ReviewItem.toRow()` is the clearest symptom: a perfectly good record is flattened into an
untyped map purely to cross the port, then reconstructed field-by-field on the other side.

This is **Primitive Obsession** feeding **Shotgun Surgery**: adding one field to a failure means
touching every construction site and the DTO mapper, with the compiler helping at none of them.

## Why it matters

- A typo in a key literal — `"reason"` vs `"resaon"` — compiles, persists, and surfaces as a
  blank field in the run report. Nothing catches it.
- `docs/standards/backend/java-conventions.md` calls for `record` types for value objects and
  result types. A run failure is exactly a result type.
- `Map<String, Object>` in a *domain* port is the strongest form of this smell: the domain layer,
  the one place with no framework and full type freedom, is passing stringly-typed data.
- It is also the reason `RunDtoMapper` needs a `text(map, key)` helper at all.

## Fix

Introduce two records in `dev.mindforge.domain.model`:

```java
public record RunFailure(String step, String path, String reason) {}
```

and something equivalent for the Lint review findings (`RunFinding`) if their shape genuinely
differs from `RunFailure` — check first; if they are the same three fields, use one record.

For `stepVersions`, `Map<String, String>` is defensible since it is a genuine
service-name-to-version map, but the key should be produced in one place rather than by
`Class::getSimpleName` at each call site (see the inconsistency noted in R11).

Then:

1. Change `IngestRunRepository` and `IngestRun` to carry `List<RunFailure>`.
2. Update the five construction sites; delete `ReviewItem.toRow()`.
3. Simplify `RunDtoMapper.java:48-64` to a straight field mapping and delete the `text(...)`
   helper.
4. The JPA layer still stores JSON — serialise the record rather than a hand-built map. Check
   whether the existing column is `jsonb` and whether stored rows need a migration; **if the
   serialised shape is unchanged, no migration is needed**, and it should be unchanged if the
   record's component names match today's key literals. Verify this explicitly.

## Acceptance criteria

- [x] No `Map<String, Object>` remains in `dev.mindforge.domain.port` or `dev.mindforge.domain.model`.
- [x] Failure construction is type-checked at every site.
- [x] `RunDtoMapper`'s string-key reads and the `text(...)` helper are gone.
- [x] Existing persisted run rows still render correctly in the run report — prove it with an
      integration test that reads a row written before the change, or confirm the JSON shape is
      byte-identical.

## Resolution

Added `RunFailure(step, item, path, reason, dropped, omittedPages)` in `domain.model` (one record for every failure
shape; the Lint findings already had the `ReviewItem` record, so `RunFinding` was not needed). `IngestRun`,
`IngestRunRepository`, the entity and adapter, `Resolver.Plan`, `IngestPipeline` and `LintService` now carry
`List<RunFailure>` / `List<ReviewItem>`; `ReviewItem.toRow()` and `RunDtoMapper`'s `text(...)` helper are deleted and
the mapper is a straight field mapping. No `Map<String, Object>` remains in `domain.port` or `domain.model`.

Persisted shape: the record component names are the old key literals, so rows written before the change deserialize
unchanged (missing keys read as null); new rows additionally carry explicit `null` components. No migration.
`IngestRunTest.aRowWrittenBeforeFailuresWereTypedStillReadsBack` writes a legacy-shaped row with SQL and reads it back.

`stepVersions` stays `Map<String, String>` as the ticket allows; producing its key in one place is left to R11.
`mvn verify`: 210 unit + 64 integration tests green.
