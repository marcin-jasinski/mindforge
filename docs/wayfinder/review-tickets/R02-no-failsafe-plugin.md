---
id: R02
title: Integration tests run under surefire, so the build needs Docker
type: chore
status: open
severity: high
assignee:
blocked_by: []
---

## Problem

`pom.xml` configures `maven-compiler-plugin`, `spring-boot-maven-plugin`,
`frontend-maven-plugin`, `maven-resources-plugin` and `jacoco-maven-plugin`
(`pom.xml:228-338`). **There is no `maven-failsafe-plugin`.**

So the 59 Testcontainers-backed tests under `src/test/java/dev/mindforge/integration/` are
picked up by surefire along with the 204 unit tests, and three things follow:

1. **`-DskipITs` is a silent no-op.** It looks like it works — the command is accepted and
   exits — but every integration test still runs. Verified: `mvn test -DskipITs` ran all 263.
2. **The whole build fails without a Docker daemon.** With Docker stopped, all 59 error with
   `IllegalStateException: Could not find a valid Docker environment`, cascading into
   `NoClassDefFoundError: Could not initialize class dev.mindforge.support.TestContainerBase`.
3. **There is no way to run the fast tests alone.** `docs/standards/testing/test-writing.md`
   documents the `unit/` / `integration/` / `e2e/` split precisely so unit tests can be fast
   and context-free — but the build offers no way to act on that split.

## Why it matters

A contributor without Docker running cannot get a green build, and what they see is 59
`NoClassDefFoundError`s rather than "start Docker". The fast feedback loop that the test-folder
convention was designed for does not exist.

## Fix

Bind `**/integration/**` and `**/e2e/**` to failsafe and leave `unit/` to surefire:

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-surefire-plugin</artifactId>
    <configuration>
        <excludes>
            <exclude>**/integration/**</exclude>
            <exclude>**/e2e/**</exclude>
        </excludes>
    </configuration>
</plugin>
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-failsafe-plugin</artifactId>
    <executions>
        <execution>
            <goals><goal>integration-test</goal><goal>verify</goal></goals>
            <configuration>
                <includes>
                    <include>**/integration/**/*Test.java</include>
                    <include>**/e2e/**/*Test.java</include>
                </includes>
            </configuration>
        </execution>
    </executions>
</plugin>
```

Note the jacoco consequence. Coverage is currently measured across both sets into one
`target/jacoco.exec`. Splitting the phases means either merging two exec files or pointing the
70% `BUNDLE` rule (`pom.xml:318-336`) at the merged report. **Do not let the coverage gate
silently start measuring unit tests only** — that would drop it far below 70%, when the real
combined number today is 91.1%.

Also state the Docker requirement for `mvn verify` in `docs/project/deployment.md` or the README.

## Acceptance criteria

- [ ] `mvn test` runs only `unit/` and passes with **no Docker daemon running**.
- [ ] `mvn verify` runs everything and still enforces the 70% gate against combined coverage.
- [ ] `-DskipITs` actually skips integration tests.
- [ ] The Docker requirement for `mvn verify` is documented.

## Resolution

<!-- filled on close -->
