---
id: Q05
status: decided
decision: D05
impact: medium
blocks: [W02, W03, W05]
---
## Question
Which embedded images do the parsers emit, in what format, and what bounds apply (minimum size, per-image byte cap,
per-document image count, duplicates, opt-out)?

## Context
- Plan 16.1/16.2 give no filter or cap: "For each IMAGE block" one VISION call.
- Real PDFs and DOCX files carry logos, bullets, rules and repeated headers/footers — the same small image on every
  page. Each one costs a VISION call and a permit from the shared background pool (`PipelineConfig`).
- Formats: PDFBox `PDImageXObject` can be JPEG (DCT), JPX, JBIG2, CCITT or raw; DOCX pictures can be PNG, JPEG, GIF,
  BMP, TIFF, EMF, WMF, SVG. OpenAI-compatible vision models accept PNG, JPEG, WEBP and non-animated GIF.
  `javax.imageio` (JDK) can re-encode a `BufferedImage` to PNG; EMF/WMF/SVG have no JDK decoder.
- Uploads up to 50 MB (`mindforge.upload.max-size`). Large images can exceed provider limits.
- `ai_agents.md`: "Fail loudly, never truncate" — a cap that silently describes the first N images would break this
  rule unless the omission is counted on the run (or the run fails, like the claim cap).
- Phase 17 puts its feature behind a disabled-by-default flag (`ProcessingSettings.featureFlags`); Phase 16 does not
  say. The completion checklist expects descriptions in pages, so on by default is implied.

## Known options
- A: Emit every decodable raster image; re-encode to PNG unless already PNG/JPEG; skip non-raster formats (EMF/WMF/SVG)
  and images below a fixed minimum dimension (e.g. 64 px); describe each distinct image (by content hash) once per
  document; no count cap.
- B: As A, plus a per-document cap in `ProcessingSettings` (e.g. `maxImagesPerDocument`); over the cap the run fails
  loudly (like the claim cap) — or the excess is counted on the run as omitted.
- C: Emit everything the parser finds; no filter; rely on the model to say "decorative" (Q06).
- Any option: whether an `ProcessingSettings`/`mindforge.*` switch turns image description off (then
  `docs/project/deployment.md` documents it).

## Constraints
- No new dependency without need (`docs/standards/global/conventions.md`; `docs/project/tech-stack.md`).
- Fail loudly or count omissions (`ai_agents.md`).
- Parsers stay in `dev.mindforge.infrastructure.parsing`; `ParserRegistry` unchanged (`CLAUDE.md`).
