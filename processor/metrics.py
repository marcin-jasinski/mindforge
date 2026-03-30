"""
Operational metrics — thread-safe in-process counters.

Tracks three categories as requested in the P2 review:
  egress_blocked      — outbound URL violations rejected by egress_policy
  pipeline_skipped    — lesson files skipped because already processed/claimed
  llm_tokens_*       — cumulative prompt/completion token spend per model

Usage::

    from processor import metrics

    metrics.increment("egress_blocked")
    metrics.record_llm_usage("openai/gpt-4o", {"prompt_tokens": 120, ...})
    data = metrics.snapshot()   # returns a plain dict for serialisation
"""
from __future__ import annotations

import threading
from collections import defaultdict
from typing import Any

_lock = threading.Lock()

_counters: dict[str, int] = {}

# keyed by model name → {prompt_tokens, completion_tokens, total_tokens}
_llm_usage: dict[str, dict[str, int]] = defaultdict(
    lambda: {"prompt_tokens": 0, "completion_tokens": 0, "total_tokens": 0}
)


def increment(name: str, value: int = 1) -> None:
    """Increment named counter by *value* (thread-safe)."""
    with _lock:
        _counters[name] = _counters.get(name, 0) + value


def record_llm_usage(model: str, usage: dict[str, Any] | None) -> None:
    """Accumulate token usage from an OpenAI-style ``usage`` dict."""
    if not usage:
        return
    prompt = int(usage.get("prompt_tokens", 0))
    completion = int(usage.get("completion_tokens", 0))
    total = int(usage.get("total_tokens", 0)) or prompt + completion
    with _lock:
        entry = _llm_usage[model]
        entry["prompt_tokens"] += prompt
        entry["completion_tokens"] += completion
        entry["total_tokens"] += total


def snapshot() -> dict[str, Any]:
    """Return a serialisable snapshot of all current metric values."""
    with _lock:
        return {
            "counters": dict(_counters),
            "llm_usage_by_model": {k: dict(v) for k, v in _llm_usage.items()},
        }


def reset() -> None:
    """Reset all counters (intended for use in tests only)."""
    with _lock:
        _counters.clear()
        _llm_usage.clear()
