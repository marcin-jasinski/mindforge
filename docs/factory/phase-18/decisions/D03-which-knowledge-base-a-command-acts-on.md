---
id: D03
question: Q03
status: decided
method: majority
class: adr
votes: { pragmatist: A, architect: A, skeptic: E }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q03.r1.tally.md]
adr: docs/adr/0030-discord-commands-name-the-knowledge-base-and-provisioning-creates-a-default-one.md
---
## Decision
Use A: `/ask`, `/quiz start` and `/upload` each take a required `kb` string option with Discord autocomplete over the
caller's own knowledge bases (name shown, `kbId` as value). The handler parses the value as a UUID and calls
`KnowledgeBaseService.requireOwner(kbId, userId)` before any work. Provisioning a new bot user (D01) also creates one
knowledge base named after the platform ("Discord"), in the same transaction as the user and the identity link, and
only on the winning insert. There is no stored "current knowledge base", no `/kb` command and no migration.

## Why
- Same shape as every other surface: web passes `kbId` in the path, the CLI requires `--kb=<id>` (CliUsageRunner.java:17-18, ADR 0019). No new concept or state.
- Names are not unique per owner (V1__baseline.sql, no unique index on `knowledge_bases.name`), so the value must be the id.
- Discord does not enforce autocomplete choices, so the value is untrusted; `requireOwner` (KnowledgeBaseService.java:45-47) is the boundary, as web-security.md requires.
- The default knowledge base makes `/upload` work for a new user; `KnowledgeBaseService.create` / `KnowledgeBaseRepository.insert` already exist.
- B needs a fifth command (scope.md:33-34 "exactly four commands") and a migration; C silently picks a tenant; D breaks single-owner tenancy (ADR 0019).
- Majority 2/3 at mean confidence 0.775; the dissent (E) raised no hard objection to A.

## Rejected
- B: fifth command `/kb use` out of scope, migration, stale pointer after delete.
- C: silent wrong-knowledge-base writes for multi-KB users.
- D: members would act in the configured owner's knowledge base; no membership model exists.
- E (optional `kb`, implicit when exactly one): extra branch and error path; can be added later without a break (required -> optional is compatible).

## Dissent
Skeptic (E): with a required option, a first-contact user hits autocomplete before any command, so autocomplete must
provision or the user sees no choices. Risk to watch: the autocomplete handler must resolve-or-provision (after the
D04 allowlist) and stay fast; provisioning is one insert on first contact only and a plain read afterwards.

## Implementation notes
- Command options (JDA): `new OptionData(OptionType.STRING, "kb", "Knowledge base", true, true)` on `/ask`,
  `/quiz start`, `/upload`. `/quiz answer` takes no `kb`: it uses the `kbId` stored with the quiz session (Q05).
- Autocomplete (`CommandAutoCompleteInteractionEvent`, option `kb`, in the Discord adapter package): D04 allowlist
  first (rejected -> empty choices); then resolve-or-provision the caller (same service as commands); then
  `knowledgeBases.list(userId)`, filter by case-insensitive contains on the typed text, max 25, `Command.Choice(name
  truncated to 100 chars, kbId.toString())`.
- Command handler, JDA-free helper e.g. `UUID resolveKnowledgeBase(String kbOption, UUID userId)`: `UUID.fromString`
  (IllegalArgumentException -> fixed ephemeral reply "Unknown knowledge base.", no service call), then
  `requireOwner`; `NotFoundException` and `NotOwnerException` map to the same fixed reply (no existence oracle, no echo
  of the value).
- Default knowledge base: in the identity-resolution application service (D01; name per Q13), provisioning branch only.
  Run `createUserAndLink(...)` and `knowledgeBases.insert(new KnowledgeBase(UUID.randomUUID(), userId,
  DEFAULT_KNOWLEDGE_BASE_NAME, null, null, 0, 0))` in one transaction via the injected `TransactionOperations`
  (precedent: IngestPipeline.java:105, RunWorker.java:45). On a unique violation the transaction rolls back and the
  resolver re-reads `findUserId` outside it; the loser creates no knowledge base. The name comes from the platform
  (`"Discord"` for Phase 18; parameter, not hard-coded in the port, so Phase 19 passes `"Slack"`).
- A user with zero knowledge bases (cannot happen through Discord; only if a row was removed by hand): autocomplete
  shows no choices and any typed value fails `requireOwner`. Do not add a backstop create.
- Reminder job (W10/Q10): there is no stored selection; which knowledge bases it counts is Q10's call.
- Tests: first contact creates exactly one user and exactly one knowledge base "Discord" owned by it; a second contact
  creates none; two concurrent first contacts -> one user, one knowledge base (Testcontainers); another user's kbId ->
  fixed reply through the real `requireOwner`, no downstream service call; a non-UUID value -> fixed reply, no service
  call; autocomplete lists only the caller's knowledge bases, filters by text, caps at 25; autocomplete from a
  non-allowed guild returns no choices and provisions nothing.
