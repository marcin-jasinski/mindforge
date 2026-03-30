"""
Image analyzer agent — describes diagrams/schemas from lesson images using a vision model.
"""
from __future__ import annotations

import logging
from typing import Any

from processor.llm_client import LLMClient
from processor.tools.egress_policy import EgressPolicyError, validate_outbound_url

log = logging.getLogger(__name__)

SYSTEM_PROMPT = """\
Jesteś ekspertem od analizy diagramów i schematów technicznych. \
Otrzymujesz obrazek załączony do materiału edukacyjnego.

Twoim zadaniem jest:
1. Opisać co przedstawia schemat/diagram (przepływy danych, relacje, architekturę)
2. Wyjaśnić kluczowe koncepcje widoczne na obrazku
3. Zidentyfikować nazwy narzędzi, modeli, komponentów

Zasady:
- Odpowiadaj w języku polskim
- Bądź precyzyjny i konkretny (2-5 zdań)
- Jeśli obrazek to dekoracyjna ilustracja/cover image bez wartości merytorycznej, \
odpowiedz DOKŁADNIE: "SKIP"
- Skup się na informacjach przydatnych do nauki\
"""


def analyze_images(
    images: list[dict[str, str]],
    llm: LLMClient,
    model: str,
) -> list[dict[str, str]]:
    """Analyze lesson images using a vision model.

    Args:
        images: List of {"alt": ..., "url": ...} from extract_images().
        llm: LLM client instance.
        model: Vision-capable model name.

    Returns:
        List of {"url": ..., "alt": ..., "description": ...} for non-decorative images.
    """
    if not images:
        return []

    results: list[dict[str, str]] = []
    for img in images:
        url = img.get("url", "")
        # Validate the image URL against the egress policy before forwarding
        # it to the vision model, so an attacker cannot force requests to
        # internal infrastructure via the provider's model fetch.
        try:
            validate_outbound_url(url)
        except EgressPolicyError as exc:
            log.warning(
                "Image URL blocked by egress policy, skipping: %s — %s",
                url[:80],
                exc,
            )
            continue
        try:
            description = _analyze_single(img, llm, model)
            if description and description.strip().upper() != "SKIP":
                results.append({
                    "url": img["url"],
                    "alt": img.get("alt", ""),
                    "description": description,
                })
                log.info("Image analyzed: %s → %d chars", img["url"][:60], len(description))
            else:
                log.info("Image skipped (decorative): %s", img["url"][:60])
        except Exception:
            log.warning("Failed to analyze image: %s", img["url"][:60], exc_info=True)

    return results


def _analyze_single(image: dict[str, str], llm: LLMClient, model: str) -> str:
    """Send a single image to the vision model for analysis."""
    user_content: list[dict[str, Any]] = [
        {"type": "text", "text": f"Opisz ten schemat/diagram z lekcji. Alt text: \"{image.get('alt', '')}\"."},
        {
            "type": "image_url",
            "image_url": {"url": image["url"]},
        },
    ]

    return llm.complete(
        model=model,
        messages=[
            {"role": "system", "content": SYSTEM_PROMPT},
            {"role": "user", "content": user_content},
        ],
        temperature=0.2,
    )


def format_image_descriptions(descriptions: list[dict[str, str]]) -> str:
    """Format image descriptions as markdown to inject into lesson content."""
    if not descriptions:
        return ""

    parts = ["\n\n## Opisy schematów i diagramów\n"]
    for desc in descriptions:
        alt = desc.get("alt", "Schemat")
        parts.append(f"### {alt or 'Schemat'}\n{desc['description']}\n")

    return "\n".join(parts)
