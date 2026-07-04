# Resilience4j Retry and CircuitBreaker in AIGatewayAdapter

`AIGatewayAdapter` gets a Resilience4j `Retry` and `CircuitBreaker`, applied programmatically
(`Decorators.ofSupplier(...)`, no `resilience4j-spring-boot3` starter, no annotations) around
both `complete()` and `embed()`. Spring AI's own built-in retry (`spring-ai-retry`, active by
default via `spring.ai.retry.*` since `spring-ai-starter-model-openai` pulls it in transitively)
is disabled (`spring.ai.retry.max-attempts=1`) so Resilience4j is the single retry authority —
otherwise Resilience4j's attempts would each re-trigger Spring AI's own internal retry loop,
multiplying tail latency unpredictably.

## Considered Options

- **`resilience4j-spring-boot3` + `@Retry`/`@CircuitBreaker` annotations** — rejected: requires
  AOP proxying and pulls in actuator/Micrometer wiring the project doesn't have yet; also
  `callWithDeadline()` is called from within `complete()` on the same object, so annotating the
  public method still works, but keeping the decoration inline is easier to unit-test without a
  Spring context (a hard project rule — see `docs/standards/testing/test-writing.md`).
- **Layering Resilience4j retry on top of Spring AI's existing retry** — rejected: both retry on
  the same `TransientAiException`, so failures would be retried by Spring AI, then that whole
  (already-retried) attempt retried again by Resilience4j.
- **Per-`ModelTier` `CircuitBreaker` instances** — rejected: all tiers call the same upstream
  OpenRouter endpoint, so failures are provider-wide, not model-specific. One shared instance.

## Consequences

- Retry only fires for `TransientAiException` and raw `IOException`/connection failures.
  `NonTransientAiException` (bad request, auth, unknown model) and `CallNotPermittedException`
  (circuit open) are never retried — retrying a guaranteed-repeat failure just burns the
  deadline budget.
- The Retry decorator wraps the call *inside* the existing virtual-thread deadline race
  (`callWithDeadline()`), not around it — all retry attempts for one logical call share a single
  `DeadlineProfile` timeout budget. An `INTERACTIVE` call still cannot exceed 10s wall-clock even
  if it retries twice internally.
- `DeadlineExceededException` counts as a recorded failure for the `CircuitBreaker`'s
  failure-rate window — a provider that keeps timing out is unhealthy, same as one returning 5xx.
- When the circuit is `OPEN`, the adapter catches `CallNotPermittedException` and rethrows a new
  `dev.mindforge.domain.model.AIGatewayUnavailableException` (same pattern as
  `DeadlineExceededException`) — callers in `application`/`domain` never see a resilience4j type,
  preserving the hexagonal boundary.
- `embed()` gets the same Retry/CircuitBreaker treatment but no new deadline timeout — it had
  none before this change, and adding one is a separate, pre-existing gap.
