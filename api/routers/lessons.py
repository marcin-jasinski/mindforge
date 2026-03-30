"""
Lessons router — list indexed lessons, upload new lesson files.
"""
from __future__ import annotations

import logging
from pathlib import Path
from typing import Any

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException, UploadFile

from api.auth import require_auth
from api.deps import get_base_dir, get_config, get_neo4j_driver, get_settings
from api.schemas import LessonDetail, LessonSummary, UploadResponse, UserInfo
from processor.tools.upload_sanitize import sanitize_upload_filename, unique_upload_dest

log = logging.getLogger(__name__)
router = APIRouter(prefix="/api/lessons", tags=["lessons"])

ALLOWED_EXTENSIONS = {".md"}
MAX_UPLOAD_SIZE = 5 * 1024 * 1024  # 5 MB


@router.get("", response_model=list[LessonDetail])
async def list_lessons(
    driver: Any = Depends(get_neo4j_driver),
    _user: UserInfo = Depends(require_auth),
):
    """List all indexed lessons with stats."""
    from processor.tools.graph_rag import get_indexed_lessons

    lessons = get_indexed_lessons(driver)
    result: list[LessonDetail] = []

    with driver.session() as session:
        for lesson in lessons:
            num = lesson["number"]
            # Count concepts, chunks
            record = session.run(
                """
                MATCH (l:Lesson {number: $n})
                OPTIONAL MATCH (l)-[:HAS_CONCEPT]->(c:Concept)
                OPTIONAL MATCH (l)-[:HAS_CHUNK]->(ch:Chunk)
                RETURN count(DISTINCT c) AS concepts, count(DISTINCT ch) AS chunks
                """,
                n=num,
            ).single()

            # Count flashcards from artifact JSON
            flashcard_count = _count_flashcards(num, driver)

            result.append(LessonDetail(
                number=num,
                title=lesson["title"],
                processed_at="",
                concept_count=record["concepts"] if record else 0,
                flashcard_count=flashcard_count,
                chunk_count=record["chunks"] if record else 0,
            ))

    return result


@router.post("/upload", response_model=UploadResponse)
async def upload_lesson(
    file: UploadFile,
    background_tasks: BackgroundTasks,
    base_dir: Path = Depends(get_base_dir),
    config: Any = Depends(get_config),
    _user: UserInfo = Depends(require_auth),
):
    """Upload a .md lesson file and trigger pipeline processing."""
    if not file.filename:
        raise HTTPException(status_code=400, detail="No filename")

    # Sanitise the supplied filename — raises ValueError for unsafe names.
    try:
        safe_name = sanitize_upload_filename(file.filename)
    except ValueError as exc:
        raise HTTPException(status_code=400, detail=str(exc))

    ext = Path(safe_name).suffix.lower()
    if ext not in ALLOWED_EXTENSIONS:
        raise HTTPException(status_code=400, detail=f"Only {ALLOWED_EXTENSIONS} files allowed")

    content = await file.read()
    if len(content) > MAX_UPLOAD_SIZE:
        raise HTTPException(status_code=400, detail="File too large (max 5 MB)")

    # Save to new/ directory — use unique_upload_dest to avoid silent overwrites
    # and to verify the final path stays inside new/.
    new_dir = base_dir / "new"
    new_dir.mkdir(parents=True, exist_ok=True)
    try:
        dest = unique_upload_dest(new_dir, safe_name)
    except (ValueError, RuntimeError) as exc:
        log.error("Upload path resolution failed for %r: %s", file.filename, exc)
        raise HTTPException(status_code=400, detail="Invalid upload filename")
    dest.write_bytes(content)
    log.info("Uploaded lesson file: %s (%d bytes)", dest.name, len(content))

    # Trigger pipeline in background
    background_tasks.add_task(_run_pipeline, dest, config)

    return UploadResponse(
        filename=dest.name,
        message="File uploaded. Pipeline processing started.",
    )


def _run_pipeline(filepath: Path, config: Any) -> None:
    """Run the lesson pipeline in a background task."""
    try:
        from processor.pipeline import run
        run(filepath, config)
    except Exception:
        log.error("Background pipeline failed for %s", filepath, exc_info=True)


def _count_flashcards(lesson_number: str, driver: Any) -> int:
    """Count flashcards from stored artifact JSON."""
    # Flashcard count comes from the artifact, not Neo4j
    # We check the study pack or artifact file
    try:
        import json
        from pathlib import Path as P

        # Try study pack first
        base = P(__file__).resolve().parent.parent
        artifact_dir = base / "state" / "artifacts"
        for f in artifact_dir.glob(f"*{lesson_number}*.json"):
            data = json.loads(f.read_text(encoding="utf-8"))
            return len(data.get("flashcards", []))
    except Exception:
        pass
    return 0
