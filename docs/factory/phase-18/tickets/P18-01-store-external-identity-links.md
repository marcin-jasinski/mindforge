---
id: P18-01
title: Store external identity links with an ExternalIdentityRepository port and the V8 external_identities table
status: ready
plan_tasks: [18.1]
decisions: [D02]
depends_on: []
size: M
issue: 33
commit:
fix_rounds: 0
---
## Goal
A chat-platform identity (platform + external id) can be stored as a link to a MindForge user, read back, and listed
per platform. Creating a user and its link is one transaction. Deleting a user deletes its links. There is no
`link(...)` method.

## Context
- Nothing of Phase 18 exists: no `ExternalIdentity*` type, no `external_identities` table. Migrations are `V1`..`V7`
  in `src/main/resources/db/migration/`; the next one is `V8__create_external_identities.sql`.
- `users` table: `V1__baseline.sql` (`email VARCHAR(255) NOT NULL` with unique index `idx_users_email`).
- Precedent for a non-tenant-scoped port: `src/main/java/dev/mindforge/domain/port/UserRepository.java` and
  `infrastructure/persistence/adapter/UserRepositoryAdapter.java` (accounts live above knowledge bases, so no `kbId`).
- Persistence code lives in the sub-packages `infrastructure/persistence/{entity,jpa,mapper,adapter}` with MapStruct
  mappers (`docs/standards/backend/models.md`). Adapter beans are wired in
  `infrastructure/config/PersistenceConfig.java` (or the config that wires `UserRepositoryAdapter`).
- Testcontainers adapter tests: `src/test/java/dev/mindforge/integration/persistence/` (follow
  `DocumentRepositoryAdapterTest.java`).
- Port shape (answered question Q13, from plan 18.1, hexagonal.md and migrations.md): port in
  `dev.mindforge.domain.port`, not tenant-scoped; domain enum for the platform with only `DISCORD` now (Phase 19 adds
  `SLACK`; do not add it); table
  `external_identities (platform VARCHAR(50) NOT NULL, external_id VARCHAR(64) NOT NULL, user_id UUID NOT NULL
  REFERENCES users(id) ON DELETE CASCADE, created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(), PRIMARY KEY (platform,
  external_id))` plus an index on `user_id`.
- Rules: domain is JDK only; records for value types; names must not be Discord-only (Phase 19 reuses the port) but no
  Slack-shaped code now (`docs/standards/global/minimal-implementation.md`).

## Decisions to apply
**D02 — Linking to an existing account.** Decision: Phase 18 does not link a Discord user to an existing web account.
Build `ExternalIdentityRepository` with `findUserId`, `createUserAndLink` and the reminder job's listing method only.
Do not add `link(...)` to the port, the adapter or any command. A Discord user has a separate, auto-provisioned account
(ADR 0027).
Implementation notes:
- `dev.mindforge.domain.port.ExternalIdentityRepository`:
  `Optional<UUID> findUserId(ExternalPlatform platform, String externalId)`;
  `User createUserAndLink(ExternalPlatform platform, String externalId, User user)`; the reminder listing method. No `link`.
- No test may reference `link`.

Listing method (for the reminder job, D10): `List<ExternalIdentity> findByPlatform(ExternalPlatform platform)` with
`record ExternalIdentity(ExternalPlatform platform, String externalId, UUID userId)` in `dev.mindforge.domain.model`.

Lost-race contract (D01 implementation notes): `createUserAndLink` inserts the `users` row and the
`external_identities` row in one `@Transactional` method in the adapter (it joins a caller's transaction when there is
one). It must flush, and translate a unique violation (on the primary key or on `idx_users_email`) into a domain
exception, e.g. `ExternalIdentityConflictException` in `dev.mindforge.domain.model` (extends a meaningful base, not bare
`RuntimeException`), so the application layer can catch it without importing `org.springframework.dao`.

## Acceptance criteria
- [ ] AC1: `ExternalPlatform` enum (`DISCORD` only), `ExternalIdentity` record, `ExternalIdentityConflictException` and
  the `ExternalIdentityRepository` port exist in the domain and import JDK types only.
- [ ] AC2: `V8__create_external_identities.sql` creates the table above with the composite primary key, the
  `ON DELETE CASCADE` foreign key and an index on `user_id`; Flyway migrates a fresh database.
- [ ] AC3: `createUserAndLink` inserts the user and the link; `findUserId` then returns that user id; an unknown
  `(platform, externalId)` returns empty.
- [ ] AC4: A second `createUserAndLink` for the same `(platform, externalId)` (with a new user) throws
  `ExternalIdentityConflictException` and leaves no new `users` row; the same holds for a user whose email already exists.
- [ ] AC5: `findByPlatform(DISCORD)` returns every Discord link with its user id.
- [ ] AC6: Deleting the `users` row removes its link (cascade).
- [ ] AC7: The port, the adapter and the tests have no `link` method or reference.

## Test plan
- Integration (Testcontainers, real PostgreSQL): `src/test/java/dev/mindforge/integration/persistence/ExternalIdentityRepositoryAdapterTest.java`
  covers AC2–AC6. AssertJ; `make*` factories for `User`.
- AC1/AC7 are checked by review and by the existing architecture/guard tests staying green.

## Files likely touched
- `src/main/java/dev/mindforge/domain/model/ExternalPlatform.java` (new)
- `src/main/java/dev/mindforge/domain/model/ExternalIdentity.java` (new)
- `src/main/java/dev/mindforge/domain/model/ExternalIdentityConflictException.java` (new)
- `src/main/java/dev/mindforge/domain/port/ExternalIdentityRepository.java` (new)
- `src/main/resources/db/migration/V8__create_external_identities.sql` (new)
- `src/main/java/dev/mindforge/infrastructure/persistence/entity/ExternalIdentityEntity.java` (new; composite id class or `@IdClass`)
- `src/main/java/dev/mindforge/infrastructure/persistence/jpa/ExternalIdentityJpaRepository.java` (new)
- `src/main/java/dev/mindforge/infrastructure/persistence/mapper/ExternalIdentityMapper.java` (new)
- `src/main/java/dev/mindforge/infrastructure/persistence/adapter/ExternalIdentityRepositoryAdapter.java` (new)
- `src/main/java/dev/mindforge/infrastructure/config/PersistenceConfig.java`
- `src/test/java/dev/mindforge/integration/persistence/ExternalIdentityRepositoryAdapterTest.java` (new)

## Out of scope
- `link(...)`, any account-linking flow, `ExternalPlatform.SLACK`.
- The synthetic email rule and the identity-resolution service (P18-02, P18-03).
- Any change to the `users` schema.

## Notes
