"""
Langfuse telemetry — single integration point for LLM usage, cost, and pipeline tracing.

Initialization requires LANGFUSE_SECRET_KEY, LANGFUSE_PUBLIC_KEY in .env.
When keys are missing, the langfuse package is absent, or tracing is disabled,
all operations are silent no-ops — the pipeline runs unchanged.
"""
from __future__ import annotations

import logging
from contextlib import contextmanager
from typing import Any, Generator

log = logging.getLogger(__name__)

_langfuse: Any = None
_enabled: bool = False
_current_trace: Any = None


def init(
    *,
    secret_key: str,
    public_key: str,
    host: str = "https://cloud.langfuse.com",
) -> bool:
    """Initialize Langfuse client. Returns True if successful.

    Idempotent — subsequent calls with the same host are silently skipped so that
    entry points calling ``init_tracing()`` explicitly and legacy paths that call
    ``load_config()`` (which also inits tracing as a side-effect) do not cause
    double initialization.
    """
    global _langfuse, _enabled
    if _langfuse is not None:
        log.debug("Langfuse already initialized — skipping duplicate init")
        return True
    try:
        from langfuse import Langfuse  # type: ignore[import-untyped]

        _langfuse = Langfuse(
            secret_key=secret_key,
            public_key=public_key,
            host=host,
        )
        _enabled = True
        log.info("Langfuse tracing initialized (host=%s)", host)
        return True
    except ImportError:
        log.info("langfuse package not installed — tracing disabled")
        return False
    except Exception:
        log.warning("Failed to initialize Langfuse", exc_info=True)
        return False


def is_active() -> bool:
    """Check whether Langfuse tracing is currently active."""
    return _enabled and _langfuse is not None


def init_tracing(settings: Any, creds: Any) -> None:
    """Initialize Langfuse tracing from AppSettings + LLMCredentials.

    Call this explicitly at the top of each entry-point main() / lifespan instead
    of relying on the side-effect inside ``load_config()``.

    Accepts any objects that have the required attributes so this module avoids
    a circular import on ``processor.settings``.
    """
    if not getattr(settings, "enable_tracing", False):
        return

    import os
    from pathlib import Path as _Path
    from dotenv import dotenv_values as _dv

    base_dir = getattr(settings, "base_dir", None) or _Path(__file__).resolve().parent.parent
    env_path = base_dir / ".env"
    env = _dv(env_path) if env_path.exists() else {}

    def _get(key: str, default: str = "") -> str:
        val = env.get(key)
        if val is None:
            val = os.environ.get(key, default)
        return str(val).strip()

    secret_key = _get("LANGFUSE_SECRET_KEY")
    public_key = _get("LANGFUSE_PUBLIC_KEY")
    host = _get("LANGFUSE_HOST", "http://localhost:3100")

    if secret_key and public_key:
        init(secret_key=secret_key, public_key=public_key, host=host)
    else:
        log.warning("ENABLE_TRACING=true but LANGFUSE_SECRET_KEY/LANGFUSE_PUBLIC_KEY missing")


@contextmanager
def trace(
    name: str,
    *,
    input_data: Any = None,
    metadata: dict[str, Any] | None = None,
    tags: list[str] | None = None,
) -> Generator[Any, None, None]:
    """Context manager for a pipeline trace.

    Yields the Langfuse trace object (or None when tracing is disabled).
    Automatically flushes on exit.
    """
    global _current_trace
    if not is_active():
        yield None
        return

    t = _langfuse.trace(name=name, input=input_data, metadata=metadata, tags=tags)
    prev = _current_trace
    _current_trace = t
    try:
        yield t
    except Exception as exc:
        t.update(metadata={"error": str(exc)})
        raise
    finally:
        _current_trace = prev
        _langfuse.flush()


@contextmanager
def span(
    name: str,
    *,
    input_data: Any = None,
    metadata: dict[str, Any] | None = None,
) -> Generator[Any, None, None]:
    """Context manager for a pipeline step span within the current trace."""
    if not is_active() or _current_trace is None:
        yield None
        return

    s = _current_trace.span(name=name, input=input_data, metadata=metadata)
    try:
        yield s
    except Exception as exc:
        s.update(level="ERROR", status_message=str(exc))
        raise
    finally:
        s.end()


def start_generation(
    *,
    name: str,
    model: str,
    input_data: Any = None,
    metadata: dict[str, Any] | None = None,
) -> _GenerationHandle:
    """Create a Langfuse generation observation within the current trace.

    Returns a handle with `end()` and `error()` — safe to call even when
    tracing is disabled (no-ops).
    """
    if not is_active() or _current_trace is None:
        return _GenerationHandle(None)

    gen = _current_trace.generation(
        name=name,
        model=model,
        input=input_data,
        metadata=metadata,
    )
    return _GenerationHandle(gen)


class _GenerationHandle:
    """Thin wrapper around a Langfuse generation with safe no-op fallback."""

    __slots__ = ("_gen",)

    def __init__(self, gen: Any) -> None:
        self._gen = gen

    def end(
        self,
        *,
        output: Any = None,
        usage: dict[str, int] | None = None,
    ) -> None:
        if self._gen is None:
            return
        kwargs: dict[str, Any] = {}
        if output is not None:
            kwargs["output"] = output
        if usage:
            kwargs["usage"] = {
                "input": usage.get("prompt_tokens", usage.get("input", 0)),
                "output": usage.get("completion_tokens", usage.get("output", 0)),
                "total": usage.get("total_tokens", usage.get("total", 0)),
            }
        self._gen.end(**kwargs)

    def error(self, message: str) -> None:
        if self._gen is None:
            return
        self._gen.update(level="ERROR", status_message=message)
        self._gen.end()


def score(
    *,
    name: str,
    value: float,
    comment: str = "",
) -> None:
    """Record a score on the current trace. No-op when tracing is disabled."""
    if not is_active() or _current_trace is None:
        return
    try:
        _current_trace.score(name=name, value=value, comment=comment)
    except Exception:
        log.debug("Failed to record score '%s'", name, exc_info=True)


def flush() -> None:
    """Flush pending events to Langfuse."""
    if _langfuse:
        _langfuse.flush()


def shutdown() -> None:
    """Shutdown Langfuse client and release resources."""
    global _langfuse, _enabled, _current_trace
    if _langfuse:
        try:
            _langfuse.flush()
            _langfuse.shutdown()
        except Exception:
            log.debug("Error during Langfuse shutdown", exc_info=True)
    _langfuse = None
    _current_trace = None
    _enabled = False
