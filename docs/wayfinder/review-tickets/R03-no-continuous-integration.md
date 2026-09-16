---
id: R03
title: There is no CI
type: chore
status: open
severity: high
assignee:
blocked_by: [R01, R02]
---

## Problem

The repository has **no continuous integration of any kind**. There is no `.github/` directory,
and no GitLab, Travis, Circle or Jenkins configuration anywhere:

```
$ ls -a .github                                    -> no such directory
$ ls -a | grep -iE "gitlab|travis|circle|jenkins"  -> nothing
```

Meanwhile `pom.xml:309-338` configures a jacoco `check` rule requiring **70% instruction
coverage** at BUNDLE level, bound to `verify`. Nothing ever runs it.

## Why it matters

This is how R01 survived. Thirteen phases and 42,390 lines landed with a suite that is red on a
default Windows checkout, and there was nothing to catch it. The test suite, the coverage gate
and the Docker-dependent build are all quality machinery that only runs when a human remembers
to run it locally.

## Fix

Add `.github/workflows/ci.yml`. Minimum viable:

- Trigger on `push` and `pull_request`.
- `ubuntu-latest` (Docker is present, so Testcontainers works with no extra setup).
- `actions/setup-java` with Temurin 21 and `cache: maven`.
- `mvn -B verify` — which, after R02, runs unit tests, integration tests and the coverage gate.
- Upload `target/site/jacoco/` as an artifact.

Two additions once that is green:

- **A `windows-latest` leg running `mvn -B verify`.** R01 was a Windows-only failure; without a
  Windows job the next one is invisible again. This is the single highest-value part of this
  ticket.
- The frontend build (`npm ci && npm run build` in `frontend/`), and its tests once R04 lands.

Keep it one file and boring. No matrix beyond the two operating systems, no release automation,
no caching cleverness.

## Acceptance criteria

- [ ] A push to `develop` runs the full suite and the coverage gate automatically.
- [ ] The workflow runs on both Linux and Windows.
- [ ] A failing test fails the workflow — verified once by deliberately breaking a test.
- [ ] The README carries a status badge.

## Resolution

<!-- filled on close -->
