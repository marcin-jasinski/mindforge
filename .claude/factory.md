---
# Dark factory config for MindForge. Plugin: D:\Dokumenty\Projekty\dark-factory

plan: docs/project/implementation-plan.md
unit_kind: phase
next_unit_hint: "first '## [ ] Phase N' heading in the plan; the roadmap's 'Next' line agrees"
read_first:
  - CLAUDE.md
  - docs/INDEX.md
  - CONTEXT.md
  - docs/project/architecture.md
  - docs/standards/global/minimal-implementation.md
docs_to_update:
  - docs/project/implementation-plan.md
  - docs/project/roadmap.md
  - docs/INDEX.md
  - docs/project/deployment.md
  - CLAUDE.md                      # only the "Project State" paragraph
adr_dir: docs/adr
run_dir: docs/factory

git:
  integration_branch: develop
  protected_branches: [master, main]
  phase_branch: "factory/{run_id}"
  push: true
  park_mode: branch
commit_format: "feat({scope}): {ticket} {title}"   # scope = phase15, phase16, ...
ignore_dirty: [".ai/"]

commands:
  fast: "mvn -q -o test -DskipFrontend=true -Djacoco.skip=true"
  gate: "mvn -B verify -DskipFrontend=true"     # unit + integration (Testcontainers, needs Docker) + coverage gate
  extra_gates:
    - when: ["frontend/**"]
      run: "cd frontend && npm test -- --no-watch && npm run build"

review:
  lenses: [correctness, standards, spec-security]
  blocker_min_confidence: 0.5
  major_min_confidence: 0.7

limits:
  max_fix_rounds: 3
  decisions_per_ticket: 2
  parallel_agents: 6

tracker:
  github: true
  label: factory
  labels: { ready: ready-for-agent, human: ready-for-human }

standards_map:
  "src/main/java/dev/mindforge/domain/**": [docs/standards/architecture/hexagonal.md, docs/standards/backend/java-conventions.md]
  "src/main/java/dev/mindforge/application/**": [docs/standards/architecture/hexagonal.md, docs/standards/backend/java-conventions.md, docs/standards/global/error-handling.md]
  "src/main/java/dev/mindforge/agent/**": [docs/standards/backend/ai_agents.md, docs/standards/architecture/hexagonal.md]
  "src/main/java/dev/mindforge/infrastructure/**": [docs/standards/backend/models.md, docs/standards/backend/queries.md, docs/standards/architecture/hexagonal.md]
  "src/main/java/dev/mindforge/api/**": [docs/standards/backend/api.md, docs/standards/security/web-security.md]
  "src/main/java/dev/mindforge/cli/**": [docs/standards/architecture/hexagonal.md, docs/standards/backend/java-conventions.md]
  "src/main/resources/db/migration/**": [docs/standards/backend/migrations.md]
  "src/test/**": [docs/standards/testing/test-writing.md]
  "frontend/**": [docs/standards/frontend/angular-patterns.md, docs/standards/frontend/accessibility.md, docs/standards/frontend/css.md]
---

# House rules for factory agents

- CLAUDE.md "Non-Negotiable Rules" are hard constraints. A violation is a review `blocker`
  and a hard objection in a panel ballot.
- Use the vocabulary of `CONTEXT.md` in code, tests, tickets and decisions.
- A decision that contradicts an accepted ADR in `docs/adr/` is a blocker for the human, not a judge call.
- The code review map (`docs/wayfinder/code-review-map.md`) lists things verified correct.
  Do not re-open them.
- Prompt files: bump the model service's `VERSION` only on a logic or prompt change.
- Reports to the user and `REPORT.md` use ASD-STE100 Simplified Technical English.
- New ADRs follow the format of the newest file in `docs/adr/`.
- Integration tests need Docker (Testcontainers). If Docker is not running, preflight fails — do not skip integration tests.
