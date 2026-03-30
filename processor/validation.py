"""
Deterministic quality validation for pipeline artifacts.

Checks concept consistency, flashcard quality, summary completeness,
and concept map integrity without LLM calls.
"""
from __future__ import annotations

import logging
from dataclasses import dataclass, field

from processor.models import LessonArtifact, ConceptEntry, SummaryData, ConceptMapData
from processor.tools.concept_normalizer import dedupe_key

log = logging.getLogger(__name__)


@dataclass
class ValidationIssue:
    severity: str  # "error", "warning", "info"
    category: str  # "concepts", "flashcards", "summary", "map", "grounding"
    message: str
    detail: str = ""


@dataclass
class ValidationResult:
    passed: bool
    issues: list[ValidationIssue] = field(default_factory=list)
    scores: dict[str, float] = field(default_factory=dict)

    @property
    def errors(self) -> list[ValidationIssue]:
        return [i for i in self.issues if i.severity == "error"]

    @property
    def warnings(self) -> list[ValidationIssue]:
        return [i for i in self.issues if i.severity == "warning"]


def validate_artifact(artifact: LessonArtifact) -> ValidationResult:
    """Run all deterministic validation checks on a processed lesson artifact."""
    issues: list[ValidationIssue] = []
    scores: dict[str, float] = {}

    if artifact.summary:
        s_issues, s_score = _validate_summary(artifact)
        issues.extend(s_issues)
        scores["summary_completeness"] = s_score

    if artifact.summary and artifact.summary.key_concepts:
        c_issues, c_score = _validate_concepts(artifact.summary.key_concepts)
        issues.extend(c_issues)
        scores["concept_quality"] = c_score

    if artifact.flashcards:
        f_issues, f_score = _validate_flashcards(artifact)
        issues.extend(f_issues)
        scores["flashcard_quality"] = f_score

    if artifact.concept_map:
        m_issues, m_score = _validate_concept_map(artifact)
        issues.extend(m_issues)
        scores["map_integrity"] = m_score

    if artifact.summary and artifact.concept_map:
        x_issues = _validate_cross_references(artifact)
        issues.extend(x_issues)

    has_errors = any(i.severity == "error" for i in issues)
    result = ValidationResult(passed=not has_errors, issues=issues, scores=scores)

    log.info(
        "Validation %s: %d errors, %d warnings, scores=%s",
        "PASSED" if result.passed else "FAILED",
        len(result.errors),
        len(result.warnings),
        {k: f"{v:.2f}" for k, v in scores.items()},
    )
    return result


def _validate_summary(
    artifact: LessonArtifact,
) -> tuple[list[ValidationIssue], float]:
    issues: list[ValidationIssue] = []
    assert artifact.summary is not None
    s: SummaryData = artifact.summary
    score = 1.0

    if not s.overview or len(s.overview) < 50:
        issues.append(ValidationIssue(
            "warning", "summary", "Overview too short",
            f"length={len(s.overview or '')}",
        ))
        score -= 0.2

    if len(s.key_concepts) < 3:
        issues.append(ValidationIssue(
            "warning", "summary", "Too few key concepts",
            f"count={len(s.key_concepts)}",
        ))
        score -= 0.15

    if len(s.key_facts) < 3:
        issues.append(ValidationIssue(
            "warning", "summary", "Too few key facts",
            f"count={len(s.key_facts)}",
        ))
        score -= 0.15

    if not s.practical_tips:
        issues.append(ValidationIssue("info", "summary", "No practical tips"))
        score -= 0.1

    return issues, max(score, 0.0)


def _validate_concepts(
    concepts: list[ConceptEntry],
) -> tuple[list[ValidationIssue], float]:
    issues: list[ValidationIssue] = []
    score = 1.0

    # Empty or very short definitions
    empty_def = [c for c in concepts if not c.definition or len(c.definition.strip()) < 10]
    if empty_def:
        names = [c.name for c in empty_def]
        issues.append(ValidationIssue(
            "warning", "concepts", "Empty or very short definitions",
            f"concepts={names}",
        ))
        score -= min(0.1 * len(empty_def), 0.3)

    # Duplicate concepts after normalization
    seen_keys: dict[str, list[str]] = {}
    for c in concepts:
        key = dedupe_key(c.name)
        seen_keys.setdefault(key, []).append(c.name)
    duplicates = {k: v for k, v in seen_keys.items() if len(v) > 1}
    if duplicates:
        issues.append(ValidationIssue(
            "warning", "concepts",
            "Duplicate concepts detected (after normalization)",
            f"duplicates={duplicates}",
        ))
        score -= min(0.15 * len(duplicates), 0.3)

    # Tautological definitions (definition just restates the name)
    for c in concepts:
        if c.definition and c.name.lower() in c.definition.lower()[:len(c.name) + 10]:
            clean_def = c.definition.lower().replace(c.name.lower(), "").strip()
            if len(clean_def) < 20:
                issues.append(ValidationIssue(
                    "info", "concepts",
                    f"Definition for '{c.name}' may be tautological",
                    f"definition='{c.definition[:80]}'",
                ))

    return issues, max(score, 0.0)


def _validate_flashcards(
    artifact: LessonArtifact,
) -> tuple[list[ValidationIssue], float]:
    issues: list[ValidationIssue] = []
    score = 1.0

    if len(artifact.flashcards) < 5:
        issues.append(ValidationIssue(
            "warning", "flashcards", "Too few flashcards",
            f"count={len(artifact.flashcards)}",
        ))
        score -= 0.2

    # Empty fronts/backs
    empty = [i for i, card in enumerate(artifact.flashcards)
             if not card.front.strip() or not card.back.strip()]
    if empty:
        issues.append(ValidationIssue(
            "error", "flashcards", "Flashcards with empty front or back",
            f"indices={empty}",
        ))
        score -= 0.3

    # Duplicate questions
    fronts = [c.front.strip().lower() for c in artifact.flashcards]
    seen: set[str] = set()
    dup_count = 0
    for f in fronts:
        if f in seen:
            dup_count += 1
        seen.add(f)
    if dup_count:
        issues.append(ValidationIssue(
            "warning", "flashcards", "Duplicate flashcard questions",
            f"count={dup_count}",
        ))
        score -= 0.1

    # Card type variety
    types = {c.card_type for c in artifact.flashcards}
    if len(types) < 2:
        issues.append(ValidationIssue(
            "info", "flashcards",
            f"Only one card type used: {types}",
        ))

    return issues, max(score, 0.0)


def _validate_concept_map(
    artifact: LessonArtifact,
) -> tuple[list[ValidationIssue], float]:
    issues: list[ValidationIssue] = []
    score = 1.0
    assert artifact.concept_map is not None
    cmap: ConceptMapData = artifact.concept_map

    node_ids = {n.id for n in cmap.nodes}

    # Orphan nodes
    connected: set[str] = set()
    for rel in cmap.relationships:
        connected.add(rel.source_id)
        connected.add(rel.target_id)
    orphans = node_ids - connected
    if orphans:
        issues.append(ValidationIssue(
            "warning", "map", "Orphan nodes (no relationships)",
            f"nodes={sorted(orphans)}",
        ))
        score -= min(0.05 * len(orphans), 0.2)

    # Dangling relationship references
    for rel in cmap.relationships:
        if rel.source_id not in node_ids:
            issues.append(ValidationIssue(
                "error", "map",
                f"Relationship references missing source node: {rel.source_id}",
            ))
            score -= 0.15
        if rel.target_id not in node_ids:
            issues.append(ValidationIssue(
                "error", "map",
                f"Relationship references missing target node: {rel.target_id}",
            ))
            score -= 0.15

    # Self-referencing relationships
    self_refs = [r for r in cmap.relationships if r.source_id == r.target_id]
    if self_refs:
        issues.append(ValidationIssue(
            "warning", "map",
            f"Self-referencing relationships: {len(self_refs)}",
        ))
        score -= 0.1

    # Group membership referencing missing nodes
    for group in cmap.groups:
        for nid in group.node_ids:
            if nid not in node_ids:
                issues.append(ValidationIssue(
                    "warning", "map",
                    f"Group '{group.name}' references missing node: {nid}",
                ))

    return issues, max(score, 0.0)


def _validate_cross_references(artifact: LessonArtifact) -> list[ValidationIssue]:
    """Check alignment between summary concepts and concept map nodes."""
    issues: list[ValidationIssue] = []
    assert artifact.summary is not None and artifact.concept_map is not None

    summary_keys = {dedupe_key(c.name) for c in artifact.summary.key_concepts}
    map_labels = {dedupe_key(n.label) for n in artifact.concept_map.nodes}

    missing_from_map = summary_keys - map_labels
    if len(missing_from_map) > len(summary_keys) * 0.5:
        issues.append(ValidationIssue(
            "info", "grounding",
            "Many summary concepts missing from concept map",
            f"missing={len(missing_from_map)}/{len(summary_keys)}",
        ))

    return issues
