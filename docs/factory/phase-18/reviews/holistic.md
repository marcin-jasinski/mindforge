---
lens: holistic
ticket: phase-18
verdict: changes
---
- id: H1
  severity: major
  confidence: 0.8
  location: docs/project/deployment.md:106
  claim: deployment.md ("The history is in the web app") and ADR 0032 § Consequences ("the answer stays in the web Query history") promise a Discord user a web history that the Phase 18 account model makes unreachable for that user.
  evidence: ADR 0027 (P18-02/P18-03) provisions a Discord user as a separate account with `password_hash` null and the email `discord-<id>@users.invalid`. `AccountService.register` and `signInWithProvider` refuse that domain (`unreserved(...)`, AccountService.java). `link(...)` was not built (D02, plan 18.1 "As built", issue #43), and deployment.md:121-125 says "This account cannot see the knowledge bases of a web account, and there is no link between the two." So the Query interactions that `AskCommand` creates through `QueryService.start` belong to an account that cannot sign in to the web. The ephemeral replies (ADR 0032, P18-05) are the only copy the Discord user sees. The ADR uses the web-history claim to justify that losing the channel history "costs little" (D14 § Why). Two tickets (P18-05/P18-10 and P18-02/P18-03) are in conflict here, and the operator docs state something that is not true.
  fix: In deployment.md:106-107, replace "The history is in the web app" with text that says the Discord account has no web sign-in, so the ephemeral reply is the only copy until account linking (#43) exists. Update ADR 0032 § Consequences the same way, and note that the D14 trade-off depends on #43.
- id: H2
  severity: minor
  confidence: 0.8
  location: docs/project/architecture.md:80
  claim: The `web/` bullet still says the article fetch adapter is the only place in `src/main` with HTTP-client code apart from "today the OAuth2 token client in `SecurityConfig`", but P18-08 added `DiscordAttachments.java` to `OPERATOR_ENDPOINT_FILES` in `OutboundHttpGuardTest`.
  evidence: OutboundHttpGuardTest now allows `dev/mindforge/discord/DiscordAttachments.java` by exact path, and it adds the markers `okhttp3`, `.getProxy()` and `.download(`. web-security.md got a new paragraph about the Discord download, but architecture.md:80 still names only SecurityConfig as the exception. Its Discord security bullet (around line 335) does not say that the guard test allows the file.
  fix: Change architecture.md:80 to "(today the OAuth2 token client in `SecurityConfig` and the Discord attachment download in `DiscordAttachments`, ADR 0029)".
- id: H3
  severity: nit
  confidence: 0.6
  location: docs/INDEX.md:44
  claim: The Phase 18 ADRs are numbered 0027–0030 and 0032, and no file 0031 exists. INDEX.md says "0027–0030 and 0032" but gives no reason, so a reader looks for a missing ADR.
  evidence: `ls docs/adr` shows 0030 followed by 0032. `git log --all -- 'docs/adr/0031*'` is empty, and no file under docs/factory/phase-18 mentions 0031.
  fix: Rename 0032 to 0031 and update the links to it (INDEX.md, deployment.md, D14 `adr:`). Or, if the number is reserved, say so in INDEX.md.
