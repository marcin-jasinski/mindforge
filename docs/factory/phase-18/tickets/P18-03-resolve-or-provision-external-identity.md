---
id: P18-03
title: Resolve a chat-platform user to a MindForge user, provisioning the account and a default knowledge base on first contact
status: ready
plan_tasks: [18.1, 18.2, 18.4]
decisions: [D01, D03]
depends_on: [P18-01, P18-02]
size: M
issue:
commit:
fix_rounds: 0
---
## Goal
An application service turns `(platform, externalId, displayName)` into a MindForge user id. On first contact it
creates the user (synthetic email, no password), the identity link and one knowledge base named after the platform
("Discord"), all in one transaction. A second contact reuses the account. Two concurrent first contacts end with one
user and one knowledge base. This proves the 18.4 test "first contact auto-provisions".

## Context
- After P18-01: `ExternalIdentityRepository` (`findUserId`, `createUserAndLink`, `findByPlatform`),
  `ExternalPlatform.DISCORD`, `ExternalIdentityConflictException` (thrown on a unique violation).
- After P18-02: `ExternalAccounts.syntheticEmail(platform, externalId)`.
- `User` record: `src/main/java/dev/mindforge/domain/model/User.java`. `KnowledgeBase` record and
  `domain/port/KnowledgeBaseRepository.java` (`insert`). `TextRules.singleLine` in `domain/model/TextRules.java`.
- Transaction precedent in the application layer: `TransactionOperations` injected into
  `application/service/IngestPipeline.java:105` and `RunWorker.java:45`. The application layer may import only
  `org.springframework.transaction.*` and `org.slf4j.*` from Spring/third-party (CLAUDE.md layer table).
- Application services are wired in `infrastructure/config/*Config.java` (e.g. `AccountConfig.java`).
- This service is the "resolve or provision" logic; it lives in the application layer, not in the bot adapter
  (scope.md constraints; hexagonal.md). Name it platform-neutral (Phase 19 reuses it), e.g.
  `application/service/ExternalIdentityService` with `UUID resolve(ExternalPlatform platform, String externalId, String displayName)`.
- ADRs: 0027 (account model), 0030 (commands name the knowledge base; provisioning creates a default one).

## Decisions to apply
**D01 — Account for a Discord user (ADR 0027).** Decision: on first contact, provision a `users` row with the synthetic
email `<platform>-<externalId>@users.invalid`, a null password and a null avatar, and link it to the external identity
in one transaction. Do not change the `users` schema. Provisioning always inserts and never resolves a user by email.
Implementation notes:
- `findUserId(platform, externalId)`; on miss call `createUserAndLink(platform, externalId, new User(UUID.randomUUID(),
  displayName, syntheticEmail(...), null, null, null, null))` (check the real `User` constructor arity).
- Display name: global name, else username, else externalId, through `TextRules.singleLine`, truncated to 255. (The
  adapter passes the best name it has; the service applies the fallback to `externalId` when blank, `singleLine` and truncation.)
- On a unique violation (`(platform, external_id)` or `idx_users_email`) the resolver re-reads `findUserId` once and
  returns that user.
- Tests: first contact provisions a user with the synthetic email and null password; a second contact returns the same
  user; two concurrent first contacts yield one user.

**D03 — Which knowledge base a command acts on (ADR 0030), provisioning part.** Decision: provisioning a new bot user
also creates one knowledge base named after the platform ("Discord"), in the same transaction as the user and the
identity link, and only on the winning insert. No stored "current knowledge base", no migration.
Implementation notes:
- In the identity-resolution service, provisioning branch only: run `createUserAndLink(...)` and
  `knowledgeBases.insert(new KnowledgeBase(UUID.randomUUID(), userId, DEFAULT_KNOWLEDGE_BASE_NAME, null, null, 0, 0))`
  in one transaction via the injected `TransactionOperations`. On a unique violation the transaction rolls back and the
  resolver re-reads `findUserId` outside it; the loser creates no knowledge base.
- The name comes from the platform (`"Discord"` for Phase 18; not hard-coded in the port, so Phase 19 passes
  `"Slack"`). Suggested: a display label on `ExternalPlatform` (`DISCORD("Discord")`) or a constructor parameter of the service.
- A user with zero knowledge bases (only if a row was removed by hand): do not add a backstop create.
- Tests: first contact creates exactly one user and exactly one knowledge base "Discord" owned by it; a second contact
  creates none; two concurrent first contacts -> one user, one knowledge base (Testcontainers).

## Acceptance criteria
- [ ] AC1: First contact with an unknown `(DISCORD, id)` returns a new user id; the stored user has email
  `discord-<id>@users.invalid`, a null password hash and the cleaned display name; one knowledge base named "Discord"
  is owned by it.
- [ ] AC2: A second call with the same `(DISCORD, id)` returns the same user id and inserts no user and no knowledge base.
- [ ] AC3: A blank display name falls back to the external id; a multi-line or over-long name is made single-line and
  cut to 255 chars.
- [ ] AC4: When `createUserAndLink` throws `ExternalIdentityConflictException`, the service re-reads `findUserId` once,
  outside the failed transaction, returns that id and inserts no knowledge base.
- [ ] AC5: Two threads resolving the same new identity at once against a real database end with exactly one user, one
  link and one knowledge base, and both get the same user id.
- [ ] AC6: The service makes no model call and imports nothing outside the application layer's allowed list.
- [ ] AC7: The service is a Spring bean wired in a `@Configuration` (constructor injection).

## Test plan
- Unit (plain Mockito, no Spring context): `src/test/java/dev/mindforge/unit/application/ExternalIdentityServiceTest.java`
  for AC1–AC4 (mock the ports; use a `TransactionOperations` that runs the callback, e.g.
  `TransactionOperations.withoutTransaction()`).
- Integration (Testcontainers): `src/test/java/dev/mindforge/integration/application/ExternalIdentityProvisioningTest.java`
  for AC1, AC2 and AC5 (two concurrent first contacts, e.g. with a `CountDownLatch` and two virtual threads).

## Files likely touched
- `src/main/java/dev/mindforge/application/service/ExternalIdentityService.java` (new)
- `src/main/java/dev/mindforge/domain/model/ExternalPlatform.java` (label, if chosen)
- `src/main/java/dev/mindforge/infrastructure/config/AccountConfig.java`
- `src/test/java/dev/mindforge/unit/application/ExternalIdentityServiceTest.java` (new)
- `src/test/java/dev/mindforge/integration/application/ExternalIdentityProvisioningTest.java` (new)

## Out of scope
- Any JDA code, allowlist or rate limit (P18-04, P18-05).
- Linking to an existing web account (D02: not built).
- Resolving a user by email.

## Notes
