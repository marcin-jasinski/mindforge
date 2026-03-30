"""
Evaluation framework — quality assessment with Langfuse scoring.

Provides deterministic evaluators that report scores to Langfuse.
"""
from __future__ import annotations

import logging
from collections import Counter
from dataclasses import dataclass, field
from typing import Any

from processor import tracing
from processor.models import LessonArtifact
from processor.validation import validate_artifact, ValidationResult

log = logging.getLogger(__name__)

# Polish stopwords for content grounding checks
_STOPWORDS = frozenset({
    "i", "w", "z", "do", "na", "o", "a", "to", "jest", "się",
    "nie", "co", "jak", "że", "dla", "po", "od", "za", "we",
    "ze", "np", "itp", "tzw", "itd", "ten", "ta", "te", "lub",
    "the", "a", "an", "is", "of", "in", "and", "or", "by",
    "are", "as", "with", "for", "to", "it", "on", "at", "be",
})


@dataclass
class EvalScore:
    """A single evaluation metric."""
    name: str
    value: float  # 0.0 to 1.0
    comment: str = ""


@dataclass
class EvalResult:
    """Aggregated evaluation result for a lesson."""
    lesson: str
    scores: list[EvalScore] = field(default_factory=list)

    @property
    def summary(self) -> dict[str, float]:
        return {s.name: s.value for s in self.scores}

    @property
    def average(self) -> float:
        if not self.scores:
            return 0.0
        return sum(s.value for s in self.scores) / len(self.scores)


def evaluate_artifact(
    artifact: LessonArtifact,
    *,
    validation_result: ValidationResult | None = None,
) -> EvalResult:
    """Run all evaluators on a processed artifact and report scores.

    Evaluators:
    1. Deterministic validation scores (from validation.py)
    2. Concept coverage: ratio of concepts with adequate definitions
    3. Content grounding: concept definitions reference lesson content
    4. Flashcard balance: card type distribution
    5. Map connectivity: graph connectedness ratio
    """
    result = EvalResult(lesson=artifact.lesson_number)

    # 1. Deterministic validation scores
    if validation_result is None:
        validation_result = validate_artifact(artifact)
    for name, value in validation_result.scores.items():
        result.scores.append(EvalScore(
            name=name,
            value=value,
            comment=f"Deterministic check: {len(validation_result.issues)} issues",
        ))

    # 2. Concept coverage
    result.scores.append(_eval_concept_coverage(artifact))

    # 3. Content grounding
    result.scores.append(_eval_content_grounding(artifact))

    # 4. Flashcard balance
    if artifact.flashcards:
        result.scores.append(_eval_flashcard_balance(artifact))

    # 5. Map connectivity
    if artifact.concept_map:
        result.scores.append(_eval_map_connectivity(artifact))

    # Report to Langfuse
    _report_to_langfuse(result)

    log.info(
        "Eval complete for %s: avg=%.2f, scores=%s",
        artifact.lesson_number,
        result.average,
        {k: f"{v:.2f}" for k, v in result.summary.items()},
    )
    return result


def _eval_concept_coverage(artifact: LessonArtifact) -> EvalScore:
    """Score: ratio of concepts with definitions longer than 20 chars."""
    if not artifact.summary or not artifact.summary.key_concepts:
        return EvalScore("concept_coverage", 0.0, "No concepts in summary")

    concepts = artifact.summary.key_concepts
    adequate = sum(1 for c in concepts if len(c.definition) > 20)
    ratio = adequate / len(concepts)

    return EvalScore(
        "concept_coverage",
        round(ratio, 2),
        f"{adequate}/{len(concepts)} concepts have adequate definitions (>20 chars)",
    )


def _eval_content_grounding(artifact: LessonArtifact) -> EvalScore:
    """Score: ratio of concept definitions that reference terms from lesson content."""
    if not artifact.summary or not artifact.summary.key_concepts or not artifact.cleaned_content:
        return EvalScore("content_grounding", 0.0, "Insufficient data")

    content_lower = artifact.cleaned_content.lower()
    grounded = 0
    total = len(artifact.summary.key_concepts)

    for concept in artifact.summary.key_concepts:
        def_words = set(concept.definition.lower().split())
        meaningful = def_words - _STOPWORDS
        if not meaningful:
            continue

        matches = sum(1 for w in meaningful if w in content_lower)
        if matches / len(meaningful) > 0.3:
            grounded += 1

    ratio = grounded / total if total > 0 else 0.0

    return EvalScore(
        "content_grounding",
        round(ratio, 2),
        f"{grounded}/{total} concept definitions are grounded in lesson content",
    )


def _eval_flashcard_balance(artifact: LessonArtifact) -> EvalScore:
    """Score: card type distribution — penalize monoculture."""
    if not artifact.flashcards:
        return EvalScore("flashcard_balance", 0.0, "No flashcards")

    types = Counter(c.card_type for c in artifact.flashcards)
    total = len(artifact.flashcards)

    num_types = len(types)
    max_share = max(types.values()) / total

    if num_types >= 3 and max_share <= 0.5:
        score = 1.0
    elif num_types >= 2 and max_share <= 0.7:
        score = 0.8
    elif num_types >= 2:
        score = 0.6
    else:
        score = 0.3

    return EvalScore(
        "flashcard_balance",
        score,
        f"{num_types} types, max share={max_share:.0%}: {dict(types)}",
    )


def _eval_map_connectivity(artifact: LessonArtifact) -> EvalScore:
    """Score: ratio of connected nodes in the concept map."""
    cmap = artifact.concept_map
    if not cmap or not cmap.nodes:
        return EvalScore("map_connectivity", 0.0, "No concept map")

    node_ids = {n.id for n in cmap.nodes}
    connected: set[str] = set()
    for rel in cmap.relationships:
        if rel.source_id in node_ids:
            connected.add(rel.source_id)
        if rel.target_id in node_ids:
            connected.add(rel.target_id)

    ratio = len(connected) / len(node_ids) if node_ids else 0.0

    return EvalScore(
        "map_connectivity",
        round(ratio, 2),
        f"{len(connected)}/{len(node_ids)} nodes are connected by relationships",
    )


def _report_to_langfuse(result: EvalResult) -> None:
    """Report eval scores to Langfuse as trace-level scores."""
    if not tracing.is_active():
        return

    try:
        for score in result.scores:
            tracing.score(
                name=score.name,
                value=score.value,
                comment=score.comment,
            )
    except Exception:
        log.debug("Failed to report eval scores to Langfuse", exc_info=True)
