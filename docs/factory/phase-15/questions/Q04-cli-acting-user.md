---
id: Q04
status: decided
decision: D04
impact: high
blocks: [W03, W05]
---
## Question
As which user does the CLI act — for `DocumentUpload.uploadedBy`, the ownership check, and the quiz session's
`userId` and recorded quiz scores — and must the CLI authenticate that user?

## Context
- Plan 15.1 gives only `--file <path> --kb <id>`; 15.2 gives no arguments.
- `documents.uploaded_by` is a NOT NULL FK to `users` (`V1__baseline.sql`). Quiz sessions and
  `study.recordQuizScore` are per user; `QuizService.owned` rejects another user's session.
- `KnowledgeBaseService.requireOwner(kbId, userId)` is the one ownership check; `KnowledgeBase.ownerId` exists.
- `AccountService.authenticate(email, password)` exists; `UserRepository.findByEmail` exists. OAuth-only users have no
  password.
- Whoever runs the jar already holds the database credentials (`DATABASE_*`), so a password adds little real
  protection, but it decides whether OAuth-only users can use the CLI.
- The per-user rate limit (`RateLimitInterceptor`) lives in the API adapter and would not apply.

## Known options
- A: Act as the knowledge base's owner (`KnowledgeBase.ownerId`); no user argument. Matches the plan's argument list.
- B: `--user <email>`; look the user up, then `requireOwner(kbId, user)`. No password (DB access is the trust boundary).
- C: `--user <email>` plus a password prompt through `AccountService.authenticate`; OAuth-only users cannot use it.

## Constraints
- Every access verifies resource ownership (CLAUDE.md Security; docs/standards/security/web-security.md — written for
  controllers, applied here by analogy).
- Every tenant-scoped port call takes `kbId` first (CLAUDE.md).
- Thin adapter: any new lookup belongs in an application service, not in the runner.
