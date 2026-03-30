"""
Flashcards router — spaced repetition with SM-2 algorithm.

Cards come from lesson artifact JSONs. SR state is tracked per-card
in state/sr_state.json.
"""
from __future__ import annotations

import json
import logging
from pathlib import Path
from typing import Any

from fastapi import APIRouter, Depends, HTTPException, Query

from api.auth import require_auth
from api.deps import get_base_dir, get_sr_state_path
from api.schemas import (
    FlashcardReviewSchema,
    FlashcardsDueResponse,
    ReviewRequest,
    ReviewResponse,
    UserInfo,
)
from api.sr_engine import CardState, get_due_cards, load_state, review, save_state

log = logging.getLogger(__name__)
router = APIRouter(prefix="/api/flashcards", tags=["flashcards"])


def _load_all_flashcards(base_dir: Path) -> list[dict[str, Any]]:
    """Load all flashcards from artifact JSON files."""
    artifact_dir = base_dir / "state" / "artifacts"
    if not artifact_dir.exists():
        return []

    cards: list[dict[str, Any]] = []
    for f in sorted(artifact_dir.glob("*.json")):
        try:
            data = json.loads(f.read_text(encoding="utf-8"))
            lesson_number = data.get("lesson_number", "")
            for i, fc in enumerate(data.get("flashcards", [])):
                card_id = f"{lesson_number}_{i}"
                cards.append({
                    "id": card_id,
                    "front": fc["front"],
                    "back": fc["back"],
                    "card_type": fc.get("card_type", "basic"),
                    "tags": fc.get("tags", []),
                    "lesson_number": lesson_number,
                })
        except Exception:
            log.debug("Failed to load flashcards from %s", f, exc_info=True)

    return cards


@router.get("/due", response_model=FlashcardsDueResponse)
async def get_due_flashcards(
    lesson: str | None = Query(default=None, description="Filter by lesson"),
    base_dir: Path = Depends(get_base_dir),
    sr_path: Path = Depends(get_sr_state_path),
    _user: UserInfo = Depends(require_auth),
):
    """Get flashcards that are due for review."""
    all_cards = _load_all_flashcards(base_dir)
    if lesson:
        all_cards = [c for c in all_cards if c["lesson_number"] == lesson]

    sr_state = load_state(sr_path)

    # Ensure all cards have SR state (new cards are immediately due)
    for card in all_cards:
        if card["id"] not in sr_state:
            sr_state[card["id"]] = CardState(card_id=card["id"])

    due = get_due_cards(sr_state)
    due_ids = {c.card_id for c in due}

    result: list[FlashcardReviewSchema] = []
    for card in all_cards:
        if card["id"] in due_ids:
            st = sr_state[card["id"]]
            result.append(FlashcardReviewSchema(
                id=card["id"],
                front=card["front"],
                back=card["back"],
                card_type=card["card_type"],
                tags=card["tags"],
                lesson_number=card["lesson_number"],
                ease=st.ease,
                interval=st.interval,
                repetitions=st.repetitions,
                due_date=st.due_date,
            ))

    return FlashcardsDueResponse(cards=result, total_due=len(result))


@router.get("/all", response_model=list[FlashcardReviewSchema])
async def get_all_flashcards(
    lesson: str | None = Query(default=None, description="Filter by lesson"),
    base_dir: Path = Depends(get_base_dir),
    sr_path: Path = Depends(get_sr_state_path),
    _user: UserInfo = Depends(require_auth),
):
    """Get all flashcards with their SR state."""
    all_cards = _load_all_flashcards(base_dir)
    if lesson:
        all_cards = [c for c in all_cards if c["lesson_number"] == lesson]

    sr_state = load_state(sr_path)

    result: list[FlashcardReviewSchema] = []
    for card in all_cards:
        st = sr_state.get(card["id"], CardState(card_id=card["id"]))
        result.append(FlashcardReviewSchema(
            id=card["id"],
            front=card["front"],
            back=card["back"],
            card_type=card["card_type"],
            tags=card["tags"],
            lesson_number=card["lesson_number"],
            ease=st.ease,
            interval=st.interval,
            repetitions=st.repetitions,
            due_date=st.due_date,
        ))

    return result


@router.post("/review", response_model=ReviewResponse)
async def review_flashcard(
    body: ReviewRequest,
    base_dir: Path = Depends(get_base_dir),
    sr_path: Path = Depends(get_sr_state_path),
    _user: UserInfo = Depends(require_auth),
):
    """Submit a review rating for a flashcard (SM-2 update)."""
    sr_state = load_state(sr_path)

    # Get or create card state
    card = sr_state.get(body.card_id)
    if card is None:
        # Validate the card_id exists in artifacts
        all_cards = _load_all_flashcards(base_dir)
        valid_ids = {c["id"] for c in all_cards}
        if body.card_id not in valid_ids:
            raise HTTPException(status_code=404, detail=f"Card '{body.card_id}' not found")
        card = CardState(card_id=body.card_id)

    updated = review(card, body.rating)
    sr_state[body.card_id] = updated
    save_state(sr_path, sr_state)

    return ReviewResponse(
        card_id=updated.card_id,
        new_ease=updated.ease,
        new_interval=updated.interval,
        next_due=updated.due_date,
    )
