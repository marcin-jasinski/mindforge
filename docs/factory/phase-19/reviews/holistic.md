---
lens: holistic
ticket: phase-19
verdict: approve
---
- id: H1
  severity: minor
  confidence: 0.8
  location: docs/adr/0033-slack-identity-keyed-by-home-team-and-user-id.md:6
  claim: ADR 0033 says an event's home team is `user.team_id`, falling back to the payload `team_id`, but the code and ADR 0034 take the event's `user_team` only, with no fallback.
  evidence: ADR 0033:6-7 "The home team is `user_team` in a slash-command payload or `user.team_id` in an event, and the payload `team_id` only when these are absent; it is the same team the Slack allowlist checks." SlackMessageListener.toMessage (src/main/java/dev/mindforge/slack/SlackMessageListener.java:56) sets callerTeamId = string(event, "user_team"), which is null when absent, with no `team_id` fallback. SlackMessage.java:7-8 and ADR 0034:6 say the same. SlackUploadHandler.java:106 builds the identity key from that value. In a `message` event `user` is a string id, so `user.team_id` does not exist. The ADR that records the identity key describes a source and a fallback that the code does not use.
  fix: In ADR 0033, change the event case to "the event's `user_team`; an event without it is rejected by the allowlist (ADR 0034)", and drop the `team_id` fallback for events.
- id: H2
  severity: minor
  confidence: 0.75
  location: docs/standards/security/web-security.md:75
  claim: The security standard still lists HTTP-client code exceptions and platform file downloads without the new Slack downloader `SlackFiles`. architecture.md and the guard test were updated, but this file was not.
  evidence: web-security.md:75 "HTTP-client code in `src/main` lives only in the article fetch adapter (`infrastructure.web`), except operator-configured endpoints allowed by exact path in the guard test (today the OAuth2 token client in `SecurityConfig`)". Line 77 describes only the Discord `/upload` download. OutboundHttpGuardTest.java:41-43 now allows `dev/mindforge/slack/SlackFiles.java`, and architecture.md was updated with the same list. CLAUDE.md tells readers to read web-security.md "before any auth/upload work", so it now gives an incomplete list of outbound-download paths.
  fix: In web-security.md:75, add `DiscordAttachments` and `SlackFiles` to the "today" list. After line 77, add one sentence: a Slack DM file is downloaded only in `SlackFiles`, over https from `files.slack.com` with the bot token, with no redirects, and at most `mindforge.upload.max-size` (ADR 0029, ADR 0035).
- id: H3
  severity: nit
  confidence: 0.9
  location: docs/project/architecture.md:80
  claim: The edited list of guard-test exceptions has a missing comma.
  evidence: "(today the OAuth2 token client in `SecurityConfig` the Discord attachment download in `DiscordAttachments` and the Slack file download in `SlackFiles`, ADR 0029)"
  fix: Add a comma after "`SecurityConfig`".
