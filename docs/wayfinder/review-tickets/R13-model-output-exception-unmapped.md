---
id: R13
title: ModelOutputException is unmapped and untested
type: bug
status: open
severity: high
assignee:
blocked_by: []
---

## Problem

Two defects on the same boundary — the point where a model's completion becomes structured data.
This is the single most failure-prone surface in the system, and it has neither correct error
handling nor a single test.

### 1. It returns 500 instead of 503

`ModelOutputException` extends `IllegalStateException`
(`src/main/java/dev/mindforge/domain/model/ModelOutputException.java:4`).

`GlobalExceptionHandler` maps its siblings to a clean 503:

```java
@ExceptionHandler({DeadlineExceededException.class, AIGatewayUnavailableException.class})
ResponseEntity<ErrorResponse> modelUnavailable(RuntimeException e) {   // :116-118
    return error(HttpStatus.SERVICE_UNAVAILABLE, "MODEL_UNAVAILABLE", e);
}
```

`ModelOutputException` is **not in that list**, so it falls through to
`unexpected(Exception e)` (`:121-128`) -> `log.error("Unhandled exception", e)` -> **500
"Something went wrong"**.

Every interactive Query or Quiz call where the model returns unparseable output surfaces to the
user as a server error, and to the operator as an `ERROR`-level "Unhandled exception" — when it
is neither unhandled nor the server's fault. It is the *expected* behaviour of a flaky model, and
its siblings already model that correctly.

### 2. It has no test

- `grep -rn "ModelOutputException" src/test` -> **zero hits**, across all 57 test files.
- `src/main/java/dev/mindforge/infrastructure/ai/ModelJson.java:20-29` — the single choke point
  every one of the 11 model services funnels through — has **no unit test at all**.

The 6 production sites that raise it are all untested for this path.

## Failure scenario

`ModelJson.read` extracts a JSON object with `indexOf('{')` and `lastIndexOf('}')`. Given a
completion like:

```
Oto wynik: {"description":"a","body":"b"} oraz {"note":"ignore"}
```

the span runs from the first `{` to the *last* `}`, swallowing both objects, and parsing fails.
The user sees a 500.

This is not hypothetical — it is precisely the mechanism that made R01 fail with a confusing
message, where a placeholder string reached `ModelJson.read` and threw `no JSON object`.

## Fix

### The one-line part

Add `ModelOutputException.class` to the `modelUnavailable` handler at
`GlobalExceptionHandler.java:116`. A model returning garbage is a model availability problem:
503 + `MODEL_UNAVAILABLE` is the honest code, and it stops polluting `ERROR` logs.

Check the retry semantics while there — a 503 invites a client retry, which is right for this
case, and `Retry-After` may be worth setting.

### The test part

Add `src/test/java/dev/mindforge/unit/infrastructure/ai/ModelJsonTest.java` covering at minimum:

- a clean JSON object
- JSON wrapped in prose before and after
- JSON in a fenced code block
- **two objects in one completion** (the case above)
- no JSON at all — asserting the message names the service, as `ModelOutputException`'s
  constructor promises
- valid JSON missing a required field (e.g. `PageWriter`'s `description`/`body` check at
  `PageWriter.java:61-64`)

Then one integration test proving a `ModelOutputException` from a Query turn returns **503**,
not 500.

## Acceptance criteria

- [ ] `ModelOutputException` maps to 503 `MODEL_UNAVAILABLE`.
- [ ] It no longer logs at `ERROR` as "Unhandled exception".
- [ ] `ModelJsonTest` exists and covers the six cases above.
- [ ] An integration test asserts the 503 end to end.
- [ ] Consider whether `ModelJson`'s first-brace/last-brace extraction should be tightened; if it
      is left as is, say why here.

## Resolution

<!-- filled on close -->
