"""
File operations — read, write, move lesson files.
"""
from __future__ import annotations

import json
import logging
import shutil
from pathlib import Path

log = logging.getLogger(__name__)


def read_file(filepath: Path) -> str:
    content = filepath.read_text(encoding="utf-8")
    log.info("Read file: %s (%d chars)", filepath.name, len(content))
    return content


def write_summary(output_dir: Path, filename: str, content: str) -> Path:
    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / filename
    output_path.write_text(content, encoding="utf-8")
    log.info("Wrote summary: %s (%d chars)", output_path, len(content))
    return output_path


def move_to_archive(source: Path, archive_dir: Path) -> Path:
    archive_dir.mkdir(parents=True, exist_ok=True)
    dest = archive_dir / source.name
    shutil.move(str(source), str(dest))
    log.info("Archived: %s -> %s", source.name, dest)
    return dest


def write_artifact_json(artifacts_dir: Path, artifact: object) -> Path:
    """Save the canonical LessonArtifact as JSON (source of truth).

    Uses dataclasses.asdict() for serialization. The artifact parameter
    is typed as object to avoid circular imports — callers pass a LessonArtifact.
    """
    from dataclasses import asdict

    artifacts_dir.mkdir(parents=True, exist_ok=True)
    lesson_number = getattr(artifact, "lesson_number", "unknown")
    output_path = artifacts_dir / f"{lesson_number}.json"
    data = asdict(artifact)
    output_path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    log.info("Artifact JSON written: %s", output_path)
    return output_path


def write_checkpoint(artifacts_dir: Path, artifact: object, *, completed_step: str) -> Path:
    """Persist a partial artifact as a checkpoint after an expensive pipeline step.

    The checkpoint file name is derived from ``artifact.source_filename`` (stem
    only, no extension) so it is stable across runs of the same source file
    regardless of the artifact's UUID ``lesson_id``.

    Args:
        artifacts_dir: Directory where checkpoint files are stored.
        artifact: The (possibly partial) :class:`~processor.models.LessonArtifact`.
        completed_step: Human-readable name of the step just completed (for logging only).
    """
    from dataclasses import asdict
    from pathlib import Path as _Path

    artifacts_dir.mkdir(parents=True, exist_ok=True)

    source_filename = getattr(artifact, "source_filename", None) or "unknown"
    filename_stem = _Path(source_filename).stem or "unknown"

    checkpoint_path = artifacts_dir / f"{filename_stem}.checkpoint.json"
    data = asdict(artifact)
    checkpoint_path.write_text(
        json.dumps(data, ensure_ascii=False, indent=2),
        encoding="utf-8",
    )
    log.info("Checkpoint written after step '%s': %s", completed_step, checkpoint_path)
    return checkpoint_path


def load_checkpoint_by_filename(artifacts_dir: Path, filename_stem: str) -> "dict | None":
    """Load a previously written checkpoint by the source filename stem.

    The ``filename_stem`` must be the bare filename without extension
    (e.g. ``"s01e05"`` for ``"s01e05.md"``).  Returns ``None`` when no
    checkpoint exists.
    """
    checkpoint_path = artifacts_dir / f"{filename_stem}.checkpoint.json"
    if not checkpoint_path.exists():
        return None
    try:
        data = json.loads(checkpoint_path.read_text(encoding="utf-8"))
        log.info("Loaded checkpoint from %s", checkpoint_path)
        return data
    except Exception:
        log.warning("Failed to load checkpoint %s", checkpoint_path, exc_info=True)
        return None


def delete_checkpoint_by_filename(artifacts_dir: Path, filename_stem: str) -> None:
    """Delete the checkpoint file after the pipeline completes successfully."""
    checkpoint_path = artifacts_dir / f"{filename_stem}.checkpoint.json"
    if checkpoint_path.exists():
        checkpoint_path.unlink()
        log.info("Checkpoint removed: %s", checkpoint_path)
