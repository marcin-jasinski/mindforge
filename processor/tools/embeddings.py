"""
Embedding client — generate text embeddings via OpenAI-compatible API.

Uses the same base URL and API key as the LLM client, targeting an
embedding model (default: text-embedding-3-small).
"""
from __future__ import annotations

import logging
from typing import Any

import requests

log = logging.getLogger(__name__)


def embed_texts(
    texts: list[str],
    *,
    base_url: str,
    api_key: str,
    model: str = "openai/text-embedding-3-small",
    batch_size: int = 50,
    headers: dict[str, str] | None = None,
) -> list[list[float]]:
    """Generate embeddings for a list of texts.

    Args:
        texts: Texts to embed.
        base_url: OpenAI-compatible API base URL.
        api_key: API key.
        model: Embedding model name.
        batch_size: Max texts per API call.
        headers: Extra request headers (e.g. OpenRouter referer).

    Returns:
        List of embedding vectors (same order as input texts).
    """
    if not texts:
        return []

    all_embeddings: list[list[float]] = []
    endpoint = f"{base_url.rstrip('/')}/embeddings"

    req_headers: dict[str, str] = {"Content-Type": "application/json"}
    if headers:
        req_headers.update(headers)
    if api_key:
        req_headers["Authorization"] = f"Bearer {api_key}"

    for i in range(0, len(texts), batch_size):
        batch = texts[i : i + batch_size]
        body: dict[str, Any] = {"input": batch, "model": model}

        log.info("Embedding batch %d–%d of %d texts", i, i + len(batch), len(texts))
        response = requests.post(
            endpoint,
            headers=req_headers,
            json=body,
            timeout=60,
        )
        response.raise_for_status()
        data = response.json()

        # Sort by index to preserve order
        sorted_data = sorted(data["data"], key=lambda x: x["index"])
        all_embeddings.extend(d["embedding"] for d in sorted_data)

    log.info("Embedded %d texts (dim=%d)", len(all_embeddings), len(all_embeddings[0]) if all_embeddings else 0)
    return all_embeddings
