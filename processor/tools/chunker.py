"""
Content chunker — splits lesson text into overlapping chunks for RAG indexing.

Paragraph-aware splitting preserves semantic boundaries where possible.
"""
from __future__ import annotations

import hashlib
import re
from dataclasses import dataclass, field


@dataclass
class Chunk:
    id: str
    text: str
    position: int
    lesson_number: str
    metadata: dict[str, str] = field(default_factory=dict)


def chunk_content(
    text: str,
    lesson_number: str,
    *,
    chunk_size: int = 500,
    overlap: int = 100,
) -> list[Chunk]:
    """Split lesson content into overlapping chunks.

    Splits on paragraph boundaries first, then merges small paragraphs
    and splits large ones to stay within chunk_size.

    Args:
        text: Cleaned lesson content.
        lesson_number: e.g. "S01E01".
        chunk_size: Target chunk size in characters.
        overlap: Overlap between consecutive chunks in characters.

    Returns:
        List of Chunk objects with stable IDs based on content hash.
    """
    paragraphs = _split_paragraphs(text)
    merged = _merge_paragraphs(paragraphs, chunk_size)
    chunks: list[Chunk] = []

    for i, segment in enumerate(merged):
        # Add overlap from previous chunk
        if i > 0 and overlap > 0:
            prev_text = merged[i - 1]
            overlap_text = prev_text[-overlap:] if len(prev_text) > overlap else prev_text
            segment = overlap_text + "\n" + segment

        chunk_id = _make_id(lesson_number, i, segment)
        chunks.append(Chunk(
            id=chunk_id,
            text=segment.strip(),
            position=i,
            lesson_number=lesson_number,
        ))

    return chunks


def _split_paragraphs(text: str) -> list[str]:
    """Split text into paragraphs (double newline or heading boundaries)."""
    # Split on double newlines or markdown headings
    parts = re.split(r"\n{2,}|(?=\n#{1,3}\s)", text)
    return [p.strip() for p in parts if p.strip()]


def _merge_paragraphs(paragraphs: list[str], chunk_size: int) -> list[str]:
    """Merge small paragraphs and split large ones to target chunk_size."""
    result: list[str] = []
    current = ""

    for para in paragraphs:
        # If paragraph alone exceeds chunk_size, split it
        if len(para) > chunk_size:
            if current:
                result.append(current)
                current = ""
            for sub in _split_long(para, chunk_size):
                result.append(sub)
            continue

        # Try to merge with current
        if current and len(current) + len(para) + 2 > chunk_size:
            result.append(current)
            current = para
        else:
            current = f"{current}\n\n{para}" if current else para

    if current:
        result.append(current)

    return result


def _split_long(text: str, chunk_size: int) -> list[str]:
    """Split a long paragraph into sentence-boundary chunks."""
    sentences = re.split(r"(?<=[.!?])\s+", text)
    result: list[str] = []
    current = ""

    for sentence in sentences:
        if current and len(current) + len(sentence) + 1 > chunk_size:
            result.append(current)
            current = sentence
        else:
            current = f"{current} {sentence}" if current else sentence

    if current:
        result.append(current)

    return result


def _make_id(lesson_number: str, position: int, text: str) -> str:
    """Create a stable chunk ID from lesson + position + content hash."""
    content_hash = hashlib.sha256(text.encode()).hexdigest()[:8]
    return f"{lesson_number}_chunk_{position:03d}_{content_hash}"
