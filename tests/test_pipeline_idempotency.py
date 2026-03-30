"""
Tests for processor.state — P1.4 cross-process idempotency.

Covers:
  ✓ claim_for_processing returns True for a new, unclaimed file
  ✓ claim_for_processing returns False for an already-claimed file
  ✓ claim_for_processing returns False for an already-processed file
  ✓ mark_processed moves a file from claimed to processed
  ✓ mark_processed removes the claim entry
  ✓ unclaim removes the claim so the file can be re-claimed
  ✓ is_processed returns False for a new file
  ✓ is_processed returns True after mark_processed
  ✓ Concurrent claims: only one thread claims successfully
  ✓ Pipeline skipped counter increments on duplicate claim attempt
"""
from __future__ import annotations

import threading
from pathlib import Path

import pytest

import processor.metrics as metrics
from processor.state import (
    claim_for_processing,
    is_processed,
    load_processed,
    mark_processed,
    unclaim,
)


# ---------------------------------------------------------------------------
# Fixtures
# ---------------------------------------------------------------------------

@pytest.fixture()
def state_file(tmp_path: Path) -> Path:
    return tmp_path / "state" / "processed.json"


@pytest.fixture(autouse=True)
def reset_metrics():
    metrics.reset()
    yield
    metrics.reset()


# ---------------------------------------------------------------------------
# Basic claim / mark / unclaim semantics
# ---------------------------------------------------------------------------

def test_claim_returns_true_for_new_file(state_file: Path) -> None:
    assert claim_for_processing(state_file, "lesson1.md") is True


def test_claim_returns_false_for_already_claimed(state_file: Path) -> None:
    claim_for_processing(state_file, "lesson1.md")
    assert claim_for_processing(state_file, "lesson1.md") is False


def test_claim_returns_false_for_already_processed(state_file: Path) -> None:
    mark_processed(state_file, "lesson1.md")
    assert claim_for_processing(state_file, "lesson1.md") is False


def test_mark_processed_sets_processed_flag(state_file: Path) -> None:
    claim_for_processing(state_file, "lesson1.md")
    mark_processed(state_file, "lesson1.md")
    assert is_processed(state_file, "lesson1.md") is True


def test_mark_processed_removes_claim(state_file: Path) -> None:
    claim_for_processing(state_file, "lesson1.md")
    mark_processed(state_file, "lesson1.md")
    # After mark, the file is in "processed", not in "claimed"
    import json

    data = json.loads(state_file.read_text(encoding="utf-8"))
    assert "lesson1.md" not in data.get("claimed", [])
    assert "lesson1.md" in data.get("processed", [])


def test_unclaim_allows_reclaim(state_file: Path) -> None:
    claim_for_processing(state_file, "lesson1.md")
    unclaim(state_file, "lesson1.md")
    # After unclaim the file can be claimed again
    assert claim_for_processing(state_file, "lesson1.md") is True


def test_unclaim_does_not_affect_processed(state_file: Path) -> None:
    mark_processed(state_file, "lesson1.md")
    unclaim(state_file, "lesson1.md")  # no-op since it's processed, not claimed
    assert is_processed(state_file, "lesson1.md") is True


def test_is_processed_false_for_new_file(state_file: Path) -> None:
    assert is_processed(state_file, "never_seen.md") is False


def test_is_processed_true_after_mark(state_file: Path) -> None:
    mark_processed(state_file, "lesson2.md")
    assert is_processed(state_file, "lesson2.md") is True


def test_load_processed_returns_set(state_file: Path) -> None:
    mark_processed(state_file, "a.md")
    mark_processed(state_file, "b.md")
    result = load_processed(state_file)
    assert {"a.md", "b.md"}.issubset(result)


# ---------------------------------------------------------------------------
# Concurrent-claim race: only one thread wins
# ---------------------------------------------------------------------------

def test_concurrent_claims_only_one_wins(state_file: Path) -> None:
    """Two threads racing to claim the same file — exactly one must win."""
    results: list[bool] = []
    barrier = threading.Barrier(2)

    def _try_claim() -> None:
        barrier.wait()  # synchronise start
        results.append(claim_for_processing(state_file, "race.md"))

    t1 = threading.Thread(target=_try_claim)
    t2 = threading.Thread(target=_try_claim)
    t1.start()
    t2.start()
    t1.join()
    t2.join()

    assert len(results) == 2
    assert results.count(True) == 1, "Exactly one thread must claim the file"
    assert results.count(False) == 1


# ---------------------------------------------------------------------------
# Metrics integration — pipeline_skipped counter
# ---------------------------------------------------------------------------

def test_pipeline_skipped_counter_increments(state_file: Path, monkeypatch, tmp_path) -> None:
    """pipeline.run() increments the pipeline_skipped metric on duplicate attempt."""
    from unittest.mock import MagicMock, patch
    from processor.llm_client import Config, LLMClient

    # Claim and mark the file as already processed
    filename = "already_done.md"
    mark_processed(state_file, filename)

    lesson_path = tmp_path / filename
    lesson_path.write_text("# Already done\n", encoding="utf-8")

    # Build a minimal Config pointing to our temp state file
    mock_llm = MagicMock(spec=LLMClient)
    cfg = Config(
        llm=mock_llm,
        model_small="test/small",
        model_large="test/large",
        model_vision="test/vision",
        base_dir=tmp_path,
        nowe_dir=tmp_path / "new",
        podsumowane_dir=tmp_path / "summarized",
        archiwum_dir=tmp_path / "archive",
        flashcards_dir=tmp_path / "flashcards",
        quizzes_dir=tmp_path / "quizzes",
        diagrams_dir=tmp_path / "diagrams",
        knowledge_dir=tmp_path / "knowledge",
        state_file=state_file,
    )

    from processor import pipeline
    result = pipeline.run(lesson_path, cfg)

    assert result is False, "Should skip already-processed file"
    snap = metrics.snapshot()
    assert snap["counters"].get("pipeline_skipped", 0) == 1


# ---------------------------------------------------------------------------
# Checkpoint write / read / delete cycle (CRITICAL-3)
# ---------------------------------------------------------------------------

from dataclasses import dataclass, field


@dataclass
class _FakeArtifact:
    """Minimal dataclass stand-in for LessonArtifact — tests file_ops checkpoint helpers."""

    source_filename: str
    lesson_id: str
    lesson_number: str = "S01E01"
    title: str = "Test"
    processed_at: str = "2026-01-01T00:00:00Z"
    metadata: dict = field(default_factory=dict)
    cleaned_content: str = "content"
    image_descriptions: list = field(default_factory=list)
    articles: list = field(default_factory=list)
    summary: object = None
    flashcards: list = field(default_factory=list)
    concept_map: object = None
    study_packs: list = field(default_factory=list)


def test_write_checkpoint_uses_filename_stem(tmp_path: Path) -> None:
    """Checkpoint key is the source filename stem, not the lesson_id UUID."""
    from processor.tools.file_ops import write_checkpoint

    artifact = _FakeArtifact(source_filename="s01e05.md", lesson_id="some-uuid-1234")
    write_checkpoint(tmp_path, artifact, completed_step="test")

    checkpoint_path = tmp_path / "s01e05.checkpoint.json"
    assert checkpoint_path.exists(), "Checkpoint must be keyed by filename stem 's01e05'"
    # Must NOT create a file named after the UUID
    assert not (tmp_path / "some-uuid-1234.checkpoint.json").exists()


def test_load_checkpoint_by_filename_round_trip(tmp_path: Path) -> None:
    """Writing then loading a checkpoint returns the same lesson_id."""
    from processor.tools.file_ops import write_checkpoint, load_checkpoint_by_filename

    artifact = _FakeArtifact(source_filename="my_lesson.md", lesson_id="stable-uuid-abc")
    write_checkpoint(tmp_path, artifact, completed_step="preprocess")

    data = load_checkpoint_by_filename(tmp_path, "my_lesson")
    assert data is not None, "Checkpoint should be found by filename stem"
    assert data["lesson_id"] == "stable-uuid-abc"


def test_load_checkpoint_by_filename_returns_none_when_absent(tmp_path: Path) -> None:
    from processor.tools.file_ops import load_checkpoint_by_filename

    assert load_checkpoint_by_filename(tmp_path, "nonexistent") is None


def test_delete_checkpoint_by_filename_removes_file(tmp_path: Path) -> None:
    from processor.tools.file_ops import (
        write_checkpoint,
        load_checkpoint_by_filename,
        delete_checkpoint_by_filename,
    )

    artifact = _FakeArtifact(source_filename="to_delete.md", lesson_id="delete-me")
    write_checkpoint(tmp_path, artifact, completed_step="test")
    assert load_checkpoint_by_filename(tmp_path, "to_delete") is not None

    delete_checkpoint_by_filename(tmp_path, "to_delete")
    assert load_checkpoint_by_filename(tmp_path, "to_delete") is None


def test_checkpoint_stable_across_uuid_regeneration(tmp_path: Path) -> None:
    """Simulate a resume: a checkpoint written with uuid-A is found even though
    the pipeline would have generated a different uuid-B on the new run.

    This verifies that the filename-stem key makes checkpoints resumable.
    """
    from processor.tools.file_ops import write_checkpoint, load_checkpoint_by_filename

    # First run — write checkpoint with uuid-A
    artifact_run1 = _FakeArtifact(source_filename="lesson.md", lesson_id="uuid-A")
    write_checkpoint(tmp_path, artifact_run1, completed_step="summary")

    # Second run — the pipeline would generate uuid-B if it created a fresh artifact,
    # but it should look up by filename stem and find the checkpoint from run 1.
    data = load_checkpoint_by_filename(tmp_path, "lesson")
    assert data is not None, "Checkpoint must be found despite UUID change"
    assert data["lesson_id"] == "uuid-A", "Loaded checkpoint preserves the original lesson_id"
