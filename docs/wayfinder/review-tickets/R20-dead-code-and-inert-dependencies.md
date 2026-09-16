---
id: R20
title: One dead enum and two inert dependencies
type: chore
status: open
severity: low
assignee:
blocked_by: []
---

## Problem

Three items that are carried but never used. Each was grep-confirmed across both `src/` and
`frontend/src/`, twice.

### 1. `CostTier` is dead

`src/main/java/dev/mindforge/domain/model/CostTier.java`

```
$ grep -rn "CostTier\|costTier" src/ frontend/src/
src/main/java/dev/mindforge/domain/model/CostTier.java:3:public enum CostTier {
```

Its own declaration is the only hit. Ticket T04 listed `CostTier` among the types that "survive
untouched" through the wiki re-cut — but nothing ever came to use it, and Phase 14's cost work is
specified in terms of `CompletionResult.costUsd`, not a tier.

**Fix:** delete it. If Phase 14 wants a cost tier it can add one with a caller attached, per
`docs/standards/global/minimal-implementation.md` ("no future stubs").

### 2. `spring-boot-starter-oauth2-resource-server` is unused

`pom.xml:73`

```
$ grep -rn "oauth2ResourceServer\|JwtDecoder\|BearerTokenAuth" src/main/java
(no hits)
```

JWTs are handled by the hand-rolled `JwtFilter` plus jjwt. The resource-server starter pulls in
Spring Security's OAuth2 JOSE stack and is never configured.

**Fix:** remove the dependency and confirm the suite stays green. **Be careful to keep
`spring-boot-starter-oauth2-client`** if present — that one *is* used, for the Google and GitHub
registrations that `TestContainerBase` configures and that `web-security.md` requires for OAuth
CSRF handling. Only the *resource-server* starter is dead.

### 3. `spring-boot-starter-cache` and `spring.cache.type` are inert

`pom.xml:86` and `src/main/resources/application.yml:57`

```
$ grep -rn "EnableCaching\|@Cacheable\|@CacheEvict\|@CachePut\|CacheManager" src/main src/test
(no hits)
```

Spring's cache abstraction is never switched on, so `spring.cache.type: caffeine` configures a
`CacheManager` nothing consumes. The only real cache in the system is built directly:
`QuizSessionStoreAdapter.java:33` constructs Caffeine itself.

**Fix:** remove the starter and the property. **Keep the raw `caffeine` dependency** — it is
genuinely used, and ADR 0007 (`caffeine-over-redis`) is about that, not about Spring's
abstraction.

## Why it matters

Low. No runtime cost beyond a slightly larger image and slower startup. It matters mainly because
`application.yml:57` implies a caching strategy that does not exist, which will mislead whoever
next tries to add caching — they will reasonably assume `@Cacheable` works.

## Verified clean — do not re-check

The Angular side has no dead code: all 11 page components are routed in `app.routes.ts`, and
`diff.ts`, `diff-view.ts`, `markdown.ts`, `page-links.directive.ts` and `progress.service.ts`
each have confirmed importers, checked per file.

## Acceptance criteria

- [ ] `CostTier.java` is deleted.
- [ ] `spring-boot-starter-oauth2-resource-server` is removed; OAuth2 **client** login still works
      and `ApiFlowTest` still passes.
- [ ] `spring-boot-starter-cache` and `spring.cache.type` are removed; the raw `caffeine`
      dependency stays and the quiz session cache is unaffected.
- [ ] `mvn verify` green; the built JAR still starts.

## Resolution

<!-- filled on close -->
