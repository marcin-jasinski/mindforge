"""
Pipeline orchestrator — sequential processing of a lesson file.

Steps:
1. Check if already processed (state guard)
2. Parse lesson file (frontmatter + links)
3. Clean content (remove video/images)
4. Preprocess (remove story/task sections)
5. Fetch relevant articles (optional, non-blocking)
6. Summarize (large LLM)
7. Write summary to summarized/
8. Archive source to archive/
9. Mark as processed
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

log = logging.getLogger(__name__)


def run(filepath: Path, config: Config) -> bool:
    """Process a single lesson file. Returns True on success, False on skip/error."""
    filename = filepath.name
    start_time = time.time()

    log.info("=" * 60)
    log.info("Pipeline start: %s", filename)
    log.info("=" * 60)

    # Step 1: State guard
    if state.is_processed(config.state_file, filename):
        log.info("Already processed, skipping: %s", filename)
        return False

    try:
        # Step 2: Read and parse
        raw_content = file_ops.read_file(filepath)
        parsed = lesson_parser.parse_lesson_file(str(filepath), raw_content)
        log.info("Step 2/8 — Parsed: title=%s, links=%d", parsed.title, len(parsed.links))

        # Step 3: Deterministic cleaning (remove video/images)
        cleaned = lesson_parser.clean_lesson(parsed)
        log.info("Step 3/8 — Cleaned: %d chars", len(cleaned.content))

        # Step 4: Remove story/task sections (regex + LLM fallback)
        final_content = preprocess(cleaned, config.llm, config.model_small)
        log.info("Step 4/8 — Preprocessed: %d chars", len(final_content))

        # Step 5: Fetch relevant articles (optional, errors logged but non-blocking)
        articles = fetch_relevant_articles(cleaned.links, config.llm, config.model_small)
        log.info("Step 5/8 — Articles fetched: %d", len(articles))

        # Step 6: Summarize with large model
        summary_md = summarize(
            content=final_content,
            articles=articles,
            title=cleaned.title,
            source_filename=filename,
            metadata=cleaned.metadata,
            llm=config.llm,
            model=config.model_large,
        )
        log.info("Step 6/8 — Summary generated: %d chars", len(summary_md))

        # Step 7: Write summary
        file_ops.write_summary(config.podsumowane_dir, filename, summary_md)
        log.info("Step 7/8 — Summary written")

        # Step 8: Archive source
        file_ops.move_to_archive(filepath, config.archiwum_dir)
        log.info("Step 8/8 — Source archived")

        # Mark processed
        state.mark_processed(config.state_file, filename)

        elapsed = time.time() - start_time
        log.info("Pipeline complete: %s (%.1fs)", filename, elapsed)
        return True

    except Exception:
        elapsed = time.time() - start_time
        log.error("Pipeline failed for %s after %.1fs", filename, elapsed, exc_info=True)
        return False
