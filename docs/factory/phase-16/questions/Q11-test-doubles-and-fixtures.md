---
id: Q11
status: answered
impact: low
blocks: [W04, W02, W03, W05]
answered_by: docs/standards/testing/test-writing.md; docs/project/implementation-plan.md#Phase 4 (4.6)
---
## Question
How are the image path and the parsers tested without real models or stored binaries?

## Context
`StubAIGateway` is the only LLM test double and has no image call; PDF/DOCX fixtures are built in tests.

## Known options
- Settled: extend `StubAIGateway` for the new port method (record the tier and the image MIME type/size on `Call`,
  answer by prompt fragment as today); `ImageDescriberTest` uses `StubAIGateway` with VISION; parser tests build a PDF
  with `PDImageXObject`/`LosslessFactory` and a DOCX with `XWPFRun.addPicture` in the test; the end-to-end check is a
  Testcontainers integration test that uploads such a PDF and asserts the stubbed description reaches a page body
  through the stubbed Extract/Write answers. Adapter routing is tested in `AIGatewayAdapterTest` with a mocked
  `ChatModel`, as the existing VISION test does.

## Constraints
No Spring context in unit tests; never mock `ChatClient`; AssertJ; `make*` factories (`CLAUDE.md` Testing).
