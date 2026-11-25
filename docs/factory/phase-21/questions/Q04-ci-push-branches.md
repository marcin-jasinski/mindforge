---
id: Q04
status: answered
impact: low
blocks: [W07]
answered_by: .claude/factory.md#git (integration_branch develop, protected_branches) and .github/workflows/ci.yml#on
---
## Question
On which pushes does CI run, given that the plan names a branch this repository does not use?

## Context
Plan 21.1 says "Push to `main`, PRs". The integration branch is `develop` and both protected branch names are listed in
`.claude/factory.md` `git.protected_branches`. The existing `.github/workflows/ci.yml` already has `on: push:` (every
branch) and `pull_request:`, which is a superset of the plan's intent.

## Known options
- A: Keep `push` on every branch plus `pull_request` (current state; factory phase branches get CI too).
- B: Restrict `push` to `develop` and the default branch.

## Constraints
The plan's intent is "every change to the integration line and every PR is checked". Option A meets it with no change;
keep it. Record in the plan's "As built" note that the branch name in 21.1 maps to this repo's branches.
