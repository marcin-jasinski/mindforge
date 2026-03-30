"""
Search router — graph-RAG retrieval over indexed lessons.
"""
from __future__ import annotations

from typing import Any

from fastapi import APIRouter, Depends

from api.auth import require_auth
from api.deps import get_async_llm_client, get_settings, get_neo4j_driver
from api.schemas import (
    ChunkResult,
    ConceptResult,
    SearchRequest,
    SearchResponse,
    UserInfo,
)

router = APIRouter(prefix="/api/search", tags=["search"])


@router.post("", response_model=SearchResponse)
async def search(
    body: SearchRequest,
    driver: Any = Depends(get_neo4j_driver),
    settings: Any = Depends(get_settings),
    llm: Any = Depends(get_async_llm_client),
    _user: UserInfo = Depends(require_auth),
):
    """Search indexed lessons using graph-RAG (concept graph + lexical + vector)."""
    import asyncio
    from processor.tools.graph_rag import retrieve

    # Generate embedding only when explicitly enabled — graph + lexical search runs first.
    # Embed_texts is synchronous — run it in a thread to avoid blocking the event loop.
    query_embedding: list[float] | None = None
    if settings.enable_embeddings and settings.model_embedding:
        try:
            from processor.tools.embeddings import embed_texts
            embeddings = await asyncio.to_thread(
                embed_texts,
                [body.query],
                base_url=llm.base_url,
                api_key=llm.api_key,
                model=settings.model_embedding,
                headers=llm.default_headers,
            )
            if embeddings:
                query_embedding = embeddings[0]
        except Exception:
            pass  # Fall back to graph + lexical only

    result = retrieve(
        driver,
        body.query,
        query_embedding=query_embedding,
        max_results=body.max_results,
    )

    return SearchResponse(
        chunks=[
            ChunkResult(
                id=c["id"],
                text=c["text"],
                lesson_number=c.get("lesson_number", ""),
                score=c.get("score"),
            )
            for c in result.chunks
        ],
        concepts=[
            ConceptResult(name=c["name"], definition=c.get("definition", ""))
            for c in result.concepts
        ],
        facts=result.facts,
        source_lessons=result.source_lessons,
    )
