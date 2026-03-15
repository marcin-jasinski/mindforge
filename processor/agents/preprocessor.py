"""
Preprocessor agent — removes story/task sections from lesson content.

Strategy:
1. Regex search for known section headers (## Fabuła, ## Zadanie, etc.)
2. If regex finds nothing, use small LLM as fallback to identify sections.
3. Remove identified sections from content.
"""
from __future__ import annotations

import json
import logging
import re

from processor.llm_client import LLMClient
from processor.tools.lesson_parser import CleanedLesson

log = logging.getLogger(__name__)

# Headers that signal story/task sections to remove.
# We look for ## level headers containing these keywords.
_STORY_TASK_HEADERS = [
    r"fabul",           # Fabuła
    r"zadani",          # Zadanie, Zadania
    r"wskazówk",        # Wskazówki
    r"histori",         # Historia (fabularny context)
    r"cel\s+zadania",   # Cel zadania
    r"transkrypcj",     # Transkrypcja filmu z Fabułą
    r"jak\s+działają\s+zadania",  # Jak działają zadania w kursie
]

_HEADER_PATTERN = re.compile(
    r"^(#{1,3})\s+(.+)$", re.MULTILINE
)


def _find_story_sections_regex(content: str) -> list[tuple[int, int]]:
    """Find line ranges of story/task sections using regex.
    
    Returns list of (start_line, end_line) tuples (0-indexed).
    end_line is exclusive (points to the line after the section).
    """
    lines = content.split("\n")
    sections = []
    
    header_indices = []
    for i, line in enumerate(lines):
        match = _HEADER_PATTERN.match(line)
        if match:
            header_indices.append((i, len(match.group(1)), match.group(2)))

    for idx, (line_num, level, text) in enumerate(header_indices):
        is_story = any(
            re.search(pat, text, re.IGNORECASE) for pat in _STORY_TASK_HEADERS
        )
        if not is_story:
            continue

        # Find end: next header of same or higher level, or end of document
        end_line = len(lines)
        for next_line_num, next_level, _ in header_indices[idx + 1:]:
            if next_level <= level:
                end_line = next_line_num
                break

        sections.append((line_num, end_line))
        log.info("Regex found story/task section: lines %d-%d (%s)", line_num, end_line, text)

    return sections


def _find_story_sections_llm(
    content: str, llm: LLMClient, model: str
) -> list[tuple[int, int]]:
    """Use small LLM to identify story/task sections."""
    lines = content.split("\n")
    
    # Send only headers + surrounding context to reduce tokens
    headers_context = []
    for i, line in enumerate(lines):
        if _HEADER_PATTERN.match(line):
            headers_context.append(f"Line {i}: {line}")

    if not headers_context:
        return []

    response_format = {
        "type": "json_schema",
        "json_schema": {
            "name": "section_detection",
            "strict": True,
            "schema": {
                "type": "object",
                "properties": {
                    "story_sections": {
                        "type": "array",
                        "description": "Line numbers where story/task/tutorial sections start",
                        "items": {
                            "type": "object",
                            "properties": {
                                "start_line": {
                                    "type": "integer",
                                    "description": "Line number of the section header",
                                },
                                "header_text": {
                                    "type": "string",
                                    "description": "Text of the header",
                                },
                            },
                            "required": ["start_line", "header_text"],
                            "additionalProperties": False,
                        },
                    },
                },
                "required": ["story_sections"],
                "additionalProperties": False,
            },
        },
    }

    try:
        result = llm.complete(
            model=model,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "You analyze markdown documents from an online course. "
                        "Identify sections that contain: story/narrative (fabuła), "
                        "practical tasks/exercises (zadania), task hints (wskazówki), "
                        "course mechanics explanations (jak działają zadania), "
                        "or story transcripts (transkrypcja). "
                        "These sections should be REMOVED from the educational content. "
                        "Do NOT mark educational/technical content sections. "
                        "Return empty array if no such sections found."
                    ),
                },
                {
                    "role": "user",
                    "content": f"Document headers:\n{chr(10).join(headers_context)}",
                },
            ],
            response_format=response_format,
        )

        data = json.loads(result)
        sections = []
        header_indices = [
            (i, len(m.group(1)), m.group(2))
            for i, line in enumerate(lines)
            if (m := _HEADER_PATTERN.match(line))
        ]
        
        for item in data.get("story_sections", []):
            start = item["start_line"]
            # Find header level at start line
            level = 2  # default
            for line_num, h_level, _ in header_indices:
                if line_num == start:
                    level = h_level
                    break

            # Find end
            end_line = len(lines)
            found_start = False
            for line_num, h_level, _ in header_indices:
                if line_num == start:
                    found_start = True
                    continue
                if found_start and h_level <= level:
                    end_line = line_num
                    break

            sections.append((start, end_line))
            log.info("LLM found story/task section: lines %d-%d (%s)", start, end_line, item.get("header_text", ""))

        return sections

    except Exception:
        log.warning("LLM section detection failed", exc_info=True)
        return []


def _merge_sections(sections: list[tuple[int, int]]) -> list[tuple[int, int]]:
    """Merge overlapping section ranges."""
    if not sections:
        return []
    sorted_sections = sorted(sections)
    merged = [sorted_sections[0]]
    for start, end in sorted_sections[1:]:
        prev_start, prev_end = merged[-1]
        if start <= prev_end:
            merged[-1] = (prev_start, max(prev_end, end))
        else:
            merged.append((start, end))
    return merged


def _remove_sections(content: str, sections: list[tuple[int, int]]) -> str:
    """Remove identified sections from content."""
    if not sections:
        return content

    lines = content.split("\n")
    merged = _merge_sections(sections)
    
    kept = []
    remove_set = set()
    for start, end in merged:
        for i in range(start, min(end, len(lines))):
            remove_set.add(i)

    for i, line in enumerate(lines):
        if i not in remove_set:
            kept.append(line)

    result = "\n".join(kept)
    # Collapse multiple blank lines
    result = re.sub(r"\n{3,}", "\n\n", result)
    return result.strip()


def preprocess(
    cleaned: CleanedLesson,
    llm: LLMClient,
    model: str,
) -> str:
    """Remove story/task sections from cleaned lesson content.
    
    Returns the final clean content ready for summarization.
    """
    content = cleaned.content

    # Step 1: regex-based detection
    sections = _find_story_sections_regex(content)

    # Step 2: if nothing found, try LLM
    if not sections:
        log.info("Regex found no story sections, trying LLM fallback")
        sections = _find_story_sections_llm(content, llm, model)

    if not sections:
        log.info("No story/task sections detected — keeping full content")
        return content

    total_lines = content.count("\n") + 1
    removed_lines = sum(end - start for start, end in sections)
    log.info(
        "Removing %d story/task section(s): %d/%d lines removed",
        len(sections), removed_lines, total_lines,
    )

    return _remove_sections(content, sections)
