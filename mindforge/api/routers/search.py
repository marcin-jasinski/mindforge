"""Search router — hybrid Graph RAG + lexical + vector search."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request

from mindforge.api.deps import get_current_user, get_kb_repo
from mindforge.api.schemas import SearchRequest, SearchResponse, SearchResultItem
from mindforge.domain.models import User

router = APIRouter(prefix="/api/knowledge-bases/{kb_id}/search", tags=["search"])


@router.post("", response_model=SearchResponse)
async def search(
    kb_id: UUID,
    payload: SearchRequest,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> SearchResponse:
    kb_repo = get_kb_repo(request, await _open_session(request))
    if await kb_repo.get_by_id(kb_id, owner_id=current_user.user_id) is None:
        raise HTTPException(status_code=404, detail="Baza wiedzy nie istnieje.")

    retrieval = getattr(request.app.state, "retrieval", None)
    if retrieval is None:
        return SearchResponse(results=[], query=payload.query)

    results = await retrieval.retrieve(
        query=payload.query,
        kb_id=kb_id,
        top_k=payload.top_k,
    )

    return SearchResponse(
        query=payload.query,
        results=[
            SearchResultItem(
                content=r.content,
                source_lesson_id=r.source_lesson_id,
                source_document_id=r.source_document_id,
                score=r.score,
                metadata=r.metadata,
            )
            for r in results
        ],
    )


async def _open_session(request):
    async with request.app.state.session_factory() as session:
        return session
