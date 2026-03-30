"""
Application settings — pure configuration data, no live objects and no secrets.

Separates path/flag configuration from authentication credentials so that routers
and other non-privileged consumers can receive AppSettings without ever seeing
API keys or database passwords.

English field names replace the legacy Polish names
(nowe_dir→new_dir, podsumowane_dir→summaries_dir, archiwum_dir→archive_dir).
"""
from __future__ import annotations

import logging
import os
from dataclasses import dataclass
from pathlib import Path

from dotenv import dotenv_values

log = logging.getLogger(__name__)


@dataclass
class AppSettings:
    """All non-secret application configuration.

    No LLMClient, no API keys, no database passwords.
    Construct via :func:`load_settings`.
    """

    # ── Directories ──────────────────────────────────────────────────
    base_dir: Path
    new_dir: Path          # Inbound lessons waiting to be processed (was: nowe_dir)
    summaries_dir: Path    # Generated summary markdown files (was: podsumowane_dir)
    archive_dir: Path      # Processed source lesson files (was: archiwum_dir)
    flashcards_dir: Path
    quizzes_dir: Path
    diagrams_dir: Path
    knowledge_dir: Path
    state_file: Path

    # ── Model names ──────────────────────────────────────────────────
    model_small: str
    model_large: str
    model_vision: str
    model_embedding: str

    # ── Feature flags ────────────────────────────────────────────────
    enable_image_analysis: bool = True
    enable_flashcards: bool = True
    enable_quizzes: bool = True
    enable_diagrams: bool = True
    enable_knowledge_index: bool = True
    enable_validation: bool = True
    enable_tracing: bool = False
    enable_graph_rag: bool = False
    enable_embeddings: bool = True

    # ── Neo4j connection (non-secret host/user, no password) ─────────
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_username: str = "neo4j"
    neo4j_database: str = "neo4j"


def load_settings(base_dir: Path | None = None) -> AppSettings:
    """Read all application settings from .env / environment.

    Pure function — no side-effects, no live objects created.
    Raises :exc:`ValueError` only when a setting value is structurally invalid,
    not when optional keys are absent.
    """
    if base_dir is None:
        base_dir = Path(__file__).resolve().parent.parent

    env_path = base_dir / ".env"
    env: dict[str, str | None] = dotenv_values(env_path) if env_path.exists() else {}

    def _get(key: str, default: str = "") -> str:
        val = env.get(key)
        if val is None:
            val = os.environ.get(key, default)
        return str(val).strip()

    def _flag(key: str, default: bool = True) -> bool:
        val = _get(key).lower()
        if not val:
            return default
        return val in ("true", "1", "yes")

    model_small = _get("MODEL_SMALL", "openai/gpt-4o-mini")
    model_large = _get("MODEL_LARGE", "openai/gpt-4o")
    model_vision = _get("MODEL_VISION", "openai/gpt-4o")
    model_embedding = _get("MODEL_EMBEDDING", "openai/text-embedding-3-small")

    enable_image_analysis = _flag("ENABLE_IMAGE_ANALYSIS")
    enable_flashcards = _flag("ENABLE_FLASHCARDS")
    enable_quizzes = _flag("ENABLE_QUIZZES")
    enable_diagrams = _flag("ENABLE_DIAGRAMS")
    enable_knowledge_index = _flag("ENABLE_KNOWLEDGE_INDEX")
    enable_validation = _flag("ENABLE_VALIDATION")
    enable_tracing = _flag("ENABLE_TRACING", default=False)
    enable_graph_rag = _flag("ENABLE_GRAPH_RAG", default=False)
    enable_embeddings = _flag("ENABLE_EMBEDDINGS", default=True)

    neo4j_uri = _get("NEO4J_URI", "bolt://localhost:7687")
    neo4j_username = _get("NEO4J_USERNAME", "neo4j")
    neo4j_database = _get("NEO4J_DATABASE", "neo4j")

    new_dir = base_dir / "new"
    summaries_dir = base_dir / "summarized"
    archive_dir = base_dir / "archive"
    flashcards_dir = base_dir / "flashcards"
    quizzes_dir = base_dir / "quizzes"
    diagrams_dir = base_dir / "diagrams"
    knowledge_dir = base_dir / "knowledge"
    state_file = base_dir / "state" / "processed.json"

    # Ensure directories exist on first run
    for d in [
        new_dir, summaries_dir, archive_dir,
        flashcards_dir, quizzes_dir, diagrams_dir, knowledge_dir,
        state_file.parent,
    ]:
        d.mkdir(parents=True, exist_ok=True)

    return AppSettings(
        base_dir=base_dir,
        new_dir=new_dir,
        summaries_dir=summaries_dir,
        archive_dir=archive_dir,
        flashcards_dir=flashcards_dir,
        quizzes_dir=quizzes_dir,
        diagrams_dir=diagrams_dir,
        knowledge_dir=knowledge_dir,
        state_file=state_file,
        model_small=model_small,
        model_large=model_large,
        model_vision=model_vision,
        model_embedding=model_embedding,
        enable_image_analysis=enable_image_analysis,
        enable_flashcards=enable_flashcards,
        enable_quizzes=enable_quizzes,
        enable_diagrams=enable_diagrams,
        enable_knowledge_index=enable_knowledge_index,
        enable_validation=enable_validation,
        enable_tracing=enable_tracing,
        enable_graph_rag=enable_graph_rag,
        enable_embeddings=enable_embeddings,
        neo4j_uri=neo4j_uri,
        neo4j_username=neo4j_username,
        neo4j_database=neo4j_database,
    )
