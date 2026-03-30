"""
State management — tracks which files have been processed.

Cross-process idempotency: ``claim_for_processing`` uses a FileLock advisory
lock to prevent the API background task and the file watcher (two separate
processes) from both processing the same lesson concurrently.

State file format::

    {
        "processed": ["lesson1.md", ...],   // successfully completed
        "claimed":   ["lesson2.md", ...]    // currently in-progress
    }
"""
from __future__ import annotations

import json
import logging
from pathlib import Path

log = logging.getLogger(__name__)

try:
    from filelock import FileLock as _FileLock, Timeout as _FLTimeout
    _HAVE_FILELOCK = True
except ImportError:  # pragma: no cover
    _HAVE_FILELOCK = False
    log.warning(
        "filelock not installed — cross-process idempotency will be best-effort. "
        "Install it with: pip install filelock>=3.13.0"
    )


# ---------------------------------------------------------------------------
# Internal helpers
# ---------------------------------------------------------------------------

def _load_state(state_file: Path) -> dict:
    """Return the raw state dict, tolerating a missing or corrupt file."""
    if not state_file.exists():
        return {"processed": [], "claimed": []}
    try:
        return json.loads(state_file.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        log.warning("Corrupted state file %s, resetting", state_file)
        return {"processed": [], "claimed": []}


def _save_state(state_file: Path, data: dict) -> None:
    """Atomically overwrite the state file via a temporary rename."""
    state_file.parent.mkdir(parents=True, exist_ok=True)
    tmp = state_file.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(data, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(state_file)


def _lock_path(state_file: Path) -> Path:
    return state_file.with_suffix(".lock")


# ---------------------------------------------------------------------------
# Public API
# ---------------------------------------------------------------------------

def load_processed(state_file: Path) -> set[str]:
    data = _load_state(state_file)
    return set(data.get("processed", []))


def mark_processed(state_file: Path, filename: str) -> None:
    """Mark *filename* as successfully processed and remove its claim."""
    if _HAVE_FILELOCK:
        with _FileLock(str(_lock_path(state_file)), timeout=15.0):
            _mark_processed_locked(state_file, filename)
    else:
        _mark_processed_locked(state_file, filename)
    log.info("Marked as processed: %s", filename)


def _mark_processed_locked(state_file: Path, filename: str) -> None:
    data = _load_state(state_file)
    processed = set(data.get("processed", []))
    claimed = set(data.get("claimed", []))
    processed.add(filename)
    claimed.discard(filename)
    data["processed"] = sorted(processed)
    data["claimed"] = sorted(claimed)
    _save_state(state_file, data)


def is_processed(state_file: Path, filename: str) -> bool:
    """Return True if *filename* has been successfully processed."""
    return filename in load_processed(state_file)


def claim_for_processing(state_file: Path, filename: str) -> bool:
    """Atomically check and reserve *filename* for processing.

    Returns True when the caller should proceed (file was successfully claimed).
    Returns False when the file has already been processed or another process
    already holds a claim on it.

    The caller is responsible for calling ``mark_processed`` on success or
    ``unclaim`` on failure so that the file can be retried.
    """
    if not _HAVE_FILELOCK:
        return filename not in load_processed(state_file)

    try:
        with _FileLock(str(_lock_path(state_file)), timeout=15.0):
            data = _load_state(state_file)
            processed = set(data.get("processed", []))
            claimed = set(data.get("claimed", []))
            if filename in processed or filename in claimed:
                return False
            claimed.add(filename)
            data["claimed"] = sorted(claimed)
            _save_state(state_file, data)
            return True
    except _FLTimeout:
        log.warning(
            "Lock timeout for %s — falling back to unguarded is_processed check",
            filename,
        )
        return filename not in load_processed(state_file)


def unclaim(state_file: Path, filename: str) -> None:
    """Release a claim without marking as processed (call on pipeline failure)."""
    if _HAVE_FILELOCK:
        with _FileLock(str(_lock_path(state_file)), timeout=15.0):
            _unclaim_locked(state_file, filename)
    else:
        _unclaim_locked(state_file, filename)
    log.info("Unclaimed (pipeline failed): %s", filename)


def _unclaim_locked(state_file: Path, filename: str) -> None:
    data = _load_state(state_file)
    claimed = set(data.get("claimed", []))
    claimed.discard(filename)
    data["claimed"] = sorted(claimed)
    _save_state(state_file, data)
