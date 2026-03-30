"""
Pipeline orchestrator — sequential processing of a lesson file.

Steps:
 1.  Check if already processed (state guard)
 2.  Parse lesson file (frontmatter + links)
 3.  Extract image URLs (before cleaning removes them)
 4.  Analyze images with vision model (if enabled)
 5.  Clean content (remove video/images, inject image descriptions)
 6.  Create canonical LessonArtifact
 7.  Preprocess (remove story/task sections)
 8.  Fetch relevant articles
 9.  Summarize (large LLM) → structured SummaryData
10.  Generate flashcards (if enabled) → structured FlashcardData
11.  (removed — quiz generation replaced by quiz-agent)
12.  Generate concept map (if enabled) → structured ConceptMapData
13.  Write all outputs (JSON artifact + rendered files)
14.  Validate artifact quality + run evals (if enabled)
15.  Update knowledge index (if enabled)
15b. Index into Neo4j graph (if graph-RAG enabled)
16.  Archive source + mark processed
"""
from __future__ import annotations

import logging
import time
from pathlib import Path

from processor.llm_client import Config
from processor import state
from processor.tools import lesson_parser, file_ops
from processor.tools.article_fetcher import fetch_relevant_articles
from processor.agents.preprocessor import preprocess
from processor.agents.summarizer import summarize
from processor.models import LessonArtifact, ImageDescription, ArticleData
from processor.renderers import (
    render_summary_markdown,
    render_flashcards_tsv,
    render_concept_map_markdown,
    summary_as_context_text,
)
from processor import tracing

log = logging.getLogger(__name__)


def _flush_checkpoint(artifacts_dir: Path, artifact: LessonArtifact, *, completed_step: str) -> None:
    """Persist current artifact state after an expensive step (CRITICAL-3)."""
    try:
        file_ops.write_checkpoint(artifacts_dir, artifact, completed_step=completed_step)
    except Exception:
        log.warning("Checkpoint flush failed (step=%s) — continuing anyway", completed_step, exc_info=True)


def run(
    filepath: Path,
    config: Config,
    *,
    force: bool = False,
    keep_in_place: bool = False,
) -> bool:
    """Process a single lesson file. Returns True on success, False on skip/error.

    Args:
        force: Skip the already-processed state guard (used for backfill).
        keep_in_place: Don't move the file to archive after processing.
            Use when the file is already in archive/ (backfill mode).
    """
    filename = filepath.name
    start_time = time.time()

    log.info("=" * 60)
    log.info("Pipeline start: %s", filename)
    log.info("=" * 60)

    # Step 1: State guard (cross-process atomic claim prevents duplicate processing)
    if not force and not state.claim_for_processing(config.state_file, filename):
        log.info("Already processed or claimed by another process, skipping: %s", filename)
        from processor import metrics
        metrics.increment("pipeline_skipped")
        return False

    try:
        with tracing.trace(
            name="lesson-pipeline",
            input_data={"filename": filename},
            tags=["pipeline"],
        ):
            return _run_steps(filepath, filename, config, keep_in_place=keep_in_place, force=force)
    except Exception:
        elapsed = time.time() - start_time
        log.error("Pipeline failed for %s after %.1fs", filename, elapsed, exc_info=True)
        if not force:
            state.unclaim(config.state_file, filename)
        return False


def _run_steps(
    filepath: Path,
    filename: str,
    config: Config,
    *,
    keep_in_place: bool = False,
    force: bool = False,
) -> bool:
    """Execute all pipeline steps within the active trace context."""
    start_time = time.time()
    artifacts_dir = config.base_dir / "state" / "artifacts"

    # Step 2: Read and parse
    raw_content = file_ops.read_file(filepath)
    parsed = lesson_parser.parse_lesson_file(str(filepath), raw_content)
    log.info("Step 2 — Parsed: title=%s, links=%d", parsed.title, len(parsed.links))

    # Step 3: Extract image URLs (before cleaning removes them)
    images: list[dict[str, str]] = []
    if config.enable_image_analysis:
        images = lesson_parser.extract_images(parsed.content)
        log.info("Step 3 — Images extracted: %d", len(images))

    # Step 4: Analyze images with vision model
    image_descriptions_raw: list[dict[str, str]] = []
    if config.enable_image_analysis and images:
        from processor.agents.image_analyzer import analyze_images, format_image_descriptions
        image_descriptions_raw = analyze_images(images, config.llm, config.model_vision)
        log.info("Step 4 — Images analyzed: %d descriptions", len(image_descriptions_raw))

    # Step 5: Deterministic cleaning (remove video/images)
    cleaned = lesson_parser.clean_lesson(parsed)
    if image_descriptions_raw:
        from processor.agents.image_analyzer import format_image_descriptions
        desc_text = format_image_descriptions(image_descriptions_raw)
        cleaned.content += desc_text
    log.info("Step 5 — Cleaned: %d chars", len(cleaned.content))

    # Step 6: Create or load artifact from checkpoint (CRITICAL-3 fixed)
    # The checkpoint is identified by the *filename stem* — a key that is stable
    # across process restarts of the same source file.  A new UUID lesson_id is
    # only generated when no checkpoint exists.  force=True always starts fresh.
    filename_stem = Path(filename).stem
    loaded_from_checkpoint = False

    if not force:
        checkpoint_data = file_ops.load_checkpoint_by_filename(artifacts_dir, filename_stem)
        if checkpoint_data is not None:
            try:
                artifact = LessonArtifact.from_dict(checkpoint_data)
                loaded_from_checkpoint = True
                log.info(
                    "Step 6 — Loaded partial artifact from checkpoint: %s (id=%s)",
                    filename_stem,
                    artifact.lesson_id,
                )
            except Exception:
                log.warning("Step 6 — Checkpoint corrupt, starting fresh", exc_info=True)

    if not loaded_from_checkpoint:
        artifact = LessonArtifact.create(
            title=cleaned.title,
            source_filename=filename,
            metadata=cleaned.metadata,
            cleaned_content=cleaned.content,
        )
        artifact.image_descriptions = [
            ImageDescription(url=d["url"], alt=d.get("alt", ""), description=d["description"])
            for d in image_descriptions_raw
        ]
        log.info(
            "Step 6 — Artifact created%s: %s",
            " (force=True)" if force else "",
            artifact.lesson_id,
        )

    # Step 7: Preprocess (remove story/task sections) — checkpoint after LLM call.
    # Skip when resuming from a checkpoint: the stored cleaned_content is already
    # the preprocessed version.  Using the loaded_from_checkpoint flag avoids the
    # ambiguous content-equality check that would incorrectly re-run preprocessing
    # when the preprocessor returns content identical to the raw input.
    if not loaded_from_checkpoint:
        artifact.cleaned_content = preprocess(cleaned, config.llm, config.model_small)
        _flush_checkpoint(artifacts_dir, artifact, completed_step="preprocess")
    log.info("Step 7 — Preprocessed: %d chars", len(artifact.cleaned_content))

    # Step 8: Fetch relevant articles
    raw_articles = fetch_relevant_articles(cleaned.links, config.llm, config.model_small)
    artifact.articles = [
        ArticleData(url=a["url"], text=a["text"], content=a["content"])
        for a in raw_articles
    ]
    log.info("Step 8 — Articles fetched: %d", len(artifact.articles))

    # Step 9: Summarize → structured SummaryData — checkpoint after LLM call
    if artifact.summary is None:
        known_concepts: dict | None = None
        if config.enable_graph_rag:
            # CRITICAL-5: prefer graph retrieval over JSON knowledge index
            from processor.tools.graph_rag import GraphConfig, connect
            from processor.agents.summarizer import get_known_concepts_from_graph
            try:
                graph_cfg = GraphConfig(
                    uri=config.neo4j_uri,
                    username=config.neo4j_username,
                    password=config.neo4j_password,
                )
                _driver = connect(graph_cfg)
                try:
                    known_concepts = get_known_concepts_from_graph(_driver) or None
                finally:
                    _driver.close()
            except Exception:
                log.warning("Graph unavailable at summarizer — falling back to knowledge index", exc_info=True)

        if known_concepts is None and config.enable_knowledge_index:
            from processor.tools.knowledge_index import get_known_concepts
            index_file = config.base_dir / "state" / "knowledge_index.json"
            known_concepts = get_known_concepts(index_file) or None

        artifact.summary = summarize(
            content=artifact.cleaned_content,
            articles=raw_articles,
            title=artifact.title,
            llm=config.llm,
            model=config.model_large,
            known_concepts=known_concepts,
        )
        _flush_checkpoint(artifacts_dir, artifact, completed_step="summary")
        log.info("Step 9 — Summary generated: %d concepts", len(artifact.summary.key_concepts))
    else:
        log.info("Step 9 — Summary loaded from checkpoint, skipping LLM call")

    # Render summary text for downstream agents
    summary_text = summary_as_context_text(artifact.summary)

    # Step 10: Generate flashcards (if enabled) — checkpoint after LLM call
    if config.enable_flashcards and not artifact.flashcards:
        from processor.agents.flashcard_generator import generate_flashcards
        artifact.flashcards = generate_flashcards(
            content=artifact.cleaned_content,
            summary_text=summary_text,
            title=artifact.title,
            lesson_number=artifact.lesson_number,
            llm=config.llm,
            model=config.model_large,
        )
        _flush_checkpoint(artifacts_dir, artifact, completed_step="flashcards")
        log.info("Step 10 — Flashcards generated: %d", len(artifact.flashcards))
    elif artifact.flashcards:
        log.info("Step 10 — Flashcards loaded from checkpoint, skipping LLM call")

    # Step 11: (removed — quiz generation replaced by quiz-agent runner)

    # Step 12: Generate concept map (if enabled) — checkpoint after LLM call
    if config.enable_diagrams and artifact.concept_map is None:
        from processor.agents.concept_mapper import generate_concept_map
        artifact.concept_map = generate_concept_map(
            content=artifact.cleaned_content,
            summary_text=summary_text,
            title=artifact.title,
            lesson_number=artifact.lesson_number,
            llm=config.llm,
            model=config.model_large,
        )
        _flush_checkpoint(artifacts_dir, artifact, completed_step="concept_map")
        log.info(
            "Step 12 — Concept map generated: %d nodes",
            len(artifact.concept_map.nodes),
        )
    elif artifact.concept_map is not None:
        log.info("Step 12 — Concept map loaded from checkpoint, skipping LLM call")

    # Step 13: Write all outputs
    file_ops.write_artifact_json(config.base_dir / "state" / "artifacts", artifact)
    log.info("Step 13 — Artifact JSON written")
    summary_md = render_summary_markdown(artifact)
    file_ops.write_summary(config.podsumowane_dir, filename, summary_md)
    log.info("Step 13 — Summary written")

    if artifact.flashcards:
        flashcards_content = render_flashcards_tsv(artifact)
        config.flashcards_dir.mkdir(parents=True, exist_ok=True)
        csv_path = config.flashcards_dir / (Path(filename).stem + ".txt")
        csv_path.write_text(flashcards_content, encoding="utf-8-sig")
        log.info("Step 13 — Flashcards written")

    if artifact.concept_map:
        diagram_md = render_concept_map_markdown(artifact)
        file_ops.write_summary(config.diagrams_dir, filename, diagram_md)
        log.info("Step 13 — Concept map written")

    # Step 14: Validate artifact quality + run evals (if enabled)
    if config.enable_validation:
        from processor.validation import validate_artifact
        from processor.evals import evaluate_artifact

        val_result = validate_artifact(artifact)
        eval_result = evaluate_artifact(artifact, validation_result=val_result)
        log.info(
            "Step 14 — Validation %s (errors=%d, warnings=%d), eval avg=%.2f",
            "PASSED" if val_result.passed else "FAILED",
            len(val_result.errors),
            len(val_result.warnings),
            eval_result.average,
        )

    # Step 15: Update knowledge index (if enabled)
    if config.enable_knowledge_index:
        from processor.tools.knowledge_index import (
            update_index,
            generate_glossary,
            generate_cross_references,
        )
        index_file = config.base_dir / "state" / "knowledge_index.json"
        update_index(artifact.summary.key_concepts, artifact.lesson_number, index_file)
        generate_glossary(index_file, config.knowledge_dir)
        generate_cross_references(index_file, config.knowledge_dir)
        log.info("Step 15 — Knowledge index updated")

    # Step 15b: Index into Neo4j graph (if graph-RAG enabled)
    if config.enable_graph_rag:
        _index_into_graph(artifact, config)

    # Step 16: Archive source + mark processed
    if not keep_in_place:
        file_ops.move_to_archive(filepath, config.archiwum_dir)
    state.mark_processed(config.state_file, filename)

    # Remove checkpoint now that the pipeline completed successfully
    file_ops.delete_checkpoint_by_filename(artifacts_dir, filename_stem)

    elapsed = time.time() - start_time
    log.info("Pipeline complete: %s (%.1fs)", filename, elapsed)
    return True


def _index_into_graph(artifact: LessonArtifact, config: Config) -> None:
    """Index lesson into Neo4j graph for RAG retrieval (Step 15b)."""
    from processor.tools.graph_rag import GraphConfig, connect, ensure_indexes, index_lesson, clear_lesson
    from processor.tools.chunker import chunk_content

    graph_cfg = GraphConfig(
        uri=config.neo4j_uri,
        username=config.neo4j_username,
        password=config.neo4j_password,
    )

    try:
        driver = connect(graph_cfg)
    except Exception:
        log.warning("Neo4j unavailable — skipping graph indexing", exc_info=True)
        return

    try:
        ensure_indexes(driver)

        # Clear previous data for this lesson (idempotent re-indexing)
        clear_lesson(driver, artifact.lesson_number)

        # Chunk the cleaned content
        chunks = chunk_content(artifact.cleaned_content, artifact.lesson_number)
        log.info("Step 15b — Chunked content: %d chunks", len(chunks))

        # Generate embeddings if embedding model is configured
        embeddings: list[list[float]] | None = None
        try:
            from processor.tools.embeddings import embed_texts
            chunk_texts = [c.text for c in chunks]
            embeddings = embed_texts(
                chunk_texts,
                base_url=config.llm.base_url,
                api_key=config.llm.api_key,
                model=config.model_embedding,
                headers=config.llm.default_headers,
            )
            log.info("Step 15b — Embeddings generated: %d vectors", len(embeddings))
        except Exception:
            log.info("Step 15b — Embedding generation skipped (not available)", exc_info=True)

        # Index into graph
        with tracing.span(name="graph-index", input_data={"lesson": artifact.lesson_number}):
            stats = index_lesson(driver, artifact, chunks, embeddings)

        # Write study pack manifest
        from processor.models import StudyPack
        study_pack = StudyPack(
            lesson_number=artifact.lesson_number,
            title=artifact.title,
            topic_count=len(artifact.summary.key_concepts) if artifact.summary else 0,
            topics=[c.name for c in artifact.summary.key_concepts] if artifact.summary else [],
            chunk_count=len(chunks),
            graph_indexed=True,
        )
        _write_study_pack(study_pack, config.base_dir)

        log.info(
            "Step 15b — Graph indexed: %d concepts, %d chunks, %d facts, %d relationships",
            stats["concepts"], stats["chunks"], stats["facts"], stats["relationships"],
        )
    finally:
        driver.close()


def _write_study_pack(study_pack, base_dir: Path) -> None:
    """Write study pack manifest JSON (replaces static quiz.md)."""
    import json
    from dataclasses import asdict

    output_dir = base_dir / "state" / "study_packs"
    output_dir.mkdir(parents=True, exist_ok=True)
    path = output_dir / f"{study_pack.lesson_number}.json"
    path.write_text(
        json.dumps(asdict(study_pack), ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    log.info("Study pack written: %s", path)
