"""
Quiz router — generate questions and evaluate answers via graph-RAG.

The quiz flow is server-authoritative:
  POST /api/quiz/start  → returns questions identified by (session_id, question_id).
                          Grounding context and reference answers are stored
                          server-side only — the browser never receives them.
  POST /api/quiz/answer → accepts (session_id, question_id, user_answer).
                          The server looks up context and reference_answer from
                          the session store; the client cannot influence grading.
"""
from __future__ import annotations

import random
from typing import Any

from fastapi import APIRouter, Depends, HTTPException

from api.auth import require_auth
from api.deps import get_async_llm_client, get_settings, get_neo4j_driver
from api.quiz_session_store import quiz_session_store
from api.schemas import (
    QuizAnswerRequest,
    QuizEvaluationResponse,
    QuizQuestionResponse,
    QuizStartRequest,
    UserInfo,
)

router = APIRouter(prefix="/api/quiz", tags=["quiz"])


@router.post("/start", response_model=list[QuizQuestionResponse])
async def start_quiz(
    body: QuizStartRequest,
    driver: Any = Depends(get_neo4j_driver),
    llm: Any = Depends(get_async_llm_client),
    config: Any = Depends(get_settings),
    user: UserInfo = Depends(require_auth),
):
    """Generate quiz questions, store them server-side, and return browser-safe payloads."""
    import asyncio
    from processor.tools.graph_rag import (
        get_all_concepts,
        get_lesson_concepts,
        retrieve,
    )
    from processor.agents.quiz_evaluator import build_context, generate_question_async

    if body.lesson:
        concepts = await asyncio.to_thread(get_lesson_concepts, driver, body.lesson)
    else:
        concepts = await asyncio.to_thread(get_all_concepts, driver)

    if not concepts:
        raise HTTPException(
            status_code=404,
            detail="No concepts found. Index lessons first.",
        )

    selected = random.sample(concepts, min(body.count, len(concepts)))
    model = config.model_large

    # Build and store all questions, then return only the browser-safe subset.
    session_questions: list[dict] = []
    browser_questions: list[QuizQuestionResponse] = []

    for idx, topic_data in enumerate(selected):
        topic = topic_data["name"]
        result = await asyncio.to_thread(retrieve, driver, topic, max_results=8)
        context = build_context(result, topic)

        try:
            question = await generate_question_async(topic, context, llm, model)
            question.source_lessons = result.source_lessons
        except Exception:
            continue

        session_questions.append({
            "question_id": idx,
            "text": question.text,
            "topic": topic,
            "question_type": question.question_type,
            "options": question.options,
            "source_lessons": question.source_lessons,
            "context": context,
            "reference_answer": question.reference_answer,
        })

    if not session_questions:
        raise HTTPException(status_code=500, detail="Failed to generate any questions")

    session_id = quiz_session_store.create_session(
        user_id=user.discord_id,
        questions=session_questions,
    )

    for sq in session_questions:
        browser_questions.append(QuizQuestionResponse(
            session_id=session_id,
            question_id=sq["question_id"],
            question=sq["text"],
            topic=sq["topic"],
            question_type=sq["question_type"],
            options=sq["options"],
            source_lessons=sq["source_lessons"],
        ))

    return browser_questions


@router.post("/answer", response_model=QuizEvaluationResponse)
async def answer_question(
    body: QuizAnswerRequest,
    llm: Any = Depends(get_async_llm_client),
    config: Any = Depends(get_settings),
    user: UserInfo = Depends(require_auth),
):
    """Evaluate a user's answer using the server-stored question context."""
    from processor.agents.quiz_evaluator import evaluate_answer_async

    stored = quiz_session_store.get_question(
        user_id=user.discord_id,
        session_id=body.session_id,
        question_id=body.question_id,
    )
    if stored is None:
        raise HTTPException(
            status_code=404,
            detail="Quiz session or question not found. Sessions expire after 2 hours.",
        )

    evaluation = await evaluate_answer_async(
        question=stored.text,
        reference_answer=stored.reference_answer,
        user_answer=body.user_answer,
        context=stored.context,
        llm=llm,
        model=config.model_large,
    )

    return QuizEvaluationResponse(
        score=evaluation.score,
        feedback=evaluation.feedback,
        correct_answer=evaluation.correct_answer,
        grounding_sources=evaluation.grounding_sources,
    )
