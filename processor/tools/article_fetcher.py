"""
Article fetcher — optionally fetches content from article links.

Classifies links via small LLM, then fetches relevant articles.
Errors are logged but never block the pipeline.

Caching:
  - Link classifications and fetched article content are cached in
    state/article_cache.json, keyed by URL.
  - TTL is configurable (default 7 days).
  - Cache avoids redundant LLM calls and HTTP fetches.
"""
from __future__ import annotations

import json
import logging
import time
import re
from pathlib import Path
from typing import Any

import requests

from processor.llm_client import LLMClient
from processor.tools.egress_policy import EgressPolicyError, safe_get, validate_outbound_url

log = logging.getLogger(__name__)

MAX_ARTICLES = 3
FETCH_TIMEOUT = 10
MAX_ARTICLE_CHARS = 2000
CACHE_TTL_SECONDS = 7 * 24 * 3600  # 7 days


# ── Cache helpers ────────────────────────────────────────────────────────────

def _get_cache_path() -> Path:
    """Return the path to the article cache file."""
    base = Path(__file__).resolve().parent.parent.parent
    return base / "state" / "article_cache.json"


def _load_cache() -> dict[str, Any]:
    """Load the article cache from disk."""
    path = _get_cache_path()
    if not path.exists():
        return {}
    try:
        return json.loads(path.read_text(encoding="utf-8"))
    except (json.JSONDecodeError, OSError):
        log.warning("Corrupt article cache, starting fresh")
        return {}


def _save_cache(cache: dict[str, Any]) -> None:
    """Atomically save the article cache."""
    path = _get_cache_path()
    path.parent.mkdir(parents=True, exist_ok=True)
    tmp = path.with_suffix(".json.tmp")
    tmp.write_text(json.dumps(cache, ensure_ascii=False, indent=2), encoding="utf-8")
    tmp.replace(path)


def _cache_get(cache: dict[str, Any], key: str) -> Any | None:
    """Get a non-expired entry from cache."""
    entry = cache.get(key)
    if entry is None:
        return None
    if time.time() - entry.get("cached_at", 0) > CACHE_TTL_SECONDS:
        return None
    return entry.get("value")


def _cache_set(cache: dict[str, Any], key: str, value: Any) -> None:
    """Set a cache entry with current timestamp."""
    cache[key] = {"value": value, "cached_at": time.time()}

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
    cache = _load_cache()
    articles = []
    uncertain = []

    for link in links:
        # Check cache first
        cached_type = _cache_get(cache, f"classify:{link['url']}")
        if cached_type is not None:
            if cached_type == "article":
                articles.append(link)
            continue

        quick_type = _quick_classify(link["url"])
        if quick_type == "article":
            _cache_set(cache, f"classify:{link['url']}", "article")
            articles.append(link)
        elif quick_type == "tool":
            _cache_set(cache, f"classify:{link['url']}", "tool")
        else:
            uncertain.append(link)
        # skip "tool" type

    if not uncertain:
        _save_cache(cache)
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
            # Cache all classifications
            _cache_set(cache, f"classify:{item['url']}", item.get("type", "other"))

    except Exception:
        log.warning("Link classification failed, skipping uncertain links", exc_info=True)

    _save_cache(cache)
    return articles[:MAX_ARTICLES]


def fetch_article(url: str) -> str | None:
    """Fetch article text content. Returns cached content or None on error."""
    cache = _load_cache()
    cached = _cache_get(cache, f"article:{url}")
    if cached is not None:
        log.info("Article cache hit: %s", url[:60])
        return cached

    # Validate the URL against the egress policy before making any request.
    try:
        validate_outbound_url(url)
    except EgressPolicyError as exc:
        log.warning("Article fetch blocked by egress policy: %s — %s", url[:80], exc)
        return None

    try:
        resp = safe_get(url, timeout=FETCH_TIMEOUT)
        text = resp.text

        # Strip HTML tags for a rough text extraction
        text = re.sub(r"<script[^>]*>.*?</script>", "", text, flags=re.DOTALL | re.IGNORECASE)
        text = re.sub(r"<style[^>]*>.*?</style>", "", text, flags=re.DOTALL | re.IGNORECASE)
        text = re.sub(r"<[^>]+>", " ", text)
        text = re.sub(r"\s+", " ", text).strip()

        if len(text) > MAX_ARTICLE_CHARS:
            text = text[:MAX_ARTICLE_CHARS] + "..."

        log.info("Fetched article: %s (%d chars)", url, len(text))

        # Cache the fetched content
        _cache_set(cache, f"article:{url}", text)
        _save_cache(cache)

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
