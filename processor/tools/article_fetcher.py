"""
Article fetcher — optionally fetches content from article links.

Classifies links via small LLM, then fetches relevant articles.
Errors are logged but never block the pipeline.
"""
from __future__ import annotations

import json
import logging
import re
from typing import Any

import requests

from processor.llm_client import LLMClient

log = logging.getLogger(__name__)

MAX_ARTICLES = 3
FETCH_TIMEOUT = 10
MAX_ARTICLE_CHARS = 2000

# URLs that are clearly tools/platforms, not articles
_TOOL_URL_PATTERNS = [
    r"github\.com/[^/]+/[^/]+$",    # repo root (not a blog post)
    r"platform\.openai\.com/api",
    r"console\.anthropic\.com",
    r"aistudio\.google\.com/api",
    r"openrouter\.ai/settings",
    r"lmstudio\.ai$",
    r"cursor\.com$",
    r"klingai\.com",
    r"replicate\.com$",
    r"huggingface\.co$",
]

# URLs that are clearly articles/blog posts
_ARTICLE_URL_PATTERNS = [
    r"/blog/",
    r"/posts/",
    r"/docs/",
    r"/papers/",
    r"arxiv\.org",
    r"thinkingmachines\.ai/blog",
]


def _quick_classify(url: str) -> str | None:
    """Fast regex-based classification. Returns type or None if unsure."""
    for pattern in _TOOL_URL_PATTERNS:
        if re.search(pattern, url, re.IGNORECASE):
            return "tool"
    for pattern in _ARTICLE_URL_PATTERNS:
        if re.search(pattern, url, re.IGNORECASE):
            return "article"
    return None


def classify_links(
    links: list[dict[str, str]],
    llm: LLMClient,
    model: str,
) -> list[dict[str, str]]:
    """Classify links and return only article-type links worth fetching."""
    articles = []
    uncertain = []

    for link in links:
        quick_type = _quick_classify(link["url"])
        if quick_type == "article":
            articles.append(link)
        elif quick_type is None:
            uncertain.append(link)
        # skip "tool" type

    if not uncertain:
        return articles[:MAX_ARTICLES]

    # Ask small LLM to classify uncertain links
    try:
        links_text = "\n".join(
            f'- [{l["text"]}]({l["url"]})' for l in uncertain
        )
        response_format = {
            "type": "json_schema",
            "json_schema": {
                "name": "link_classification",
                "strict": True,
                "schema": {
                    "type": "object",
                    "properties": {
                        "results": {
                            "type": "array",
                            "items": {
                                "type": "object",
                                "properties": {
                                    "url": {"type": "string"},
                                    "type": {
                                        "type": "string",
                                        "enum": ["article", "tool", "docs", "social", "other"],
                                    },
                                },
                                "required": ["url", "type"],
                                "additionalProperties": False,
                            },
                        },
                    },
                    "required": ["results"],
                    "additionalProperties": False,
                },
            },
        }

        result = llm.complete(
            model=model,
            messages=[
                {
                    "role": "system",
                    "content": (
                        "Classify each link as: article (blog post, paper, tutorial worth reading), "
                        "tool (software, platform, app), docs (API/SDK documentation reference), "
                        "social (social media profile), or other. Return JSON only."
                    ),
                },
                {"role": "user", "content": links_text},
            ],
            response_format=response_format,
        )
        classified = json.loads(result)
        for item in classified.get("results", []):
            if item.get("type") == "article":
                matching = [l for l in uncertain if l["url"] == item["url"]]
                articles.extend(matching)

    except Exception:
        log.warning("Link classification failed, skipping uncertain links", exc_info=True)

    return articles[:MAX_ARTICLES]


def fetch_article(url: str) -> str | None:
    """Fetch article text content. Returns None on error."""
    try:
        resp = requests.get(
            url,
            timeout=FETCH_TIMEOUT,
            headers={"User-Agent": "AI-devs-lesson-processor/1.0"},
        )
        resp.raise_for_status()
        text = resp.text

        # Strip HTML tags for a rough text extraction
        text = re.sub(r"<script[^>]*>.*?</script>", "", text, flags=re.DOTALL | re.IGNORECASE)
        text = re.sub(r"<style[^>]*>.*?</style>", "", text, flags=re.DOTALL | re.IGNORECASE)
        text = re.sub(r"<[^>]+>", " ", text)
        text = re.sub(r"\s+", " ", text).strip()

        if len(text) > MAX_ARTICLE_CHARS:
            text = text[:MAX_ARTICLE_CHARS] + "..."

        log.info("Fetched article: %s (%d chars)", url, len(text))
        return text

    except Exception:
        log.warning("Failed to fetch article: %s", url, exc_info=True)
        return None


def fetch_relevant_articles(
    links: list[dict[str, str]],
    llm: LLMClient,
    model: str,
) -> list[dict[str, str]]:
    """Classify links and fetch article content. Returns list of {url, text, content}."""
    if not links:
        return []

    article_links = classify_links(links, llm, model)
    if not article_links:
        log.info("No article links to fetch")
        return []

    log.info("Fetching %d article(s)", len(article_links))
    results = []
    for link in article_links:
        content = fetch_article(link["url"])
        if content:
            results.append({
                "text": link["text"],
                "url": link["url"],
                "content": content,
            })

    return results
