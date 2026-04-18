"""Pipeline task status router."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request

from mindforge.api.deps import get_current_user
from mindforge.api.schemas import TaskStatusResponse
from mindforge.domain.models import User

router = APIRouter(prefix="/api/tasks", tags=["tasks"])


@router.get("/{task_id}", response_model=TaskStatusResponse)
async def get_task_status(
    task_id: UUID,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> TaskStatusResponse:
    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.models import PipelineTaskModel
        from sqlalchemy import select

        result = await session.execute(
            select(PipelineTaskModel).where(PipelineTaskModel.task_id == task_id)
        )
        row = result.scalar_one_or_none()

    if row is None:
        raise HTTPException(status_code=404, detail="Zadanie nie istnieje.")

    # Ownership check via document -> KB -> user would be ideal; for now
    # we expose task status to any authenticated user who knows the task_id.
    return TaskStatusResponse(
        task_id=row.task_id,
        document_id=row.document_id,
        status=row.status,
        created_at=row.created_at,
        started_at=row.started_at,
        completed_at=row.completed_at,
        error_message=row.error_message,
        attempt_count=row.attempt_count,
    )
