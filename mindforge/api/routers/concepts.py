"""Concepts router — return concept graph data for Cytoscape.js."""

from __future__ import annotations

from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException, Request

from mindforge.api.deps import get_current_user, get_kb_repo
from mindforge.api.schemas import (
    ConceptEdgeResponse,
    ConceptGraphResponse,
    ConceptNodeResponse,
)
from mindforge.domain.models import User

router = APIRouter(prefix="/api/knowledge-bases/{kb_id}/concepts", tags=["concepts"])


@router.get("", response_model=ConceptGraphResponse)
async def get_concepts(
    kb_id: UUID,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> ConceptGraphResponse:
    async with request.app.state.session_factory() as session:
        kb_repo = get_kb_repo(request, session)
        if await kb_repo.get_by_id(kb_id, owner_id=current_user.user_id) is None:
            raise HTTPException(status_code=404, detail="Baza wiedzy nie istnieje.")

    retrieval = getattr(request.app.state, "retrieval", None)
    if retrieval is None:
        return ConceptGraphResponse(concepts=[], edges=[])

    nodes = await retrieval.get_concepts(kb_id)
    concept_responses = [
        ConceptNodeResponse(
            key=n.key,
            label=n.label,
            description=n.description,
            related=n.related,
        )
        for n in nodes
    ]

    # Build edges from neighbor relationships
    edges: list[ConceptEdgeResponse] = []
    seen: set[tuple[str, str]] = set()
    for node in nodes:
        for related_key in node.related:
            edge_key = (min(node.key, related_key), max(node.key, related_key))
            if edge_key not in seen:
                seen.add(edge_key)
                edges.append(
                    ConceptEdgeResponse(
                        source=node.key,
                        target=related_key,
                        relation="related_to",
                    )
                )

    return ConceptGraphResponse(concepts=concept_responses, edges=edges)
