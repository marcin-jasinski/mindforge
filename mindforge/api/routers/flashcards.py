"""Flashcards router — due cards, review submission, card listing."""

from __future__ import annotations

from datetime import date
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request

from mindforge.api.deps import get_current_user, get_kb_repo
from mindforge.api.schemas import (
    DueCountResponse,
    FlashcardResponse,
    ReviewRequest,
)
from mindforge.domain.models import ReviewResult, User

router = APIRouter(
    prefix="/api/knowledge-bases/{kb_id}/flashcards", tags=["flashcards"]
)


@router.get("/due", response_model=list[FlashcardResponse])
async def get_due_cards(
    kb_id: UUID,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> list[FlashcardResponse]:
    kb_repo = get_kb_repo(request, await _open_session(request))
    if await kb_repo.get_by_id(kb_id, owner_id=current_user.user_id) is None:
        raise HTTPException(status_code=404, detail="Baza wiedzy nie istnieje.")

    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.study_progress_repo import (
            PostgresStudyProgressRepository,
        )

        repo = PostgresStudyProgressRepository(session)
        cards = await repo.get_due_cards(
            current_user.user_id, kb_id, today=date.today()
        )

    return [
        FlashcardResponse(
            card_id=c.card_id,
            lesson_id=c.lesson_id,
            card_type=c.card_type.value,
            front=c.front,
            back=c.back,
            next_review=c.next_review.isoformat(),
            ease_factor=c.ease_factor,
            interval=c.interval,
        )
        for c in cards
    ]


@router.get("/due/count", response_model=DueCountResponse)
async def get_due_count(
    kb_id: UUID,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> DueCountResponse:
    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.study_progress_repo import (
            PostgresStudyProgressRepository,
        )

        repo = PostgresStudyProgressRepository(session)
        count = await repo.due_count(current_user.user_id, kb_id, today=date.today())

    return DueCountResponse(due_count=count, kb_id=kb_id)


@router.post("/{card_id}/review", status_code=204)
async def review_card(
    kb_id: UUID,
    card_id: str,
    payload: ReviewRequest,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> None:
    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.study_progress_repo import (
            PostgresStudyProgressRepository,
        )

        repo = PostgresStudyProgressRepository(session)
        await repo.save_review(
            user_id=current_user.user_id,
            kb_id=kb_id,
            card_id=card_id,
            result=ReviewResult(rating=payload.rating),
        )
        await session.commit()


@router.get("", response_model=list[FlashcardResponse])
async def list_cards(
    kb_id: UUID,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
    lesson_id: str | None = None,
) -> list[FlashcardResponse]:
    """Return all flashcards in this KB (or filtered by lesson_id)."""
    kb_repo = get_kb_repo(request, await _open_session(request))
    if await kb_repo.get_by_id(kb_id, owner_id=current_user.user_id) is None:
        raise HTTPException(status_code=404, detail="Baza wiedzy nie istnieje.")

    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.study_progress_repo import (
            PostgresStudyProgressRepository,
        )

        repo = PostgresStudyProgressRepository(session)
        # Get all cards by requesting a far-future date
        from datetime import date as _date

        cards = await repo.get_due_cards(
            current_user.user_id, kb_id, today=_date(9999, 12, 31)
        )

    if lesson_id is not None:
        cards = [c for c in cards if c.lesson_id == lesson_id]

    return [
        FlashcardResponse(
            card_id=c.card_id,
            lesson_id=c.lesson_id,
            card_type=c.card_type.value,
            front=c.front,
            back=c.back,
            next_review=c.next_review.isoformat(),
            ease_factor=c.ease_factor,
            interval=c.interval,
        )
        for c in cards
    ]


async def _open_session(request):
    async with request.app.state.session_factory() as session:
        return session
