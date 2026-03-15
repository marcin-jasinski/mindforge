"""
Lesson parser — deterministic markdown cleaning using frontmatter + regex.

Strips video embeds, CDN images, and extracts metadata + links.
"""
from __future__ import annotations

import re
import logging
from dataclasses import dataclass, field
from typing import Any

import frontmatter

log = logging.getLogger(__name__)

# Video embed patterns (Vimeo, YouTube)
_VIDEO_EMBED_RE = re.compile(
    r"!\[.*?\]\(https?://(?:vimeo\.com|youtu\.be|www\.youtube\.com)\S*\)\s*",
    re.IGNORECASE,
)

# Standalone video URLs on their own line (e.g. <https://vimeo.com/...>)
_VIDEO_URL_LINE_RE = re.compile(
    r"^.*<?\s*https?://(?:vimeo\.com|youtu\.be|www\.youtube\.com)\S*>?\s*$",
    re.MULTILINE | re.IGNORECASE,
)

# CDN/cloud images (overment CDN) — remove image but keep surrounding text
_CDN_IMAGE_RE = re.compile(
    r"!\[.*?\]\(https?://cloud\.overment\.com/\S+\)\s*",
    re.IGNORECASE,
)

# Any remaining markdown image syntax
_ANY_IMAGE_RE = re.compile(
    r"!\[.*?\]\(https?://\S+\)\s*",
    re.IGNORECASE,
)

# Link extraction: [text](url)
_LINK_RE = re.compile(r"\[([^\]]*)\]\((https?://[^\s)]+)\)")


@dataclass
class ParsedLesson:
    """Result of parsing a lesson markdown file."""
    title: str
    metadata: dict[str, Any]
    content: str  # raw content without frontmatter
    links: list[dict[str, str]]  # [{"text": ..., "url": ...}]


@dataclass
class CleanedLesson:
    """Result after deterministic cleaning (before LLM section removal)."""
    title: str
    metadata: dict[str, Any]
    content: str  # cleaned content
    links: list[dict[str, str]]


def parse_lesson_file(filepath: str, raw_content: str | None = None) -> ParsedLesson:
    """Parse a lesson markdown file, extracting frontmatter and links."""
    if raw_content is None:
        with open(filepath, "r", encoding="utf-8") as f:
            raw_content = f.read()

    post = frontmatter.loads(raw_content)
    metadata = dict(post.metadata)
    title = str(metadata.get("title", "Untitled"))
    content = post.content

    links = extract_links(content)
    log.info("Parsed lesson: title=%s, links=%d", title, len(links))

    return ParsedLesson(
        title=title,
        metadata=metadata,
        content=content,
        links=links,
    )


def clean_content(content: str) -> str:
    """Remove video embeds and images from markdown content."""
    cleaned = content

    # Remove video embeds
    cleaned = _VIDEO_EMBED_RE.sub("", cleaned)
    cleaned = _VIDEO_URL_LINE_RE.sub("", cleaned)

    # Remove CDN images
    cleaned = _CDN_IMAGE_RE.sub("", cleaned)

    # Remove any remaining images
    cleaned = _ANY_IMAGE_RE.sub("", cleaned)

    # Collapse multiple blank lines to max 2
    cleaned = re.sub(r"\n{3,}", "\n\n", cleaned)

    return cleaned.strip()


def extract_links(content: str) -> list[dict[str, str]]:
    """Extract all markdown links from content."""
    matches = _LINK_RE.findall(content)
    seen = set()
    links = []
    for text, url in matches:
        if url not in seen:
            seen.add(url)
            links.append({"text": text.strip(), "url": url.strip()})
    return links


def clean_lesson(parsed: ParsedLesson) -> CleanedLesson:
    """Apply deterministic cleaning to a parsed lesson."""
    cleaned_content = clean_content(parsed.content)
    return CleanedLesson(
        title=parsed.title,
        metadata=parsed.metadata,
        content=cleaned_content,
        links=parsed.links,
    )
