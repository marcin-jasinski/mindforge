"""
Renderers — convert LessonArtifact data into human-readable output formats.

All outputs (markdown summary, Anki TSV, Mermaid diagram) are derived from
the canonical LessonArtifact, never generated independently.
"""
from __future__ import annotations

import html
import re

from processor.models import LessonArtifact, SummaryData


# ── Summary as context text (for passing to other LLM agents) ────────────────


def summary_as_context_text(summary: SummaryData) -> str:
    """Render summary data as plain text for use in LLM prompts."""
    lines = [f"Podsumowanie: {summary.overview}", ""]
    lines.append("Kluczowe koncepcje:")
    for c in summary.key_concepts:
        lines.append(f"- {c.name}: {c.definition}")
    lines.append("")
    lines.append("Najważniejsze informacje:")
    for fact in summary.key_facts:
        lines.append(f"- {fact}")
    return "\n".join(lines)


# ── Summary Markdown ─────────────────────────────────────────────────────────


def render_summary_markdown(artifact: LessonArtifact) -> str:
    """Render full summary markdown with YAML frontmatter from artifact."""
    if not artifact.summary:
        return ""
    frontmatter = _build_frontmatter(artifact)
    body = _build_summary_body(artifact.summary)
    return f"{frontmatter}\n\n{body}\n"


def _build_frontmatter(artifact: LessonArtifact) -> str:
    safe_title = artifact.title.replace('"', '\\"')
    topics = artifact.summary.key_concepts[:10] if artifact.summary else []
    topics_yaml = ", ".join(f'"{c.name}"' for c in topics)
    lines = [
        "---",
        f'title: "{safe_title}"',
        f"source: {artifact.source_filename}",
        f"processed_at: {artifact.processed_at}",
        f"lesson_number: {artifact.lesson_number}",
        f"topics: [{topics_yaml}]",
    ]
    if "published_at" in artifact.metadata:
        lines.append(f"original_published_at: {artifact.metadata['published_at']}")
    lines.append("---")
    return "\n".join(lines)


def _build_summary_body(s: SummaryData) -> str:
    lines: list[str] = []

    lines.append("## Podsumowanie")
    lines.append(s.overview)
    lines.append("")

    lines.append("## Kluczowe koncepcje")
    for c in s.key_concepts:
        lines.append(f"- **{c.name}**: {c.definition}")
    lines.append("")

    lines.append("## Najważniejsze informacje")
    for i, fact in enumerate(s.key_facts, 1):
        lines.append(f"{i}. {fact}")
    lines.append("")

    lines.append("## Praktyczne wskazówki")
    for tip in s.practical_tips:
        lines.append(f"- {tip}")
    lines.append("")

    if s.important_links:
        lines.append("## Ważne linki")
        for link in s.important_links:
            lines.append(f"- [{link.name}]({link.url}) — {link.description}")
        lines.append("")

    return "\n".join(lines)


# ── Anki TSV ─────────────────────────────────────────────────────────────────


def render_flashcards_tsv(artifact: LessonArtifact) -> str:
    """Render flashcards as Anki-importable tab-separated text."""
    lines: list[str] = []
    for card in artifact.flashcards:
        front = _md_to_html(card.front).replace("\t", " ")
        back = _md_to_html(card.back).replace("\t", " ")
        tags = " ".join(card.tags)
        lines.append(f"{front}\t{back}\t{tags}")
    return "\n".join(lines) + "\n" if lines else ""


def _md_to_html(text: str) -> str:
    """Minimal markdown-to-HTML for Anki card fields."""
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    text = re.sub(
        r"```[\w]*\n(.*?)```",
        lambda m: "<pre>" + html.escape(m.group(1)) + "</pre>",
        text,
        flags=re.DOTALL,
    )
    text = text.replace("\n", "<br>")
    return text


# ── Mermaid Concept Map ──────────────────────────────────────────────────────

_COLOR_HEX = {
    "green": "#4CAF50",
    "blue": "#2196F3",
    "orange": "#FF9800",
    "purple": "#9C27B0",
}

_COLOR_LEGEND = {
    "green": "🟢 **Zielony** — koncepcje fundamentalne (bazowe dla reszty)",
    "blue": "🔵 **Niebieski** — narzędzia i technologie",
    "orange": "🟠 **Pomarańczowy** — techniki i wzorce",
    "purple": "🟣 **Fioletowy** — koncepcje zaawansowane",
}


def render_concept_map_markdown(artifact: LessonArtifact) -> str:
    """Render Mermaid concept map markdown from artifact data."""
    cmap = artifact.concept_map
    if not cmap:
        return ""

    lines: list[str] = [f"## Mapa pojęć: {artifact.title}", "", "```mermaid", "graph TD"]

    # Nodes with labels
    for node in cmap.nodes:
        lines.append(f"    {node.id}[{node.label}]")
    lines.append("")

    # Edges
    for rel in cmap.relationships:
        lines.append(f"    {rel.source_id} -->|{rel.label}| {rel.target_id}")
    lines.append("")

    # Subgraphs
    for group in cmap.groups:
        safe_name = re.sub(r"[^a-zA-Z0-9_]", "_", group.name)
        lines.append(f'    subgraph {safe_name}["{group.name}"]')
        for nid in group.node_ids:
            lines.append(f"        {nid}")
        lines.append("    end")
    lines.append("")

    # Styles
    for node in cmap.nodes:
        hex_color = _COLOR_HEX.get(node.color, "#2196F3")
        lines.append(f"    style {node.id} fill:{hex_color},color:#fff")

    lines.append("```")
    lines.append("")

    # Legend
    lines.append("### Legenda")
    used_colors = {n.color for n in cmap.nodes}
    for color, desc in _COLOR_LEGEND.items():
        if color in used_colors:
            lines.append(f"- {desc}")
    lines.append("")

    # Relationship descriptions
    lines.append("### Opis relacji")
    node_labels = {n.id: n.label for n in cmap.nodes}
    for rel in cmap.relationships:
        src = node_labels.get(rel.source_id, rel.source_id)
        tgt = node_labels.get(rel.target_id, rel.target_id)
        lines.append(f"- **{src} → {tgt}**: {rel.description}")
    lines.append("")

    return "\n".join(lines)
