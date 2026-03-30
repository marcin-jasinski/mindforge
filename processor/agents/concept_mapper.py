"""
Concept mapper agent — generates structured concept map data from lesson content.

Returns ConceptMapData (structured) instead of markdown. Mermaid rendering
is handled by processor.renderers.
"""
from __future__ import annotations

import json
import logging

from processor.llm_client import LLMClient
from processor.models import ConceptMapData, ConceptNode, ConceptRelation, ConceptGroup

log = logging.getLogger(__name__)

SYSTEM_PROMPT = """\
Jesteś ekspertem od wizualizacji wiedzy i map pojęć. Twoim zadaniem jest \
wyodrębnienie struktury relacji między kluczowymi koncepcjami \
z dostarczonego materiału.

Zasady:
1. Wyodrębnij 8-15 najważniejszych pojęć z lekcji
2. Określ relacje między nimi (uses, extends, requires, produces, contains, interacts)
3. Pogrupuj powiązane koncepcje
4. Użyj krótkich, czytelnych etykiet węzłów (max 3-4 słowa)
5. Identyfikatory węzłów powinny być prostymi literami (A, B, C, ...)

Zwróć JSON z polami:
- "nodes": lista {"id": "A", "label": "Nazwa koncepcji", "group": "Nazwa grupy", \
"color": "green|blue|orange|purple"}
- "relationships": lista {"source_id": "A", "target_id": "B", "label": "relacja", \
"description": "opis relacji (1 zdanie)"}
- "groups": lista {"name": "Nazwa grupy", "node_ids": ["A", "B"]}

Kolory:
- "green": koncepcje fundamentalne (bazowe dla reszty)
- "blue": narzędzia i technologie
- "orange": techniki i wzorce
- "purple": koncepcje zaawansowane

Odpowiadaj po polsku. NIE dodawaj żadnych komentarzy — tylko JSON.\
"""

RESPONSE_FORMAT = {
    "type": "json_schema",
    "json_schema": {
        "name": "concept_map",
        "strict": True,
        "schema": {
            "type": "object",
            "properties": {
                "nodes": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "id": {"type": "string"},
                            "label": {"type": "string"},
                            "group": {"type": "string"},
                            "color": {
                                "type": "string",
                                "enum": ["green", "blue", "orange", "purple"],
                            },
                        },
                        "required": ["id", "label", "group", "color"],
                        "additionalProperties": False,
                    },
                },
                "relationships": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "source_id": {"type": "string"},
                            "target_id": {"type": "string"},
                            "label": {"type": "string"},
                            "description": {"type": "string"},
                        },
                        "required": ["source_id", "target_id", "label", "description"],
                        "additionalProperties": False,
                    },
                },
                "groups": {
                    "type": "array",
                    "items": {
                        "type": "object",
                        "properties": {
                            "name": {"type": "string"},
                            "node_ids": {
                                "type": "array",
                                "items": {"type": "string"},
                            },
                        },
                        "required": ["name", "node_ids"],
                        "additionalProperties": False,
                    },
                },
            },
            "required": ["nodes", "relationships", "groups"],
            "additionalProperties": False,
        },
    },
}


def generate_concept_map(
    content: str,
    summary_text: str,
    title: str,
    lesson_number: str,
    llm: LLMClient,
    model: str,
) -> ConceptMapData:
    """Generate structured concept map data for the lesson.

    Returns ConceptMapData with nodes, relationships, and groups.
    """
    user_message = (
        f"# Lekcja: {title} ({lesson_number})\n\n"
        f"## Treść lekcji\n{content}\n\n"
        f"## Podsumowanie lekcji\n{summary_text}\n"
    )

    log.info("Generating concept map for: %s", title)

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

    concept_map = ConceptMapData(
        nodes=[
            ConceptNode(id=n["id"], label=n["label"], group=n["group"], color=n["color"])
            for n in data.get("nodes", [])
        ],
        relationships=[
            ConceptRelation(
                source_id=r["source_id"],
                target_id=r["target_id"],
                label=r["label"],
                description=r["description"],
            )
            for r in data.get("relationships", [])
        ],
        groups=[
            ConceptGroup(name=g["name"], node_ids=g["node_ids"])
            for g in data.get("groups", [])
        ],
    )

    log.info(
        "Concept map generated: %d nodes, %d relationships",
        len(concept_map.nodes), len(concept_map.relationships),
    )
    return concept_map
