---
id: Q03
status: decided
decision: D03
impact: high
blocks: [W04, W05]
---
## Question
What does the `AIGateway` port method that sends an image to the VISION model look like?

## Context
- Plan 16.2: "For each IMAGE block, `AIGateway.complete(VISION, ...)`".
- Code: `AIGateway` has only `complete(ModelTier tier, String prompt, DeadlineProfile deadline)`
  (`src/main/java/dev/mindforge/domain/port/AIGateway.java`). Text only; no way to attach an image. Plan/code gap.
- Implementors to change: `AIGatewayAdapter` (Spring AI 2.0.0, OpenAI-compatible client → OpenRouter; a
  `UserMessage` with `Media` carries the image), `PermitGateway`, `TracingGateway`, `StubAIGateway` (test double; its
  `Call` record has no image and answers match on prompt fragment).
- ADR 0013: "Every call stays single-shot, so `AIGateway`, `DeadlineProfile`, `CostTier` and the Resilience4j
  configuration are unchanged." `ai_agents.md`: "`AIGateway` exposes chat completion only". A multimodal chat
  completion is still one single-shot chat completion; it extends the port, it does not add a loop or embeddings.
- The type of the image argument lives in the domain (port signature), so it is JDK-only.

## Known options
- A: Overload: `complete(ModelTier tier, String prompt, byte[] image, String mimeType, DeadlineProfile deadline)`.
- B: Overload with a domain record, e.g. `complete(ModelTier tier, String prompt, ImageInput image, DeadlineProfile deadline)`
  where `ImageInput(byte[] data, String mimeType)` lives in `dev.mindforge.domain.model`.
- C: One method with an optional list of attachments (`List<ImageInput>`), the old method delegating with an empty
  list. More general; more change to every implementor; nobody needs several images per call today.
- For each option: whether the adapter rejects a non-VISION tier with an image (code enforces the tier) or trusts the
  caller.

## Constraints
- All LLM calls through `AIGateway`; by `ModelTier`; single-shot (`CLAUDE.md`; ADR 0013; `ai_agents.md`).
- Deadline race, Retry and CircuitBreaker apply to the image call the same way (`AIGatewayAdapter`).
- `TracingGateway` must still name the span and cost entry after the service; `PermitGateway` must still take a permit.
- Tests use `StubAIGateway`, never a `ChatClient` mock (`docs/standards/testing/test-writing.md`); the adapter test may
  mock `ChatModel` as it does today.
- Minimal implementation: no speculative generality (`docs/standards/global/minimal-implementation.md`).
