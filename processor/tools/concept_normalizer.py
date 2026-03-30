"""
Concept normalization — canonical names, deduplication, and merge rules.

Handles both Polish and English concept names with acronym preservation.
"""
from __future__ import annotations

import logging
import re
from typing import Any

log = logging.getLogger(__name__)

# Known acronyms to preserve in uppercase during title-casing
ACRONYMS = frozenset({
    "AI", "API", "APIs", "CLI", "CoT", "CSS", "DOM",
    "GPT", "HTML", "HTTP", "HTTPS", "ID", "JSON",
    "JWT", "LLM", "LLMs", "MCP", "ML", "NER",
    "NLP", "PDF", "RAG", "REST", "SDK", "SQL",
    "SSE", "STT", "TTS", "URL", "URLs", "XML", "YAML", "YML",
})


def title_case(text: str) -> str:
    """Title-case preserving known acronyms."""
    def _case_word(word: str) -> str:
        if word.upper() in ACRONYMS:
            return word.upper()
        return word.capitalize()
    return " ".join(_case_word(w) for w in text.split())


def _singularize(text: str) -> str:
    """Simplistic singular form for dedup keys (English + basic Polish)."""
    text = re.sub(r"ies$", "y", text, flags=re.IGNORECASE)
    text = re.sub(r"(?<!s)s$", "", text, flags=re.IGNORECASE)
    return text


def dedupe_key(name: str) -> str:
    """Lowercased + whitespace-normalized + singularized dedup key."""
    normalized = " ".join(name.strip().lower().split())
    return _singularize(normalized)


def canonical_name(name: str) -> str:
    """Produce a canonical display name: trimmed + title-cased with acronym preservation."""
    trimmed = " ".join(name.strip().split())
    return title_case(trimmed)


def normalize_concepts(
    concepts: list,
    *,
    lesson: str = "",
    agent: str = "summarizer",
) -> tuple[list[dict[str, Any]], dict[str, str]]:
    """Normalize a batch of concepts: dedup, canonical names, merge definitions.

    Args:
        concepts: List of ConceptEntry objects or {"name": ..., "definition": ...} dicts.
        lesson: Lesson identifier (e.g. "S01E01").
        agent: Agent that produced the concepts.

    Returns:
        (normalized_concepts, alias_map) where:
        - normalized_concepts: list of enriched concept dicts with canonical name, aliases, sources.
        - alias_map: mapping from original variant (lowered) → canonical name.
    """
    canon_map: dict[str, dict[str, Any]] = {}
    alias_map: dict[str, str] = {}

    for concept in concepts:
        if hasattr(concept, "name"):
            raw_name = concept.name
            definition = concept.definition
        else:
            raw_name = concept["name"]
            definition = concept["definition"]

        key = dedupe_key(raw_name)
        canon = canonical_name(raw_name)
        source = {"lesson": lesson, "agent": agent}

        if key in canon_map:
            existing = canon_map[key]
            # Keep longer name form (e.g. "Large Language Model" over "LLM")
            if len(canon) > len(existing["name"]):
                existing["name"] = canon
            # Keep longer/better definition
            if len(definition) > len(existing.get("definition", "")):
                existing["definition"] = definition
            # Merge sources
            if source not in existing["sources"]:
                existing["sources"].append(source)
            # Track alias
            if raw_name.strip() != existing["name"]:
                existing["_alias_set"].add(raw_name.strip())
        else:
            canon_map[key] = {
                "name": canon,
                "definition": definition,
                "_alias_set": {raw_name.strip()} if raw_name.strip() != canon else set(),
                "sources": [source],
                "normalized_key": key,
            }

        alias_map[raw_name.strip().lower()] = canon_map[key]["name"]

    # Convert internal sets to sorted lists
    result = []
    for entry in canon_map.values():
        aliases = sorted(entry.pop("_alias_set") - {entry["name"]})
        entry["aliases"] = aliases
        result.append(entry)

    log.info(
        "Normalized %d raw concepts → %d canonical (%d aliases)",
        len(concepts), len(result), sum(len(e["aliases"]) for e in result),
    )
    return result, alias_map


def merge_into_index(
    index_concepts: dict[str, Any],
    new_concepts: list[dict[str, Any]],
    new_aliases: dict[str, str],
    lesson: str,
    *,
    existing_alias_map: dict[str, str] | None = None,
) -> tuple[dict[str, Any], dict[str, str]]:
    """Merge normalized concepts into the existing knowledge index.

    Handles:
    - Matching by dedupe_key to find existing canonical entries
    - Merging aliases from both old and new
    - Updating definitions (prefer longer)
    - Updating sources and lessons
    - Building the combined alias map

    Returns:
        (updated_concepts, updated_alias_map)
    """
    if existing_alias_map is None:
        existing_alias_map = {}

    # Build reverse index: dedupe_key → canonical name in existing index
    existing_keys: dict[str, str] = {}
    for name in index_concepts:
        key = dedupe_key(name)
        existing_keys[key] = name

    merged_alias_map = dict(existing_alias_map)

    for concept in new_concepts:
        key = concept["normalized_key"]
        new_name = concept["name"]

        if key in existing_keys:
            old_name = existing_keys[key]
            entry = index_concepts[old_name]

            # Update definition if new is longer
            if len(concept["definition"]) > len(entry.get("definition", "")):
                entry["definition"] = concept["definition"]

            # Merge lessons
            if lesson not in entry.get("lessons", []):
                entry.setdefault("lessons", []).append(lesson)
                entry["lessons"].sort()

            # Merge aliases
            old_aliases = set(entry.get("aliases", []))
            old_aliases.update(concept.get("aliases", []))
            if new_name != old_name:
                old_aliases.add(new_name)
            old_aliases.discard(old_name)
            entry["aliases"] = sorted(old_aliases)

            # Merge sources
            existing_sources = entry.get("sources", [])
            for src in concept.get("sources", []):
                if src not in existing_sources:
                    existing_sources.append(src)
            entry["sources"] = existing_sources

            # Ensure normalized_key
            entry["normalized_key"] = key

            # Rename entry if new name is longer (more descriptive)
            if len(new_name) > len(old_name):
                index_concepts[new_name] = entry
                del index_concepts[old_name]
                existing_keys[key] = new_name
                merged_alias_map[old_name.lower()] = new_name
        else:
            # New concept
            index_concepts[new_name] = {
                "definition": concept["definition"],
                "first_seen": lesson,
                "lessons": [lesson],
                "aliases": concept.get("aliases", []),
                "sources": concept.get("sources", []),
                "confidence": compute_confidence_from_new(concept, lesson),
                "normalized_key": key,
            }
            existing_keys[key] = new_name

        # Update alias map
        canonical = existing_keys[key]
        for alias in index_concepts[canonical].get("aliases", []):
            merged_alias_map[alias.lower()] = canonical
        merged_alias_map[canonical.lower()] = canonical

    # Recompute confidence for all touched entries
    for concept in new_concepts:
        key = concept["normalized_key"]
        if key in existing_keys:
            name = existing_keys[key]
            index_concepts[name]["confidence"] = compute_confidence(index_concepts[name])

    return index_concepts, merged_alias_map


def compute_confidence(entry: dict[str, Any]) -> float:
    """Compute confidence score for a concept index entry.

    Factors:
    - Has definition: +0.3
    - Definition length > 50 chars: +0.1
    - Appears in multiple lessons: +0.1 per extra lesson (max +0.3)
    - Multiple source agents: +0.1

    Base: 0.2, Max: 1.0
    """
    score = 0.2

    definition = entry.get("definition", "")
    if definition:
        score += 0.3
        if len(definition) > 50:
            score += 0.1

    lessons = entry.get("lessons", [])
    extra = min(len(lessons) - 1, 3)
    score += extra * 0.1

    sources = entry.get("sources", [])
    agents = {s.get("agent") for s in sources if isinstance(s, dict)}
    if len(agents) > 1:
        score += 0.1

    return min(round(score, 2), 1.0)


def compute_confidence_from_new(concept: dict[str, Any], lesson: str) -> float:
    """Compute initial confidence for a newly seen concept."""
    score = 0.2
    if concept.get("definition"):
        score += 0.3
        if len(concept["definition"]) > 50:
            score += 0.1
    return min(round(score, 2), 1.0)
