"""
Server-side quiz session store for the MindForge REST API.

Stores generated quiz questions (with their grounding context and reference
answers) server-side, keyed by an opaque session ID issued at quiz-start
time.  The browser never receives context or reference answers.

Design
------
* Each session is bound to one authenticated user (Discord ID).  A session
  belonging to user A cannot be accessed with user B's credentials.
* Sessions expire after SESSION_TTL_SECONDS (default 2 hours) of inactivity.
* A background-style cleanup pass runs on every write to evict expired
  sessions, bounding memory use without requiring a separate thread.
* The store is intentionally in-process only (dict-based).  If MindForge
  is ever deployed with multiple workers, replace this with a shared cache
  (e.g. Redis) using the same interface.
"""
from __future__ import annotations

import time
import uuid
from dataclasses import dataclass, field
from typing import Any

SESSION_TTL_SECONDS = 2 * 60 * 60  # 2 hours


@dataclass
class StoredQuestion:
    question_id: int
    text: str
    topic: str
    question_type: str
    options: list[str] | None
    source_lessons: list[str]
    context: str
    reference_answer: str


@dataclass
class QuizSession:
    session_id: str
    user_id: str
    created_at: float
    questions: list[StoredQuestion] = field(default_factory=list)

    def is_expired(self) -> bool:
        return (time.monotonic() - self.created_at) > SESSION_TTL_SECONDS

    def get_question(self, question_id: int) -> StoredQuestion | None:
        for q in self.questions:
            if q.question_id == question_id:
                return q
        return None


class QuizSessionStore:
    """Thread-safe (GIL-protected) in-memory session store."""

    def __init__(self) -> None:
        self._store: dict[str, QuizSession] = {}

    # ------------------------------------------------------------------

    def create_session(
        self,
        user_id: str,
        questions: list[dict[str, Any]],
    ) -> str:
        """Create a new session and return the opaque session_id.

        Parameters
        ----------
        user_id:
            The authenticated user's Discord ID.
        questions:
            List of dicts with keys: question_id, text, topic, question_type,
            options, source_lessons, context, reference_answer.
        """
        self._evict_expired()
        session_id = str(uuid.uuid4())
        stored_qs = [
            StoredQuestion(
                question_id=q["question_id"],
                text=q["text"],
                topic=q["topic"],
                question_type=q["question_type"],
                options=q.get("options"),
                source_lessons=q.get("source_lessons", []),
                context=q["context"],
                reference_answer=q["reference_answer"],
            )
            for q in questions
        ]
        self._store[session_id] = QuizSession(
            session_id=session_id,
            user_id=user_id,
            created_at=time.monotonic(),
            questions=stored_qs,
        )
        return session_id

    def get_question(
        self,
        user_id: str,
        session_id: str,
        question_id: int,
    ) -> StoredQuestion | None:
        """Return the stored question, or None if not found / expired / wrong user."""
        session = self._store.get(session_id)
        if session is None:
            return None
        if session.user_id != user_id:
            return None
        if session.is_expired():
            del self._store[session_id]
            return None
        return session.get_question(question_id)

    # ------------------------------------------------------------------

    def _evict_expired(self) -> None:
        expired = [sid for sid, s in self._store.items() if s.is_expired()]
        for sid in expired:
            del self._store[sid]


# Module-level singleton — shared across all requests in the same process.
quiz_session_store = QuizSessionStore()
