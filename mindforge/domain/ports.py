"""
Domain layer — port (abstract interface) definitions.

Each Protocol here is the «port» side of a hexagonal adapter pair.
Concrete implementations live in ``mindforge/infrastructure/``.

Pure Python only.  Zero I/O, zero framework imports.
The ``connection`` parameters are typed as ``Any`` to avoid importing
SQLAlchemy at the domain level.
"""

from __future__ import annotations

from datetime import date
from typing import Any, Protocol, runtime_checkable
from uuid import UUID

from mindforge.domain.events import DomainEvent
from mindforge.domain.models import (
    CardState,
    CompletionResult,
    ConceptNeighborhood,
    ConceptNode,
    DeadlineProfile,
    Document,
    DocumentArtifact,
    Interaction,
    InteractionTurn,
    KnowledgeBase,
    QuizSession,
    RetrievalResult,
    ReviewResult,
    TokenBudget,
    User,
    WeakConcept,
)


# ---------------------------------------------------------------------------
# Document Repository
# ---------------------------------------------------------------------------


@runtime_checkable
class DocumentRepository(Protocol):
    async def save(self, document: Document, connection: Any) -> None:
        """Persist a new document within the caller-supplied transaction."""
        ...

    async def get_by_id(self, document_id: UUID) -> Document | None: ...

    async def get_by_content_hash(self, kb_id: UUID, sha256: str) -> Document | None:
        """Return the active document matching the given content hash, or None."""
        ...

    async def update_status(
        self, document_id: UUID, status: str, connection: Any
    ) -> None: ...

    async def list_by_knowledge_base(
        self,
        kb_id: UUID,
        *,
        limit: int = 50,
        offset: int = 0,
    ) -> list[Document]: ...


# ---------------------------------------------------------------------------
# Artifact Repository
# ---------------------------------------------------------------------------


@runtime_checkable
class ArtifactRepository(Protocol):
    async def save_checkpoint(
        self, artifact: DocumentArtifact, connection: Any
    ) -> None:
        """UPSERT artifact JSON and step fingerprints within the caller's transaction."""
        ...

    async def load_latest(self, document_id: UUID) -> DocumentArtifact | None:
        """Load the highest-version artifact for a document."""
        ...

    async def count_flashcards(self, kb_id: UUID, lesson_id: str) -> int: ...


# ---------------------------------------------------------------------------
# Retrieval Port
# ---------------------------------------------------------------------------


@runtime_checkable
class RetrievalPort(Protocol):
    async def retrieve(
        self,
        query: str,
        kb_id: UUID,
        *,
        top_k: int = 5,
        budget: TokenBudget | None = None,
    ) -> list[RetrievalResult]:
        """Graph-first, lexical fallback, embedding last."""
        ...

    async def retrieve_concept_neighborhood(
        self,
        kb_id: UUID,
        concept_key: str,
        *,
        depth: int = 2,
    ) -> ConceptNeighborhood | None: ...

    async def find_weak_concepts(
        self,
        user_id: UUID,
        kb_id: UUID,
        today: date,
        *,
        limit: int = 10,
    ) -> list[WeakConcept]: ...

    async def get_concepts(self, kb_id: UUID) -> list[ConceptNode]: ...

    async def get_lesson_concepts(
        self, kb_id: UUID, lesson_id: str
    ) -> list[ConceptNode]: ...


# ---------------------------------------------------------------------------
# AI Gateway
# ---------------------------------------------------------------------------


@runtime_checkable
class AIGateway(Protocol):
    async def complete(
        self,
        *,
        model: str,
        messages: list[dict[str, Any]],
        deadline: DeadlineProfile = DeadlineProfile.INTERACTIVE,
        temperature: float = 0.7,
        max_tokens: int | None = None,
        response_format: dict[str, Any] | None = None,
    ) -> CompletionResult: ...

    async def embed(
        self,
        *,
        model: str,
        texts: list[str],
    ) -> list[list[float]]: ...


# ---------------------------------------------------------------------------
# Study Progress Store
# ---------------------------------------------------------------------------


@runtime_checkable
class StudyProgressStore(Protocol):
    async def get_due_cards(
        self, user_id: UUID, kb_id: UUID, today: date
    ) -> list[CardState]: ...

    async def save_review(
        self,
        user_id: UUID,
        kb_id: UUID,
        card_id: str,
        result: ReviewResult,
    ) -> None: ...

    async def due_count(self, user_id: UUID, kb_id: UUID, today: date) -> int: ...


# ---------------------------------------------------------------------------
# Event Publisher (outbox pattern)
# ---------------------------------------------------------------------------


@runtime_checkable
class EventPublisher(Protocol):
    async def publish_in_tx(self, event: DomainEvent, connection: Any) -> None:
        """Write event to the outbox table within the caller's transaction."""
        ...


# ---------------------------------------------------------------------------
# Interaction Store
# ---------------------------------------------------------------------------


@runtime_checkable
class InteractionStore(Protocol):
    async def create_interaction(self, interaction: Interaction) -> None: ...

    async def add_turn(self, turn: InteractionTurn) -> None: ...

    async def get_interaction(self, interaction_id: UUID) -> Interaction | None: ...

    async def list_for_user(
        self,
        user_id: UUID,
        *,
        limit: int = 50,
        offset: int = 0,
    ) -> list[Interaction]:
        """Return interactions for the user with redaction applied.

        Strips ``reference_answer``, ``grounding_context``, ``raw_prompt``,
        ``raw_completion`` from ``output_data``, and hides ``cost``.
        Redaction is enforced here, not in the router (defense in depth).
        """
        ...

    async def list_unredacted(
        self,
        *,
        limit: int = 50,
        offset: int = 0,
    ) -> list[Interaction]:
        """Admin-only: return full unredacted interaction data."""
        ...


# ---------------------------------------------------------------------------
# External Identity Repository
# ---------------------------------------------------------------------------


@runtime_checkable
class ExternalIdentityRepository(Protocol):
    async def find_user_id(self, provider: str, external_id: str) -> UUID | None: ...

    async def link(
        self,
        user_id: UUID,
        provider: str,
        external_id: str,
        email: str | None,
        metadata: dict[str, Any],
    ) -> None: ...

    async def create_user_and_link(
        self,
        provider: str,
        external_id: str,
        display_name: str,
        email: str | None = None,
        avatar_url: str | None = None,
        metadata: dict[str, Any] | None = None,
    ) -> UUID:
        """Atomically create a user row and link the external identity."""
        ...


# ---------------------------------------------------------------------------
# Quiz Session Store
# ---------------------------------------------------------------------------


@runtime_checkable
class QuizSessionStore(Protocol):
    async def create_session(self, session: QuizSession) -> None: ...

    async def get_session(self, session_id: UUID) -> QuizSession | None: ...

    async def update_session(self, session: QuizSession) -> None: ...

    async def delete_session(self, session_id: UUID) -> None: ...


# ---------------------------------------------------------------------------
# Graph Indexer
# ---------------------------------------------------------------------------


@runtime_checkable
class GraphIndexer(Protocol):
    async def index_artifact(self, artifact: DocumentArtifact, connection: Any) -> None:
        """Write/update graph nodes and edges from a processed artifact."""
        ...

    async def rebuild_knowledge_base(self, kb_id: UUID) -> None:
        """Rebuild the full graph projection for a knowledge base from PostgreSQL."""
        ...

    async def remove_lesson(self, kb_id: UUID, lesson_id: str) -> None:
        """Remove all graph nodes and edges for a deactivated lesson."""
        ...


# ---------------------------------------------------------------------------
# Knowledge Base Repository
# ---------------------------------------------------------------------------


@runtime_checkable
class KnowledgeBaseRepository(Protocol):
    async def save(self, kb: KnowledgeBase, connection: Any) -> None: ...

    async def get_by_id(self, kb_id: UUID) -> KnowledgeBase | None: ...

    async def list_by_owner(self, owner_id: UUID) -> list[KnowledgeBase]: ...

    async def delete(self, kb_id: UUID, connection: Any) -> None: ...


# ---------------------------------------------------------------------------
# User Repository
# ---------------------------------------------------------------------------


@runtime_checkable
class UserRepository(Protocol):
    async def save(self, user: User, connection: Any) -> None: ...

    async def get_by_id(self, user_id: UUID) -> User | None: ...

    async def get_by_email(self, email: str) -> User | None: ...
