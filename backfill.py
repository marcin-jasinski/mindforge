#!/usr/bin/env python3
"""
Backfill — re-process archived lessons through the modernized pipeline.

Usage:
  python backfill.py                        # Backfill all archived lessons (full pipeline)
  python backfill.py --lesson S01E01        # Backfill a specific lesson only
  python backfill.py --graph-only           # Re-index into Neo4j from existing artifact JSONs
  python backfill.py --force-graph          # Force graph indexing (override ENABLE_GRAPH_RAG=false)
  python backfill.py --reset-index          # Clear knowledge index before processing
  python backfill.py --dry-run              # List files that would be processed, then exit

Feature flags (same as mindforge.py):
  --no-flashcards    Skip flashcard generation
  --no-diagrams      Skip concept map generation
  --no-images        Skip image analysis (vision model)
  --no-index         Skip knowledge index update
  --no-graph         Skip Neo4j graph indexing

This script reads .md files from archive/ and re-processes them through the full
pipeline (including graph-RAG indexing). Files are NOT moved — they are already
archived. processed.json is updated so subsequent normal runs won't re-process them.

To populate Neo4j on first use:
  1. Start Neo4j:  docker compose --profile graph up -d
  2. Set in .env:  ENABLE_GRAPH_RAG=true  (or use --force-graph)
  3. Run:          python backfill.py
  4. Verify:       python quiz_agent.py --list-lessons
"""
from __future__ import annotations

import argparse
import json
import logging
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)-7s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("backfill")

# CLI flags that override feature flags from config (same as mindforge.py)
_FLAG_OVERRIDES = {
    "--no-flashcards": "enable_flashcards",
    "--no-diagrams": "enable_diagrams",
    "--no-images": "enable_image_analysis",
    "--no-index": "enable_knowledge_index",
    "--no-graph": "enable_graph_rag",
}


def _lesson_number_from_filename(filename: str) -> str:
    from processor.models import extract_lesson_number
    return extract_lesson_number(filename)


def _find_archive_files(archive_dir: Path, lesson_filter: str | None) -> list[Path]:
    """List .md files in archive/, optionally filtered by lesson number."""
    all_files = sorted(archive_dir.glob("*.md"))
    if not lesson_filter:
        return all_files
    target = lesson_filter.upper()
    return [f for f in all_files if _lesson_number_from_filename(f.name) == target]


def _reset_knowledge_index(state_dir: Path) -> None:
    index_path = state_dir / "knowledge_index.json"
    if index_path.exists():
        index_path.unlink()
        log.info("Knowledge index cleared: %s", index_path)
    else:
        log.info("Knowledge index not found, nothing to reset")


def _graph_only_backfill(files: list[Path], config) -> int:
    """Re-index existing artifact JSONs into Neo4j without running any LLM steps."""
    from processor.models import LessonArtifact
    from processor.pipeline import _index_into_graph

    artifacts_dir = config.base_dir / "state" / "artifacts"
    count = 0
    for filepath in files:
        lesson_number = _lesson_number_from_filename(filepath.name)
        artifact_path = artifacts_dir / f"{lesson_number}.json"
        if not artifact_path.exists():
            log.warning(
                "No artifact JSON for %s — run without --graph-only first to generate it",
                lesson_number,
            )
            continue

        log.info("Graph-only indexing: %s", lesson_number)
        try:
            data = json.loads(artifact_path.read_text(encoding="utf-8"))
            artifact = LessonArtifact.from_dict(data)
            _index_into_graph(artifact, config)
            count += 1
        except Exception:
            log.error("Graph indexing failed for %s", lesson_number, exc_info=True)

    return count


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Re-process archived lessons through the modernized pipeline.",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--lesson",
        metavar="LESSON",
        help="Process only this lesson (e.g. S01E01)",
    )
    parser.add_argument(
        "--graph-only",
        action="store_true",
        help="Only run graph indexing (Step 15b) — loads existing artifact JSONs, no LLM calls",
    )
    parser.add_argument(
        "--force-graph",
        action="store_true",
        help="Enable graph indexing even if ENABLE_GRAPH_RAG=false in .env",
    )
    parser.add_argument(
        "--reset-index",
        action="store_true",
        help="Clear the knowledge index before processing (triggers a clean rebuild)",
    )
    parser.add_argument(
        "--dry-run",
        action="store_true",
        help="List files that would be processed and exit without doing any work",
    )
    # Pass --no-* flags through to config overrides
    for flag in _FLAG_OVERRIDES:
        parser.add_argument(flag, action="store_true", default=False)

    args = parser.parse_args()

    from processor.llm_client import load_config
    config = load_config(ROOT)

    # Apply --no-* overrides
    for flag, attr in _FLAG_OVERRIDES.items():
        flag_dest = flag.lstrip("-").replace("-", "_")
        if getattr(args, flag_dest, False):
            setattr(config, attr, False)
            log.info("Feature disabled via CLI: %s", attr)

    # --force-graph overrides ENABLE_GRAPH_RAG from .env
    if args.force_graph:
        config.enable_graph_rag = True
        log.info("Graph indexing forced via --force-graph")

    archive_dir = config.archiwum_dir
    files = _find_archive_files(archive_dir, args.lesson)

    if not files:
        log.info("No archived lesson files found for: %s", args.lesson or "(all)")
        sys.exit(0)

    log.info("Found %d archived lesson(s):", len(files))
    artifacts_dir = config.base_dir / "state" / "artifacts"
    for f in files:
        lesson_number = _lesson_number_from_filename(f.name)
        has_artifact = (artifacts_dir / f"{lesson_number}.json").exists()
        log.info("  %s  [%s]  artifact=%s", f.name, lesson_number, "yes" if has_artifact else "no")

    if args.dry_run:
        log.info("Dry run — exiting without processing")
        sys.exit(0)

    if args.reset_index:
        _reset_knowledge_index(config.base_dir / "state")

    # ── Graph-only mode ──────────────────────────────────────────────────────
    if args.graph_only:
        if not config.enable_graph_rag:
            log.error(
                "--graph-only requires graph indexing to be enabled. "
                "Set ENABLE_GRAPH_RAG=true in .env or add --force-graph."
            )
            sys.exit(1)
        count = _graph_only_backfill(files, config)
        log.info(
            "Graph-only backfill complete: %d/%d lessons indexed",
            count,
            len(files),
        )
        sys.exit(0 if count == len(files) else 1)

    # ── Full pipeline mode ───────────────────────────────────────────────────
    from processor import pipeline

    if config.enable_graph_rag:
        log.info("Graph indexing: ENABLED (Neo4j will be populated)")
    else:
        log.info(
            "Graph indexing: DISABLED (set ENABLE_GRAPH_RAG=true or use --force-graph to enable)"
        )

    successes = 0
    failures = 0
    for filepath in files:
        lesson_number = _lesson_number_from_filename(filepath.name)
        log.info("=" * 60)
        log.info("Backfilling: %s  [%s]", filepath.name, lesson_number)
        log.info("=" * 60)
        try:
            ok = pipeline.run(filepath, config, force=True, keep_in_place=True)
            if ok:
                successes += 1
            else:
                log.warning("Pipeline returned False for %s", lesson_number)
                failures += 1
        except Exception:
            log.error("Pipeline error for %s", lesson_number, exc_info=True)
            failures += 1

    total = len(files)
    log.info(
        "Backfill complete: %d succeeded, %d failed out of %d total",
        successes,
        failures,
        total,
    )
    sys.exit(0 if failures == 0 else 1)


if __name__ == "__main__":
    main()
