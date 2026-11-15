---
id: P18-09
title: Send a daily DM to each Discord-linked user who has due Flashcards
status: done
plan_tasks: [18.3]
decisions: [D10]
depends_on: [P18-01, P18-04]
size: M
issue: 41
commit: 46a8ebb
fix_rounds: 0
---
## Goal
Once a day (cron, configurable), every user with a Discord identity link gets one DM that lists, per owned knowledge
base, how many Flashcards are due — only knowledge bases with a non-zero count, counts and names only. No DM when
nothing is due. A failed DM for one user does not stop the others. No model call. This meets the completion checklist
item "SR reminder DMs sent to users with due cards".

## Context
- After P18-01: `ExternalIdentityRepository.findByPlatform(ExternalPlatform)` -> `List<ExternalIdentity(platform,
  externalId, userId)>`.
- After P18-04: `dev.mindforge.discord.DiscordConfig` (built only with a token, `@Profile("!cli")`) with the JDA bean.
- `src/main/java/dev/mindforge/application/service/FlashcardService.java`: `due(UUID kbId, StudyScope scope)` (:69)
  returns due cards and generates nothing; `deck`/`refresh` generate (must not be called).
  `KnowledgeBaseService.list(ownerId)` lists the user's knowledge bases.
- Scheduling: `@EnableScheduling` on `infrastructure/config/PipelineConfig.java`; `@Scheduled` precedents
  `infrastructure/event/QuizSessionCleanup.java:18` and `RunWorkerTriggers.java:39` with `${…}` settings.
- Rules: application layer imports only domain, `org.springframework.transaction.*`, `org.slf4j.*`; no JDA outside
  `dev.mindforge.discord`; no `@Transactional` around the send loop; never log the token or the DM text; study data
  never enters a wiki page or export (the job writes neither). Names must be platform-neutral (Phase 19 reuses them).
- Settings go in `application.yml` under `mindforge.discord` with env overrides (documented in P18-10).

## Decisions to apply
**D10 — Reminder job details.** Decision: run the reminder on a cron `mindforge.discord.reminder-cron` (default
`0 0 9 * * *`) in zone `mindforge.discord.reminder-zone` (default `UTC`). For each user with a Discord identity link,
list every knowledge base the user owns, count due cards per knowledge base with
`FlashcardService.due(kbId, new StudyScope.WholeKnowledgeBase())`, and send one DM that lists only the knowledge bases
with a non-zero count ("You have 12 flashcards due in *Biology*"). Send no DM when the total is zero. Send counts and
knowledge base names only, never card fronts. There is no opt-out command in this phase; a user who blocks the bot gets
a failed DM. A failed DM (or a failed count) for one user is logged at WARN with the user id and the exception class
only, and the job continues with the next user.
Implementation notes:
- Application service (e.g. `application/service/ReminderService.sendDueReminders()`) depends on a DM-sender port, e.g.
  `ReminderSender.send(String externalUserId, String text)` in `dev.mindforge.domain.port` — platform-neutral. The JDA
  adapter implements it (e.g. `discord/JdaReminderSender`: retrieve user -> open private channel -> send, blocking
  `complete()`); unit tests use a fake.
- Source of users: the identity-link store (only linked Discord users); no new table, no migration.
- Scheduler class in infrastructure (e.g. `infrastructure/event/DueReminderTrigger`),
  `@Scheduled(cron = "${mindforge.discord.reminder-cron:0 0 9 * * *}", zone = "${mindforge.discord.reminder-zone:UTC}")`,
  active only when the Discord adapter is enabled (build it in `DiscordConfig`); it only delegates.
- Message: one line per knowledge base with due > 0, ordered by name; cap at 2000 chars (truncate with "…and N more").
- Logging: `log.warn("Reminder DM failed for user {}: {}", userId, e.getClass().getSimpleName())` — never the token or the text.
- Tests (plain Mockito + fake sender): two KBs with due cards -> one DM listing both; zero due -> no DM; KB with zero
  omitted; sender throws for user 1 -> user 2 still gets a DM; no `refresh`/`deck`/model call (verify never invoked,
  `StubAIGateway` untouched); message contains no card front text; long list truncated to <= 2000 chars.

## Acceptance criteria
- [x] AC1: `ReminderSender` port (domain, JDK only) and `ReminderService` (application) exist; the service lists
  `findByPlatform(DISCORD)`, each user's knowledge bases, and `due(kbId, new StudyScope.WholeKnowledgeBase())` per base.
- [x] AC2: A user with due cards in two knowledge bases gets exactly one DM with one line per base ("You have N
  flashcards due in *Name*"), ordered by name; bases with zero are omitted.
- [x] AC3: A user with zero due cards gets no DM.
- [x] AC4: When sending (or counting) fails for user 1, user 2 still gets a DM, and one WARN line with user 1's id and the
  exception class name is logged.
- [x] AC5: `FlashcardService.deck`/`refresh` and the AI gateway are never called; the DM contains no card front text.
- [x] AC6: A DM over 2000 chars is truncated to <= 2000 chars ending with "…and N more".
- [x] AC7: The trigger has `@Scheduled(cron = "${mindforge.discord.reminder-cron:0 0 9 * * *}", zone =
  "${mindforge.discord.reminder-zone:UTC}")`, exists only as a bean of `DiscordConfig` (no token or `cli` profile -> no
  trigger), and only delegates; `application.yml` has `reminder-cron` and `reminder-zone` with env overrides.
- [x] AC8: The JDA sender sends a DM to the given external user id with blocking calls and imports JDA only inside
  `dev.mindforge.discord`.

## Test plan
- Unit (plain Mockito, no Spring context, AssertJ, `make*` factories):
  `src/test/java/dev/mindforge/unit/application/ReminderServiceTest.java` (new) for AC1–AC6 with a recording fake
  `ReminderSender`; mock `FlashcardService`/`KnowledgeBaseService` (or real services over mocked ports, with
  `StubAIGateway` asserted untouched).
- Unit: `src/test/java/dev/mindforge/unit/infrastructure/event/DueReminderTriggerTest.java` (new) — delegates once; a
  reflection check of the `@Scheduled` attributes (AC7).
- Unit: `src/test/java/dev/mindforge/unit/discord/JdaReminderSenderTest.java` (new) with Mockito mocks of JDA (AC8), if
  feasible; otherwise review.

## Files likely touched
- `src/main/java/dev/mindforge/domain/port/ReminderSender.java` (new)
- `src/main/java/dev/mindforge/application/service/ReminderService.java` (new)
- `src/main/java/dev/mindforge/infrastructure/event/DueReminderTrigger.java` (new)
- `src/main/java/dev/mindforge/discord/JdaReminderSender.java` (new)
- `src/main/java/dev/mindforge/discord/DiscordConfig.java`
- `src/main/resources/application.yml`
- tests listed above (new)

## Out of scope
- An opt-out command or stored preference, a per-user time, card fronts in the DM.
- Any change to study tables or a migration.
- Reminders for users without a Discord link.

## Notes
- Decided: `ReminderService` is built inline inside the `DueReminderTrigger` bean in `DiscordConfig` (not a separate bean) because only the trigger uses it, and it must not exist without the bot token.
- Decided: knowledge bases are ordered by name case-insensitively, and the line always says "flashcards" (also for 1) because that is the exact text in D10/AC2.
- Decided: one identity whose list, count or send throws is caught as `RuntimeException`, and the job continues, because D10 treats a failed count and a failed DM the same way.
- Tester r1 AC map: AC1/AC2 ReminderServiceTest.aUserWithDueCardsInTwoKnowledgeBases...; AC3 ...aUserWithZeroDueCards_getsNoDm; AC4 ...aFailedSendForUser1... and aFailedCountForUser1...; AC5 ...neverGeneratesCards_andTheDmContainsNoCardFront; AC6 ...aDmOver2000Chars...; AC7 DueReminderTriggerTest (both) + DiscordConfigTest doesNotHaveBean(DueReminderTrigger); AC8 JdaReminderSenderTest.send_opensTheUsersPrivateChannel...
