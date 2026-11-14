---
id: Q10
status: decided
decision: D10
impact: medium
blocks: [W10]
---
## Question
When does the daily SR reminder run, what does a DM contain, which knowledge bases count, and what happens on a
failed DM or a user who does not want reminders?

## Context
Plan 18.3: "`@Scheduled` daily: DM each Discord-linked user with due cards." Checklist: "SR reminder DMs sent to users
with due cards." `FlashcardService.due(kbId, StudyScope)` returns due cards without generating any (good: the job must
not spend model calls). Study state is per knowledge base. Users have no time zone stored. Discord refuses a DM when the
user blocks the bot or shares no guild with it. The job runs in the app process (single instance), and a restart near
the trigger time can skip or repeat a day. `@EnableScheduling` is on `PipelineConfig`; other jobs use `${…}` settings.

## Known options
- Schedule: A1 a cron setting `mindforge.discord.reminder-cron` (default e.g. `0 0 9 * * *`) with zone
  `mindforge.discord.reminder-zone` (default UTC); A2 fixed delay of 24 h from start.
- Content: B1 count only per knowledge base ("You have 12 flashcards due in *Biology*"); B2 count plus the first few
  card fronts. Card fronts are study data — allowed to the owner, but they appear in a DM history outside MindForge.
- Scope: C1 every knowledge base the user owns, one DM listing those with due cards; C2 only the knowledge base chosen
  in Q03.
- Opt-out: D1 none in this phase (the user can block the bot); D2 a `/reminders off` command with a stored flag
  (migration + column).
- Failure: a failed DM is logged at WARN with the user id (never the token or content) and the job continues.

## Constraints
- No model call in the job; no DM for zero due cards.
- No study data in a page or export (unchanged); DM content shows nothing beyond what the owner can see in the SPA.
- The DM sender is a seam the unit test can drive (checklist proof without a live token).
