"""
Canonical data models for the lesson processing pipeline.

LessonArtifact is the single source of truth for a processed lesson.
All generators populate sections of this artifact, and all outputs
(markdown, TSV, Mermaid, JSON) are rendered from it.
"""
from __future__ import annotations

import re
import uuid
from dataclasses import asdict, dataclass, field
from datetime import datetime, timezone
from typing import Any


def extract_lesson_number(filename: str) -> str:
    """Extract lesson number from filename (e.g. 's01e05.md' -> 'S01E05').

    Returns the empty string when no SxxExx pattern is found.
    Callers that previously received ``'unknown'`` should treat ``''`` the same
    way — do not use the result as a storage key.
    """
    match = re.match(r"s(\d+)e(\d+)", filename, re.IGNORECASE)
    if match:
        return f"S{match.group(1).zfill(2)}E{match.group(2).zfill(2)}"
    return ""


# ── Lesson identity (CRITICAL-6) ─────────────────────────────────────────────


@dataclass(frozen=True)
class LessonIdentity:
    """Stable, immutable identity for a lesson.

    ``lesson_id`` is the canonical primary key — a UUID string that is always
    present and never derived from a filename convention.

    ``lesson_number`` is optional human-facing metadata (e.g. ``"S01E01"``)
    used only for display and filtering.  It is ``None`` for uploads whose
    filenames do not match the SxxExx pattern.
    """

    lesson_id: str
    lesson_number: str | None


def resolve_lesson_identity(
    source_filename: str,
    metadata: dict[str, Any] | None = None,
    *,
    existing_lesson_id: str | None = None,
) -> LessonIdentity:
    """Resolve the canonical identity for a lesson at intake.

    If ``existing_lesson_id`` is supplied (e.g. when reprocessing an artifact
    that already has an ID), it is used unchanged.  Otherwise a new UUID is
    generated.

    ``lesson_number`` is derived from the filename via :func:`extract_lesson_number`
    and is ``None`` when the filename does not match the SxxExx pattern.
    """
    if metadata is None:
        metadata = {}

    lesson_id = existing_lesson_id or str(uuid.uuid4())
    raw_number = extract_lesson_number(source_filename)
    lesson_number = raw_number if raw_number else None

    return LessonIdentity(lesson_id=lesson_id, lesson_number=lesson_number)


@dataclass
class ImageDescription:
    url: str
    alt: str
    description: str


@dataclass
class ArticleData:
    url: str
    text: str
    content: str


@dataclass
class ConceptEntry:
    name: str
    definition: str


@dataclass
class LinkEntry:
    name: str
    url: str
    description: str


@dataclass
class SummaryData:
    overview: str
    key_concepts: list[ConceptEntry]
    key_facts: list[str]
    practical_tips: list[str]
    important_links: list[LinkEntry]


@dataclass
class FlashcardData:
    front: str
    back: str
    card_type: str  # "basic", "cloze", "reverse"
    tags: list[str]


@dataclass
class ConceptNode:
    id: str
    label: str
    group: str
    color: str  # "green", "blue", "orange", "purple"


@dataclass
class ConceptRelation:
    source_id: str
    target_id: str
    label: str
    description: str


@dataclass
class ConceptGroup:
    name: str
    node_ids: list[str]


@dataclass
class ConceptMapData:
    nodes: list[ConceptNode]
    relationships: list[ConceptRelation]
    groups: list[ConceptGroup]


@dataclass
class StudyPack:
    """Assessment manifest for the quiz agent — no pre-generated answers."""

    lesson_number: str
    title: str
    topic_count: int
    topics: list[str]
    chunk_count: int
    graph_indexed: bool


@dataclass
class LessonArtifact:
    """Canonical representation of a processed lesson — single source of truth."""

    title: str
    source_filename: str
    lesson_number: str  # display/filter label; may be empty for non-SxxExx filenames
    processed_at: str
    metadata: dict[str, Any]
    cleaned_content: str
    lesson_id: str = field(default_factory=lambda: str(uuid.uuid4()))  # stable UUID primary key (CRITICAL-6)
    image_descriptions: list[ImageDescription] = field(default_factory=list)
    articles: list[ArticleData] = field(default_factory=list)
    summary: SummaryData | None = None
    flashcards: list[FlashcardData] = field(default_factory=list)
    concept_map: ConceptMapData | None = None

    @staticmethod
    def create(
        title: str,
        source_filename: str,
        metadata: dict[str, Any],
        cleaned_content: str,
        *,
        existing_lesson_id: str | None = None,
    ) -> "LessonArtifact":
        identity = resolve_lesson_identity(
            source_filename,
            metadata,
            existing_lesson_id=existing_lesson_id,
        )
        return LessonArtifact(
            title=title,
            source_filename=source_filename,
            lesson_number=identity.lesson_number or "",
            lesson_id=identity.lesson_id,
            processed_at=datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
            metadata=metadata,
            cleaned_content=cleaned_content,
        )

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "LessonArtifact":
        """Reconstruct a LessonArtifact from a serialized dict (e.g., loaded from JSON)."""
        summary = None
        if data.get("summary"):
            s = data["summary"]
            summary = SummaryData(
                overview=s.get("overview", ""),
                key_concepts=[ConceptEntry(**c) for c in s.get("key_concepts", [])],
                key_facts=s.get("key_facts", []),
                practical_tips=s.get("practical_tips", []),
                important_links=[LinkEntry(**lk) for lk in s.get("important_links", [])],
            )

        concept_map = None
        if data.get("concept_map"):
            cm = data["concept_map"]
            concept_map = ConceptMapData(
                nodes=[ConceptNode(**n) for n in cm.get("nodes", [])],
                relationships=[ConceptRelation(**r) for r in cm.get("relationships", [])],
                groups=[ConceptGroup(**g) for g in cm.get("groups", [])],
            )

        # Backward compat: older artifacts may use "unknown" for lesson_number; normalise to ""
        lesson_number = data.get("lesson_number", "")
        if lesson_number == "unknown":
            lesson_number = ""

        return cls(
            title=data["title"],
            source_filename=data["source_filename"],
            lesson_number=lesson_number,
            lesson_id=data.get("lesson_id") or str(uuid.uuid4()),  # generate if missing in old artifacts
            processed_at=data["processed_at"],
            metadata=data.get("metadata", {}),
            cleaned_content=data.get("cleaned_content", ""),
            image_descriptions=[ImageDescription(**d) for d in data.get("image_descriptions", [])],
            articles=[ArticleData(**a) for a in data.get("articles", [])],
            summary=summary,
            flashcards=[FlashcardData(**f) for f in data.get("flashcards", [])],
            concept_map=concept_map,
        )
