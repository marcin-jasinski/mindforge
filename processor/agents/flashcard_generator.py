"""
Flashcard generator agent — creates Anki-ready flashcards from lesson content.

Returns FlashcardData objects (from processor.models). TSV rendering for Anki
is handled by processor.renderers.
"""
from __future__ import annotations

import json
import logging
import re

from processor.llm_client import LLMClient
from processor.models import FlashcardData

log = logging.getLogger(__name__)


SYSTEM_PROMPT = """\
Jesteś ekspertem od tworzenia fiszek do nauki (spaced repetition / Anki). \
Twoim zadaniem jest stworzenie zestawu wysokiej jakości fiszek na podstawie \
dostarczonego materiału.

Zasady tworzenia fiszek:
1. **Minimum information principle** — każda fiszka testuje JEDNĄ atomową informację
2. **Pytania precyzyjne** — unikaj "Co to jest X?" na rzecz "Jaka jest główna zaleta X w kontekście Y?"
3. **Cloze deletions** — dla definicji, parametrów, komend używaj formatu {{c1::odpowiedź}}
4. **Kontekst techniczny** — zachowaj nazwy API, modeli, narzędzi, parametrów, komend
5. **Różne poziomy** — mieszaj wiedzę faktograficzną, konceptualną i proceduralną
6. **Bez trywialnych pytań** — fiszki muszą testować zrozumienie, nie recall trywialnych faktów

Typy kart:
- "basic": Pytanie → Odpowiedź
- "cloze": Zdanie z luką {{c1::...}} do uzupełnienia
- "reverse": Pytanie odwracalne (można pytać z obu stron)

Generuj 15-25 fiszek na lekcję.

Odpowiedz w formacie JSON (bez żadnego dodatkowego tekstu):
{
  "flashcards": [
    {
      "front": "tekst przodu karty",
      "back": "tekst tyłu karty",
      "card_type": "basic|cloze|reverse",
      "tags": ["tag1", "tag2"]
    }
  ]
}\
"""

RESPONSE_FORMAT = {
    "type": "json_schema",
    "json_schema": {
        "name": "flashcards",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "flashcards": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "front": {"type": "string"},
                            "back": {"type": "string"},
                            "card_type": {"type": "string", "enum": ["basic", "cloze", "reverse"]},
                            "tags": {"type": "array", "items": {"type": "string"}},
                        },
                        "required": ["front", "back", "card_type", "tags"],
                        "additionalProperties": False,
                    },
                },
            },
            "required": ["flashcards"],
            "additionalProperties": False,
        },
    },
}


def generate_flashcards(
    content: str,
    summary_text: str,
    title: str,
    lesson_number: str,
    llm: LLMClient,
    model: str,
) -> list[FlashcardData]:
    """Generate flashcards from lesson content and its summary.

    Args:
        content: Preprocessed lesson content (story/tasks removed).
        summary_text: Summary rendered as plain text (for LLM context).
        title: Lesson title.
        lesson_number: Lesson number (e.g. "S01E01").
        llm: LLM client instance.
        model: Model name (use large model for quality).

    Returns:
        List of FlashcardData objects ready for Anki export.
    """
    user_message = (
        f"# Lekcja: {title} ({lesson_number})\n\n"
        f"## Treść lekcji\n{content}\n\n"
        f"## Podsumowanie lekcji\n{summary_text}\n"
    )

    log.info("Generating flashcards for: %s", title)

    raw = llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        temperature=0.4,
        response_format=RESPONSE_FORMAT,
    )

    try:
        data = json.loads(raw)
    except json.JSONDecodeError:
        log.error("Failed to parse flashcard JSON response")
        return []

    cards: list[FlashcardData] = []
    for item in data.get("flashcards", []):
        tags = [f"ai_devs::{lesson_number}"]
        for tag in item.get("tags", []):
            safe_tag = re.sub(r"[^a-zA-Z0-9_ąćęłńóśźżĄĆĘŁŃÓŚŹŻ-]", "_", tag)
            tags.append(f"ai_devs::{safe_tag}")

        cards.append(FlashcardData(
            front=item["front"],
            back=item["back"],
            card_type=item.get("card_type", "basic"),
            tags=tags,
        ))

    log.info("Generated %d flashcards for %s", len(cards), title)
    return cards
