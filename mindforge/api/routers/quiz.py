"""Quiz router — server-authoritative session management and answer evaluation.

Security: reference_answer and grounding_context are NEVER sent to the client.
All grading is done server-side.
"""

from __future__ import annotations

import logging
from datetime import datetime, timezone
from typing import Annotated
from uuid import UUID, uuid4

from fastapi import APIRouter, Depends, HTTPException, Request, status

from mindforge.api.deps import get_current_user, get_kb_repo
from mindforge.api.schemas import (
    AnswerEvaluationResponse,
    QuizQuestionResponse,
    StartQuizRequest,
    SubmitAnswerRequest,
)
from mindforge.domain.models import QuizSession, User

log = logging.getLogger(__name__)
router = APIRouter(prefix="/api/knowledge-bases/{kb_id}/quiz", tags=["quiz"])


@router.post("/start", response_model=QuizQuestionResponse)
async def start_quiz(
    kb_id: UUID,
    payload: StartQuizRequest,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> QuizQuestionResponse:
    """Start a new quiz session and return the first question (no answers)."""
    kb_repo = get_kb_repo(request, await _open_session(request))
    if await kb_repo.get_by_id(kb_id, owner_id=current_user.user_id) is None:
        raise HTTPException(status_code=404, detail="Baza wiedzy nie istnieje.")

    retrieval = getattr(request.app.state, "retrieval", None)
    if retrieval is None:
        raise HTTPException(
            status_code=503,
            detail="Graf wiedzy nie jest dostępny. Przetwórz najpierw dokumenty.",
        )

    from datetime import date

    weak_concepts = await retrieval.find_weak_concepts(
        current_user.user_id, kb_id, today=date.today(), limit=5
    )
    if not weak_concepts:
        raise HTTPException(
            status_code=404,
            detail="Brak dostępnych konceptów do quizu. Dodaj i przetwórz dokumenty.",
        )

    # Select the weakest concept and retrieve its neighbourhood
    target = weak_concepts[0]
    neighbourhood = await retrieval.retrieve_concept_neighborhood(
        kb_id=kb_id, concept_key=target.key, depth=2
    )

    # Generate question via QuizGeneratorAgent
    gateway = request.app.state.gateway
    from mindforge.agents.quiz_generator import QuizGeneratorAgent
    from mindforge.domain.agents import AgentContext

    ctx = AgentContext(
        document_id=uuid4(),  # not tied to a specific document
        knowledge_base_id=kb_id,
        artifact=None,  # type: ignore[arg-type]
        gateway=gateway,
        retrieval=retrieval,
        settings=request.app.state.settings,
        tracer=None,
        metadata={"concept_neighbourhood": neighbourhood},
    )
    agent = QuizGeneratorAgent()
    result = await agent.execute(ctx)
    if not result.success:
        raise HTTPException(
            status_code=500, detail="Nie udało się wygenerować pytania."
        )

    question = result.output_data.get("question")  # type: ignore[union-attr]
    if question is None:
        raise HTTPException(status_code=500, detail="Agent nie zwrócił pytania.")

    # Persist session (server-side, with reference_answer stored securely)
    session_id = uuid4()
    quiz_session_store = request.app.state.quiz_session_store
    settings = request.app.state.settings
    now = datetime.now(timezone.utc)
    from mindforge.domain.models import QuizQuestion

    quiz_q = QuizQuestion(
        question_id=str(uuid4()),
        question_text=question.get("question_text", ""),
        question_type=question.get("question_type", "open"),
        reference_answer=question.get("reference_answer", ""),
        grounding_context=question.get("grounding_context", ""),
        lesson_id=target.key,
    )
    quiz_session = QuizSession(
        session_id=session_id,
        user_id=current_user.user_id,
        kb_id=kb_id,
        questions=[quiz_q],
        created_at=now,
        expires_at=datetime.fromtimestamp(
            now.timestamp() + settings.quiz_session_ttl_seconds, tz=timezone.utc
        ),
    )
    await quiz_session_store.create_session(quiz_session)

    return QuizQuestionResponse(
        session_id=session_id,
        question_id=quiz_q.question_id,
        question_text=quiz_q.question_text,
        question_type=quiz_q.question_type,
        lesson_id=quiz_q.lesson_id,
    )


@router.post("/submit", response_model=AnswerEvaluationResponse)
async def submit_answer(
    kb_id: UUID,
    payload: SubmitAnswerRequest,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> AnswerEvaluationResponse:
    """Evaluate a user answer server-side. Never returns reference_answer."""
    quiz_session_store = request.app.state.quiz_session_store

    # The session_id must be provided as a query parameter or derived from payload.
    # For simplicity, the client should pass session_id in the request body.
    # We use question_id to find the right session via the store.
    raise HTTPException(
        status_code=501,
        detail="Submit answer requires session_id. Use POST /quiz/{session_id}/answer.",
    )


@router.post("/{session_id}/answer", response_model=AnswerEvaluationResponse)
async def submit_session_answer(
    kb_id: UUID,
    session_id: UUID,
    payload: SubmitAnswerRequest,
    request: Request,
    current_user: Annotated[User, Depends(get_current_user)],
) -> AnswerEvaluationResponse:
    """Server-authoritative answer evaluation. Never exposes reference_answer."""
    quiz_session_store = request.app.state.quiz_session_store
    quiz_session: QuizSession | None = await quiz_session_store.get_session(session_id)

    if quiz_session is None:
        raise HTTPException(
            status_code=404, detail="Sesja quizu nie istnieje lub wygasła."
        )
    if quiz_session.user_id != current_user.user_id:
        raise HTTPException(status_code=403, detail="Brak dostępu do tej sesji.")

    # Find the question
    question = next(
        (q for q in quiz_session.questions if q.question_id == payload.question_id),
        None,
    )
    if question is None:
        raise HTTPException(status_code=404, detail="Pytanie nie istnieje w tej sesji.")

    # Evaluate using QuizEvaluatorAgent with stored reference_answer (never regenerated)
    gateway = request.app.state.gateway
    from mindforge.agents.quiz_evaluator import QuizEvaluatorAgent
    from mindforge.domain.agents import AgentContext

    ctx = AgentContext(
        document_id=uuid4(),
        knowledge_base_id=kb_id,
        artifact=None,  # type: ignore[arg-type]
        gateway=gateway,
        retrieval=None,  # type: ignore[arg-type]
        settings=request.app.state.settings,
        tracer=None,
        metadata={
            "user_answer": payload.user_answer,
            "reference_answer": question.reference_answer,
            "grounding_context": question.grounding_context,
        },
    )
    agent = QuizEvaluatorAgent()
    result = await agent.execute(ctx)
    if not result.success:
        raise HTTPException(status_code=500, detail="Nie udało się ocenić odpowiedzi.")

    eval_data = result.output_data or {}  # type: ignore[union-attr]
    score = float(eval_data.get("score", 0.0))
    feedback = eval_data.get("feedback", "")
    is_correct = score >= 0.6

    # Update SR state
    async with request.app.state.session_factory() as session:
        from mindforge.domain.models import ReviewResult
        from mindforge.infrastructure.persistence.study_progress_repo import (
            PostgresStudyProgressRepository,
        )

        sr_repo = PostgresStudyProgressRepository(session)
        rating = min(5, max(0, round(score * 5)))
        await sr_repo.save_review(
            user_id=current_user.user_id,
            kb_id=kb_id,
            card_id=question.question_id,
            result=ReviewResult(rating=rating),
        )
        await session.commit()

    # Delete session after answer
    await quiz_session_store.delete_session(session_id)

    return AnswerEvaluationResponse(
        question_id=payload.question_id,
        score=score,
        feedback=feedback,
        is_correct=is_correct,
    )


async def _open_session(request):
    async with request.app.state.session_factory() as session:
        return session
