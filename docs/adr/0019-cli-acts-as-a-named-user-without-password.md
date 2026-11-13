# The CLI acts as a named user, checked for ownership, without a password

Status: Accepted (2026-11-13).

The command-line runners (`mindforge-pipeline`, `mindforge-quiz`) take a required `--user <email>`. An application
service resolves the email to a user, and the runner calls `KnowledgeBaseService.requireOwner(kbId, userId)` before
any work. That user uploads the document, owns the quiz session and receives the quiz scores. The CLI asks for no
password: whoever runs the jar already holds the database credentials, so the database is the trust boundary, and a
password would protect nothing that those credentials do not already expose.

The ownership check is kept because it catches operator error: a wrong `--kb` fails with `NotOwnerException`
instead of silently writing a document, or quiz scores, into another user's knowledge base.

## Considered Options

- **Act as the knowledge base's owner, no user argument**: matches the original plan's arguments, but the ownership
  check then compares the owner with itself and can never fail, and it would pick the wrong person once knowledge
  bases are shared.
- **`--user` plus a password prompt through `AccountService.authenticate`**: no real protection behind database
  credentials, locks out accounts that sign in only through an identity provider, needs console secret handling, and
  records a login on every run.
- **A fixed system or configured user**: owns no knowledge base, so it fails the ownership check or needs a bypass.

## Consequences

- Any person with the jar and the database credentials can act as any user. Deployments must treat those
  credentials as administrator access.
- The per-user rate limit lives in the API adapter and does not apply to the CLI; the operator pays model cost
  directly.
- Shared knowledge bases would replace `requireOwner` with a membership check here as in the web adapter; the CLI
  argument does not change.

Decided in [D04](../factory/phase-15/decisions/D04-cli-acting-user.md) by the dark-factory run `phase-15`.
