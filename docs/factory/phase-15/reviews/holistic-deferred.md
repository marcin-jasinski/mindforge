# Holistic review — deferred findings

## Notes

### Deferred findings
- holistic/H1 minor 0.85 docs/INDEX.md:46: The phase added ADR-0019 and a CLI section to deployment.md, but docs/INDEX.md still describes the ADRs as "0001–0009 ... 0010–0018" and the deployment.md entry does not mention the CLI.
- holistic/H2 minor 0.8 src/main/java/dev/mindforge/cli/CliUsageRunner.java:20: The exit-code text differs between tickets: the usage text and the CliExit javadoc say code 1 means only "the ingest ran (or ran before) and failed", but the runners also return 1 for a timeout and for any unexpected error (also in the quiz command), as deployment.md documents.
- holistic/H3 minor 0.75 src/main/java/dev/mindforge/cli/QuizCommandRunner.java:153: The option(...) and parseUuid(...) helpers are copied verbatim between PipelineCommandRunner (P15-05) and QuizCommandRunner (P15-07), although P15-03 created CliCommand as the shared argument reader for the cli package.
