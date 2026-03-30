"""
Pydantic schemas for the MindForge REST API.

Maps from processor.models dataclasses to Pydantic models for
request/response validation and OpenAPI documentation.
"""
from __future__ import annotations

from pydantic import BaseModel, Field


# ── Concepts / Graph ────────────────────────────────────────────────


class ConceptNodeSchema(BaseModel):
    id: str
    label: str
    group: str
    color: str


class ConceptEdgeSchema(BaseModel):
    source: str
    target: str
    label: str
    description: str = ""


class ConceptGraphResponse(BaseModel):
    nodes: list[ConceptNodeSchema]
    edges: list[ConceptEdgeSchema]


# ── Lessons ─────────────────────────────────────────────────────────


class LessonSummary(BaseModel):
    number: str
    title: str


class LessonDetail(BaseModel):
    number: str
    title: str
    processed_at: str
    concept_count: int = 0
    flashcard_count: int = 0
    chunk_count: int = 0


class UploadResponse(BaseModel):
    filename: str
    message: str


# ── Quiz ────────────────────────────────────────────────────────────


class QuizStartRequest(BaseModel):
    lesson: str | None = None
    count: int = Field(default=5, ge=1, le=20)


class QuizQuestionResponse(BaseModel):
    """Browser-safe question payload — no grounding context or reference answer."""
    session_id: str
    question_id: int
    question: str
    topic: str
    question_type: str
    options: list[str] | None = None
    source_lessons: list[str]


class QuizAnswerRequest(BaseModel):
    """Answer submission using server-issued IDs — no client-supplied context."""
    session_id: str
    question_id: int
    user_answer: str = Field(min_length=1, max_length=2000)


class QuizEvaluationResponse(BaseModel):
    score: float
    feedback: str
    correct_answer: str
    grounding_sources: list[str]


# ── Flashcards / Spaced Repetition ──────────────────────────────────


class FlashcardSchema(BaseModel):
    id: str
    front: str
    back: str
    card_type: str
    tags: list[str]
    lesson_number: str


class FlashcardReviewSchema(BaseModel):
    """SR state for a single flashcard."""
    id: str
    front: str
    back: str
    card_type: str
    tags: list[str]
    lesson_number: str
    ease: float
    interval: int
    repetitions: int
    due_date: str


class ReviewRequest(BaseModel):
    card_id: str
    rating: int = Field(ge=0, le=3, description="0=Again, 1=Hard, 2=Good, 3=Easy")


class ReviewResponse(BaseModel):
    card_id: str
    new_ease: float
    new_interval: int
    next_due: str


class FlashcardsDueResponse(BaseModel):
    cards: list[FlashcardReviewSchema]
    total_due: int


# ── Search ──────────────────────────────────────────────────────────


class SearchRequest(BaseModel):
    query: str = Field(min_length=1, max_length=500)
    max_results: int = Field(default=10, ge=1, le=50)


class ChunkResult(BaseModel):
    id: str
    text: str
    lesson_number: str
    score: float | None = None


class ConceptResult(BaseModel):
    name: str
    definition: str


class SearchResponse(BaseModel):
    chunks: list[ChunkResult]
    concepts: list[ConceptResult]
    facts: list[str]
    source_lessons: list[str]


# ── Auth ────────────────────────────────────────────────────────────


class UserInfo(BaseModel):
    discord_id: str
    username: str
    avatar: str | None = None


class HealthResponse(BaseModel):
    status: str
    neo4j: str
