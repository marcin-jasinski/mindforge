---
lens: holistic
ticket: phase-15
verdict: approve
---
- id: H1
  severity: minor
  confidence: 0.85
  location: docs/INDEX.md:46
  claim: The phase added ADR-0019 and a CLI section to deployment.md, but docs/INDEX.md still describes the ADRs as "0001–0009 ... 0010–0018" and the deployment.md entry does not mention the CLI.
  evidence: docs/INDEX.md:46 "0001–0009 cover the original stack; 0010–0018 record the wiki re-cut (...)" with no mention of 0019; docs/INDEX.md:29-30 lists deployment.md content as "Dockerfile, compose.yml ..., graceful shutdown and the one-live-instance ceiling" with no CLI. The factory config lists docs/INDEX.md in docs_to_update, and scope.md says to update INDEX.md "only if a doc is added": docs/adr/0019-cli-acts-as-a-named-user-without-password.md is a new doc. INDEX.md:26 also still says "Phase 14 ... is where work resumes", which stays true, so only the ADR and deployment lines are stale.
  fix: Add "0019 records the CLI trust model (named user, no password)" to the ADR paragraph, and add "the CLI commands (pipeline, quiz), their exit codes and trust model" to the deployment.md entry.

- id: H2
  severity: minor
  confidence: 0.8
  location: src/main/java/dev/mindforge/cli/CliUsageRunner.java:20
  claim: The exit-code text differs between tickets: the usage text and the CliExit javadoc say code 1 means only "the ingest ran (or ran before) and failed", but the runners also return 1 for a timeout and for any unexpected error (also in the quiz command), as deployment.md documents.
  evidence: PipelineCommandRunner.java:117 and :216 return RUN_FAILED for an unexpected RuntimeException and for a timeout; QuizCommandRunner.java:328 returns RUN_FAILED for an unexpected error in the quiz command, where no ingest exists. deployment.md (CLI section) says "1 | The ingest ran (or ran before) and failed, the wait timed out, or an unexpected error occurred". CliUsageRunner.USAGE line "1  the ingest ran (or ran before) and failed" and CliExit.java RUN_FAILED javadoc "The ingest ran (or ran before) and failed." do not include these cases. A script author who reads `--help`-style usage gets an incomplete contract.
  fix: Change the USAGE line and the CliExit.RUN_FAILED javadoc to the deployment.md wording ("the ingest failed, the wait timed out, or an unexpected error occurred").

- id: H3
  severity: minor
  confidence: 0.75
  location: src/main/java/dev/mindforge/cli/QuizCommandRunner.java:153
  claim: The option(...) and parseUuid(...) helpers are copied verbatim between PipelineCommandRunner (P15-05) and QuizCommandRunner (P15-07), although P15-03 created CliCommand as the shared argument reader for the cli package.
  evidence: PipelineCommandRunner.java:236-250 and QuizCommandRunner.java:153-167 (file-relative lines) are identical: same single-value/blank rule for options and the same UUID parse returning null. A later change to the option rule (for example accepting `--name value`, which deployment.md says is not supported) must be made in two places, or the two commands will parse options differently.
  fix: Move both helpers into CliCommand (for example CliCommand.option(args, name) and CliCommand.uuidOption(args, name)) and call them from both runners.
