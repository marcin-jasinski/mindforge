"""
Summarizer agent — generates structured lesson summary using large LLM.
"""
from __future__ import annotations

import logging
import re
from datetime import datetime, timezone
from typing import Any

from processor.llm_client import LLMClient

log = logging.getLogger(__name__)

SYSTEM_PROMPT = """\
Jesteś ekspertem od syntezy wiedzy. Twoim zadaniem jest stworzenie kompleksowego \
podsumowania lekcji z kursu o budowaniu aplikacji \
z wykorzystaniem generatywnej sztucznej inteligencji).

Podsumowanie powinno:
- Być napisane w języku polskim
- Umożliwiać jak najlepsze przyswojenie wiedzy z lekcji
- Być precyzyjne i konkretne, bez zbędnych ogólników
- Zachować techniczne szczegóły (nazwy API, modeli, narzędzi, parametrów)
- Uwzględnić praktyczne wskazówki i rekomendacje autora

Format odpowiedzi (Markdown):

## Podsumowanie
[2-4 zdania streszczające całą lekcję — co jest głównym tematem i jakie kluczowe wnioski płyną z jej treści]

## Kluczowe koncepcje
[Lista koncepcji z krótkim, precyzyjnym opisem każdej, w formacie:]
- **Nazwa koncepcji**: opis (1-2 zdania)

## Najważniejsze informacje
[Ponumerowana lista najważniejszych informacji/faktów z lekcji, które warto zapamiętać]

## Praktyczne wskazówki
[Lista praktycznych rad i rekomendacji z lekcji, przydatnych przy budowaniu aplikacji]

## Ważne linki
[Lista zewnętrznych zasobów wspomnianych w lekcji w formacie:]
- [Nazwa](URL) — krótki opis, do czego służy

NIE dodawaj żadnych wstępów, uwag ani komentarzy od siebie poza powyższymi sekcjami.\
"""


def _extract_lesson_number(filename: str) -> str:
    """Extract lesson number from filename."""
    match = re.match(r"s(\d+)e(\d+)", filename, re.IGNORECASE)
    if match:
        return f"S{match.group(1).zfill(2)}E{match.group(2).zfill(2)}"
    return "unknown"


def _extract_topics(summary_text: str) -> list[str]:
    """Extract topic tags from the key concepts section."""
    topics = []
    in_concepts = False
    for line in summary_text.split("\n"):
        if "## Kluczowe koncepcje" in line:
            in_concepts = True
            continue
        if in_concepts and line.startswith("## "):
            break
        if in_concepts and line.startswith("- **"):
            match = re.match(r"- \*\*(.+?)\*\*", line)
            if match:
                topics.append(match.group(1).strip())
    return topics[:10]


def _build_frontmatter(
    title: str,
    source_filename: str,
    topics: list[str],
    metadata: dict[str, Any],
) -> str:
    """Build YAML frontmatter for the summary file."""
    lesson_number = _extract_lesson_number(source_filename)
    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

    # Escape YAML strings
    safe_title = title.replace('"', '\\"')
    topics_yaml = ", ".join(f'"{t}"' for t in topics)

    lines = [
        "---",
        f'title: "{safe_title}"',
        f"source: {source_filename}",
        f"processed_at: {now}",
        f"lesson_number: {lesson_number}",
        f"topics: [{topics_yaml}]",
    ]

    # Preserve useful metadata from original
    if "published_at" in metadata:
        lines.append(f"original_published_at: {metadata['published_at']}")

    lines.append("---")
    return "\n".join(lines)


def summarize(
    content: str,
    articles: list[dict[str, str]],
    title: str,
    source_filename: str,
    metadata: dict[str, Any],
    llm: LLMClient,
    model: str,
) -> str:
    """Generate a structured summary of the lesson content.
    
    Returns full markdown file content including frontmatter.
    """
    # Build user message with content + optional articles
    user_parts = [f"# Lekcja: {title}\n\n{content}"]

    if articles:
        user_parts.append("\n\n---\n\n## Dodatkowy kontekst z artykułów zewnętrznych\n")
        for article in articles:
            user_parts.append(
                f"\n### {article['text']} ({article['url']})\n{article['content']}\n"
            )

    user_message = "\n".join(user_parts)
    log.info(
        "Summarizing lesson: %s (%d chars content, %d articles)",
        title, len(content), len(articles),
    )

    summary_text = llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_message},
        ],
        temperature=0.3,
    )

    topics = _extract_topics(summary_text)
    frontmatter = _build_frontmatter(title, source_filename, topics, metadata)

    full_output = f"{frontmatter}\n\n{summary_text}\n"
    log.info("Summary generated: %d chars, %d topics", len(full_output), len(topics))
    return full_output
