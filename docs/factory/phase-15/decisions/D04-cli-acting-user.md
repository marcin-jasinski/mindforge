---
id: D04
question: Q04
status: decided
method: majority
class: adr
votes: { pragmatist: A, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-15/ballots/Q04.r1.tally.md]
adr: docs/adr/0019-cli-acts-as-a-named-user-without-password.md
---
## Decision
Use B: both CLI runners take a required `--user=<email>`. An application-service method resolves the email to a
`User` (normalised the same way as sign-in; unknown email gives `NotFoundException("User")`), then the runner calls
`KnowledgeBaseService.requireOwner(kbId, user.userId())` before any work. That user is `DocumentUpload.uploadedBy`,
the quiz session `userId` and the owner of the recorded quiz scores. No password: the database credentials are the
trust boundary.

## Why
- Ownership is a real check, not a tautology: with A, `requireOwner(kbId, kb.ownerId())` can never fail
  (KnowledgeBaseService.java:35-47), so a wrong `--kb` silently writes uploads and quiz scores to another user.
- B reuses the web path exactly: resolve a principal, `requireOwner`, pass `userId` on (scope.md:74-75).
- B stays correct if shared knowledge bases arrive (ADR 0018:33-34, roadmap.md:143); A would silently pick the owner.
- C adds no protection (the operator holds `DATABASE_*`), locks out OAuth-only users (AccountService.java:40-46,
  53-58), needs console secret handling, and updates last-login on every run.

## Rejected
- A: simplest and matches the plan's argument list, but turns the ownership check into a check that cannot fail.
- C: password prompt gives no real protection, blocks OAuth-only users and scripted runs.
- D variants (raw UUID, seeded system user, config user): users do not know UUIDs; a synthetic user owns no KB.

## Dissent
Pragmatist (A): with one owner per KB, `--kb` already fixes the user, so `--user` adds only an error path and a
deviation from plan 15.1's argument list. Risk to watch: operators find the extra flag tedious; do not add a default
that falls back to the KB owner, because that brings back A's silent-misattribution failure.

## Implementation notes
- Application: `AccountService.byEmail(String email): User` — `users.findByEmail(normalise(email))
  .orElseThrow(() -> new NotFoundException("User"))`. Do not call `recordLogin`. The runners never touch
  `UserRepository`.
- `mindforge-pipeline` (W03): `--file=<path> --kb=<id> --user=<email>`; `mindforge-quiz` (W05): `--kb=<id> --user=<email>`
  (plus Q09 scope flags). Order: parse args, `byEmail`, `requireOwner(kbId, userId)`, then work.
- Pipeline: `new DocumentUpload(kbId, user.userId(), UploadSource.CLI, ...)` (D06). Quiz: pass `user.userId()` to every
  `QuizService` call so `owned` and `recordQuizScore` use it.
- Missing `--user`, unknown email, unknown KB or `NotOwnerException`: one line to stderr, non-zero exit, no ingest and
  no quiz session. Message for KB failures: "knowledge base not found or not owned by <email>".
- Unit tests (Mockito, no Spring): unknown email -> non-zero exit, ingest never called; KB owned by another user ->
  non-zero exit, no ingest / no quiz session; mixed-case and padded email resolves; happy path passes the resolved id
  into `DocumentUpload` and `QuizService`. `byEmail` test: unknown email throws `NotFoundException`, `recordLogin`
  never called.
- Docs: `deployment.md` — the CLI trusts the operator (DB credentials are the boundary), takes `--user`, no password,
  and the per-user API rate limit does not apply. Record `--user` as an "as built" note on plan tasks 15.1/15.2.
