"""
Application layer — Flashcard Service.

Manages the flashcard catalog and spaced-repetition review scheduling.

The SM-2 algorithm lives in ``mindforge.domain.models.sm2_update`` (pure
domain logic, zero I/O).  This service joins card content from
:class:`~mindforge.domain.ports.ArtifactRepository` with SM-2 scheduling
state from :class:`~mindforge.domain.ports.StudyProgressStore`.
"""

from __future__ import annotations

import logging
from datetime import date
from uuid import UUID

from mindforge.domain.models import CardState, FlashcardData, ReviewResult
from mindforge.domain.ports import ArtifactRepository, StudyProgressStore

log = logging.getLogger(__name__)


class FlashcardService:
    """Manages the flashcard catalog and spaced-repetition scheduling.

    Parameters
    ----------
    artifact_repo:
        Artifact repository — provides card content (front, back, card_type,
        lesson_id) from processed :class:`~mindforge.domain.models.DocumentArtifact`
        objects.
    study_progress:
        Study-progress store — provides SM-2 scheduling state (ease_factor,
        interval, next_review, repetitions).
    """

    def __init__(
        self,
        artifact_repo: ArtifactRepository,
        study_progress: StudyProgressStore,
    ) -> None:
        self._artifacts = artifact_repo
        self._progress = study_progress

    # ------------------------------------------------------------------
    # Public interface
    # ------------------------------------------------------------------

    async def get_due_cards(self, user_id: UUID, kb_id: UUID) -> list[CardState]:
        """Return cards due for review today, populated with flashcard content.

        Joins the SM-2 scheduling state from *study_progress* with card
        content (front, back, card_type, lesson_id) from artifact data.

        Cards whose ``card_id`` is no longer present in any artifact (e.g.
        after a document is re-processed with different flashcard content) are
        still returned with empty content fields so the caller can handle them
        gracefully.
        """
        today = date.today()
        due_states = await self._progress.get_due_cards(user_id, kb_id, today)
        if not due_states:
            return []

        # Build flashcard content index from artifact repository
        all_cards = await self._artifacts.list_flashcards_for_kb(kb_id)
        card_index: dict[str, FlashcardData] = {c.card_id: c for c in all_cards}

        # Join: populate front/back/card_type/lesson_id from artifact data
        populated: list[CardState] = []
        for state in due_states:
            card_data = card_index.get(state.card_id)
            if card_data is not None:
                populated.append(
                    CardState(
                        card_id=state.card_id,
                        kb_id=state.kb_id,
                        lesson_id=card_data.lesson_id,
                        card_type=card_data.card_type,
                        front=card_data.front,
                        back=card_data.back,
                        next_review=state.next_review,
                        interval=state.interval,
                        ease_factor=state.ease_factor,
                        repetitions=state.repetitions,
                    )
                )
            else:
                # Card content no longer in artifacts — include state as-is
                populated.append(state)

        return populated

    async def review_card(
        self,
        user_id: UUID,
        kb_id: UUID,
        card_id: str,
        result: ReviewResult,
    ) -> None:
        """Apply SM-2 update for a card review and persist the new schedule.

        The SM-2 algorithm itself runs inside
        ``StudyProgressStore.save_review()`` in the infrastructure layer.
        """
        await self._progress.save_review(user_id, kb_id, card_id, result)

    async def list_all_cards(
        self,
        kb_id: UUID,
        lesson_id: str | None = None,
    ) -> list[FlashcardData]:
        """Return all flashcards in the KB, optionally filtered by lesson.

        Returns raw :class:`~mindforge.domain.models.FlashcardData` objects
        from the latest active artifact per document.  Does not include
        SM-2 scheduling state (use :meth:`get_due_cards` for that).
        """
        return await self._artifacts.list_flashcards_for_kb(kb_id, lesson_id)

    async def due_count(self, user_id: UUID, kb_id: UUID) -> int:
        """Return the number of cards due for review today."""
        today = date.today()
        return await self._progress.due_count(user_id, kb_id, today)
