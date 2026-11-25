---
lens: holistic
ticket: phase-21
verdict: approve
---
- id: H1
  severity: minor
  confidence: 0.85
  location: .github/workflows/ci.yml:24
  claim: The new comment on the linux job's `mvn -B verify` step says the step builds the frontend, but it does not, so the comment, the D14 rationale and the Phase 21 scope note ("passes in CI, which builds the SPA") disagree with the build.
  evidence: pom.xml:39 sets `<skipFrontend>true</skipFrontend>` by default, and pom.xml:385 skips frontend-maven-plugin when it is true. No profile and no `-DskipFrontend=false` in ci.yml switch it off. So `mvn -B verify` in the linux job never runs `npm run build`, and copy-frontend copies nothing. The real SPA bundle is built only in the e2e job, through the Dockerfile. The SPA routing test in that job passes only because of the P21-01 fixture `src/test/resources/static/index.html`. The new deployment.md CI table (line 35) correctly leaves out a frontend build, so the comment also disagrees with the deployment guide.
  fix: Remove "frontend build," from the ci.yml:24 comment, or add `-DskipFrontend=false` to the linux step if the JAR's SPA must be built there. The comment on the windows job (ci.yml:55) has the same claim, which came before this phase.
