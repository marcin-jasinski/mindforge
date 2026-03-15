#!/usr/bin/env python3
"""
Markdown Lesson Summarizer — entry point.

Usage:
  python markdown_summarizer.py              # Process existing files in nowe/ then watch for new ones
  python markdown_summarizer.py --watch      # Watch mode only (skip initial processing)
  python markdown_summarizer.py --once       # Process existing files and exit (no watching)
  python markdown_summarizer.py FILE.md      # Process a single file
"""
from __future__ import annotations

import logging
import sys
from pathlib import Path

# Ensure project root is on sys.path
ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))

from processor.llm_client import load_config
from processor import pipeline, state
from processor.watcher import start_watcher

logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s [%(levelname)-7s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("summarizer")


def process_existing(config) -> int:
    """Process all unprocessed .md files currently in nowe/. Returns count."""
    md_files = sorted(config.nowe_dir.glob("*.md"))
    if not md_files:
        log.info("No files in nowe/ to process")
        return 0

    processed = 0
    for filepath in md_files:
        if state.is_processed(config.state_file, filepath.name):
            log.info("Skipping (already processed): %s", filepath.name)
            continue
        if pipeline.run(filepath, config):
            processed += 1

    log.info("Processed %d/%d files", processed, len(md_files))
    return processed


def main() -> None:
    config = load_config(ROOT)

    args = sys.argv[1:]

    # Single file mode
    if args and not args[0].startswith("--"):
        filepath = Path(args[0])
        if not filepath.is_absolute():
            filepath = config.nowe_dir / filepath
        if not filepath.exists():
            log.error("File not found: %s", filepath)
            sys.exit(1)
        success = pipeline.run(filepath, config)
        sys.exit(0 if success else 1)

    watch_only = "--watch" in args
    once_only = "--once" in args

    # Process existing files (unless --watch)
    if not watch_only:
        count = process_existing(config)
        if once_only:
            log.info("Done (--once mode)")
            sys.exit(0)

    # Start watcher
    log.info("Starting watcher on %s", config.nowe_dir)
    start_watcher(config, blocking=True)


if __name__ == "__main__":
    main()
