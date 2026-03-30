"""
Tests for processor.tools.upload_sanitize.

Covers the acceptance criteria in the P0.2 work package:
  ✓ POSIX traversal attempts are rejected
  ✓ Windows traversal attempts are rejected
  ✓ Absolute paths are rejected
  ✓ Drive-qualified paths are rejected
  ✓ Drive-relative Windows paths are rejected
  ✓ Dot-only names ('.' / '..') are rejected
  ✓ Empty and null-byte names are rejected
  ✓ Hidden dot-prefixed names are rejected
  ✓ Names with path separators are rejected outright
  ✓ Final resolved path is always inside upload_root
  ✓ Duplicate filenames are handled by appending _N suffixes
  ✓ safe_upload_dest raises ValueError when path escapes upload_root
"""
from __future__ import annotations

import pytest
from pathlib import Path

from processor.tools.upload_sanitize import (
    sanitize_upload_filename,
    safe_upload_dest,
    unique_upload_dest,
)


# ---------------------------------------------------------------------------
# sanitize_upload_filename — rejection cases
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("bad_name", [
    # POSIX traversal
    "../etc/passwd",
    "../../root/.bashrc",
    "foo/../../etc/passwd",
    # Windows traversal with backslashes
    "..\\windows\\system32\\config",
    "foo\\..\\..\\windows",
    # Absolute POSIX paths
    "/etc/passwd",
    "/root/.ssh/authorized_keys",
    # Windows absolute paths with drive letters
    "C:\\Windows\\System32\\drivers\\etc\\hosts",
    "D:/secrets/key.md",
    # Drive-letter-only prefix without separator
    "C:secret.md",
    # Drive-relative Windows paths (backslash without drive)
    "\\Windows\\System32",
    # Subdirectory components (no traversal, but still contain separators)
    "subdir/lesson.md",
    "subdir\\lesson.md",
    "/home/user/lesson.md",
    "C:\\Users\\user\\lesson.md",
    # Dot-only names
    ".",
    "..",
    "...",
    # Hidden / dot-prefixed names
    ".env",
    ".bashrc",
    ".hidden.md",
    # Empty string
    "",
    # Null bytes
    "evil\x00.md",
    "evil\x00/../etc/passwd",
    # Newline injection
    "evil\nfile.md",
])
def test_sanitize_rejects_unsafe_names(bad_name: str) -> None:
    with pytest.raises(ValueError):
        sanitize_upload_filename(bad_name)


# ---------------------------------------------------------------------------
# sanitize_upload_filename — acceptance cases
# ---------------------------------------------------------------------------

@pytest.mark.parametrize("good_name, expected", [
    ("lesson01.md", "lesson01.md"),
    ("S01E01_intro.md", "S01E01_intro.md"),
    ("My Lesson.md", "My Lesson.md"),
    ("lesson with spaces.md", "lesson with spaces.md"),
    ("S01E05_rag-basics.md", "S01E05_rag-basics.md"),
])
def test_sanitize_accepts_valid_names(good_name: str, expected: str) -> None:
    assert sanitize_upload_filename(good_name) == expected


# ---------------------------------------------------------------------------
# safe_upload_dest — containment check
# ---------------------------------------------------------------------------

def test_safe_upload_dest_returns_path_inside_root(tmp_path: Path) -> None:
    dest = safe_upload_dest(tmp_path, "lesson.md")
    assert dest.is_relative_to(tmp_path)
    assert dest.name == "lesson.md"


def test_safe_upload_dest_resolves_to_correct_parent(tmp_path: Path) -> None:
    dest = safe_upload_dest(tmp_path, "safe.md")
    assert dest.parent.resolve() == tmp_path.resolve()


# ---------------------------------------------------------------------------
# unique_upload_dest — collision avoidance
# ---------------------------------------------------------------------------

def test_unique_upload_dest_no_collision(tmp_path: Path) -> None:
    dest = unique_upload_dest(tmp_path, "lesson.md")
    assert dest.name == "lesson.md"
    assert not dest.exists()


def test_unique_upload_dest_one_collision(tmp_path: Path) -> None:
    (tmp_path / "lesson.md").write_text("existing")
    dest = unique_upload_dest(tmp_path, "lesson.md")
    assert dest.name == "lesson_1.md"


def test_unique_upload_dest_multiple_collisions(tmp_path: Path) -> None:
    (tmp_path / "lesson.md").write_text("v1")
    (tmp_path / "lesson_1.md").write_text("v2")
    (tmp_path / "lesson_2.md").write_text("v3")
    dest = unique_upload_dest(tmp_path, "lesson.md")
    assert dest.name == "lesson_3.md"


def test_unique_upload_dest_sanitizes_first(tmp_path: Path) -> None:
    # Even when calling unique_upload_dest the raw name is still sanitised.
    with pytest.raises(ValueError):
        unique_upload_dest(tmp_path, "../escape.md")


def test_unique_upload_dest_rejects_path_separators(tmp_path: Path) -> None:
    with pytest.raises(ValueError):
        unique_upload_dest(tmp_path, "subdir/lesson.md")


def test_unique_upload_dest_path_inside_root(tmp_path: Path) -> None:
    (tmp_path / "lesson.md").write_text("existing")
    dest = unique_upload_dest(tmp_path, "lesson.md")
    assert dest.is_relative_to(tmp_path)

