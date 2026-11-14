---
id: D10
question: Q10
status: decided
method: unanimous
class: local
votes: { pragmatist: A1+B1+C1+D1, architect: A1+B1+C1+D1, skeptic: A1+B1+C1+D1 }
vote_changes: {}
models: { pragmatist: sonnet, architect: sonnet, skeptic: sonnet, judge: opus }
tallies: [docs/factory/phase-18/ballots/Q10.r1.tally.md]
adr: 
---
## Decision
Use A1+B1+C1+D1: run the reminder on a cron `mindforge.discord.reminder-cron` (default `0 0 9 * * *`) in zone
`mindforge.discord.reminder-zone` (default `UTC`). For each user with a Discord identity link, list every knowledge base
the user owns, count due cards per knowledge base with `FlashcardService.due(kbId, new StudyScope.WholeKnowledgeBase())`,
and send one DM that lists only the knowledge bases with a non-zero count ("You have 12 flashcards due in *Biology*").
Send no DM when the total is zero. Send counts and knowledge base names only, never card fronts. There is no opt-out
command in this phase; a user who blocks the bot gets a failed DM. A failed DM (or a failed count) for one user is
logged at WARN with the user id and the exception class only, and the job continues with the next user.

## Why
- Unanimous, all three at 0.75-0.8 confidence, no hard objection against the bundle.
- C2 has no referent: D03 stores no current knowledge base. D2 needs a fifth command (scope.md:33-34) and a migration.
- `due()` generates nothing (FlashcardService.java:68-73), so the job makes no model call; `deck()` would refresh.
- Cron + zone follows the `${mindforge.*}` setting pattern (RunWorkerTriggers.java:39, QuizSessionCleanup.java:18) and gives a fixed daily slot; a restart can skip a day, which is harmless.
- Counts show nothing beyond the SPA and put no study text in Discord's DM history.

## Rejected
- A2: time of day drifts with each restart and fires again on start (double reminders).
- B2: card fronts leave MindForge into DM history with no requirement for it.
- C2: no stored knowledge base selection exists (D03).
- D2: fifth command and a migration, out of scope; can be added later without a break.

## Dissent
None. Risks to watch: a user who blocks the bot causes one WARN per day (accepted); a user with many knowledge bases
can exceed Discord's 2000-character DM limit, so cap the message.

## Implementation notes
- Application service (e.g. `application/discord/ReminderService.sendDueReminders()`, package per the run's earlier
  decisions on the platform port) depends on a DM-sender port, e.g. `ReminderSender.send(String externalUserId, String text)`
  — name it platform-neutral (Phase 19 reuses it). The JDA adapter implements it; unit tests use a fake.
- Source of users: the identity-link store (only linked Discord users); no new table, no migration.
- Scheduler class in infrastructure, `@Scheduled(cron = "${mindforge.discord.reminder-cron:0 0 9 * * *}", zone = "${mindforge.discord.reminder-zone:UTC}")`,
  active only when the Discord adapter is enabled; it only delegates. No `@Transactional` around the send loop.
- Message: one line per knowledge base with due > 0, ordered by name; cap at 2000 chars (truncate with "…and N more").
- Logging: `log.warn("Reminder DM failed for user {}: {}", userId, e.getClass().getSimpleName())` — never the token or the text.
- Document both settings in deployment.md and env example.
- Tests (plain Mockito + fake sender): two KBs with due cards -> one DM listing both; zero due -> no DM; KB with zero
  omitted; sender throws for user 1 -> user 2 still gets a DM; no `refresh`/model call (verify `deck`/`refresh` never
  invoked, StubAIGateway untouched); message contains no card front text; long list truncated to <= 2000 chars.
