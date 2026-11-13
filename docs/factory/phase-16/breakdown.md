# Phase 16 — Image Analysis: draft breakdown

Plan tasks: 16.1 (PDF/DOCX image extraction), 16.2 (`ImageDescriber`), 16.3 (unit tests), plus the completion
checklist (CL1: VISION routes to the configured model; CL2: an image's description appears in the pages) and phase
close-out docs. Tests ship with the item that adds the behaviour (16.3 is spread over W02–W06).

| Id | Intent | Plan tasks | Size | Depends on | Questions |
|---|---|---|---|---|---|
| W01 | `ContentBlock` can carry an image: bytes/MIME representation and an `image(...)` factory; JSONB round-trip of an IMAGE block in `DocumentRepositoryAdapter` test; existing rows still read | 16.1 | S | — | Q01, Q02 |
| W02 | `PdfParser` emits IMAGE blocks (filtered, encoded, positioned) after/among page text; `PdfParserTest` builds a PDF with an image in the test | 16.1, 16.3 | M | W01 | Q05, Q09, Q11 |
| W03 | `DocxParser` emits IMAGE blocks for pictures in paragraphs, in body order; `DocxParserTest` builds a DOCX with a picture | 16.1, 16.3 | M | W01 | Q05, Q09, Q11 |
| W04 | `AIGateway` gains a single-shot image completion; `AIGatewayAdapter` sends it as a Spring AI media message routed by tier; `PermitGateway`, `TracingGateway`, `StubAIGateway` carry it; `AIGatewayAdapterTest` proves VISION routes to `mindforge.ai.model.vision` for the image call | 16.2, CL1 | M | — | Q03, Q07, Q11 |
| W05 | `ImageDescriber` model service (`VERSION`, `TIER = VISION`, `BACKGROUND`), prompt `image_describer.en.md`, output checks in code; bean in `PipelineConfig` via `background(...)`; `ImageDescriberTest` with `StubAIGateway(VISION)`; added to `ModelServiceVersionTest` | 16.2, 16.3 | M | W01, W04 | Q04, Q05, Q06, Q07 |
| W06 | `IngestPipeline` describes IMAGE blocks first (before Preprocess drops them / before `RelevanceGuard`), replaces each in place with a TEXT block at the same position, records `step_versions` and any omission; progress step; integration test: an uploaded PDF with an image yields a page whose body carries the description (CL2) | 16.2, CL2 | M | W02, W05 | Q01, Q04 |
| W07 | Run-report label for the new step/failure name in `frontend/src/app/shared/labels.ts` (only if W06 adds a new progress or failure step name) | 16.2 | S | W06 | Q04 |
| W08 | Docs: ADR 0013 amendment (Describe step; gateway image call), `docs/project/architecture.md` ingest flow, `docs/project/deployment.md` (only if a setting is added), plan tasks and checklist `[x]`, roadmap, CLAUDE.md "Project State", `docs/INDEX.md` if a description changes | close-out | S | W01–W07 | Q08 |

Notes:
- If Q01 resolves to option B (describe at upload), W06 moves the call into `IngestionService` before its transaction,
  W01 keeps the bytes in memory only, and the integration test in W06 stays the same.
- W02 and W03 are independent and can run in parallel; W04 is independent of W01–W03.
