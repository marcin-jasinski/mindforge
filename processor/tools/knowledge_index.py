"""
Knowledge index — cumulative concept tracker across lessons.

Maintains a JSON index of concepts with canonical names, aliases,
confidence scores, and source tracking. Generates glossary and
cross-reference docs.

Schema v2 adds: aliases, sources, confidence, normalized_key, alias_map.
Old v1 entries are migrated transparently on read.
"""
from __future__ import annotations

import json
import logging
import re
import tempfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from processor.models import ConceptEntry
from processor.tools.concept_normalizer import (
    normalize_concepts,
    merge_into_index,
    compute_confidence,
    dedupe_key,
)

log = logging.getLogger(__name__)

_SCHEMA_VERSION = 2


def _load_index(index_file: Path) -> dict[str, Any]:
    """Load existing knowledge index or return empty structure."""
    if not index_file.exists():
        return {
            "schema_version": _SCHEMA_VERSION,
            "concepts": {},
            "alias_map": {},
            "lessons_processed": [],
        }
    try:
        data = json.loads(index_file.read_text(encoding="utf-8"))
        return _migrate_if_needed(data)
    except (json.JSONDecodeError, OSError):
        log.warning("Corrupt knowledge index, starting fresh")
        return {
            "schema_version": _SCHEMA_VERSION,
            "concepts": {},
            "alias_map": {},
            "lessons_processed": [],
        }


def _migrate_if_needed(data: dict[str, Any]) -> dict[str, Any]:
    """Migrate v1 index entries to v2 schema (add aliases, sources, confidence)."""
    if data.get("schema_version", 1) >= _SCHEMA_VERSION:
        return data

    log.info("Migrating knowledge index from v1 to v2")
    data["schema_version"] = _SCHEMA_VERSION
    data.setdefault("alias_map", {})

    for name, entry in data.get("concepts", {}).items():
        entry.setdefault("aliases", [])
        entry.setdefault("normalized_key", dedupe_key(name))
        entry.setdefault("confidence", compute_confidence(entry))
        # Build sources from lessons if not present
        if "sources" not in entry:
            entry["sources"] = [
                {"lesson": lesson, "agent": "summarizer"}
                for lesson in entry.get("lessons", [])
            ]

    # Build alias_map from existing concepts
    alias_map: dict[str, str] = {}
    for name, entry in data.get("concepts", {}).items():
        alias_map[name.lower()] = name
        for alias in entry.get("aliases", []):
            alias_map[alias.lower()] = name
    data["alias_map"] = alias_map

    return data


def _save_index(index_file: Path, data: dict[str, Any]) -> None:
    """Atomically save knowledge index."""
    index_file.parent.mkdir(parents=True, exist_ok=True)
    fd, tmp_path = tempfile.mkstemp(
        dir=index_file.parent, suffix=".tmp", prefix="idx_"
    )
    try:
        with open(fd, "w", encoding="utf-8") as f:
            json.dump(data, f, ensure_ascii=False, indent=2, sort_keys=True)
        Path(tmp_path).replace(index_file)
    except Exception:
        Path(tmp_path).unlink(missing_ok=True)
        raise


def extract_concepts_from_summary(summary_text: str) -> list[dict[str, str]]:
    """Extract concepts and definitions from '## Kluczowe koncepcje' section."""
    concepts: list[dict[str, str]] = []
    in_section = False
    for line in summary_text.split("\n"):
        if "## Kluczowe koncepcje" in line:
            in_section = True
            continue
        if in_section and line.startswith("## "):
            break
        if in_section and line.startswith("- **"):
            match = re.match(r"- \*\*(.+?)\*\*:\s*(.+)", line)
            if match:
                concepts.append({
                    "name": match.group(1).strip(),
                    "definition": match.group(2).strip(),
                })
    return concepts


def update_index(
    concepts: list,
    lesson_number: str,
    index_file: Path,
) -> None:
    """Add or update concepts in the knowledge index with normalization.

    Normalizes concept names (canonical form, dedup, merge), tracks aliases,
    confidence scores, and source provenance.

    Args:
        concepts: List of ConceptEntry objects or legacy {"name": ..., "definition": ...} dicts.
        lesson_number: e.g. "S01E01".
        index_file: Path to knowledge_index.json.
    """
    data = _load_index(index_file)

    if lesson_number not in data.get("lessons_processed", []):
        data.setdefault("lessons_processed", []).append(lesson_number)
        data["lessons_processed"].sort()

    # Normalize incoming concepts
    normalized, new_aliases = normalize_concepts(
        concepts, lesson=lesson_number, agent="summarizer",
    )

    # Merge into existing index
    data["concepts"], data["alias_map"] = merge_into_index(
        data["concepts"],
        normalized,
        new_aliases,
        lesson_number,
        existing_alias_map=data.get("alias_map"),
    )

    _save_index(index_file, data)
    log.info(
        "Knowledge index updated: %d concepts total, %d aliases, lesson %s",
        len(data["concepts"]),
        len(data.get("alias_map", {})),
        lesson_number,
    )


def get_known_concepts(index_file: Path) -> dict[str, dict[str, Any]]:
    """Return current concept index for use as context in other agents."""
    data = _load_index(index_file)
    return data.get("concepts", {})


def resolve_concept(name: str, index_file: Path) -> dict[str, Any] | None:
    """Look up a concept by name or alias, returning the canonical entry."""
    data = _load_index(index_file)
    concepts = data.get("concepts", {})
    alias_map = data.get("alias_map", {})

    # Direct match
    if name in concepts:
        return concepts[name]

    # Alias lookup
    canonical = alias_map.get(name.lower())
    if canonical and canonical in concepts:
        return concepts[canonical]

    # Fuzzy match by dedupe_key
    key = dedupe_key(name)
    for cname, entry in concepts.items():
        if entry.get("normalized_key") == key or dedupe_key(cname) == key:
            return entry

    return None


def generate_glossary(index_file: Path, output_dir: Path) -> Path:
    """Generate alphabetical glossary markdown from knowledge index."""
    data = _load_index(index_file)
    concepts = data.get("concepts", {})

    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "glossary.md"

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [
        "---",
        f"generated_at: {now}",
        f"total_concepts: {len(concepts)}",
        f"lessons_processed: {len(data.get('lessons_processed', []))}",
        "---",
        "",
        "# Słownik pojęć — AI_devs 4: Builders",
        "",
    ]

    for name in sorted(concepts.keys(), key=str.lower):
        entry = concepts[name]
        lessons = ", ".join(entry.get("lessons", []))
        lines.append(f"### {name}")
        lines.append(f"{entry.get('definition', '')}")
        aliases = entry.get("aliases", [])
        if aliases:
            lines.append(f"*Aliasy: {', '.join(aliases)}*")
        confidence = entry.get("confidence")
        if confidence is not None:
            lines.append(f"*Lekcje: {lessons} | Pewność: {confidence:.0%}*")
        else:
            lines.append(f"*Lekcje: {lessons}*")
        lines.append("")

    output_path.write_text("\n".join(lines), encoding="utf-8")
    log.info("Glossary generated: %s (%d concepts)", output_path, len(concepts))
    return output_path


def generate_cross_references(index_file: Path, output_dir: Path) -> Path:
    """Generate cross-lesson reference map."""
    data = _load_index(index_file)
    concepts = data.get("concepts", {})
    lessons = sorted(data.get("lessons_processed", []))

    output_dir.mkdir(parents=True, exist_ok=True)
    output_path = output_dir / "cross_references.md"

    now = datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")
    lines = [
        "---",
        f"generated_at: {now}",
        "---",
        "",
        "# Cross-references: Pojęcia × Lekcje",
        "",
    ]

    if not lessons or not concepts:
        lines.append("*Brak danych — przetworz więcej lekcji.*")
        output_path.write_text("\n".join(lines), encoding="utf-8")
        return output_path

    # Table header
    header = "| Pojęcie | " + " | ".join(lessons) + " |"
    separator = "|---|" + "|".join(":---:" for _ in lessons) + "|"
    lines.append(header)
    lines.append(separator)

    for name in sorted(concepts.keys(), key=str.lower):
        entry = concepts[name]
        concept_lessons = set(entry.get("lessons", []))
        cells = []
        for lesson in lessons:
            cells.append("✓" if lesson in concept_lessons else "")
        lines.append(f"| {name} | " + " | ".join(cells) + " |")

    lines.append("")

    # Concepts appearing in multiple lessons
    multi = {
        name: entry
        for name, entry in concepts.items()
        if len(entry.get("lessons", [])) > 1
    }
    if multi:
        lines.append("## Pojęcia powtarzające się")
        lines.append("")
        for name in sorted(multi.keys(), key=str.lower):
            entry = multi[name]
            lesson_list = ", ".join(entry["lessons"])
            lines.append(f"- **{name}** — pojawia się w: {lesson_list}")
        lines.append("")

    output_path.write_text("\n".join(lines), encoding="utf-8")
    log.info("Cross-references generated: %s", output_path)
    return output_path
