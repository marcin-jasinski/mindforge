---
id: D11
question: Q11
status: decided
method: judge
class: local
votes: { pragmatist: A }
vote_changes: {}
models: { pragmatist: sonnet, judge: opus }
tallies: [docs/factory/phase-20/ballots/Q11.r1.tally.md]
adr: 
---
## Decision
Use A: record the trace inside `docs/standards/security/web-security.md`. Under each section, add one short
"Enforced by:" line that names the implementing class(es) and the test(s) that fail when the requirement breaks.
Use class and test names (with method name where one test method is the guard), not file paths. Where no test enforces
a requirement, name the class and write "no test" so the gap stays visible. Do not add a new doc and do not change
`docs/INDEX.md`.

## Why
- `.claude/factory.md:58` loads web-security.md for every `api/**` review, so the trace sits where later security
  work reads it; the plan "As built" note and REPORT.md are not read later (question context).
- The standard already names an enforcing test in prose (web-security.md:75, `OutboundHttpGuardTest`), so A follows
  an existing pattern and adds no new kind of content.
- Naming the test makes each line checkable; the test, not the doc, keeps the requirement true.
- B adds a second file plus an INDEX entry that can drift from the standard it traces.

## Rejected
- B: separate `docs/project/security-trace.md` duplicates the standard's structure in a second place that reviewers do not load.
- C: plan "As built" and REPORT.md are not read by later work, so the trace goes stale unseen.

## Dissent
No dissent (one ballot). Risk to watch: class renames make an "Enforced by" line stale; the reviewer of a rename in
`api/**` or `infrastructure/security/**` loads this standard and must update the line.

## Implementation notes
- File: `docs/standards/security/web-security.md`, sections: Server-Authoritative State, Password Hashing, JWT Token
  Storage, OAuth CSRF Protection, Untrusted Input Handling, API Authorization, CORS Configuration, plus any section
  Phase 20 adds (auth rate limit, client address, sign-in timing — ADRs 0038–0040).
- Format, one line at the end of each section: `Enforced by: \`ClassA\`, \`ClassB\`; tested by \`SomeTest\`, \`ApiFlowTest.assertNoSensitiveFields\`.`
- Verify every named class and test exists (grep `src/`) before writing it; no file paths, no dates, no phase numbers
  (commenting.md:10 — timeless text, no change log).
- Close checklist item 1 in the plan's "As built" note with one line that points to web-security.md.
