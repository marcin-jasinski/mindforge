"""
State management — tracks which files have been processed.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

log = logging.getLogger(__name__)


def load_processed(state_file: Path) -> set[str]:
    if not state_file.exists():
        return set()
    try:
        data = json.loads(state_file.read_text(encoding="utf-8"))
        return set(data.get("processed", []))
    except (json.JSONDecodeError, OSError):
        log.warning("Corrupted state file %s, resetting", state_file)
        return set()


def mark_processed(state_file: Path, filename: str) -> None:
    processed = load_processed(state_file)
    processed.add(filename)
    state_file.parent.mkdir(parents=True, exist_ok=True)
    tmp = state_file.with_suffix(".json.tmp")
    tmp.write_text(
        json.dumps({"processed": sorted(processed)}, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    tmp.replace(state_file)
    log.info("Marked as processed: %s", filename)


def is_processed(state_file: Path, filename: str) -> bool:
    return filename in load_processed(state_file)
