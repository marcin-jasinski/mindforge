---
id: D03
question: Q03
status: decided
method: majority
class: adr
votes: { pragmatist: A, architect: B, skeptic: B }
vote_changes: {}
models: { pragmatist: opus, architect: opus, skeptic: opus, judge: opus }
tallies: [docs/factory/phase-16/ballots/Q03.r1.tally.md]
adr: docs/adr/0021-aigateway-image-overload.md
---
## Decision
Use B: add an abstract overload `CompletionResult complete(ModelTier tier, String prompt, ImageInput image, DeadlineProfile deadline)` to `AIGateway`, with a new `record ImageInput(byte[] data, String mimeType)` in `dev.mindforge.domain.model` that validates itself. `AIGatewayAdapter` and `StubAIGateway` reject an image sent with a tier other than `ModelTier.VISION` (`IllegalArgumentException`) before any provider call. `ImageInput` is the only domain image value: if Q02 needs an image type on `ContentBlock`, it reuses `ImageInput` and does not add a second type.

## Why
- Two of three ballots, mean confidence 0.75; the dissent named B as its second choice and raised no objection to it.
- The adapter wraps the provider call in Retry + CircuitBreaker inside the deadline race (AIGatewayAdapter.java:133-183). Bad loose arguments (null/empty bytes, non-image MIME) would fail inside that wrap, be retried and count as breaker failures for every KB. A self-validating record fails once, before the race.
- The bytes and MIME type always travel together: a value object (CLAUDE.md "Use `record` for value objects"). It keeps the port at four arguments, parallel to the text method.
- Code enforces the tier (CLAUDE.md "Every rule a prompt teaches is enforced in code"); it costs one line and one test.
- Abstract, not `default`: the compiler finds all four implementors, so `PermitGateway` takes its permit and `TracingGateway` names its span and cost entry.
- Still one single-shot chat completion: ADR 0013's single-shot rule and ai_agents.md:64 "chat completion only" hold. ADR 0021 records the port extension.

## Rejected
- A: two loose positional arguments, unvalidated until inside the retry/breaker wrap; a second image type would follow if Q02 needs one.
- C: list of attachments is speculative generality (minimal-implementation.md:15-16) and changes the text path of every implementor.
- D: tier-less method contradicts "Request models by role" and plan 16.2.
- E: `default` method lets a decorator skip the permit or span with no compile error.

## Dissent
Pragmatist: a record is one more domain type for one call site. Risk to watch: `ImageInput` grows options (detail level, URL source) that nobody uses; keep it to two fields.

## Implementation notes
- `src/main/java/dev/mindforge/domain/model/ImageInput.java`: `public record ImageInput(byte[] data, String mimeType)`. Compact constructor: `Objects.requireNonNull` both; reject `data.length == 0`; reject a `mimeType` that does not start with `image/` (`IllegalArgumentException`); store `data.clone()`. Accessor `data()` returns a clone. Override `equals`/`hashCode` with `Arrays.equals`/`Arrays.hashCode` (tests compare by content) and `toString` as `ImageInput[mimeType=..., bytes=<length>]` — never the bytes. JDK only.
- `AIGateway`: add the abstract overload above. No `default`.
- `AIGatewayAdapter`: `if (tier != ModelTier.VISION) throw new IllegalArgumentException("An image needs the VISION tier");` before building the request. Build `UserMessage.builder().text(prompt).media(new Media(MimeTypeUtils.parseMimeType(image.mimeType()), new ByteArrayResource(image.data()))).build()` with the same options/model routing as the text method. Extract a private shared path (e.g. `complete(Prompt request, String model, DeadlineProfile deadline)`) so both methods use the same `callWithDeadline` and result mapping.
- `PermitGateway`, `TracingGateway`: new overload with the same permit / observation and cost logic, delegating to the image overload; factor the body into a private helper so the two methods cannot drift.
- `StubAIGateway`: implement the overload with the same VISION check; answers match on prompt fragment as today; `Call` gains a nullable `ImageInput image` (text calls record `null`); keep existing call sites compiling (add a field and update the one constructor use, or keep a convenience constructor).
- Tests (`AIGatewayAdapterTest`, mocked `ChatModel` + `ArgumentCaptor<Prompt>`): `shouldRouteVisionTierToItsConfiguredModelStringForImageCall` (model string = vision route; the `UserMessage` carries exactly one `Media` with the MIME type); `shouldRejectImageOnNonVisionTier` (throws, `ChatModel` never called). `ImageInputTest`: rejects null/empty data, non-`image/` MIME; defensive copy; `toString` has no bytes. Decorator tests: image call takes a permit; image call records span/cost under the service name. `StubAIGateway` rejects non-VISION image calls.
- Update `docs/standards/backend/ai_agents.md` to show the image overload next to the text example.
