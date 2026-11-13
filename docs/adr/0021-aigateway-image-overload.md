# AIGateway takes an image through one overload, on the VISION tier only

Status: Accepted (2026-11-13).

`AIGateway` gains one abstract overload, `complete(ModelTier tier, String prompt, ImageInput image, DeadlineProfile
deadline)`. `ImageInput(byte[] data, String mimeType)` is a JDK-only record in `dev.mindforge.domain.model` that
rejects empty data and a MIME type outside `image/*`, and copies its bytes. The adapter sends the image as one
`UserMessage` with `Media` through the same deadline race, Retry and CircuitBreaker as a text call. An image with a tier
other than `VISION` is a caller bug: the adapter and `StubAIGateway` throw `IllegalArgumentException` before any
provider call.

The call is still one single-shot chat completion. ADR 0013's single-shot rule holds; its note that `AIGateway` is
unchanged now reads "unchanged except this overload". There are still no embeddings (ADR 0016).

## Considered Options

- **Loose `byte[]` and `String` arguments**: no new type, but bad input is checked only inside the retry and breaker
  wrap, so one bad image is retried and counts against the shared circuit breaker.
- **One method with a list of attachments**: nobody sends more than one image per call; it changes the text path of
  every implementor.
- **A tier-less `describeImage` method**: removes the wrong-tier state, but breaks "request models by role" and the
  shape every other call uses.
- **A `default` method on the port**: a decorator that does not override it skips the permit or the span with no
  compile error.

## Consequences

- All four implementors (`AIGatewayAdapter`, `PermitGateway`, `TracingGateway`, `StubAIGateway`) implement the
  overload; the decorators keep the permit, span and cost entry.
- `ImageInput` is the only domain image value; an image carried in a document block reuses it.
- A second image per call, or an image URL, needs a new decision; the record stays two fields.

Decided in [D03](../factory/phase-16/decisions/D03-aigateway-image-call-shape.md) by the dark-factory run `phase-16`.
