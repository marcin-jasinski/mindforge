"""Chat router — conversational RAG delegating to ChatService."""

from __future__ import annotations

import logging
from typing import Annotated
from uuid import UUID

from fastapi import APIRouter, Depends, HTTPException

from mindforge.api.deps import get_chat_service, get_current_user, get_kb_repo
from mindforge.api.schemas import (
    ChatMessageRequest,
    ChatMessageResponse,
    ChatSessionResponse,
    StartChatRequest,
)
from mindforge.application.chat import (
    ChatSessionAccessDeniedError,
    ChatSessionNotFoundError,
)
from mindforge.domain.models import User

log = logging.getLogger(__name__)
router = APIRouter(prefix="/api/chat", tags=["chat"])


@router.post("/sessions", response_model=ChatSessionResponse, status_code=201)
async def start_chat(
    payload: StartChatRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    kb_repo: Annotated[object, Depends(get_kb_repo)],
    chat_service: Annotated[object, Depends(get_chat_service)],
) -> ChatSessionResponse:
    if (
        await kb_repo.get_by_id(
            payload.knowledge_base_id, owner_id=current_user.user_id
        )
        is None
    ):
        raise HTTPException(status_code=404, detail="Baza wiedzy nie istnieje.")

    info = await chat_service.start_session(
        current_user.user_id, payload.knowledge_base_id
    )
    return ChatSessionResponse(
        session_id=info.session_id,
        knowledge_base_id=info.knowledge_base_id,
        created_at=info.created_at,
        turns=[],
    )


@router.post("/sessions/{session_id}/messages", response_model=ChatMessageResponse)
async def send_message(
    session_id: UUID,
    payload: ChatMessageRequest,
    current_user: Annotated[User, Depends(get_current_user)],
    chat_service: Annotated[object, Depends(get_chat_service)],
) -> ChatMessageResponse:
    """Process a chat message and return the AI answer.

    No grounding context, raw prompts, or raw completions are returned.
    """
    try:
        result = await chat_service.send_message(
            session_id, current_user.user_id, payload.message
        )
    except ChatSessionNotFoundError:
        raise HTTPException(status_code=404, detail="Sesja czatu nie istnieje.")
    except ChatSessionAccessDeniedError:
        raise HTTPException(status_code=403, detail="Brak dostępu do tej sesji.")
    return ChatMessageResponse(
        session_id=result.session_id,
        answer=result.answer,
        source_concept_keys=result.source_concept_keys,
    )


@router.get("/sessions", response_model=list[ChatSessionResponse])
async def list_sessions(
    kb_id: UUID,
    current_user: Annotated[User, Depends(get_current_user)],
    chat_service: Annotated[object, Depends(get_chat_service)],
) -> list[ChatSessionResponse]:
    sessions = await chat_service.list_sessions(current_user.user_id, kb_id)
    return [
        ChatSessionResponse(
            session_id=s.session_id,
            knowledge_base_id=s.knowledge_base_id,
            created_at=s.created_at,
            turns=[],
        )
        for s in sessions
    ]


@router.post("/sessions", response_model=ChatSessionResponse, status_code=201)
async def start_chat(
    payload: StartChatRequest,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> ChatSessionResponse:
    kb_repo = get_kb_repo(request, await _open_session(request))
    if (
        await kb_repo.get_by_id(
            payload.knowledge_base_id, owner_id=current_user.user_id
        )
        is None
    ):
        raise HTTPException(status_code=404, detail="Baza wiedzy nie istnieje.")

    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.interaction_repo import (
            PostgresInteractionStore,
        )

        store = PostgresInteractionStore(session)
        interaction_id = await store.create_interaction(
            interaction_type="chat",
            user_id=current_user.user_id,
            kb_id=payload.knowledge_base_id,
        )
        await session.commit()

    return ChatSessionResponse(
        session_id=interaction_id,
        knowledge_base_id=payload.knowledge_base_id,
        created_at=datetime.now(timezone.utc),
        turns=[],
    )


@router.post("/sessions/{session_id}/messages", response_model=ChatSessionResponse)
async def send_message(
    session_id: UUID,
    payload: ChatMessageRequest,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> ChatSessionResponse:
    """Process a chat message via RAG and return updated session."""
    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.interaction_repo import (
            PostgresInteractionStore,
        )

        store = PostgresInteractionStore(session)
        interaction = await store.get_interaction(session_id)
        if interaction is None or interaction.user_id != current_user.user_id:
            raise HTTPException(status_code=404, detail="Sesja czatu nie istnieje.")

        # Retrieve context
        retrieval = getattr(request.app.state, "retrieval", None)
        context_text = ""
        if retrieval is not None and interaction.knowledge_base_id:
            results = await retrieval.retrieve(
                query=payload.message,
                kb_id=interaction.knowledge_base_id,
                top_k=5,
            )
            context_text = "\n\n".join(r.content for r in results)

        # Generate response
        gateway = request.app.state.gateway
        messages = [
            {
                "role": "system",
                "content": (
                    "Jesteś asystentem edukacyjnym MindForge. "
                    "Odpowiadaj na pytania na podstawie poniższego kontekstu.\n\n"
                    f"Kontekst:\n{context_text}"
                    if context_text
                    else "Jesteś asystentem edukacyjnym MindForge."
                ),
            }
        ]
        # Add turn history (last 10 turns)
        for turn in interaction.turns[-10:]:
            messages.append({"role": turn.role, "content": turn.content})
        messages.append({"role": "user", "content": payload.message})

        from mindforge.domain.models import DeadlineProfile

        completion = await gateway.complete(
            model="small",
            messages=messages,
            deadline=DeadlineProfile.INTERACTIVE,
        )

        # Record turns (no grounding context in output_data)
        await store.add_turn(
            session_id,
            actor_type="user",
            actor_id=str(current_user.user_id),
            action="chat_message",
            input_data={"message": payload.message},
        )
        await store.add_turn(
            session_id,
            actor_type="assistant",
            actor_id="mindforge",
            action="chat_response",
            output_data={"response": completion.content},
            cost=completion.cost_usd,
        )
        await session.commit()

        # Reload interaction for response
        updated = await store.get_interaction(session_id)

    turns = [
        ChatTurnResponse(
            role=t.role,
            content=t.content,
            created_at=t.created_at,
        )
        for t in (updated.turns if updated else [])
    ]
    return ChatSessionResponse(
        session_id=session_id,
        knowledge_base_id=interaction.knowledge_base_id,
        created_at=interaction.created_at,
        turns=turns,
    )


@router.get("/sessions", response_model=list[ChatSessionResponse])
async def list_sessions(
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
    limit: int = 20,
    offset: int = 0,
) -> list[ChatSessionResponse]:
    async with request.app.state.session_factory() as session:
        from mindforge.infrastructure.persistence.interaction_repo import (
            PostgresInteractionStore,
        )

        store = PostgresInteractionStore(session)
        interactions = await store.list_for_user(
            current_user.user_id, limit=limit, offset=offset
        )

    return [
        ChatSessionResponse(
            session_id=i.interaction_id,
            knowledge_base_id=i.knowledge_base_id,
            created_at=i.created_at,
            turns=[
                ChatTurnResponse(
                    role=t.role, content=t.content, created_at=t.created_at
                )
                for t in i.turns
            ],
        )
        for i in interactions
        if i.interaction_type == "chat"
    ]


async def _open_session(request):
    async with request.app.state.session_factory() as session:
        return session
