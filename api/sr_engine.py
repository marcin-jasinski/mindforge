"""
SM-2 Spaced Repetition Engine.

Implements the SuperMemo SM-2 algorithm for flashcard scheduling.
State is persisted in a JSON file (state/sr_state.json).

Rating scale:
  0 = Again  — complete failure, reset
  1 = Hard   — correct but with difficulty
  2 = Good   — correct with moderate effort
  3 = Easy   — effortless recall
"""
from __future__ import annotations

import json
import logging
import os
from dataclasses import asdict, dataclass, field
from datetime import date, timedelta
from pathlib import Path
from typing import Any

log = logging.getLogger(__name__)

AGAIN, HARD, GOOD, EASY = 0, 1, 2, 3
DEFAULT_EASE = 2.5
MIN_EASE = 1.3


@dataclass
class CardState:
    card_id: str
    ease: float = DEFAULT_EASE
    interval: int = 0  # days
    repetitions: int = 0
    due_date: str = ""  # ISO date string YYYY-MM-DD

    def is_due(self, today: date | None = None) -> bool:
        if not self.due_date:
            return True
        today = today or date.today()
        return date.fromisoformat(self.due_date) <= today


def review(card: CardState, rating: int) -> CardState:
    """Apply SM-2 algorithm and return updated state (new object)."""
    ease = card.ease
    interval = card.interval
    repetitions = card.repetitions

    if rating < GOOD:  # Again or Hard
        repetitions = 0
        interval = 1
        if rating == AGAIN:
            ease = max(MIN_EASE, ease - 0.20)
        else:  # Hard
            ease = max(MIN_EASE, ease - 0.15)
    else:
        if repetitions == 0:
            interval = 1
        elif repetitions == 1:
            interval = 6
        else:
            interval = round(interval * ease)

        repetitions += 1

        # Adjust ease factor
        ease += 0.1 - (3 - rating) * (0.08 + (3 - rating) * 0.02)
        ease = max(MIN_EASE, ease)

    next_due = (date.today() + timedelta(days=interval)).isoformat()

    return CardState(
        card_id=card.card_id,
        ease=round(ease, 2),
        interval=interval,
        repetitions=repetitions,
        due_date=next_due,
    )


# ── State persistence ───────────────────────────────────────────────


def _lock_path(state_path: Path) -> Path:
    return state_path.with_suffix(".lock")


def load_state(state_path: Path) -> dict[str, CardState]:
    """Load SR state from JSON file."""
    if not state_path.exists():
        return {}
    try:
        data = json.loads(state_path.read_text(encoding="utf-8"))
        return {
            card_id: CardState(**entry)
            for card_id, entry in data.items()
        }
    except (json.JSONDecodeError, OSError):
        log.warning("Corrupted SR state file %s, returning empty state", state_path)
        return {}


def save_state(state_path: Path, cards: dict[str, CardState]) -> None:
    """Save SR state to JSON file with atomic write."""
    state_path.parent.mkdir(parents=True, exist_ok=True)
    tmp = state_path.with_suffix(".json.tmp")
    tmp.write_text(
        json.dumps(
            {card_id: asdict(card) for card_id, card in cards.items()},
            ensure_ascii=False,
            indent=2,
        ),
        encoding="utf-8",
    )
    tmp.replace(state_path)


def get_due_cards(state: dict[str, CardState], today: date | None = None) -> list[CardState]:
    """Return cards that are due for review."""
    today = today or date.today()
    return [card for card in state.values() if card.is_due(today)]
