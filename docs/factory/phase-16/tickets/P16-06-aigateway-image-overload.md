---
id: P16-06
title: Add the AIGateway image overload, routed to the VISION model, through the adapter, decorators and stub
status: done
plan_tasks: [16.2]
decisions: [D03]
depends_on: [P16-01]
size: M
issue: 16
commit:
fix_rounds: 0
---
## Goal
`AIGateway` can send one image with a prompt as one single-shot chat completion:
`complete(ModelTier tier, String prompt, ImageInput image, DeadlineProfile deadline)`. The adapter routes it to the
configured `VISION` model string (`mindforge.ai.model.vision`), rejects any other tier before calling the provider,
and every decorator (`PermitGateway`, `TracingGateway`) and the test double `StubAIGateway` carries it. This proves
the Phase 16 checklist item "`VISION` tier routes to the correct model string" for the call that carries an image.

## Context
- Port: `src/main/java/dev/mindforge/domain/port/AIGateway.java` — one method
  `CompletionResult complete(ModelTier tier, String prompt, DeadlineProfile deadline)`.
- P16-01 added `dev.mindforge.domain.model.ImageInput(byte[] data, String mimeType)` (self-validating; `data()`
  returns a copy; `toString` without bytes). Use it; do not add another image type.
- Implementors (all four must implement the new method; there are no lambda implementors):
  - `src/main/java/dev/mindforge/infrastructure/ai/AIGatewayAdapter.java`: `complete(...)` builds
    `new Prompt(prompt, OpenAiChatOptions.builder().model(model).build())`, `resolveModel(tier)` maps VISION to
    `modelRouting.getVision()`, then `callWithDeadline(request, deadline)` (deadline race around Retry +
    CircuitBreaker) and maps the `ChatResponse` to `CompletionResult` (tokens, resolved model, `"openrouter"`, latency,
    `costUsd(response)`).
  - `PermitGateway` (same package): acquires a permit from the shared background `Semaphore`, delegates, releases.
  - `TracingGateway` (same package): wraps the delegate call in a `mindforge.service` observation named after the
    model service and adds the call's cost to the current `ObservationRunMeter.Run`.
  - `src/test/java/dev/mindforge/support/StubAIGateway.java`: answers by prompt fragment (or per-tier default), records
    `Call(ModelTier tier, String prompt, DeadlineProfile deadline, String response)`.
- Existing test: `src/test/java/dev/mindforge/unit/infrastructure/ai/AIGatewayAdapterTest.java` (mocked `ChatModel`,
  `makeAdapter()`, `capturedRequestModel()`, `shouldRouteVisionTierToItsConfiguredModelString` at ~line 89).
  `RunCostTest` in the same folder builds `TracingGateway` around a `StubAIGateway`.
- ADR 0021 (`docs/adr/0021-aigateway-image-overload.md`, Accepted) records this overload. ADR 0013 single-shot rule
  holds. All LLM calls through `AIGateway`; request models by `ModelTier`, never by provider string (CLAUDE.md).
- Never mock `ChatClient`; unit tests load no Spring context; AssertJ.

## Decisions to apply
**D03 — Decision:** add an abstract overload `CompletionResult complete(ModelTier tier, String prompt, ImageInput
image, DeadlineProfile deadline)` to `AIGateway`, with a `record ImageInput(byte[] data, String mimeType)` in
`dev.mindforge.domain.model` that validates itself. `AIGatewayAdapter` and `StubAIGateway` reject an image sent with a
tier other than `ModelTier.VISION` (`IllegalArgumentException`) before any provider call. `ImageInput` is the only
domain image value.

**D03 — Implementation notes:**
- `AIGateway`: add the abstract overload above. No `default`.
- `AIGatewayAdapter`: `if (tier != ModelTier.VISION) throw new IllegalArgumentException("An image needs the VISION
  tier");` before building the request. Build `UserMessage.builder().text(prompt).media(new
  Media(MimeTypeUtils.parseMimeType(image.mimeType()), new ByteArrayResource(image.data()))).build()` with the same
  options/model routing as the text method. Extract a private shared path (e.g. `complete(Prompt request, String
  model, DeadlineProfile deadline)`) so both methods use the same `callWithDeadline` and result mapping.
- `PermitGateway`, `TracingGateway`: new overload with the same permit / observation and cost logic, delegating to the
  image overload; factor the body into a private helper so the two methods cannot drift.
- `StubAIGateway`: implement the overload with the same VISION check; answers match on prompt fragment as today;
  `Call` gains a nullable `ImageInput image` (text calls record `null`); keep existing call sites compiling (add a
  field and update the one constructor use, or keep a convenience constructor).
- Tests (`AIGatewayAdapterTest`, mocked `ChatModel` + `ArgumentCaptor<Prompt>`):
  `shouldRouteVisionTierToItsConfiguredModelStringForImageCall` (model string = vision route; the `UserMessage`
  carries exactly one `Media` with the MIME type); `shouldRejectImageOnNonVisionTier` (throws, `ChatModel` never
  called). Decorator tests: image call takes a permit; image call records span/cost under the service name.
  `StubAIGateway` rejects non-VISION image calls.
- Update `docs/standards/backend/ai_agents.md` to show the image overload next to the text example (section "LLM
  Gateway", ~line 51-57).
- Dissent risk: keep `ImageInput` to two fields.

## Acceptance criteria
- [x] AC1: `AIGateway` declares the abstract image overload (no `default`); the project compiles with all four
  implementors overriding it.
- [x] AC2: `AIGatewayAdapter` image call with `VISION` sends a `Prompt` whose options model equals the configured vision
  model string and whose single `UserMessage` holds the prompt text and exactly one `Media` with the image's MIME type
  and bytes.
- [x] AC3: `AIGatewayAdapter` image call with `SMALL` or `LARGE` throws `IllegalArgumentException` and `ChatModel.call`
  is never invoked.
- [x] AC4: The image call uses the same deadline race, Retry, CircuitBreaker and `CompletionResult` mapping as the text
  call (one shared private path; the text call's behaviour and tests are unchanged).
- [x] AC5: `PermitGateway` takes and releases a permit around the image call (a test with a 1-permit semaphore or a
  permit count check).
- [x] AC6: `TracingGateway` records the image call's cost under its service name on the current run meter, the same
  as a text call.
- [x] AC7: `StubAIGateway` answers image calls by prompt fragment, records the `ImageInput` on `Call` (null for text
  calls), and throws `IllegalArgumentException` for a non-VISION image call; all existing tests compile and pass.
- [x] AC8: `docs/standards/backend/ai_agents.md` shows the image overload example.

## Test plan
- Unit (mocked `ChatModel`, no Spring): `src/test/java/dev/mindforge/unit/infrastructure/ai/AIGatewayAdapterTest.java`
  — add `shouldRouteVisionTierToItsConfiguredModelStringForImageCall` and `shouldRejectImageOnNonVisionTier` (AC2–AC4).
- Unit: `src/test/java/dev/mindforge/unit/infrastructure/ai/PermitGatewayTest.java` (new) for AC5;
  extend `RunCostTest` or add `TracingGatewayTest.java` (new) in the same folder for AC6, wrapping `StubAIGateway`.
- Unit: a small `StubAIGatewayTest` is optional; AC7's VISION check may be proved in the decorator tests through the
  stub. Use `make*` factories (e.g. `makeImage()` returning a small PNG `ImageInput`).

## Files likely touched
- `src/main/java/dev/mindforge/domain/port/AIGateway.java`
- `src/main/java/dev/mindforge/infrastructure/ai/AIGatewayAdapter.java`
- `src/main/java/dev/mindforge/infrastructure/ai/PermitGateway.java`
- `src/main/java/dev/mindforge/infrastructure/ai/TracingGateway.java`
- `src/test/java/dev/mindforge/support/StubAIGateway.java`
- `src/test/java/dev/mindforge/unit/infrastructure/ai/AIGatewayAdapterTest.java`
- `src/test/java/dev/mindforge/unit/infrastructure/ai/PermitGatewayTest.java` (new)
- `src/test/java/dev/mindforge/unit/infrastructure/ai/TracingGatewayTest.java` (new) or `RunCostTest.java`
- `docs/standards/backend/ai_agents.md`

## Out of scope
- `ImageDescriber` and its prompt (P16-07); any pipeline change (P16-08).
- A list of attachments, image URLs, detail-level options, a tier-less method, or a `default` method (D03 rejected).
- Changing model routing properties or adding settings.

## Notes
- Decided: `StubAIGateway.Call` gains `ImageInput image` as its third component (no convenience constructor) because the stub is the only constructor call site.
- Decided: the stub's shared private path is named `respond(...)`, not `answer(...)`, because `answer(...)` already names the public fragment-registration methods.
- Decided: AC7's VISION check and image recording are proved through `PermitGatewayTest` (stub as delegate), with no separate `StubAIGatewayTest`, as the test plan allows.
- Tester r1 AC map: AC1 compile (gate) | AC2 AIGatewayAdapterTest.shouldRouteVisionTierToItsConfiguredModelStringForImageCall | AC3 AIGatewayAdapterTest.shouldRejectImageOnNonVisionTier (SMALL, LARGE) | AC4 AIGatewayAdapterTest.shouldRetryTransientFailureOfAnImageCallThroughTheSharedPath | AC5 PermitGatewayTest.shouldTakeAndReleaseAPermitAroundAnImageCall | AC6 TracingGatewayTest.shouldRecordAnImageCallsSpanAndCostUnderTheServiceName | AC7 PermitGatewayTest (stub image recorded, null for text, non-VISION rejected) | AC8 docs/standards/backend/ai_agents.md diff
