"""
Summarizer agent — generates structured lesson summary using large LLM.

Returns SummaryData (structured) instead of markdown. Markdown rendering
is handled by processor.renderers.
"""
from __future__ import annotations

import itertools
import json
import logging
from typing import Any

from processor.llm_client import LLMClient
from processor.models import SummaryData, ConceptEntry, LinkEntry

log = logging.getLogger(__name__)


def get_known_concepts_from_graph(driver: Any, max_concepts: int = 50) -> dict[str, Any]:
    """Retrieve known concept names and their associated lesson numbers from Neo4j.

    This is the preferred source for prior-concept context (CRITICAL-5).  The
    result has the same shape as the JSON knowledge-index entries so the
    summarizer ``known_concepts`` parameter works unchanged.

    Returns an empty dict if the graph is unavailable or empty.
    """
    try:
        result = driver.execute_query(
            """
            MATCH (c:Concept)-[:MENTIONED_IN]->(l:Lesson)
            WITH c, collect(l.lesson_number) AS lesson_numbers
            RETURN c.name AS name, lesson_numbers
            ORDER BY c.name
            LIMIT $limit
            """,
            {"limit": max_concepts},
        )
        concepts: dict[str, Any] = {}
        for record in result.records:
            name = record["name"]
            lesson_numbers = record["lesson_numbers"] or []
            concepts[name] = {"lessons": lesson_numbers, "definition": ""}
        log.info("Loaded %d concepts from graph for summarizer context", len(concepts))
        return concepts
    except Exception:
        log.warning("Failed to load concepts from graph — summarizer will run without prior context", exc_info=True)
        return {}

SYSTEM_PROMPT = """\
Jesteś ekspertem od syntezy wiedzy. Twoim zadaniem jest stworzenie kompleksowego \
podsumowania dostarczonego materiału tekstowego.

Podsumowanie powinno:
- Być napisane w języku polskim
- Umożliwiać jak najlepsze przyswojenie wiedzy z materiału
- Być precyzyjne i konkretne, bez zbędnych ogólników
- Zachować techniczne szczegóły (nazwy API, modeli, narzędzi, parametrów)
- Uwzględnić praktyczne wskazówki i rekomendacje autora

Zwróć odpowiedź w formacie JSON z polami:
- "overview": 2-4 zdania streszczające całą lekcję — co jest głównym tematem \
i jakie kluczowe wnioski płyną z jej treści
- "key_concepts": lista obiektów {"name": "Nazwa koncepcji", "definition": \
"precyzyjny opis (1-2 zdania)"} — kluczowe koncepcje z lekcji
- "key_facts": lista stringów — najważniejsze informacje/fakty z lekcji do zapamiętania
- "practical_tips": lista stringów — praktyczne rady i rekomendacje z lekcji
- "important_links": lista obiektów {"name": "Nazwa", "url": "URL", \
"description": "krótki opis, do czego służy"} — zewnętrzne zasoby wspomniane w lekcji

NIE dodawaj żadnych wstępów, uwag ani komentarzy — tylko JSON.\
"""

RESPONSE_FORMAT = {
    "type": "json_schema",
    "json_schema": {
        "name": "lesson_summary",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "overview": {"type": "string"},
                "key_concepts": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "definition": {"type": "string"},
                        },
                        "required": ["name", "definition"],
                        "additionalProperties": False,
                    },
                },
                "key_facts": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "practical_tips": {
                    "type": "array",
                    "items": {"type": "string"},
                },
                "important_links": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "url": {"type": "string"},
                            "description": {"type": "string"},
                        },
                        "required": ["name", "url", "description"],
                        "additionalProperties": False,
                    },
                },
            },
            "required": [
                "overview",
                "key_concepts",
                "key_facts",
                "practical_tips",
                "important_links",
            ],
            "additionalProperties": False,
        },
    },
}

# Cap on prior concepts injected into the summarizer prompt.  Bounding this
# prevents unbounded token growth as the knowledge base accumulates lessons.
_MAX_PRIOR_CONCEPTS = 50


def summarize(
    content: str,
    articles: list[dict[str, str]],
    title: str,
    llm: LLMClient,
    model: str,
    known_concepts: dict[str, Any] | None = None,
) -> SummaryData:
    """Generate a structured summary of the lesson content.

    Returns SummaryData with structured sections (no markdown rendering).
    """
    # Build user message with content + optional articles
    user_parts = [f"# Lekcja: {title}\n\n{content}"]

    if articles:
        user_parts.append("\n\n---\n\n## Dodatkowy kontekst z artykułów zewnętrznych\n")
        for article in articles:
            user_parts.append(
                f"\n### {article['text']} ({article['url']})\n{article['content']}\n"
            )

    if known_concepts:
        user_parts.append("\n\n---\n\n## Pojęcia znane z wcześniejszych lekcji\n")
        for name, info in itertools.islice(sorted(known_concepts.items()), _MAX_PRIOR_CONCEPTS):
            lessons = ", ".join(info.get("lessons", []))
            user_parts.append(f"- **{name}** (lekcje: {lessons}): {info.get('definition', '')}")

    user_message = "\n".join(user_parts)
    log.info(
        "Summarizing lesson: %s (%d chars content, %d articles)",
        title, len(content), len(articles),
    )

    raw = llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        temperature=0.3,
        response_format=RESPONSE_FORMAT,
    )

    data = json.loads(raw)

    summary = SummaryData(
        overview=data["overview"],
        key_concepts=[
            ConceptEntry(name=c["name"], definition=c["definition"])
            for c in data.get("key_concepts", [])
        ],
        key_facts=data.get("key_facts", []),
        practical_tips=data.get("practical_tips", []),
        important_links=[
            LinkEntry(name=l["name"], url=l["url"], description=l["description"])
            for l in data.get("important_links", [])
        ],
    )

    log.info(
        "Summary generated: %d concepts, %d facts",
        len(summary.key_concepts), len(summary.key_facts),
    )
    return summary
