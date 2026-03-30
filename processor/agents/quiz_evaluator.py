"""
Quiz evaluator — reusable quiz logic extracted from quiz_agent.py (CRITICAL-2).

This module is the single home for:
  - Question / Evaluation / SessionResult dataclasses
  - build_context()       — builds LLM prompt context from retrieval result
  - generate_question()   — creates a Question grounded in retrieved context
  - evaluate_answer()     — scores a user's answer against the reference

quiz_agent.py uses this module for its CLI loop.
api/routers/quiz.py and discord_bot/cogs/quiz.py import from here —
no file may import directly from quiz_agent.py.
"""
from __future__ import annotations

import json
import logging
from dataclasses import dataclass

from processor.llm_client import LLMClient

log = logging.getLogger(__name__)


# ── Data models ─────────────────────────────────────────────────────────────


@dataclass
class Question:
    text: str
    topic: str
    question_type: str  # "open", "multiple_choice", "true_false"
    grounding_chunks: list[str]
    source_lessons: list[str]
    options: list[str] | None = None  # for MC questions
    reference_answer: str = ""  # generated at question-creation time; never sent to the client


@dataclass
class Evaluation:
    score: float  # 0.0–1.0
    feedback: str
    correct_answer: str
    grounding_sources: list[str]


@dataclass
class SessionResult:
    total_questions: int
    answered: int
    average_score: float
    evaluations: list[dict]


# ── System prompts ───────────────────────────────────────────────────────────

QUESTION_SYSTEM_PROMPT = """\
Jesteś egzaminatorem. Na podstawie podanego kontekstu (fragmenty materiałów,
pojęcia, fakty) wygeneruj JEDNO pytanie sprawdzające zrozumienie materiału.

Zasady:
- Pytanie musi być UGRUNTOWANE w podanym kontekście — nie wymyślaj informacji
- Testuj ZROZUMIENIE, nie pamięciowe odtwarzanie tekstu
- Uwzględniaj pytania o praktyczne zastosowania i konsekwencje
- Odpowiadaj w języku kontekstu

Zwróć odpowiedź w formacie JSON:
{
  "question": "Treść pytania",
  "question_type": "open" | "multiple_choice" | "true_false",
  "options": ["A) ...", "B) ...", "C) ...", "D) ..."],
  "reference_answer": "Wzorcowa odpowiedź na podstawie kontekstu (2-4 zdania)"
}

Dla pytań otwartych: options = null.
Dla prawda/fałsz: options = ["Prawda", "Fałsz"].
Dla wielokrotnego wyboru: 4 opcje, dokładnie 1 poprawna.\
"""

EVAL_SYSTEM_PROMPT = """\
Jesteś egzaminatorem oceniającym odpowiedź studenta.

Masz dostęp do:
- Pytania
- Wzorcowej odpowiedzi (opartej na materiałach źródłowych)
- Odpowiedzi studenta
- Kontekstu źródłowego

Zasady oceny:
- Oceń MERYTORYCZNIE — czy student rozumie koncepcję
- Nie wymagaj dosłownego powtórzenia tekstu
- Akceptuj poprawne odpowiedzi wyrażone innymi słowami
- Bądź sprawiedliwy, ale wymagający

Zwróć odpowiedź w formacie JSON:
{
  "score": 0.0-1.0,
  "feedback": "Konstruktywna informacja zwrotna w języku pytania (2-3 zdania)",
  "correct_answer": "Poprawna odpowiedź na podstawie źródeł"
}\
"""


# ── Core functions ───────────────────────────────────────────────────────────


def build_context(result: object, topic: str) -> str:
    """Build context text from a graph-RAG retrieval result.

    Works with any object that exposes ``.concepts``, ``.chunks``, and ``.facts``
    attributes matching the shape returned by ``processor.tools.graph_rag.retrieve``.
    """
    parts: list[str] = [f"Temat: {topic}\n"]

    concepts = getattr(result, "concepts", [])
    if concepts:
        parts.append("Pojęcia:")
        for c in concepts:
            parts.append(f"- {c['name']}: {c.get('definition', '')}")
        parts.append("")

    chunks = getattr(result, "chunks", [])
    if chunks:
        parts.append("Fragmenty lekcji:")
        for chunk in chunks[:5]:  # Limit to 5 chunks
            parts.append(f"[{chunk.get('lesson_number', '?')}] {chunk['text']}")
            parts.append("---")
        parts.append("")

    facts = getattr(result, "facts", [])
    if facts:
        parts.append("Kluczowe fakty:")
        for fact in facts[:5]:
            parts.append(f"- {fact}")

    return "\n".join(parts)


def generate_question(
    topic: str,
    context: str,
    llm: LLMClient,
    model: str,
) -> Question:
    """Generate a single question based on retrieved context.

    The ``reference_answer`` field of the returned :class:`Question` is stored
    server-side only — it must not be serialized in browser-facing API responses.
    """
    response = llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": QUESTION_SYSTEM_PROMPT},
            {"role": "user", "content": context},
        ],
        temperature=0.7,
        response_format={"type": "json_object"},
    )

    data = json.loads(response)
    return Question(
        text=data["question"],
        topic=topic,
        question_type=data.get("question_type", "open"),
        grounding_chunks=[],
        source_lessons=[],
        options=data.get("options"),
        reference_answer=data.get("reference_answer", ""),
    )


async def generate_question_async(
    topic: str,
    context: str,
    llm: object,
    model: str,
) -> Question:
    """Async variant of :func:`generate_question` for use in route handlers.

    ``llm`` should be an :class:`~processor.llm_client.AsyncLLMClient`.
    """
    response = await llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": QUESTION_SYSTEM_PROMPT},
            {"role": "user", "content": context},
        ],
        temperature=0.7,
        response_format={"type": "json_object"},
    )

    data = json.loads(response)
    return Question(
        text=data["question"],
        topic=topic,
        question_type=data.get("question_type", "open"),
        grounding_chunks=[],
        source_lessons=[],
        options=data.get("options"),
        reference_answer=data.get("reference_answer", ""),
    )


def evaluate_answer(
    question: str,
    reference_answer: str,
    user_answer: str,
    context: str,
    llm: LLMClient,
    model: str,
) -> Evaluation:
    """Evaluate a user's answer against source material.

    Reuses the ``reference_answer`` generated at question-creation time —
    never regenerates it (one LLM call per evaluation, not two).
    """
    user_message = (
        f"## Pytanie\n{question}\n\n"
        f"## Wzorcowa odpowiedź\n{reference_answer}\n\n"
        f"## Odpowiedź studenta\n{user_answer}\n\n"
        f"## Kontekst źródłowy\n{context}\n"
    )

    response = llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": EVAL_SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        temperature=0.0,
        response_format={"type": "json_object"},
    )

    data = json.loads(response)
    return Evaluation(
        score=float(data.get("score", 0.0)),
        feedback=data.get("feedback", ""),
        correct_answer=data.get("correct_answer", reference_answer),
        grounding_sources=[],
    )


async def evaluate_answer_async(
    question: str,
    reference_answer: str,
    user_answer: str,
    context: str,
    llm: object,
    model: str,
) -> Evaluation:
    """Async variant of :func:`evaluate_answer` for use in route handlers.

    ``llm`` should be an :class:`~processor.llm_client.AsyncLLMClient`.
    """
    user_message = (
        f"## Pytanie\n{question}\n\n"
        f"## Wzorcowa odpowiedź\n{reference_answer}\n\n"
        f"## Odpowiedź studenta\n{user_answer}\n\n"
        f"## Kontekst źródłowy\n{context}\n"
    )

    response = await llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": EVAL_SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        temperature=0.0,
        response_format={"type": "json_object"},
    )

    data = json.loads(response)
    return Evaluation(
        score=float(data.get("score", 0.0)),
        feedback=data.get("feedback", ""),
        correct_answer=data.get("correct_answer", reference_answer),
        grounding_sources=[],
    )
