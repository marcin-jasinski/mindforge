"""
File operations — read, write, move lesson files.
"""
from __future__ import annotations

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
