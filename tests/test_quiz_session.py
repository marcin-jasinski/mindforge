"""
Tests for api.quiz_session_store and the P0.1 quiz flow invariants.

Covers:
  ✓ Session creation returns a unique opaque session_id
  ✓ Questions retrieved by correct (user, session, question_id) succeed
  ✓ Questions are not accessible by a different user (cross-user isolation)
  ✓ Missing session returns None
  ✓ Missing question_id within a valid session returns None
  ✓ Expired sessions are rejected
  ✓ QuizAnswerRequest enforces max_length on user_answer
  ✓ QuizAnswerRequest rejects empty user_answer
  ✓ QuizQuestionResponse does not expose context or reference_answer fields
"""
from __future__ import annotations

import time
from unittest.mock import patch

import pytest
from pydantic import ValidationError

from api.quiz_session_store import (
    QuizSession,
    QuizSessionStore,
    SESSION_TTL_SECONDS,
)
from api.schemas import QuizAnswerRequest, QuizQuestionResponse


# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------

def _make_question(question_id: int = 0) -> dict:
    return {
        "question_id": question_id,
        "text": "What is RAG?",
        "topic": "RAG",
        "question_type": "open",
        "options": None,
        "source_lessons": ["S01E01"],
        "context": "Retrieval Augmented Generation is a technique...",
        "reference_answer": "RAG combines retrieval with generation.",
    }


# ---------------------------------------------------------------------------
# QuizSessionStore — happy path
# ---------------------------------------------------------------------------

def test_create_session_returns_session_id() -> None:
    store = QuizSessionStore()
    sid = store.create_session("user_1", [_make_question()])
    assert isinstance(sid, str)
    assert len(sid) == 36  # UUID4


def test_get_question_returns_stored_data() -> None:
    store = QuizSessionStore()
    sid = store.create_session("user_1", [_make_question(0)])
    q = store.get_question("user_1", sid, 0)
    assert q is not None
    assert q.text == "What is RAG?"
    assert q.reference_answer == "RAG combines retrieval with generation."
    assert q.context == "Retrieval Augmented Generation is a technique..."


def test_multiple_questions_in_session() -> None:
    store = QuizSessionStore()
    questions = [_make_question(i) for i in range(3)]
    sid = store.create_session("user_1", questions)
    for i in range(3):
        q = store.get_question("user_1", sid, i)
        assert q is not None
        assert q.question_id == i


# ---------------------------------------------------------------------------
# QuizSessionStore — isolation and security
# ---------------------------------------------------------------------------

def test_different_user_cannot_access_session() -> None:
    store = QuizSessionStore()
    sid = store.create_session("user_1", [_make_question()])
    result = store.get_question("user_2", sid, 0)
    assert result is None, "Another user must not access this session"


def test_unknown_session_id_returns_none() -> None:
    store = QuizSessionStore()
    result = store.get_question("user_1", "00000000-0000-0000-0000-000000000000", 0)
    assert result is None


def test_unknown_question_id_returns_none() -> None:
    store = QuizSessionStore()
    sid = store.create_session("user_1", [_make_question(0)])
    result = store.get_question("user_1", sid, 99)
    assert result is None


def test_expired_session_returns_none() -> None:
    store = QuizSessionStore()
    sid = store.create_session("user_1", [_make_question()])

    # Patch the session's created_at so it appears expired
    session = store._store[sid]
    session.created_at = time.monotonic() - SESSION_TTL_SECONDS - 1

    result = store.get_question("user_1", sid, 0)
    assert result is None
    assert sid not in store._store, "Expired session should be evicted"


def test_evict_expired_removes_old_sessions() -> None:
    store = QuizSessionStore()
    sid1 = store.create_session("user_1", [_make_question()])
    sid2 = store.create_session("user_2", [_make_question()])

    # Expire sid1
    store._store[sid1].created_at = time.monotonic() - SESSION_TTL_SECONDS - 1

    # Trigger eviction by creating a new session
    store.create_session("user_3", [_make_question()])

    assert sid1 not in store._store
    assert sid2 in store._store


# ---------------------------------------------------------------------------
# QuizAnswerRequest schema — input size limits
# ---------------------------------------------------------------------------

def test_answer_request_accepts_valid_payload() -> None:
    req = QuizAnswerRequest(
        session_id="abc",
        question_id=0,
        user_answer="This is a valid answer.",
    )
    assert req.user_answer == "This is a valid answer."


def test_answer_request_rejects_empty_answer() -> None:
    with pytest.raises(ValidationError):
        QuizAnswerRequest(session_id="abc", question_id=0, user_answer="")


def test_answer_request_rejects_oversized_answer() -> None:
    with pytest.raises(ValidationError):
        QuizAnswerRequest(
            session_id="abc",
            question_id=0,
            user_answer="x" * 2001,
        )


def test_answer_request_accepts_max_length_answer() -> None:
    req = QuizAnswerRequest(
        session_id="abc",
        question_id=0,
        user_answer="x" * 2000,
    )
    assert len(req.user_answer) == 2000


# ---------------------------------------------------------------------------
# QuizQuestionResponse — browser-safe fields only
# ---------------------------------------------------------------------------

def test_question_response_has_no_context_field() -> None:
    """context must not be serialisable via the browser-facing schema."""
    fields = QuizQuestionResponse.model_fields
    assert "context" not in fields, \
        "QuizQuestionResponse must not expose grounding context to the browser"


def test_question_response_has_no_reference_answer_field() -> None:
    fields = QuizQuestionResponse.model_fields
    assert "reference_answer" not in fields, \
        "QuizQuestionResponse must not expose reference_answer to the browser"


def test_question_response_has_session_and_question_ids() -> None:
    resp = QuizQuestionResponse(
        session_id="test-session",
        question_id=0,
        question="What is RAG?",
        topic="RAG",
        question_type="open",
        options=None,
        source_lessons=["S01E01"],
    )
    assert resp.session_id == "test-session"
    assert resp.question_id == 0
