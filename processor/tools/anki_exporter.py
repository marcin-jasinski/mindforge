"""
Anki CSV exporter — writes flashcards as tab-separated file importable by Anki.

Format: Front<TAB>Back<TAB>Tags
Encoding: UTF-8 with BOM (Anki requirement for non-ASCII content).
"""
from __future__ import annotations

import html
import logging
import re
from pathlib import Path

log = logging.getLogger(__name__)


def _md_to_html(text: str) -> str:
    """Minimal markdown-to-HTML for Anki card fields."""
    # Bold
    text = re.sub(r"\*\*(.+?)\*\*", r"<b>\1</b>", text)
    # Inline code
    text = re.sub(r"`([^`]+)`", r"<code>\1</code>", text)
    # Code blocks
    text = re.sub(
        r"```[\w]*\n(.*?)```",
        lambda m: "<pre>" + html.escape(m.group(1)) + "</pre>",
        text,
        flags=re.DOTALL,
    )
    # Newlines → <br>
    text = text.replace("\n", "<br>")
    return text


def export_to_anki_csv(
    flashcards: list,
    output_dir: Path,
    filename: str,
) -> Path:
    """Export flashcards to Anki-importable tab-separated CSV.

    Args:
        flashcards: List of Flashcard objects (front, back, tags).
        output_dir: Directory to write the CSV file.
        filename: Original lesson filename (used for output naming).

    Returns:
        Path to the written CSV file.
    """
    output_dir.mkdir(parents=True, exist_ok=True)
    csv_name = Path(filename).stem + ".txt"
    output_path = output_dir / csv_name

    lines: list[str] = []
    for card in flashcards:
        front = _md_to_html(card.front)
        back = _md_to_html(card.back)

        # Handle cloze cards — Anki expects {{c1::...}} in front field
        if card.card_type == "cloze":
            # For cloze, front IS the cloze text, back is ignored by Anki
            # but we still include it as extra context
            pass

        # Sanitize for TSV (escape tabs and newlines in content)
        front = front.replace("\t", " ")
        back = back.replace("\t", " ")

        tags = " ".join(card.tags)
        lines.append(f"{front}\t{back}\t{tags}")

    # Write with UTF-8 BOM for Anki compatibility
    content = "\n".join(lines) + "\n"
    output_path.write_text(content, encoding="utf-8-sig")

    log.info("Anki CSV written: %s (%d cards)", output_path, len(flashcards))
    return output_path
