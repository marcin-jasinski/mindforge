# Caffeine in-process cache instead of Redis

MindForge uses Caffeine as its application-level cache rather than Redis. Caffeine is
a high-performance, bounded in-memory cache that lives inside the JVM process. It is
declared as a Spring Boot auto-configured `CacheManager` via the
`spring-boot-starter-cache` + `com.github.ben-manes.caffeine:caffeine` dependency pair.
No external cache service is required.

## Considered Options

- **Redis** — rejected: Redis is a valuable choice when session state must survive
  process restarts or be shared across multiple instances. MindForge is deployed as a
  single-instance application on Railway; the cache holds computed aggregations and
  frequently-read query results that are cheap to rebuild from PostgreSQL on restart.
  Adding Redis would mean operating, monitoring, and paying for an additional managed
  service without a corresponding benefit at this scale.

- **EhCache** — rejected: more configuration overhead than Caffeine for no meaningful
  advantage at the use-case size; Caffeine is the Spring Boot recommended default for
  in-process caching and has first-class auto-configuration support.

## Consequences

- Cache entries are lost on process restart. This is acceptable; all durable state is
  in PostgreSQL, and caches are warm within seconds of the first requests.
- Cache is bounded by JVM heap; entry TTLs and maximum sizes must be configured to
  prevent unbounded memory growth. These are set per-cache in `application.yml`.
- If the application is scaled horizontally in the future, cache invalidation will
  require either a distributed cache (Redis) or a cache-aside pattern. The
  `CachePort` interface in the application layer allows the Caffeine adapter to be
  swapped for a Redis adapter without touching use-case logic.
- There is no Redis dependency in `compose.yml`; local development requires only
  PostgreSQL and Neo4j containers.
