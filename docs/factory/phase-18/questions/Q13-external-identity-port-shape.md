---
id: Q13
status: answered
impact: low
blocks: [W01]
answered_by: docs/project/implementation-plan.md#Phase 18 — Discord Bot (18.1); docs/standards/architecture/hexagonal.md (structural tenancy; persistence sub-packages); docs/factory/phase-15/decisions/D06-cli-upload-source.md#Implementation notes; docs/standards/backend/migrations.md; docs/standards/backend/models.md
---
## Question
What shape do the `ExternalIdentityRepository` port, its platform type and its table take?

## Context
Plan 18.1 names `findUserId(platform, externalId)`, `link(...)`, `createUserAndLink(...)`; Phase 19 reuses it.
`UserRepository` is not tenant-scoped ("a user owns knowledge bases rather than living in one"). D06: one enum constant
per real channel, added with its producer.

## Known options
- Settled: port in `dev.mindforge.domain.port`, not tenant-scoped (no `kbId`); a domain enum for the platform with only
  `DISCORD` now (Phase 19 adds `SLACK`); `findUserId` returns `Optional<UUID>`; `createUserAndLink(platform, externalId,
  User)` inserts the user and the link in one transaction and returns the user (on a lost race the unique key fails and
  the caller re-reads `findUserId`); `link` only if Q02 gives it a caller. The reminder job (W10) needs one system
  method listing linked identities per platform. Table `external_identities (platform VARCHAR(50), external_id
  VARCHAR(64), user_id UUID NOT NULL REFERENCES users ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (platform, external_id))` plus an index on `user_id`, in `V8__create_external_identities.sql`; entity,
  JPA repository, MapStruct mapper and adapter in the persistence sub-packages; a Testcontainers adapter test.

## Constraints
- Domain JDK only; records for value types.
- FK indexed; timestamps; migration small and focused (migrations.md, models.md).
- Deleting a user removes their identities (cascade) so a later contact provisions afresh.
