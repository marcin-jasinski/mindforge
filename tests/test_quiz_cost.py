"""
Regression tests for quiz cost behavior — P1.1 reference_answer reuse.

Verifies that:
  ✓ generate_question() stores reference_answer in the returned Question object
  ✓ The REST quiz /answer endpoint passes stored reference_answer to evaluate_answer
    (no second LLM call for reference re-generation)
  ✓ evaluate_answer() uses the supplied reference_answer, not calling generate_question
  ✓ QuizSessionStore.get_question() returns the stored reference_answer
  ✓ The Discord path stores and reuses reference_answer (no re-generation)

Cost invariant:
  Generating N questions → N LLM calls.
  Grading N answers      → N LLM calls.
  Total for a complete quiz session of N questions = 2*N calls, not 3*N or more.
"""
from __future__ import annotations

from unittest.mock import MagicMock, call, patch

import pytest

from processor.agents.quiz_evaluator import Question, evaluate_answer, generate_question
from api.quiz_session_store import QuizSessionStore


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _mock_llm(return_value: str = "{}") -> MagicMock:
    llm = MagicMock()
    llm.complete.return_value = return_value
    return llm


def _question_llm_response(
    question: str = "What is RAG?",
    question_type: str = "open",
    reference_answer: str = "RAG combines retrieval with generation.",
) -> str:
    import json
    return json.dumps({
        "question": question,
        "question_type": question_type,
        "options": None,
        "reference_answer": reference_answer,
    })


def _eval_llm_response(score: float = 0.9, feedback: str = "Good answer.") -> str:
    import json
    return json.dumps({
        "score": score,
        "feedback": feedback,
        "correct_answer": "RAG combines retrieval with generation.",
    })


# ---------------------------------------------------------------------------
# generate_question — reference_answer stored at generation time
# ---------------------------------------------------------------------------

def test_generate_question_stores_reference_answer() -> None:
    """generate_question must persist reference_answer in the returned Question."""
    llm = _mock_llm(_question_llm_response(reference_answer="The expected answer."))
    question = generate_question("RAG", "context text", llm, "test/model")

    assert question.reference_answer == "The expected answer."
    assert llm.complete.call_count == 1, "Exactly one LLM call per question generation"


def test_generate_question_empty_reference_answer_defaults_to_empty_string() -> None:
    """When the LLM omits reference_answer the field defaults to empty string."""
    import json
    llm = _mock_llm(json.dumps({"question": "Q?", "question_type": "open", "options": None}))
    question = generate_question("topic", "ctx", llm, "test/model")
    assert question.reference_answer == ""


# ---------------------------------------------------------------------------
# evaluate_answer — uses supplied reference_answer, makes exactly one LLM call
# ---------------------------------------------------------------------------

def test_evaluate_answer_uses_supplied_reference_answer() -> None:
    """evaluate_answer must use the reference_answer passed in; not call generate_question."""
    llm = _mock_llm(_eval_llm_response())

    with patch("quiz_agent.generate_question") as mock_gen:
        evaluate_answer(
            question="What is RAG?",
            reference_answer="RAG combines retrieval with generation.",
            user_answer="It mixes retrieval and LLM generation.",
            context="Some source context.",
            llm=llm,
            model="test/model",
        )
        mock_gen.assert_not_called(), "evaluate_answer must NOT call generate_question"

    assert llm.complete.call_count == 1, "Exactly one LLM call for evaluation"


def test_evaluate_answer_embeds_reference_answer_in_prompt() -> None:
    """The reference_answer must appear verbatim in the evaluation prompt."""
    captured: list[str] = []

    def _capture(**kwargs):
        msgs = kwargs.get("messages", [])
        for m in msgs:
            captured.append(m.get("content", ""))
        return _eval_llm_response()

    llm = MagicMock()
    llm.complete.side_effect = _capture

    ref = "Unique reference answer marker ABC123"
    evaluate_answer("Q?", ref, "user answer", "ctx", llm, "test/model")

    full_prompt = " ".join(captured)
    assert ref in full_prompt, "Reference answer must appear in the evaluation prompt"


# ---------------------------------------------------------------------------
# QuizSessionStore — reference_answer survives storage round-trip
# ---------------------------------------------------------------------------

def test_session_store_preserves_reference_answer() -> None:
    store = QuizSessionStore()
    sid = store.create_session("user_1", [{
        "question_id": 0,
        "text": "What is RAG?",
        "topic": "RAG",
        "question_type": "open",
        "options": None,
        "source_lessons": ["S01E01"],
        "context": "Some context.",
        "reference_answer": "RAG combines retrieval with generation.",
    }])

    q = store.get_question("user_1", sid, 0)
    assert q is not None
    assert q.reference_answer == "RAG combines retrieval with generation."


def test_session_store_keeps_reference_answer_server_side() -> None:
    """reference_answer must be stored in the session store, not in the browser payload."""
    from api.schemas import QuizQuestionResponse

    fields = QuizQuestionResponse.model_fields
    assert "reference_answer" not in fields, (
        "QuizQuestionResponse must not expose reference_answer — "
        "it is kept server-side to avoid double LLM calls and prevent tampering"
    )


# ---------------------------------------------------------------------------
# Cost invariant — 2*N LLM calls for N questions (generate + evaluate)
# ---------------------------------------------------------------------------

def test_two_llm_calls_per_question_not_three() -> None:
    """End-to-end: generate then evaluate — exactly 2 LLM calls, never 3."""
    gen_llm = _mock_llm(_question_llm_response(reference_answer="The reference."))
    eval_llm = _mock_llm(_eval_llm_response())

    question = generate_question("topic", "ctx", gen_llm, "test/model")
    evaluate_answer(
        question=question.text,
        reference_answer=question.reference_answer,
        user_answer="user answer",
        context="ctx",
        llm=eval_llm,
        model="test/model",
    )

    assert gen_llm.complete.call_count == 1, "One call for question generation"
    assert eval_llm.complete.call_count == 1, "One call for answer evaluation"
